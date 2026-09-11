package kr.contec.satpass.ui.passes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.location.LocationProvider
import kr.contec.satpass.data.repository.SatelliteRepository
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.data.repository.TleSyncResult
import kr.contec.satpass.data.settings.SatPassSettings
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.ui.appContainer
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.domain.usecase.PredictPassesUseCase
import kr.contec.satpass.ui.format.PassFormat
import java.time.Duration
import java.time.Instant

data class PassListUiState(
    val passes: List<SatellitePass> = emptyList(),
    val problems: List<PredictPassesUseCase.SatelliteProblem> = emptyList(),
    /** 최초 계산 중(화면에 아직 아무것도 없을 때) */
    val isLoading: Boolean = true,
    /** 당겨서 새로고침 진행 중 */
    val isRefreshing: Boolean = false,
    val observer: ObserverLocation = ObserverLocation.DEFAULT,
    val usingDefaultLocation: Boolean = true,
    val locationPermissionGranted: Boolean = false,
    val registeredSatelliteCount: Int = 0,
    val selectedSatelliteCount: Int = 0,
    val lastTleFetchedAt: Instant? = null,
    val tleCachedCount: Int = 0,
    val predictionDays: Int = SatPassSettings.DEFAULT_PREDICTION_DAYS,
)

class PassListViewModel(
    private val satelliteRepository: SatelliteRepository,
    private val tleRepository: TleRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val predictPasses: PredictPassesUseCase,
    private val getPassTrack: GetPassTrackUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassListUiState())
    val uiState: StateFlow<PassListUiState> = _uiState.asStateFlow()

    /** 스낵바로 한 번만 보여줄 메시지 */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var selectedSatellites: List<SatelliteEntity> = emptyList()
    private var settings: SatPassSettings = SatPassSettings.DEFAULT

    init {
        observeInputs()
        observeTleCacheState()
        refreshLocation()
    }

    /**
     * 선택된 위성 / 설정이 바뀔 때마다 패스를 다시 계산한다.
     */
    private fun observeInputs() {
        viewModelScope.launch {
            combine(
                satelliteRepository.all,
                settingsRepository.settings,
            ) { all, settings -> all to settings }
                .distinctUntilChanged()
                .collect { (all, newSettings) ->
                    selectedSatellites = all.filter { it.selected }
                    settings = newSettings
                    _uiState.update {
                        it.copy(
                            registeredSatelliteCount = all.size,
                            selectedSatelliteCount = selectedSatellites.size,
                            predictionDays = newSettings.predictionDays,
                        )
                    }
                    recalculate()
                }
        }
    }

    private fun observeTleCacheState() {
        viewModelScope.launch {
            combine(
                tleRepository.lastFetchedAt,
                tleRepository.cachedCount,
            ) { fetchedAt, count -> fetchedAt to count }
                .collect { (fetchedAt, count) ->
                    _uiState.update {
                        it.copy(
                            lastTleFetchedAt = fetchedAt?.let(Instant::ofEpochMilli),
                            tleCachedCount = count,
                        )
                    }
                }
        }
    }

    /** 위치 권한을 받은 직후 등에 호출 */
    fun refreshLocation() {
        viewModelScope.launch {
            val granted = locationProvider.hasPermission()
            val location = if (granted) locationProvider.getCurrentLocation() else null

            _uiState.update {
                it.copy(
                    locationPermissionGranted = granted,
                    observer = location ?: ObserverLocation.DEFAULT,
                    usingDefaultLocation = location == null,
                )
            }
            recalculate()
        }
    }

    /**
     * 당겨서 새로고침.
     *
     * 최소 갱신 간격(설정값)이 지나지 않았으면 네트워크 요청 없이 안내만 하고,
     * 패스 계산은 캐시로 다시 수행한다.
     */
    fun onPullToRefresh() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                when (val result = tleRepository.sync()) {
                    is TleSyncResult.Updated -> {
                        val via = if (result.usedFallback) " (대체 주소 사용)" else ""
                        _messages.send("TLE ${result.count}건 갱신 완료$via")
                    }

                    is TleSyncResult.Skipped -> {
                        val remaining = Duration.between(Instant.now(), result.nextAvailableAt)
                        _messages.send(
                            "최근에 갱신했습니다. ${PassFormat.duration(remaining)} 후에 다시 받을 수 있습니다."
                        )
                    }

                    is TleSyncResult.Failed -> {
                        val suffix = if (result.cachedCount > 0) " 기존 캐시로 계산합니다." else ""
                        _messages.send("TLE 갱신 실패: ${result.message}$suffix")
                    }
                }
                refreshLocation()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 설정에서 TLE 주소를 바꾼 뒤처럼 즉시 받아와야 할 때 */
    fun forceSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                when (val result = tleRepository.sync(force = true)) {
                    is TleSyncResult.Updated ->
                        _messages.send("TLE ${result.count}건 갱신 완료")

                    is TleSyncResult.Failed ->
                        _messages.send("TLE 갱신 실패: ${result.message}")

                    is TleSyncResult.Skipped -> Unit // force 라서 발생하지 않음
                }
                recalculate()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 상세 시트에서 보여줄 궤적 계산 */
    suspend fun loadTrack(
        pass: SatellitePass,
        observer: ObserverLocation,
    ): List<GetPassTrackUseCase.TrackPoint> = getPassTrack(pass, observer)

    private suspend fun recalculate() {
        val state = _uiState.value
        val result = predictPasses(
            satellites = selectedSatellites,
            observer = state.observer,
            days = settings.predictionDays,
            minElevationDeg = settings.minElevationDeg,
        )
        _uiState.update {
            it.copy(
                passes = result.passes,
                problems = result.problems,
                isLoading = false,
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                PassListViewModel(
                    satelliteRepository = container.satelliteRepository,
                    tleRepository = container.tleRepository,
                    settingsRepository = container.settingsRepository,
                    locationProvider = container.locationProvider,
                    predictPasses = container.predictPassesUseCase,
                    getPassTrack = container.getPassTrackUseCase,
                )
            }
        }
    }
}
