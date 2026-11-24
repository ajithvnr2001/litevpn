package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivePeerTunnelSummary
import com.example.model.ConnectionTelemetryFormatter
import com.example.ui.MainUiState
import com.example.ui.theme.CloudflareAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProtected
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ConnectionDetailsCard(
    uiState: MainUiState,
    accentColor: Color,
    isAndroidTv: Boolean,
    onOpenLocations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = remember(
        uiState.vpnSession,
        uiState.selectedMode,
        uiState.recommendedServer,
        uiState.splitMode,
        uiState.activeSplitPackages.size,
        uiState.elapsedSeconds,
        uiState.measuredLatencyMs,
        uiState.liveTxBytesPerSec,
        uiState.liveRxBytesPerSec,
        uiState.networkTrace,
        uiState.dnsPreset,
        uiState.customDnsPrimary,
        uiState.customDnsSecondary,
        uiState.lanBypassEnabled,
        uiState.customMtu
    ) {
        ConnectionTelemetryFormatter.buildSnapshot(
            session = uiState.vpnSession,
            selectedMode = uiState.selectedMode,
            recommendedServer = uiState.recommendedServer,
            splitMode = uiState.splitMode,
            splitAppCount = uiState.activeSplitPackages.size,
            elapsedSeconds = uiState.elapsedSeconds,
            measuredLatencyMs = uiState.measuredLatencyMs,
            liveTxBytesPerSec = uiState.liveTxBytesPerSec,
            liveRxBytesPerSec = uiState.liveRxBytesPerSec,
            networkTrace = uiState.networkTrace,
            dnsPreset = uiState.dnsPreset,
            customDnsPrimary = uiState.customDnsPrimary,
            customDnsSecondary = uiState.customDnsSecondary,
            lanBypassEnabled = uiState.lanBypassEnabled,
            customMtu = uiState.customMtu
        )
    }

    val cardPadding = if (isAndroidTv) 16.dp else 14.dp
    val statusColor = if (snapshot.isConnected) EmeraldProtected else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_details_stats_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Label + live uptime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = if (snapshot.isConnected) snapshot.uptimeText else snapshot.statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = statusColor
                    )
                }
            }

            // Server Endpoint Row (clickable to switch location)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(10.dp),
                        focusColor = ElectricCyan,
                        scaleOnFocus = 1.01f,
                        onClick = onOpenLocations
                    )
                    .testTag("connection_location_code_banner"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = snapshot.flagEmoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = snapshot.locationCode,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ElectricCyan,
                                modifier = Modifier.testTag("active_location_code_badge")
                            )
                            Text(
                                text = snapshot.locationName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${snapshot.hostname} · ${snapshot.endpoint}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (snapshot.countryCode.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = snapshot.countryCode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2x2 Swiss Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    label = "DOWN",
                    primaryValue = snapshot.downloadRateText,
                    secondaryValue = snapshot.downloadTotalText,
                    valueColor = if (snapshot.isConnected) EmeraldProtected else MaterialTheme.colorScheme.onSurface,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "UP",
                    primaryValue = snapshot.uploadRateText,
                    secondaryValue = snapshot.uploadTotalText,
                    valueColor = if (snapshot.isConnected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    label = "PING",
                    primaryValue = snapshot.latencyText,
                    secondaryValue = "${snapshot.secondaryStatLabel} ${snapshot.secondaryStatValue}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = snapshot.packetsOrQueriesLabel,
                    primaryValue = snapshot.packetsOrQueriesCount,
                    secondaryValue = snapshot.totalTrafficText,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
            }

            // Minimalist 30s Sparkline
            ThroughputSparklineCard(
                rxHistory = uiState.rxRateHistory,
                txHistory = uiState.txRateHistory,
                isConnected = snapshot.isConnected,
                isAndroidTv = isAndroidTv
            )

            // Active Per-App Multi-Location Tunnels (shown when multi-peer is running or configured)
            val livePeers = uiState.vpnSession.activePeerTunnels
            if (livePeers.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActiveMultiPeerList(peers = livePeers)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Crisp Spec Table
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPairRow(
                    leftLabel = "CODE",
                    leftValue = if (snapshot.countryCode.isNotBlank()) {
                        "${snapshot.locationCode} (${snapshot.countryCode})"
                    } else {
                        snapshot.locationCode
                    },
                    leftHighlight = ElectricCyan,
                    rightLabel = "PROTOCOL",
                    rightValue = snapshot.protocolLabel,
                    isAndroidTv = isAndroidTv
                )
                DetailPairRow(
                    leftLabel = "DNS",
                    leftValue = snapshot.dnsSummary,
                    rightLabel = "TUNNEL IP",
                    rightValue = snapshot.tunnelAddress,
                    isAndroidTv = isAndroidTv
                )
                DetailPairRow(
                    leftLabel = "APPS",
                    leftValue = if (uiState.appRouteRules.isNotEmpty()) {
                        "${snapshot.splitSummary} · ${uiState.appRouteRules.size} routed"
                    } else {
                        snapshot.splitSummary
                    },
                    rightLabel = "EXIT IP",
                    rightValue = snapshot.exitIpText,
                    isAndroidTv = isAndroidTv
                )
                DetailPairRow(
                    leftLabel = "LAN",
                    leftValue = snapshot.lanBypassText,
                    leftHighlight = if (uiState.lanBypassEnabled) EmeraldProtected else null,
                    rightLabel = "MTU",
                    rightValue = snapshot.mtuText,
                    isAndroidTv = isAndroidTv
                )
            }
        }
    }
}

