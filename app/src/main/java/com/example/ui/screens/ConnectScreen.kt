package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApiKeySource
import com.example.model.ConnectionMode
import com.example.model.DnsPreset
import com.example.model.NordCredentialsState
import com.example.model.SavedVpnProfile
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import com.example.ui.MainUiState
import com.example.ui.components.ConfigBackupCard
import com.example.ui.components.ConnectionDetailsCard
import com.example.ui.components.DnsPresetsCard
import com.example.ui.components.IpLeakCheckCard
import com.example.ui.components.NetworkTuningCard
import com.example.ui.components.SavedProfilesCard
import com.example.ui.components.tvFocusableClickable
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.GoogleAzure
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ConnectScreen(
    uiState: MainUiState,
    isAndroidTv: Boolean = false,
    isWideLayout: Boolean = false,
    onToggleConnection: () -> Unit,
    onApplyReconfigure: () -> Unit,
    onSelectMode: (ConnectionMode) -> Unit,
    onSelectDnsPreset: (DnsPreset) -> Unit,
    onSaveCustomDns: (String, String) -> Boolean,
    onToggleLanBypass: (Boolean) -> Unit,
    onToggleAutoFailover: (Boolean) -> Unit,
    onSelectMtu: (Int) -> Unit,
    onHopLowerLoadServer: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (SavedVpnProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onRunLeakCheck: () -> Unit,
    onExportBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit,
    onGetBackupJson: () -> String,
    onCopyBackupJson: (String) -> Unit,
    onImportBackupJson: (String) -> Boolean,
    onOpenLocations: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onRefreshServer: () -> Unit,
    onRetryCredentials: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearCustomApiKey: () -> Unit,
    onToggleAutoConnectOnBoot: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit,
    onCopyWireGuardConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSwapKeyDialog by remember { mutableStateOf(false) }

    val accentColor by animateColorAsState(
        targetValue = when {
            uiState.vpnSession.status == TunnelStatus.CONNECTED -> EmeraldProtected
            uiState.vpnSession.status == TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
            uiState.selectedMode == ConnectionMode.CLOUDFLARE_ONLY ||
                uiState.selectedMode == ConnectionMode.CLOUDFLARE_PLUS_VPN -> CloudflareAmber
            uiState.selectedMode == ConnectionMode.GOOGLE_ONLY ||
                uiState.selectedMode == ConnectionMode.GOOGLE_PLUS_VPN -> GoogleAzure
            else -> ElectricCyan
        },
        label = "accent_color"
    )

    if (showSwapKeyDialog) {
        SwapApiKeyDialog(
            apiKeySource = uiState.apiKeySource,
            maskedPreview = uiState.maskedApiKeyPreview,
            onDismiss = { showSwapKeyDialog = false },
            onSave = { newKey ->
                showSwapKeyDialog = false
                onSaveApiKey(newKey)
            },
            onClearCustom = {
                showSwapKeyDialog = false
                onClearCustomApiKey()
            }
        )
    }

    val useTwoColumnTvDashboard = isAndroidTv || isWideLayout
    val activeAppNames = remember(uiState.installedApps, uiState.activeSplitPackages) {
        uiState.installedApps
            .filter { it.packageName in uiState.activeSplitPackages }
            .map { it.appName }
    }

    if (useTwoColumnTvDashboard) {
        // Widescreen & Android TV 2-Column Command Center:
        // Keeps Power Orb + Live Connection Details & Location Code Stats always visible at 10-ft distance
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = if (isAndroidTv) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    TopBrandHeader(
                        uiState = uiState,
                        accentColor = accentColor,
                        onRefresh = onRefreshServer
                    )
                }

                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        PendingReconfigureBanner(onApply = onApplyReconfigure)
                    }
                }

                item {
                    HeroPowerOrbCard(
                        uiState = uiState,
                        accentColor = accentColor,
                        onToggleConnection = onToggleConnection
                    )
                }

                item {
                    ConnectionDetailsCard(
                        uiState = uiState,
                        accentColor = accentColor,
                        isAndroidTv = isAndroidTv,
                        onOpenLocations = onOpenLocations
                    )
                }

                item {
                    IpLeakCheckCard(
                        isRunning = uiState.isRunningLeakCheck,
                        result = uiState.leakCheckResult,
                        isVpnConnected = uiState.isConnected && uiState.vpnSession.isWireGuardVerified,
                        onRunLeakCheck = onRunLeakCheck
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SavedProfilesCard(
                        profiles = uiState.savedProfiles,
                        onSaveProfile = onSaveProfile,
                        onApplyProfile = onApplyProfile,
                        onDeleteProfile = onDeleteProfile
                    )
                }

                item {
                    RoutingCombinationSection(
                        selectedMode = uiState.selectedMode,
                        onSelectMode = onSelectMode
                    )
                }

                item {
                    DnsPresetsCard(
                        uiState = uiState,
                        onSelectDnsPreset = onSelectDnsPreset,
                        onSaveCustomDns = onSaveCustomDns
                    )
                }

                item {
                    ActiveLocationCard(
                        uiState = uiState,
                        onOpenLocations = onOpenLocations,
                        onRefreshServer = onRefreshServer
                    )
                }

                item {
                    NetworkTuningCard(
                        uiState = uiState,
                        onToggleLanBypass = onToggleLanBypass,
                        onToggleAutoFailover = onToggleAutoFailover,
                        onSelectMtu = onSelectMtu,
                        onHopLowerLoadServer = onHopLowerLoadServer
                    )
                }

                item {
                    SplitTunnelSummaryCard(
                        splitMode = uiState.splitMode,
                        selectedCount = uiState.activeSplitPackages.size,
                        selectedAppNames = activeAppNames,
                        onOpenSplitTunnel = onOpenSplitTunnel
                    )
                }

                item {
                    BackgroundOptimizationCard(
                        isUnrestricted = uiState.isIgnoringBatteryOptimizations,
                        autoConnectOnBoot = uiState.autoConnectOnBoot,
                        cacheHits = uiState.vpnSession.dnsCacheHits,
                        totalQueries = uiState.vpnSession.dnsQueriesForwarded,
                        onToggleAutoConnectOnBoot = onToggleAutoConnectOnBoot,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenSystemVpnSettings = onOpenSystemVpnSettings
                    )
                }

                item {
                    ConfigBackupCard(
                        onExportBackupFile = onExportBackupFile,
                        onImportBackupFile = onImportBackupFile,
                        onGetBackupJson = onGetBackupJson,
                        onCopyBackupJson = onCopyBackupJson,
                        onImportBackupJson = onImportBackupJson
                    )
                }

                item {
                    NordDiagnosticsCard(
                        uiState = uiState,
                        onSwapApiKey = { showSwapKeyDialog = true },
                        onRetryCredentials = onRetryCredentials,
                        onCopyWireGuardConfig = onCopyWireGuardConfig
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    TopBrandHeader(
                        uiState = uiState,
                        accentColor = accentColor,
                        onRefresh = onRefreshServer
                    )
                }

                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        PendingReconfigureBanner(onApply = onApplyReconfigure)
                    }
                }

                item {
                    HeroPowerOrbCard(
                        uiState = uiState,
                        accentColor = accentColor,
                        onToggleConnection = onToggleConnection
                    )
                }

                item {
                    ConnectionDetailsCard(
                        uiState = uiState,
                        accentColor = accentColor,
                        isAndroidTv = false,
                        onOpenLocations = onOpenLocations
                    )
                }

                item {
                    SavedProfilesCard(
                        profiles = uiState.savedProfiles,
                        onSaveProfile = onSaveProfile,
                        onApplyProfile = onApplyProfile,
                        onDeleteProfile = onDeleteProfile
                    )
                }

                item {
                    RoutingCombinationSection(
                        selectedMode = uiState.selectedMode,
                        onSelectMode = onSelectMode
                    )
                }

                item {
                    DnsPresetsCard(
                        uiState = uiState,
                        onSelectDnsPreset = onSelectDnsPreset,
                        onSaveCustomDns = onSaveCustomDns
                    )
                }

                item {
                    ActiveLocationCard(
                        uiState = uiState,
                        onOpenLocations = onOpenLocations,
                        onRefreshServer = onRefreshServer
                    )
                }

                item {
                    NetworkTuningCard(
                        uiState = uiState,
                        onToggleLanBypass = onToggleLanBypass,
                        onToggleAutoFailover = onToggleAutoFailover,
                        onSelectMtu = onSelectMtu,
                        onHopLowerLoadServer = onHopLowerLoadServer
                    )
                }

                item {
                    IpLeakCheckCard(
                        isRunning = uiState.isRunningLeakCheck,
                        result = uiState.leakCheckResult,
                        isVpnConnected = uiState.isConnected && uiState.vpnSession.isWireGuardVerified,
                        onRunLeakCheck = onRunLeakCheck
                    )
                }

                item {
                    SplitTunnelSummaryCard(
                        splitMode = uiState.splitMode,
                        selectedCount = uiState.activeSplitPackages.size,
                        selectedAppNames = activeAppNames,
                        onOpenSplitTunnel = onOpenSplitTunnel
                    )
                }

                item {
                    BackgroundOptimizationCard(
                        isUnrestricted = uiState.isIgnoringBatteryOptimizations,
                        autoConnectOnBoot = uiState.autoConnectOnBoot,
                        cacheHits = uiState.vpnSession.dnsCacheHits,
                        totalQueries = uiState.vpnSession.dnsQueriesForwarded,
                        onToggleAutoConnectOnBoot = onToggleAutoConnectOnBoot,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenSystemVpnSettings = onOpenSystemVpnSettings
                    )
                }

                item {
                    ConfigBackupCard(
                        onExportBackupFile = onExportBackupFile,
                        onImportBackupFile = onImportBackupFile,
                        onGetBackupJson = onGetBackupJson,
                        onCopyBackupJson = onCopyBackupJson,
                        onImportBackupJson = onImportBackupJson
                    )
                }

                item {
                    NordDiagnosticsCard(
                        uiState = uiState,
                        onSwapApiKey = { showSwapKeyDialog = true },
                        onRetryCredentials = onRetryCredentials,
                        onCopyWireGuardConfig = onCopyWireGuardConfig
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBrandHeader(
    uiState: MainUiState,
    accentColor: Color,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = if (uiState.isConnected) AppIcons.ShieldCheck else AppIcons.Shield,
                    contentDescription = "SwiftTunnel status",
                    tint = accentColor,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "SwiftTunnel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val traceText = uiState.networkTrace?.let { t ->
                    if (t.ip.isNotBlank()) {
                        "IP ${t.ip}${if (t.colo.isNotBlank()) " • ${t.colo}" else ""}"
                    } else null
                } ?: "NordLynx + 1.1.1.1 / 8.8.8.8"
                Text(
                    text = traceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(48.dp)
                .testTag("refresh_dashboard_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh server and IP",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PendingReconfigureBanner(onApply: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Routing updated. Re-apply to live tunnel?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onApply,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("apply_reconfigure_button")
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun HeroPowerOrbCard(
    uiState: MainUiState,
    accentColor: Color,
    onToggleConnection: () -> Unit
) {
    val isConnected = uiState.isConnected
    val isBusy = uiState.isBusy
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )
    val orbScale by animateFloatAsState(
        targetValue = if (isConnected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orb_scale"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (isConnected) 0.55f else 0.22f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isConnected) 0.16f else 0.08f),
                            Color.Transparent
                        ),
                        radius = 520f
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val activeServer = uiState.vpnSession.activeServer ?: uiState.recommendedServer
                    val statusLabel = when (uiState.vpnSession.status) {
                        TunnelStatus.CONNECTED -> {
                            val proto = if (uiState.vpnSession.isWireGuardVerified) {
                                val codePart = activeServer?.let { "${it.countryCode} ${it.serverCodeTag}" } ?: "VPN"
                                "WIREGUARD • $codePart"
                            } else {
                                "DNS • ${uiState.vpnSession.primaryDns}"
                            }
                            "$proto • ${formatDuration(uiState.elapsedSeconds)}"
                        }
                        TunnelStatus.CONNECTING -> "HANDSHAKING…"
                        TunnelStatus.DISCONNECTING -> "DISCONNECTING…"
                        TunnelStatus.ERROR -> "TUNNEL ERROR"
                        TunnelStatus.DISCONNECTED -> {
                            val codeHint = if (uiState.selectedMode.usesVpn && activeServer != null) {
                                " • ${activeServer.serverCodeTag}"
                            } else {
                                ""
                            }
                            "READY • ${uiState.selectedMode.shortTitle.uppercase()}$codeHint"
                        }
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.size(176.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.36f
                    val maxRadius = size.minDimension * 0.49f

                    drawCircle(
                        color = accentColor.copy(alpha = 0.12f),
                        radius = maxRadius * 0.9f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )

                    if (isConnected || isBusy) {
                        val ringRadius = baseRadius + (maxRadius - baseRadius) * pulseProgress
                        val ringAlpha = (1f - pulseProgress) * 0.55f
                        drawCircle(
                            color = accentColor.copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 3f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .scale(orbScale)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isConnected) {
                                    listOf(EmeraldProtected, Color(0xFF00A859))
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                }
                            )
                        )
                        .border(
                            width = 2.5.dp,
                            color = accentColor.copy(alpha = if (isConnected) 0.9f else 0.55f),
                            shape = CircleShape
                        )
                        .tvFocusableClickable(
                            enabled = !isBusy,
                            shape = CircleShape,
                            focusColor = ElectricCyan,
                            scaleOnFocus = 1.06f,
                            onClick = onToggleConnection
                        )
                        .testTag("connect_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = accentColor,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = AppIcons.Power,
                                contentDescription = if (isConnected) "Disconnect tunnel" else "Connect tunnel",
                                tint = if (isConnected) Color(0xFF002110) else accentColor,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isConnected) "ACTIVE" else "TAP TO LINK",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) Color(0xFF002110) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!uiState.vpnSession.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.vpnSession.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TelemetryMetric(
                    label = "DNS",
                    value = if (isConnected) uiState.vpnSession.primaryDns else uiState.effectiveDns.first,
                    highlight = accentColor
                )
                TelemetryMetric(
                    label = "PING",
                    value = uiState.measuredLatencyMs?.let { "${it} ms" } ?: "—",
                    highlight = MaterialTheme.colorScheme.onSurface
                )
                TelemetryMetric(
                    label = if (uiState.vpnSession.isWireGuardVerified) "PACKETS" else "QUERIES",
                    value = if (isConnected) "${uiState.vpnSession.dnsQueriesForwarded}" else "0",
                    highlight = MaterialTheme.colorScheme.onSurface
                )
                TelemetryMetric(
                    label = "TRAFFIC",
                    value = formatBytes(uiState.vpnSession.bytesTx + uiState.vpnSession.bytesRx),
                    highlight = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetric(
    label: String,
    value: String,
    highlight: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = highlight
        )
    }
}

@Composable
private fun RoutingCombinationSection(
    selectedMode: ConnectionMode,
    onSelectMode: (ConnectionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Routing Combination",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "1.1.1.1 • 8.8.8.8 • Nord",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeTile(
                mode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
                selected = selectedMode == ConnectionMode.CLOUDFLARE_PLUS_VPN,
                badgeColor = CloudflareAmber,
                tagText = "VPN + 1.1.1.1",
                onClick = { onSelectMode(ConnectionMode.CLOUDFLARE_PLUS_VPN) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_cloudflare_vpn")
            )
            ModeTile(
                mode = ConnectionMode.GOOGLE_PLUS_VPN,
                selected = selectedMode == ConnectionMode.GOOGLE_PLUS_VPN,
                badgeColor = GoogleAzure,
                tagText = "VPN + 8.8.8.8",
                onClick = { onSelectMode(ConnectionMode.GOOGLE_PLUS_VPN) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_google_vpn")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeTile(
                mode = ConnectionMode.CLOUDFLARE_ONLY,
                selected = selectedMode == ConnectionMode.CLOUDFLARE_ONLY,
                badgeColor = CloudflareAmber,
                tagText = "1.1.1.1 ALONE",
                onClick = { onSelectMode(ConnectionMode.CLOUDFLARE_ONLY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_cloudflare_only")
            )
            ModeTile(
                mode = ConnectionMode.GOOGLE_ONLY,
                selected = selectedMode == ConnectionMode.GOOGLE_ONLY,
                badgeColor = GoogleAzure,
                tagText = "8.8.8.8 ALONE",
                onClick = { onSelectMode(ConnectionMode.GOOGLE_ONLY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_google_only")
            )
        }

        val isNordDefault = selectedMode == ConnectionMode.NORD_VPN_DEFAULT
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .tvFocusableClickable(
                    shape = RoundedCornerShape(14.dp),
                    scaleOnFocus = 1.02f,
                    onClick = { onSelectMode(ConnectionMode.NORD_VPN_DEFAULT) }
                )
                .testTag("mode_nord_default"),
            shape = RoundedCornerShape(14.dp),
            color = if (isNordDefault) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = if (isNordDefault) 1.5.dp else 1.dp,
                color = if (isNordDefault) ElectricCyan else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Shield,
                        contentDescription = null,
                        tint = if (isNordDefault) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Nord VPN + Nord Default DNS",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "103.86.96.100 • 103.86.99.100",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isNordDefault) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode: ConnectionMode,
    selected: Boolean,
    badgeColor: Color,
    tagText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(18.dp),
                focusColor = badgeColor,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            badgeColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.6.dp else 1.dp,
            color = if (selected) badgeColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    imageVector = if (mode.usesVpn) AppIcons.Shield else AppIcons.Dns,
                    contentDescription = null,
                    tint = if (selected) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = mode.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${mode.primaryDns} / ${mode.secondaryDns}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveLocationCard(
    uiState: MainUiState,
    onOpenLocations: () -> Unit,
    onRefreshServer: () -> Unit
) {
    val server = uiState.recommendedServer
    val isDnsOnly = !uiState.selectedMode.usesVpn

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(20.dp),
                scaleOnFocus = 1.02f,
                onClick = onOpenLocations
            )
            .testTag("active_location_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Globe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDnsOnly) "Nord Location (Standby in DNS-Only)" else "Nord VPN Server",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${uiState.countries.size} countries",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.isLoadingServer && server == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Fetching fastest NordLynx endpoint…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (server != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.flagEmoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricCyan.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = server.serverCodeTag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = server.locationLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${server.hostname} • ${server.stationIp} • Load ${server.loadPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Choose server location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.serverError ?: "Tap to choose any Nord location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onRefreshServer) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelSummaryCard(
    splitMode: SplitTunnelMode,
    selectedCount: Int,
    selectedAppNames: List<String>,
    onOpenSplitTunnel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(20.dp),
                scaleOnFocus = 1.02f,
                onClick = onOpenSplitTunnel
            )
            .testTag("split_tunnel_summary_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = AppIcons.SplitRoute,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Split Tunneling",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val preview = selectedAppNames.take(3).joinToString(", ") +
                    if (selectedAppNames.size > 3) " +${selectedAppNames.size - 3}" else ""
                val summary = when (splitMode) {
                    SplitTunnelMode.ALL_APPS -> "All apps routed through tunnel"
                    SplitTunnelMode.EXCLUDE_APPS ->
                        if (preview.isNotEmpty()) "Excluded ($selectedCount): $preview"
                        else "$selectedCount app(s) excluded from tunnel"
                    SplitTunnelMode.INCLUDE_ONLY_APPS ->
                        if (preview.isNotEmpty()) "Include Only ($selectedCount): $preview"
                        else "Include Only: tap to select apps"
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Manage split tunneling apps",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BackgroundOptimizationCard(
    isUnrestricted: Boolean,
    autoConnectOnBoot: Boolean,
    cacheHits: Long,
    totalQueries: Long,
    onToggleAutoConnectOnBoot: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSystemVpnSettings: () -> Unit
) {
    val badgeColor = if (isUnrestricted && autoConnectOnBoot) EmeraldProtected else CloudflareAmber
    val badgeText = if (autoConnectOnBoot) "ALWAYS-ON READY" else "MANUAL START"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Bolt,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Always-On & Background",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Always-On Power-Up Auto-Connect Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(14.dp),
                        scaleOnFocus = 1.01f,
                        onClick = { onToggleAutoConnectOnBoot(!autoConnectOnBoot) }
                    )
                    .testTag("auto_connect_boot_row"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Connect on Power-Up",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Restores pointed location, DNS mode & split rules automatically on phone/TV boot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoConnectOnBoot,
                        onCheckedChange = onToggleAutoConnectOnBoot,
                        modifier = Modifier.testTag("auto_connect_boot_switch")
                    )
                }
            }

            Text(
                text = "Sticky service (stopWithTask=false) • 25s WireGuard keepalive • 120s rekey • 256-entry DNS cache ($cacheHits/$totalQueries hits).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_optimization_button")
                ) {
                    Text(
                        text = if (isUnrestricted) "Battery OK" else "Battery Opt",
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = onOpenSystemVpnSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_always_on_vpn_button")
                ) {
                    Text(
                        text = "OS Always-On",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NordDiagnosticsCard(
    uiState: MainUiState,
    onSwapApiKey: () -> Unit,
    onRetryCredentials: () -> Unit,
    onCopyWireGuardConfig: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Swappable Nord API Key",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val (badgeText, badgeColor) = when (uiState.credentialsState) {
                    is NordCredentialsState.Verified -> "KEY VERIFIED" to EmeraldProtected
                    is NordCredentialsState.Loading -> "CHECKING…" to ElectricCyan
                    is NordCredentialsState.Error -> "AUTH ERROR" to MaterialTheme.colorScheme.error
                    is NordCredentialsState.NotConfigured -> "NO KEY SET" to CloudflareAmber
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val sourceLabel = when (uiState.apiKeySource) {
                ApiKeySource.CUSTOM_USER_KEY -> "Active Key: ${uiState.maskedApiKeyPreview ?: "••••"} (Keystore Encrypted)"
                ApiKeySource.BUILD_SECRET -> "Active Key: ${uiState.maskedApiKeyPreview ?: "••••"} (Build Secret)"
                ApiKeySource.NONE -> "No key saved yet. Tap Swap API Key to paste any Nord access token."
            }
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            when (val cred = uiState.credentialsState) {
                is NordCredentialsState.Verified -> {
                    Text(
                        text = "NordLynx key verified (SHA-256 ${cred.keyFingerprint}). WireGuard profile ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldProtected
                    )
                }
                is NordCredentialsState.Loading -> {
                    Text(
                        text = "Authenticating token with api.nordvpn.com…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is NordCredentialsState.Error -> {
                    Text(
                        text = cred.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is NordCredentialsState.NotConfigured -> Unit
            }

            uiState.recommendedServer?.wireGuardPublicKey?.takeIf { it.isNotBlank() }?.let { pubKey ->
                Text(
                    text = "Peer PublicKey: ${pubKey.take(22)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSwapApiKey,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("swap_api_key_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Swap API Key")
                }

                OutlinedButton(
                    onClick = onCopyWireGuardConfig,
                    enabled = uiState.recommendedServer != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_wireguard_config_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WG Config")
                }
            }

            if (uiState.credentialsState is NordCredentialsState.Error) {
                TextButton(
                    onClick = onRetryCredentials,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("verify_nord_key_button")
                ) {
                    Text("Retry Verification")
                }
            }
        }
    }
}

@Composable
private fun SwapApiKeyDialog(
    apiKeySource: ApiKeySource,
    maskedPreview: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClearCustom: () -> Unit
) {
    var inputKey by remember { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Swap Nord API Key",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste any NordVPN Access Token below. It is encrypted with Android Keystore AES-256-GCM on this device and can be swapped anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (maskedPreview != null) {
                    Text(
                        text = "Current: $maskedPreview",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nord_api_key_input"),
                    singleLine = true,
                    label = { Text("Nord Access Token") },
                    placeholder = { Text("Paste 64-char hex token…") },
                    visualTransformation = if (revealKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { revealKey = !revealKey }) {
                            Text(if (revealKey) "Hide" else "Show")
                        }
                    }
                )

                if (apiKeySource == ApiKeySource.CUSTOM_USER_KEY) {
                    TextButton(
                        onClick = onClearCustom,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("remove_custom_api_key_button")
                    ) {
                        Text("Remove Saved Custom Key")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(inputKey) },
                enabled = inputKey.trim().length >= 16,
                modifier = Modifier.testTag("save_api_key_button")
            ) {
                Text("Save & Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "${bytes} B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }
}
