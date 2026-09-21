package kr.contec.satpass.ui.satellites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.data.repository.SatelliteRepository
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.domain.TleValidator
import kr.contec.satpass.ui.appContainer

/**
 * 위성 이름을 눌렀을 때 보여 줄 TLE 상세 상태.
 *
 * @property satelliteName 등록 당시의 위성 이름 (헤더에 바로 띄우기 위해 들고 있는다)
 */
sealed interface TleDetailState {
    val satelliteName: String
    val noradId: Int

    /** 캐시에서 읽는 중 */
    data class Loading(
        override val satelliteName: String,
        override val noradId: Int,
    ) : TleDetailState

    data class Loaded(
        override val satelliteName: String,
        override val noradId: Int,
        val tle: TleEntity,
    ) : TleDetailState

    /** 아직 TLE 를 받지 않았거나, 갱신 과정에서 이 위성이 카탈로그에서 빠진 경우 */
    data class Missing(
        override val satelliteName: String,
        override val noradId: Int,
    ) : TleDetailState
}

data class SatelliteUiState(
    /** 등록된 관심 위성 */
    val registered: List<SatelliteEntity> = emptyList(),
    val query: String = "",
    val searchResults: List<TleEntity> = emptyList(),
    val isSearching: Boolean = false,
    /** TLE 캐시 건수. 0 이면 검색이 불가하므로 안내가 필요하다. */
    val tleCachedCount: Int = 0,
    /** null 이 아니면 TLE 상세 시트를 띄운다. */
    val tleDetail: TleDetailState? = null,
) {
    val selectedCount: Int get() = registered.count { it.selected }

    /** 이미 등록된 NORAD ID 모음 (검색 결과에서 중복 추가를 막는 데 쓴다) */
    val registeredIds: Set<Int> get() = registered.map { it.noradId }.toSet()
}

class SatelliteViewModel(
    private val satelliteRepository: SatelliteRepository,
    private val tleRepository: TleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SatelliteUiState())
    val uiState: StateFlow<SatelliteUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            satelliteRepository.all.collect { list ->
                _uiState.update { it.copy(registered = list) }
            }
        }
        viewModelScope.launch {
            tleRepository.cachedCount.collect { count ->
                _uiState.update { it.copy(tleCachedCount = count) }
            }
        }
        observeQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(250)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                        return@collect
                    }
                    _uiState.update { it.copy(isSearching = true) }
                    val results = tleRepository.search(query)
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun clearQuery() = onQueryChange("")

    fun add(tle: TleEntity) {
        viewModelScope.launch {
            satelliteRepository.add(tle.noradId, tle.satelliteName)
            _messages.send("${tle.satelliteName} 추가")
        }
    }

    fun remove(satellite: SatelliteEntity) {
        viewModelScope.launch {
            satelliteRepository.remove(satellite.noradId)
            _messages.send("${satellite.name} 삭제")
        }
    }

    fun setSelected(satellite: SatelliteEntity, selected: Boolean) {
        viewModelScope.launch {
            satelliteRepository.setSelected(satellite.noradId, selected)
        }
    }

    fun setAllSelected(selected: Boolean) {
        viewModelScope.launch { satelliteRepository.setAllSelected(selected) }
    }

    /** 등록된 위성 이름을 눌렀을 때 — 캐시에서 TLE 를 읽어 상세 시트를 띄운다. */
    fun showTle(satellite: SatelliteEntity) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(tleDetail = TleDetailState.Loading(satellite.name, satellite.noradId))
            }
            val tle = tleRepository.getByNoradId(satellite.noradId)
            _uiState.update {
                // 그 사이 사용자가 시트를 닫았거나 다른 위성을 눌렀으면 결과를 버린다.
                if (it.tleDetail?.noradId != satellite.noradId) return@update it
                it.copy(
                    tleDetail = if (tle == null) {
                        TleDetailState.Missing(satellite.name, satellite.noradId)
                    } else {
                        TleDetailState.Loaded(satellite.name, satellite.noradId, tle)
                    }
                )
            }
        }
    }

    fun dismissTle() {
        _uiState.update { it.copy(tleDetail = null) }
    }

    /**
     * 직접 입력한 TLE 저장.
     *
     * 다이얼로그에서 이미 검사를 통과한 입력만 넘어오지만, 저장 직전에 한 번 더 검사한다.
     * 아직 등록되지 않은 위성이면 관심 목록에도 함께 추가한다.
     */
    fun saveManualTle(input: String, fallbackName: String) {
        viewModelScope.launch {
            when (val result = tleRepository.saveManualTle(input, fallbackName)) {
                is TleValidator.Result.Invalid ->
                    _messages.send("TLE 를 저장하지 못했습니다: ${result.errors.first()}")

                is TleValidator.Result.Valid -> {
                    val alreadyRegistered = result.noradId in _uiState.value.registeredIds
                    if (!alreadyRegistered) {
                        satelliteRepository.add(result.noradId, result.satelliteName)
                    }
                    // 상세 시트가 이 위성을 보고 있으면 방금 저장한 값으로 갱신한다.
                    refreshOpenTleDetail(result.noradId)
                    _messages.send(
                        if (alreadyRegistered) {
                            "${result.satelliteName} 의 TLE 를 직접 입력한 값으로 바꿨습니다."
                        } else {
                            "${result.satelliteName} 을(를) 추가하고 TLE 를 저장했습니다."
                        }
                    )
                }
            }
        }
    }

    /**
     * 열려 있는 상세 시트가 [noradId] 를 보고 있으면 DB 에서 다시 읽어 갱신한다.
     * (수정 직후에도 화면에 옛 값이 남지 않게 한다)
     */
    private suspend fun refreshOpenTleDetail(noradId: Int) {
        val open = _uiState.value.tleDetail ?: return
        if (open.noradId != noradId) return

        val tle = tleRepository.getByNoradId(noradId)
        _uiState.update {
            if (it.tleDetail?.noradId != noradId) return@update it
            it.copy(
                tleDetail = if (tle == null) {
                    TleDetailState.Missing(open.satelliteName, noradId)
                } else {
                    TleDetailState.Loaded(tle.satelliteName, noradId, tle)
                }
            )
        }
    }

    /** 수동 TLE 를 지운다. 이후 갱신하면 CelesTrak 값이 다시 채워진다. */
    fun deleteManualTle(noradId: Int, satelliteName: String) {
        viewModelScope.launch {
            tleRepository.deleteManualTle(noradId)
            // 시트는 닫지 않고, 카탈로그 값이 있으면 그것으로 되돌아간 모습을 보여 준다.
            refreshOpenTleDetail(noradId)
            _messages.send("$satelliteName 의 수동 TLE 를 삭제했습니다.")
        }
    }

    /** 클립보드 복사 결과 안내 (Android 13 미만에서는 시스템 안내가 없어 직접 알린다) */
    fun notifyTleCopied() {
        viewModelScope.launch { _messages.send("TLE 를 복사했습니다.") }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SatelliteViewModel(
                    satelliteRepository = container.satelliteRepository,
                    tleRepository = container.tleRepository,
                )
            }
        }
    }
}
