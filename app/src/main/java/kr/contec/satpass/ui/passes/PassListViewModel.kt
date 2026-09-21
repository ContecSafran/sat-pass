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
import kr.contec.satpass.data.local.entity.ObserverSiteEntity
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.location.LocationProvider
import kr.contec.satpass.data.repository.ObserverSiteRepository
import kr.contec.satpass.data.repository.PassAlarmRepository
import kr.contec.satpass.data.repository.SatelliteRepository
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.data.repository.TleSyncResult
import kr.contec.satpass.data.settings.SatPassSettings
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.domain.usecase.PredictPassesUseCase
import kr.contec.satpass.ui.appContainer
import kr.contec.satpass.ui.format.PassFormat
import java.time.Duration
import java.time.Instant

/** 패스를 식별하는 키. 알림 등록 여부를 대조할 때 쓴다. */
fun passKey(noradId: Int, aosMillis: Long): String = "$noradId@$aosMillis"

/** 패스 계산에 쓰인 관측 위치의 출처 */
enum class ObserverSource {
    /** GPS 로 얻은 현재 위치 */
    CURRENT_LOCATION,

    /** 사용자가 저장해 둔 관측 지점 */
    SAVED_SITE,

    /** 위치를 얻지 못해 기본 좌표로 계산 중 */
    FALLBACK,
}

data class PassListUiState(
    val passes: List<SatellitePass> = emptyList(),
    val problems: List<PredictPassesUseCase.SatelliteProblem> = emptyList(),
    /** 최초 계산 중(화면에 아직 아무것도 없을 때) */
    val isLoading: Boolean = true,
    /** 당겨서 새로고침 진행 중 */
    val isRefreshing: Boolean = false,
    val observer: ObserverLocation = ObserverLocation.DEFAULT,
    val observerSource: ObserverSource = ObserverSource.FALLBACK,
    /** 화면에 보여 줄 관측 위치 이름 */
    val observerLabel: String = "기본 위치",
    val locationPermissionGranted: Boolean = false,
    /** 저장해 둔 관측 지점들 (빠른 전환 메뉴에 쓴다) */
    val sites: List<ObserverSiteEntity> = emptyList(),
    val activeSiteId: Long = ObserverSiteEntity.CURRENT_LOCATION_ID,
    val registeredSatelliteCount: Int = 0,
    val selectedSatelliteCount: Int = 0,
    val lastTleFetchedAt: Instant? = null,
    val tleCachedCount: Int = 0,
    val predictionDays: Int = SatPassSettings.DEFAULT_PREDICTION_DAYS,
    /** 알림이 걸린 패스 키 (noradId + AOS millis) */
    val alarmedPassKeys: Set<String> = emptySet(),
    val notificationLeadMinutes: Int = SatPassSettings.DEFAULT_NOTIFICATION_LEAD_MINUTES,
    /** 알림 선택 모드 — 상단 알림 버튼으로 켠다. */
    val alarmPickerMode: Boolean = false,
) {
    /** GPS 위치를 못 얻어 기본 좌표로 계산 중인지 */
    val usingFallbackLocation: Boolean get() = observerSource == ObserverSource.FALLBACK
}

