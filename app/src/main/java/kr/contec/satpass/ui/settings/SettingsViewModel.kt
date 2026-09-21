package kr.contec.satpass.ui.settings

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.contec.satpass.data.local.entity.ObserverSiteEntity
import kr.contec.satpass.data.location.LocationProvider
import kr.contec.satpass.data.repository.ObserverSiteRepository
import kr.contec.satpass.data.repository.PassAlarmRepository
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.data.repository.TleSyncResult
import kr.contec.satpass.data.settings.SatPassSettings
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.ui.appContainer
import java.time.Instant

data class SettingsUiState(
    val settings: SatPassSettings = SatPassSettings.DEFAULT,
    val lastTleFetchedAt: Instant? = null,
    val tleCachedCount: Int = 0,
    val isSyncing: Boolean = false,
    /** 저장해 둔 관측 지점 */
    val sites: List<ObserverSiteEntity> = emptyList(),
    /** 걸어 둔 패스 알림 건수 */
    val scheduledAlarmCount: Int = 0,
    /** 정확 알람을 걸 수 있는지 (Android 12+ 는 사용자가 허용해야 한다) */
    val canScheduleExactAlarms: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tleRepository: TleRepository,
    private val observerSiteRepository: ObserverSiteRepository,
    private val passAlarmRepository: PassAlarmRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                tleRepository.lastFetchedAt,
                tleRepository.cachedCount,
            ) { settings, fetchedAt, count -> Triple(settings, fetchedAt, count) }
                .collect { (settings, fetchedAt, count) ->
                    _uiState.update {
                        it.copy(
                            settings = settings,
                            lastTleFetchedAt = fetchedAt?.let(Instant::ofEpochMilli),
                            tleCachedCount = count,
                        )
                    }
                }
        }
        viewModelScope.launch {
            observerSiteRepository.all.collect { sites ->
                _uiState.update { it.copy(sites = sites) }
            }
        }
        viewModelScope.launch {
            passAlarmRepository.all.collect { alarms ->
                _uiState.update {
                    it.copy(
                        scheduledAlarmCount = alarms.size,
                        canScheduleExactAlarms = passAlarmRepository.canScheduleExactAlarms(),
                    )
                }
            }
        }
    }

    /** 관측 지점 추가. 성공하면 바로 그 지점을 사용하도록 전환한다. */
    fun addSite(name: String, latitude: Double, longitude: Double, altitudeMeters: Double) {
        viewModelScope.launch {
            val id = observerSiteRepository.add(name, latitude, longitude, altitudeMeters)
            settingsRepository.setActiveSiteId(id)
            _messages.send("'$name' 을(를) 추가하고 관측 위치로 설정했습니다.")
        }
    }

    fun updateSite(site: ObserverSiteEntity) {
        viewModelScope.launch {
            observerSiteRepository.update(site)
            _messages.send("'${site.name}' 을(를) 수정했습니다.")
        }
    }

    fun deleteSite(site: ObserverSiteEntity) {
        viewModelScope.launch {
            observerSiteRepository.remove(site.id)
            // 쓰고 있던 지점을 지웠다면 GPS 현재 위치로 되돌린다.
            if (_uiState.value.settings.activeSiteId == site.id) {
                settingsRepository.setActiveSiteId(ObserverSiteEntity.CURRENT_LOCATION_ID)
            }
            _messages.send("'${site.name}' 을(를) 삭제했습니다.")
        }
    }

    /** [siteId] 가 0 이면 GPS 현재 위치를 쓴다. */
    fun selectSite(siteId: Long) {
        viewModelScope.launch { settingsRepository.setActiveSiteId(siteId) }
    }

    /**
     * 지점 추가 다이얼로그를 현재 위치로 채우기 위한 값.
     *
     * @return 권한이 없거나 위치를 얻지 못하면 null
     */
    suspend fun currentLocationOrNull(): ObserverLocation? =
        if (locationProvider.hasPermission()) locationProvider.getCurrentLocation() else null

    fun notifyLocationUnavailable() {
        viewModelScope.launch {
            _messages.send("현재 위치를 얻지 못했습니다. 위치 권한과 GPS 를 확인해 주세요.")
        }
    }

    fun setTleUrl(url: String) {
        viewModelScope.launch { settingsRepository.setTleSourceUrl(url) }
    }

    fun setFallbackTleUrl(url: String) {
        viewModelScope.launch { settingsRepository.setFallbackTleSourceUrl(url) }
    }

    fun setMinSyncIntervalHours(hours: Int) {
        viewModelScope.launch { settingsRepository.setMinSyncIntervalHours(hours) }
    }

    fun setPredictionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setPredictionDays(days) }
    }

    fun setMinElevationDeg(deg: Double) {
        viewModelScope.launch { settingsRepository.setMinElevationDeg(deg) }
    }

    /**
     * 알림 시간을 바꾼다.
     * 이미 걸어 둔 알람도 새 시간으로 다시 맞춘다.
     */
    fun setNotificationLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setNotificationLeadMinutes(minutes)
            passAlarmRepository.applyLeadMinutes(minutes)
            _messages.send("알림 시간을 ${minutes}분 전으로 바꿨습니다.")
        }
    }

    /**
     * 정확 알람 권한 상태를 다시 읽는다.
     *
     * 이 권한은 시스템 설정에서 켜기 때문에 앱이 그 변화를 바로 알 수 없다.
     * 설정 화면으로 돌아왔을 때와 사용자가 버튼을 다시 눌렀을 때 호출한다.
     * 권한이 새로 켜졌다면, 부정확 알람으로 등록돼 있던 알람들을 정확 알람으로 다시 건다.
     */
    fun refreshExactAlarmState() {
        viewModelScope.launch {
            val canSchedule = passAlarmRepository.canScheduleExactAlarms()
            val wasBlocked = !_uiState.value.canScheduleExactAlarms

            _uiState.update { it.copy(canScheduleExactAlarms = canSchedule) }

            if (canSchedule && wasBlocked) {
                passAlarmRepository.rescheduleAll()
                if (_uiState.value.scheduledAlarmCount > 0) {
                    _messages.send("정확 알람이 허용되어 예약된 알림을 다시 등록했습니다.")
                }
            }
        }
    }

    fun clearAlarms() {
        viewModelScope.launch {
            passAlarmRepository.removeAll()
            _messages.send("걸어 둔 알림을 모두 해제했습니다.")
        }
    }

    fun resetTleUrls() {
        viewModelScope.launch {
            settingsRepository.setTleSourceUrl(SatPassSettings.DEFAULT_TLE_URL)
            settingsRepository.setFallbackTleSourceUrl(SatPassSettings.DEFAULT_FALLBACK_TLE_URL)
            _messages.send("TLE 주소를 기본값으로 되돌렸습니다.")
        }
    }

    /**
     * 설정 화면에서의 즉시 갱신.
     * 주소를 바꾼 직후에도 받아올 수 있어야 하므로 최소 갱신 간격을 무시한다.
     */
    fun syncNow() {
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                when (val result = tleRepository.sync(force = true)) {
                    is TleSyncResult.Updated -> {
                        val via = if (result.usedFallback) " (대체 주소 사용)" else ""
                        _messages.send("TLE ${result.count}건 갱신 완료$via")
                    }

                    is TleSyncResult.Failed ->
                        _messages.send("TLE 갱신 실패: ${result.message}")

                    is TleSyncResult.Skipped -> Unit // force 라서 발생하지 않음
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    tleRepository = container.tleRepository,
                    observerSiteRepository = container.observerSiteRepository,
                    passAlarmRepository = container.passAlarmRepository,
                    locationProvider = container.locationProvider,
                )
            }
        }
    }
}
