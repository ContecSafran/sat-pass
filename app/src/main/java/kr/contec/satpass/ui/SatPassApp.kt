package kr.contec.satpass.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.ui.passes.PassDetailSheet
import kr.contec.satpass.ui.passes.PassListScreen
import kr.contec.satpass.ui.passes.PassListViewModel
import kr.contec.satpass.ui.satellites.SatelliteScreen
import kr.contec.satpass.ui.satellites.SatelliteViewModel
import kr.contec.satpass.ui.settings.SettingsScreen
import kr.contec.satpass.ui.settings.SettingsViewModel

/** 하단 탭 */
private enum class SatPassTab(
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    Passes("스케줄", "통과 스케줄", Icons.Outlined.Schedule),
    Satellites("위성", "위성 목록", Icons.Outlined.SatelliteAlt),
    Settings("설정", "설정", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatPassApp() {
    var currentTab by remember { mutableStateOf(SatPassTab.Passes) }
    val snackbarHostState = remember { SnackbarHostState() }

    val passViewModel: PassListViewModel = viewModel(factory = PassListViewModel.Factory)
    val satelliteViewModel: SatelliteViewModel = viewModel(factory = SatelliteViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)

    val passState by passViewModel.uiState.collectAsStateWithLifecycle()
    val satelliteState by satelliteViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // 진행도 표시를 위해 1초마다 갱신되는 현재 시각
    val now = rememberTickingInstant()

    var detailPass by remember { mutableStateOf<SatellitePass?>(null) }

    // 위치 권한 요청
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { passViewModel.refreshLocation() }

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
                contentPadding = innerPadding,
            )
        }
    }

    detailPass?.let { pass ->
        PassDetailSheet(
            pass = pass,
            observer = passState.observer,
            now = now,
            loadTrack = passViewModel::loadTrack,
            onDismiss = { detailPass = null },
        )
    }
}