@Composable
private fun ActiveMultiPeerList(
    peers: List<ActivePeerTunnelSummary>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_multi_peer_tunnels_list"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "ACTIVE MULTI-APP TUNNELS (${peers.size})",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = EmeraldProtected
        )
        peers.forEach { peer ->
            val traffic = ConnectionTelemetryFormatter.formatBytes(peer.bytesRx + peer.bytesTx)
            val appText = if (peer.isDefaultPeer) {
                "Default"
            } else {
                val count = peer.appPackages.size
                "$count ${if (count == 1) "app" else "apps"}"
            }
            val locationTitle = if (peer.cityName.isNotBlank()) {
                "${peer.flagEmoji} ${peer.cityName}, ${peer.countryCode}"
            } else {
                "${peer.flagEmoji} ${peer.countryName}"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldProtected)
                    )
                    Text(
                        text = peer.serverCodeTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElectricCyan
                    )
                    Text(
                        text = locationTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$appText · $traffic",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThroughputSparklineCard(
    rxHistory: List<Long>,
    txHistory: List<Long>,
    isConnected: Boolean,
    isAndroidTv: Boolean
) {
    val maxPeak = remember(rxHistory, txHistory) {
        val peak = maxOf(rxHistory.maxOrNull() ?: 0L, txHistory.maxOrNull() ?: 0L)
        peak.coerceAtLeast(1024L)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("throughput_sparkline_graph"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "30S RATE",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "● DL",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = EmeraldProtected
                    )
                    Text(
                        text = "● UL",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = ElectricCyan
                    )
                }
                Text(
                    text = if (isConnected) ConnectionTelemetryFormatter.formatRate(maxPeak) else "IDLE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isAndroidTv) 52.dp else 44.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                for (i in 0..2) {
                    val y = h * (i / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                val slots = 30
                val stepX = w / (slots - 1).coerceAtLeast(1)

                fun drawSeries(samples: List<Long>, strokeColor: Color) {
                    if (samples.isEmpty()) return
                    val padded = if (samples.size < slots) {
                        List(slots - samples.size) { 0L } + samples
                    } else {
                        samples.takeLast(slots)
                    }
                    val path = Path()
                    padded.forEachIndexed { idx, value ->
                        val x = idx * stepX
                        val ratio = (value.toFloat() / maxPeak.toFloat()).coerceIn(0f, 1f)
                        val y = h - (ratio * (h - 6f)) - 3f
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(
                            width = 2.2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                drawSeries(rxHistory, EmeraldProtected)
                drawSeries(txHistory, ElectricCyan)
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    primaryValue: String,
    secondaryValue: String,
    valueColor: Color,
    isAndroidTv: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (isAndroidTv) 10.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = primaryValue,
                style = (if (isAndroidTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall).copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryValue,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailPairRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    isAndroidTv: Boolean,
    leftHighlight: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = leftValue,
                style = (if (isAndroidTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall).copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (leftHighlight != null) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = leftHighlight ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = rightValue,
                style = (if (isAndroidTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall).copy(
                    fontFamily = JetBrainsMonoFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
