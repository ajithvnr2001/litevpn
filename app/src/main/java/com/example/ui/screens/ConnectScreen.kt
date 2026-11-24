package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionMode
import com.example.model.NordCredentialsState
import com.example.model.SavedVpnProfile
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import com.example.ui.MainUiState
import com.example.ui.components.ConnectionDetailsCard
import com.example.ui.components.SavedProfilesCard
import com.example.ui.components.tvFocusableClickable
import com.example.ui.icons.AppIcons
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg

@Composable
fun ConnectScreen(
    uiState: MainUiState,
    isAndroidTv: Boolean = false,
    isWideLayout: Boolean = false,
    onToggleConnection: () -> Unit,
    onApplyReconfigure: () -> Unit,
    onSelectMode: (ConnectionMode) -> Unit,
    onHopLowerLoadServer: () -> Unit,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (SavedVpnProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onOpenTools: () -> Unit,
    onRefreshServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusAccent = when (uiState.vpnSession.status) {
        TunnelStatus.CONNECTED -> EmeraldProtected
        TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
        TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> CloudflareAmber
        TunnelStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val useTwoColumnLayout = isAndroidTv || isWideLayout

    if (useTwoColumnLayout) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = if (isAndroidTv) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MinimalTopBar(
                        uiState = uiState,
                        onRefresh = onRefreshServer
                    )
                }
                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        PendingReconfigureBanner(onApply = onApplyReconfigure)
                    }
                }
                item {
                    TunnelControlCard(
                        uiState = uiState,
                        statusAccent = statusAccent,
                        onToggleConnection = onToggleConnection,
                        onHopLowerLoadServer = onHopLowerLoadServer,
                        onOpenLocations = onOpenLocations
                    )
                }
                item {
                    ConnectionDetailsCard(
                        uiState = uiState,
                        accentColor = statusAccent,
                        isAndroidTv = isAndroidTv,
                        onOpenLocations = onOpenLocations
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RoutingModeCard(
                        selectedMode = uiState.selectedMode,
                        onSelectMode = onSelectMode
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
                    QuickWorkspaceLinksCard(
                        uiState = uiState,
                        onOpenSplitTunnel = onOpenSplitTunnel,
                        onOpenTools = onOpenTools
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MinimalTopBar(
                        uiState = uiState,
                        onRefresh = onRefreshServer
                    )
                }
                item {
                    AnimatedVisibility(visible = uiState.hasPendingReconfigure) {
                        PendingReconfigureBanner(onApply = onApplyReconfigure)
                    }
                }
                item {
                    TunnelControlCard(
                        uiState = uiState,
                        statusAccent = statusAccent,
                        onToggleConnection = onToggleConnection,
                        onHopLowerLoadServer = onHopLowerLoadServer,
                        onOpenLocations = onOpenLocations
                    )
                }
                item {
                    ConnectionDetailsCard(
                        uiState = uiState,
                        accentColor = statusAccent,
                        isAndroidTv = false,
                        onOpenLocations = onOpenLocations
                    )
                }
                item {
                    RoutingModeCard(
                        selectedMode = uiState.selectedMode,
                        onSelectMode = onSelectMode
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
                    QuickWorkspaceLinksCard(
                        uiState = uiState,
                        onOpenSplitTunnel = onOpenSplitTunnel,
                        onOpenTools = onOpenTools
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MinimalTopBar(
    uiState: MainUiState,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SwiftTunnel",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            val traceText = uiState.networkTrace?.let { t ->
                if (t.ip.isNotBlank()) {
                    "${t.ip}${if (t.colo.isNotBlank()) " · ${t.colo}" else ""}"
                } else null
            } ?: "WireGuard · DNS"
            Text(
                text = traceText,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, CloudflareAmber.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Routing changed while active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("apply_reconfigure_button")
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun TunnelControlCard(
    uiState: MainUiState,
    statusAccent: Color,
    onToggleConnection: () -> Unit,
    onHopLowerLoadServer: () -> Unit,
    onOpenLocations: () -> Unit
) {
    val isConnected = uiState.isConnected
    val isBusy = uiState.isBusy
    val activeServer = (if (isConnected) uiState.vpnSession.activeServer else null)
        ?: uiState.recommendedServer

    val statusText = when (uiState.vpnSession.status) {
        TunnelStatus.CONNECTED -> {
            val peerCount = uiState.vpnSession.activePeerTunnels.size
            val prefix = when {
                peerCount > 1 -> "MULTI-TUNNEL ($peerCount)"
                uiState.vpnSession.isWireGuardVerified -> "WIREGUARD"
                else -> "DNS"
            }
            "$prefix · ${formatDuration(uiState.elapsedSeconds)}"
        }
        TunnelStatus.CONNECTING -> "CONNECTING…"
        TunnelStatus.DISCONNECTING -> "DISCONNECTING…"
        TunnelStatus.ERROR -> "ERROR"
        TunnelStatus.DISCONNECTED -> "STANDBY"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected) EmeraldProtected.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusAccent)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isConnected) EmeraldProtected else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = uiState.selectedMode.shortTitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Target server selector row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(10.dp),
                        focusColor = ElectricCyan,
                        onClick = onOpenLocations
                    )
                    .testTag("active_location_card"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (uiState.isLoadingServer && activeServer == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Resolving server…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeServer?.flagEmoji ?: "🌐",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (activeServer != null) {
                                    Text(
                                        text = activeServer.serverCodeTag,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ElectricCyan
                                    )
                                }
                                Text(
                                    text = activeServer?.locationLabel ?: "Fastest Available",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = activeServer?.let { "${it.hostname} · Load ${it.loadPercent}%" }
                                    ?: "Tap to choose country, city or #code",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Choose server",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary decisive action row: Connect/Disconnect + Hop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleConnection,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) EmeraldProtected else MaterialTheme.colorScheme.primary,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("connect_toggle_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianBg,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.vpnSession.status == TunnelStatus.CONNECTING) "Connecting…" else "Stopping…",
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Power,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "Disconnect" else "Connect",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (uiState.selectedMode.usesVpn) {
                    OutlinedButton(
                        onClick = onHopLowerLoadServer,
                        enabled = !uiState.isHoppingServer && !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("quick_hop_button")
                    ) {
                        if (uiState.isHoppingServer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Hop", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (!uiState.vpnSession.errorMessage.isNullOrBlank()) {
                Text(
                    text = uiState.vpnSession.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RoutingModeCard(
    selectedMode: ConnectionMode,
    onSelectMode: (ConnectionMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ROUTING MODE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeOptionTile(
                    title = "VPN + 1.1.1.1",
                    dns = "1.1.1.1",
                    selected = selectedMode == ConnectionMode.CLOUDFLARE_PLUS_VPN,
                    onClick = { onSelectMode(ConnectionMode.CLOUDFLARE_PLUS_VPN) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_cloudflare_vpn")
                )
                ModeOptionTile(
                    title = "VPN + 8.8.8.8",
                    dns = "8.8.8.8",
                    selected = selectedMode == ConnectionMode.GOOGLE_PLUS_VPN,
                    onClick = { onSelectMode(ConnectionMode.GOOGLE_PLUS_VPN) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_google_vpn")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeOptionTile(
                    title = "1.1.1.1 DNS",
                    dns = "DNS only",
                    selected = selectedMode == ConnectionMode.CLOUDFLARE_ONLY,
                    onClick = { onSelectMode(ConnectionMode.CLOUDFLARE_ONLY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_cloudflare_only")
                )
                ModeOptionTile(
                    title = "8.8.8.8 DNS",
                    dns = "DNS only",
                    selected = selectedMode == ConnectionMode.GOOGLE_ONLY,
                    onClick = { onSelectMode(ConnectionMode.GOOGLE_ONLY) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_google_only")
                )
            }

            ModeOptionTile(
                title = "VPN + Nord Default DNS",
                dns = "103.86.96.100",
                selected = selectedMode == ConnectionMode.NORD_VPN_DEFAULT,
                onClick = { onSelectMode(ConnectionMode.NORD_VPN_DEFAULT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_nord_default")
            )
        }
    }
}

@Composable
private fun ModeOptionTile(
    title: String,
    dns: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .tvFocusableClickable(
                shape = RoundedCornerShape(10.dp),
                focusColor = ElectricCyan,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dns,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selected) EmeraldProtected else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected mode",
                    tint = EmeraldProtected,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickWorkspaceLinksCard(
    uiState: MainUiState,
    onOpenSplitTunnel: () -> Unit,
    onOpenTools: () -> Unit
) {
    val splitText = remember(uiState.splitMode, uiState.activeSplitPackages.size, uiState.appRouteRules.size) {
        val base = when (uiState.splitMode) {
            SplitTunnelMode.ALL_APPS -> "All apps"
            SplitTunnelMode.EXCLUDE_APPS -> "${uiState.activeSplitPackages.size} excluded"
            SplitTunnelMode.INCLUDE_ONLY_APPS -> "${uiState.activeSplitPackages.size} included"
        }
        if (uiState.appRouteRules.isNotEmpty()) {
            "$base · ${uiState.appRouteRules.size} per-app routes"
        } else {
            base
        }
    }

    val toolsText = remember(uiState.dnsPreset, uiState.customMtu, uiState.credentialsState) {
        val keyState = when (uiState.credentialsState) {
            is NordCredentialsState.Verified -> "Key OK"
            is NordCredentialsState.NotConfigured -> "No Key"
            is NordCredentialsState.Error -> "Key Error"
            NordCredentialsState.Loading -> "Checking"
        }
        "${uiState.dnsPreset.shortLabel} · MTU ${uiState.customMtu} · $keyState"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        focusColor = ElectricCyan,
                        onClick = onOpenSplitTunnel
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("split_tunnel_summary_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.SplitRoute,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Routing & Split Tunnel",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = splitText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (uiState.appRouteRules.isNotEmpty()) EmeraldProtected
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Apps",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        focusColor = ElectricCyan,
                        onClick = onOpenTools
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("open_tools_summary_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Sliders,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DNS, MTU, Leak Check & Key",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = toolsText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Tools",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return "%02d:%02d:%02d".format(hrs, mins, secs)
}
