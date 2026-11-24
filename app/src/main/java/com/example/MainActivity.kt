package com.example

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.icons.AppIcons
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.LocationsScreen
import com.example.ui.screens.SplitTunnelScreen
import com.example.ui.theme.SwiftTunnelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwiftTunnelTheme(darkTheme = true) {
                SwiftTunnelRoot()
            }
        }
    }
}

@Composable
private fun SwiftTunnelRoot(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryOptimizationStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnService(context)
        } else {
            viewModel.showMessage(context.getString(R.string.vpn_permission_denied))
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnService(context)
        }
    }

    fun requestConnectOrApply() {
        val validationError = viewModel.validateBeforeConnect()
        if (validationError != null) {
            viewModel.showMessage(validationError)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnService(context)
        }
    }

    fun handleToggleConnection() {
        if (uiState.isConnected) {
            viewModel.stopVpnService(context)
        } else {
            requestConnectOrApply()
        }
    }

    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            viewModel.showMessage("Open Settings > Apps > SwiftTunnel > Battery to set Unrestricted")
        }
    }

    fun openSystemVpnSettings() {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            viewModel.showMessage("Open Settings > Network & Internet > VPN > SwiftTunnel > Always-on VPN")
        }
    }

    fun copyWireGuardConfig() {
        val config = viewModel.buildWireGuardConfigText()
        if (config == null) {
            viewModel.showMessage("Select or refresh a Nord server first")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SwiftTunnel WireGuard Config", config))
        viewModel.showMessage("NordLynx WireGuard config copied to clipboard")
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    BackHandler(enabled = uiState.selectedTab != AppTab.CONNECT) {
        viewModel.selectTab(AppTab.CONNECT)
    }

    val isAndroidTv = remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = isAndroidTv || maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected && uiState.isConnected),
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = if (isAndroidTv) 24.dp else 0.dp, vertical = if (isAndroidTv) 14.dp else 0.dp)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected && uiState.isConnected),
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (uiState.selectedTab) {
                        AppTab.CONNECT -> ConnectScreen(
                            uiState = uiState,
                            onToggleConnection = ::handleToggleConnection,
                            onApplyReconfigure = ::requestConnectOrApply,
                            onSelectMode = viewModel::selectConnectionMode,
                            onOpenLocations = { viewModel.selectTab(AppTab.LOCATIONS) },
                            onOpenSplitTunnel = { viewModel.selectTab(AppTab.SPLIT_TUNNEL) },
                            onRefreshServer = {
                                viewModel.refreshRecommendedServer()
                                viewModel.refreshNetworkTrace()
                            },
                            onRetryCredentials = viewModel::verifyNordCredentials,
                            onSaveApiKey = viewModel::saveAndVerifyApiKey,
                            onClearCustomApiKey = viewModel::clearCustomApiKey,
                            onToggleAutoConnectOnBoot = viewModel::setAutoConnectOnBoot,
                            onOpenBatterySettings = ::openBatteryOptimizationSettings,
                            onOpenSystemVpnSettings = ::openSystemVpnSettings,
                            onCopyWireGuardConfig = ::copyWireGuardConfig
                        )

                        AppTab.LOCATIONS -> LocationsScreen(
                            uiState = uiState,
                            onQueryChange = viewModel::updateLocationQuery,
                            onSelectRegion = viewModel::selectRegionFilter,
                            onSelectLocation = { countryId, cityName ->
                                viewModel.selectLocation(countryId, cityName, switchToConnect = true)
                            },
                            onConnectLocation = { countryId, cityName ->
                                viewModel.selectLocationAndConnect(countryId, cityName) {
                                    requestConnectOrApply()
                                }
                            },
                            onLoadCountryServers = viewModel::loadServersForCountry,
                            onConnectServer = { srv, countryId ->
                                viewModel.selectSpecificServer(srv, countryId, switchToConnect = true) {
                                    requestConnectOrApply()
                                }
                            },
                            onToggleFavorite = viewModel::toggleFavoriteCountry,
                            onRetryServerCodeSearch = {
                                viewModel.retryServerCodeSearch(forceRefresh = true)
                            },
                            onRefresh = viewModel::refreshLocationsDirectory
                        )

                        AppTab.SPLIT_TUNNEL -> SplitTunnelScreen(
                            uiState = uiState,
                            onSelectSplitMode = viewModel::setSplitTunnelMode,
                            onToggleApp = viewModel::toggleAppInSplitList,
                            onQueryChange = viewModel::updateAppSearchQuery,
                            onToggleSelectedOnly = viewModel::toggleShowSelectedAppsOnly,
                            onClearSelected = viewModel::clearActiveSplitApps,
                            onApplyReconfigure = ::requestConnectOrApply
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: AppTab): String = when (tab) {
    AppTab.CONNECT -> stringResource(R.string.nav_connect)
    AppTab.LOCATIONS -> stringResource(R.string.nav_locations)
    AppTab.SPLIT_TUNNEL -> stringResource(R.string.nav_split_tunnel)
}

private fun tabIcon(tab: AppTab, isProtected: Boolean) = when (tab) {
    AppTab.CONNECT -> if (isProtected) AppIcons.ShieldCheck else AppIcons.Shield
    AppTab.LOCATIONS -> AppIcons.Globe
    AppTab.SPLIT_TUNNEL -> AppIcons.SplitApps
}
