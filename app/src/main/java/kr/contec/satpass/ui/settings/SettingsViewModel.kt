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
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.data.repository.TleSyncResult
import kr.contec.satpass.data.settings.SatPassSettings
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.ui.appContainer
import java.time.Instant

data class SettingsUiState(
    val settings: SatPassSettings = SatPassSettings.DEFAULT,
    val lastTleFetchedAt: Instant? = null,
    val tleCachedCount: Int = 0,
    val isSyncing: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tleRepository: TleRepository,
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
                )
            }
        }
    }
}
