package kr.contec.satpass.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.AppSettingsAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.ui.export.ExportFormat
import kr.contec.satpass.ui.export.PassExport
import kr.contec.satpass.ui.export.copyToClipboard
import kr.contec.satpass.notification.PassNotifications
import kr.contec.satpass.ui.theme.PassImminent
import kr.contec.satpass.ui.passes.PassDetailSheet
import kr.contec.satpass.ui.passes.PassListScreen
import kr.contec.satpass.ui.passes.PassListViewModel
import kr.contec.satpass.ui.passes.passKey
import kr.contec.satpass.ui.satellites.SatelliteScreen
import kr.contec.satpass.ui.satellites.SatelliteViewModel
import kr.contec.satpass.ui.satellites.TleDetailSheet
import kr.contec.satpass.ui.settings.SettingsScreen
import kr.contec.satpass.ui.settings.SettingsViewModel

/**
 * 상단 바의 클립보드 복사 메뉴.
 *
 * 형식은 [ExportFormat] 에 항목을 추가하면 자동으로 늘어난다.
 */
@Composable
private fun CopyMenu(
    enabled: Boolean,
    onCopy: (ExportFormat) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "클립보드로 복사",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExportFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = {
                        onCopy(format)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 하단 탭 */
private enum class SatPassTab(
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    Passes("스케줄", "패스 스케줄", Icons.Outlined.Schedule),
    Satellites("위성", "위성 목록", Icons.Outlined.SatelliteAlt),
    Settings("설정", "설정", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatPassApp() {
    var currentTab by remember { mutableStateOf(SatPassTab.Passes) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val passViewModel: PassListViewModel = viewModel(factory = PassListViewModel.Factory)
    val satelliteViewModel: SatelliteViewModel = viewModel(factory = SatelliteViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    val passState by passViewModel.uiState.collectAsStateWithLifecycle()
    val satelliteState by satelliteViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // 진행도 표시를 위해 1초마다 갱신되는 현재 시각
    val now = rememberTickingInstant()

    var detailPass by remember { mutableStateOf<SatellitePass?>(null) }

    // 알림 권한 요청 (Android 13+). 허용 여부와 무관하게 알람은 걸리되, 알림이 뜨지 않을 뿐이다.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) passViewModel.rescheduleAlarms()
    }

    // 위치 권한 요청
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { passViewModel.refreshLocation() }

    // 정확 알람 권한은 시스템 설정에서 켜기 때문에, 앱으로 돌아올 때마다 상태를 다시 읽는다.
    LifecycleResumeEffect(settingsViewModel) {
        settingsViewModel.refreshExactAlarmState()
        onPauseOrDispose { }
    }

    // 각 화면의 일회성 메시지를 스낵바로
    LaunchedEffect(passViewModel) {
        passViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(satelliteViewModel) {
        satelliteViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(currentTab.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    if (currentTab == SatPassTab.Passes) {
                        // 알림 설정 버튼 — 알릴 패스를 고르는 모드를 켜고 끈다.
                        IconButton(
                            onClick = {
                                if (!passState.alarmPickerMode &&
                                    !PassNotifications.hasPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                                if (!passViewModel.canScheduleExactAlarms()) {
                                    scope.launch {
                                        val action = snackbarHostState.showSnackbar(
                                            message = "정확한 시각에 알리려면 알람 권한이 필요합니다.",
                                            actionLabel = "설정 열기",
                                        )
                                        if (action == SnackbarResult.ActionPerformed) {
                                            openExactAlarmSettings(context)
                                        }
                                    }
                                }
                                passViewModel.toggleAlarmPickerMode()
                            }
                        ) {
                            Icon(
                                imageVector = if (passState.alarmPickerMode) {
                                    Icons.Filled.NotificationsActive
                                } else {
                                    Icons.Outlined.NotificationsNone
                                },
                                contentDescription = "알림 설정",
                                tint = if (passState.alarmPickerMode) {
                                    PassImminent
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                            )
                        }

                        // 스케줄 화면에서만 목록 전체를 클립보드로 복사할 수 있게 한다.
                        CopyMenu(
                            enabled = passState.passes.isNotEmpty(),
                            onCopy = { format ->
                                copyToClipboard(
                                    context = context,
                                    label = "패스 스케줄",
                                    text = PassExport.passes(passState.passes, format),
                                )
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "패스 ${passState.passes.size}건을 복사했습니다."
                                        )
                                    }
                                }
                            },
                        )
                    }

                    // 설정 화면에서는 시스템의 앱 정보 화면으로 바로 갈 수 있게 한다.
                    if (currentTab == SatPassTab.Settings) {
                        IconButton(
                            onClick = {
                                if (!openAppDetailsSettings(context)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("앱 정보 화면을 열 수 없습니다.")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AppSettingsAlt,
                                contentDescription = "휴대폰 설정에서 앱 정보 열기",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                SatPassTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (currentTab) {
            SatPassTab.Passes -> PassListScreen(
                state = passState,
                now = now,
                onRefresh = passViewModel::onPullToRefresh,
                onPassClick = { detailPass = it },
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                onSelectSite = passViewModel::selectSite,
                onToggleAlarm = passViewModel::togglePassAlarm,
                onLocationClick = {
                    val opened = openInMaps(
                        context = context,
                        latitude = passState.observer.latitude,
                        longitude = passState.observer.longitude,
                        label = "관측 위치",
                    )
                    if (!opened) {
                        scope.launch {
                            snackbarHostState.showSnackbar("이 좌표를 열 수 있는 지도 앱이 없습니다.")
                        }
                    }
                },
                onGoToSatellites = { currentTab = SatPassTab.Satellites },
                contentPadding = innerPadding,
            )

            SatPassTab.Satellites -> SatelliteScreen(
                state = satelliteState,
                onQueryChange = satelliteViewModel::onQueryChange,
                onClearQuery = satelliteViewModel::clearQuery,
                onAdd = satelliteViewModel::add,
                onRemove = satelliteViewModel::remove,
                onSelectedChange = satelliteViewModel::setSelected,
                onSetAllSelected = satelliteViewModel::setAllSelected,
                onShowTle = satelliteViewModel::showTle,
                onSaveManualTle = satelliteViewModel::saveManualTle,
                contentPadding = innerPadding,
            )

            SatPassTab.Settings -> SettingsScreen(
                state = settingsState,
                onTleUrlChange = settingsViewModel::setTleUrl,
                onFallbackTleUrlChange = settingsViewModel::setFallbackTleUrl,
                onMinSyncIntervalChange = settingsViewModel::setMinSyncIntervalHours,
                onPredictionDaysChange = settingsViewModel::setPredictionDays,
                onMinElevationChange = settingsViewModel::setMinElevationDeg,
                onResetUrls = settingsViewModel::resetTleUrls,
                onSyncNow = settingsViewModel::syncNow,
                onSelectSite = settingsViewModel::selectSite,
                onAddSite = settingsViewModel::addSite,
                onUpdateSite = settingsViewModel::updateSite,
                onDeleteSite = settingsViewModel::deleteSite,
                loadCurrentLocation = settingsViewModel::currentLocationOrNull,
                onLocationUnavailable = settingsViewModel::notifyLocationUnavailable,
                onNotificationLeadChange = settingsViewModel::setNotificationLeadMinutes,
                onClearAlarms = settingsViewModel::clearAlarms,
                onOpenExactAlarmSettings = {
                    // 버튼을 다시 누르면 그 사이 바뀐 권한 상태를 먼저 반영한다.
                    settingsViewModel.refreshExactAlarmState()
                    openExactAlarmSettings(context)
                },
                contentPadding = innerPadding,
            )
        }
    }

    detailPass?.let { pass ->
        PassDetailSheet(
            pass = pass,
            observer = passState.observer,
            now = now,
            loadTrack = passViewModel::loadTrackTable,
            onCopied = { scope.launch { snackbarHostState.showSnackbar("클립보드에 복사했습니다.") } },
            alarmEnabled = passKey(pass.noradId, pass.aosTime.toEpochMilli()) in passState.alarmedPassKeys,
            onToggleAlarm = { passViewModel.togglePassAlarm(pass) },
            onDismiss = { detailPass = null },
        )
    }

    satelliteState.tleDetail?.let { detail ->
        TleDetailSheet(
            detail = detail,
            onCopied = satelliteViewModel::notifyTleCopied,
            onSaveTle = satelliteViewModel::saveManualTle,
            onDeleteManual = satelliteViewModel::deleteManualTle,
            onDismiss = satelliteViewModel::dismissTle,
        )
    }
}