class PassListViewModel(
    private val satelliteRepository: SatelliteRepository,
    private val tleRepository: TleRepository,
    private val settingsRepository: SettingsRepository,
    private val observerSiteRepository: ObserverSiteRepository,
    private val passAlarmRepository: PassAlarmRepository,
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
        observeAlarms()
    }

    /** 걸어 둔 알림 목록을 화면 상태에 반영한다. */
    private fun observeAlarms() {
        viewModelScope.launch {
            passAlarmRepository.all.collect { alarms ->
                _uiState.update { state ->
                    state.copy(
                        alarmedPassKeys = alarms.map { passKey(it.noradId, it.aosMillis) }.toSet()
                    )
                }
            }
        }
    }

    /**
     * 선택된 위성 / 설정 / 저장된 지점이 바뀔 때마다 관측 위치를 다시 정하고 패스를 계산한다.
     */
    private fun observeInputs() {
        viewModelScope.launch {
            combine(
                satelliteRepository.all,
                settingsRepository.settings,
                observerSiteRepository.all,
            ) { all, newSettings, sites -> Triple(all, newSettings, sites) }
                .distinctUntilChanged()
                .collect { (all, newSettings, sites) ->
                    selectedSatellites = all.filter { it.selected }
                    settings = newSettings
                    _uiState.update {
                        it.copy(
                            registeredSatelliteCount = all.size,
                            selectedSatelliteCount = selectedSatellites.size,
                            predictionDays = newSettings.predictionDays,
                            sites = sites,
                            activeSiteId = newSettings.activeSiteId,
                            notificationLeadMinutes = newSettings.notificationLeadMinutes,
                        )
                    }
                    resolveObserverAndRecalculate()
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
        viewModelScope.launch { resolveObserverAndRecalculate() }
    }

    /** 관측 위치를 바꾼다. [siteId] 가 0 이면 GPS 현재 위치를 쓴다. */
    fun selectSite(siteId: Long) {
        viewModelScope.launch { settingsRepository.setActiveSiteId(siteId) }
    }

    /**
     * 설정에 지정된 관측 지점(없으면 GPS)을 읽어 [PassListUiState.observer] 를 정하고
     * 패스를 다시 계산한다.
     */
    private suspend fun resolveObserverAndRecalculate() {
        val granted = locationProvider.hasPermission()
        val activeSiteId = settings.activeSiteId

        // 저장된 지점이 지정돼 있으면 그것을 쓰고, 그 지점이 삭제됐다면 GPS 로 돌아간다.
        val site = if (activeSiteId != ObserverSiteEntity.CURRENT_LOCATION_ID) {
            observerSiteRepository.getById(activeSiteId)
        } else {
            null
        }

        val resolved: Triple<ObserverLocation, ObserverSource, String> = when {
            site != null -> Triple(site.toObserverLocation(), ObserverSource.SAVED_SITE, site.name)

            else -> {
                val current = if (granted) locationProvider.getCurrentLocation() else null
                if (current != null) {
                    Triple(current, ObserverSource.CURRENT_LOCATION, "현재 위치")
                } else {
                    Triple(ObserverLocation.DEFAULT, ObserverSource.FALLBACK, "기본 위치")
                }
            }
        }

        _uiState.update {
            it.copy(
                locationPermissionGranted = granted,
                observer = resolved.first,
                observerSource = resolved.second,
                observerLabel = resolved.third,
            )
        }
        recalculate()
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
                resolveObserverAndRecalculate()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 상단 알림 버튼 — 알림을 걸 패스를 고르는 모드를 켜고 끈다. */
    fun toggleAlarmPickerMode() {
        _uiState.update { it.copy(alarmPickerMode = !it.alarmPickerMode) }
    }

    /**
     * 패스 알림을 켜거나 끈다.
     * 오른쪽 스와이프, 알림 선택 모드에서의 탭, 상세 시트의 버튼이 모두 이 함수를 쓴다.
     */
    fun togglePassAlarm(pass: SatellitePass) {
        viewModelScope.launch {
            when (val result = passAlarmRepository.toggle(pass)) {
                is PassAlarmRepository.ToggleResult.Enabled -> _messages.send(
                    "${pass.satelliteName} 알림 등록 — ${PassFormat.dateTime(result.triggerAt)}" +
                        " (${result.leadMinutes}분 전)"
                )

                PassAlarmRepository.ToggleResult.Disabled ->
                    _messages.send("${pass.satelliteName} 알림 해제")

                is PassAlarmRepository.ToggleResult.TooLate -> _messages.send(
                    "이미 ${result.leadMinutes}분 전 시각이 지나 알림을 걸 수 없습니다."
                )
            }
        }
    }

    fun clearAllAlarms() {
        viewModelScope.launch {
            passAlarmRepository.removeAll()
            _messages.send("걸어 둔 알림을 모두 해제했습니다.")
        }
    }

    /** 정확 알람 권한을 새로 받은 뒤 다시 등록한다. */
    fun rescheduleAlarms() {
        viewModelScope.launch { passAlarmRepository.rescheduleAll() }
    }

    fun canScheduleExactAlarms(): Boolean = passAlarmRepository.canScheduleExactAlarms()

    /** 상세 시트에서 보여줄 궤적 계산 */
    suspend fun loadTrack(
        pass: SatellitePass,
        observer: ObserverLocation,
    ): List<GetPassTrackUseCase.TrackPoint> = getPassTrack(pass, observer)

    /** 패스 1건의 초 단위 궤적 (상세 시트의 표에 쓴다) */
    suspend fun loadTrackTable(
        pass: SatellitePass,
        observer: ObserverLocation,
        stepSeconds: Int,
    ): List<GetPassTrackUseCase.TrackPoint> =
        getPassTrack(pass, observer, stepSeconds = stepSeconds, limitSamples = false)

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
                    observerSiteRepository = container.observerSiteRepository,
                    passAlarmRepository = container.passAlarmRepository,
                    locationProvider = container.locationProvider,
                    predictPasses = container.predictPassesUseCase,
                    getPassTrack = container.getPassTrackUseCase,
                )
            }
        }
    }
}
