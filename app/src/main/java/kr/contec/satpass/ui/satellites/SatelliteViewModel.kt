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
import kr.contec.satpass.ui.appContainer

data class SatelliteUiState(
    /** 등록된 관심 위성 */
    val registered: List<SatelliteEntity> = emptyList(),
    val query: String = "",
    val searchResults: List<TleEntity> = emptyList(),
    val isSearching: Boolean = false,
    /** TLE 캐시 건수. 0 이면 검색이 불가하므로 안내가 필요하다. */
    val tleCachedCount: Int = 0,
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
