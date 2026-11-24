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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    val cardPadding = if (isAndroidTv) 18.dp else 16.dp
    val statusColor = if (snapshot.isConnected) EmeraldProtected else accentColor

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_details_stats_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (snapshot.isConnected) 1.4.dp else 1.dp,
            color = statusColor.copy(alpha = if (snapshot.isConnected) 0.55f else 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar: Title + Live Status Dot + Session Uptime
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
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = "Connection Details & Stats",
                        style = if (isAndroidTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = if (snapshot.isConnected) "LIVE ${snapshot.uptimeText}" else snapshot.statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Highlighted Location + Server Code Strip (clickable/focusable to switch server)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(16.dp),
                        focusColor = ElectricCyan,
                        scaleOnFocus = 1.015f,
                        onClick = onOpenLocations
                    )
                    .testTag("connection_location_code_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.32f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = snapshot.flagEmoji,
                        style = if (isAndroidTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = ElectricCyan.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                                modifier = Modifier.testTag("active_location_code_badge")
                            ) {
                                Text(
                                    text = snapshot.locationCode,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = snapshot.locationName,
                                style = if (isAndroidTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${snapshot.hostname} • ${snapshot.endpoint}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (snapshot.countryCode.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = snapshot.countryCode,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Live Throughput & Traffic Telemetry Grid (2x2 tiles for crisp readability on Phone & TV)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "↓ DOWNLOAD",
                    primaryValue = snapshot.downloadRateText,
                    secondaryValue = "Total ${snapshot.downloadTotalText}",
                    valueColor = EmeraldProtected,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "↑ UPLOAD",
                    primaryValue = snapshot.uploadRateText,
                    secondaryValue = "Total ${snapshot.uploadTotalText}",
                    valueColor = ElectricCyan,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "LATENCY",
                    primaryValue = snapshot.latencyText,
                    secondaryValue = "${snapshot.secondaryStatLabel}: ${snapshot.secondaryStatValue}",
                    valueColor = CloudflareAmber,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = snapshot.packetsOrQueriesLabel,
                    primaryValue = snapshot.packetsOrQueriesCount,
                    secondaryValue = "Session ${snapshot.totalTrafficText}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    isAndroidTv = isAndroidTv,
                    modifier = Modifier.weight(1f)
                )
            }

            // Live 30-second Throughput Sparkline Graph (pure Compose Canvas, 0 extra deps)
            ThroughputSparklineCard(
                rxHistory = uiState.rxRateHistory,
                txHistory = uiState.txRateHistory,
                isConnected = snapshot.isConnected,
                isAndroidTv = isAndroidTv
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Structured Connection Specifications (2-column compact rows)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPairRow(
                    leftLabel = "LOCATION CODE",
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
                    leftLabel = "DNS SERVERS",
                    leftValue = snapshot.dnsSummary,
                    rightLabel = "TUNNEL IP",
                    rightValue = snapshot.tunnelAddress,
                    isAndroidTv = isAndroidTv
                )
                DetailPairRow(
                    leftLabel = "SPLIT TUNNEL",
                    leftValue = snapshot.splitSummary,
                    rightLabel = "EXIT / TRACE IP",
                    rightValue = snapshot.exitIpText,
                    isAndroidTv = isAndroidTv
                )
                DetailPairRow(
                    leftLabel = "LAN ROUTING",
                    leftValue = snapshot.lanBypassText,
                    leftHighlight = if (uiState.lanBypassEnabled) EmeraldProtected else null,
                    rightLabel = "INTERFACE MTU",
                    rightValue = snapshot.mtuText,
                    isAndroidTv = isAndroidTv
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
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("throughput_sparkline_graph"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        text = "LIVE THROUGHPUT (30s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldProtected)
                        )
                        Text(
                            text = "DL",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldProtected
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                        )
                        Text(
                            text = "UL",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
                Text(
                    text = if (isConnected) "Peak ${ConnectionTelemetryFormatter.formatRate(maxPeak)}" else "Idle",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isAndroidTv) 64.dp else 54.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // Subtle 3-tier horizontal gridlines
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

                fun drawSeries(samples: List<Long>, strokeColor: Color, fillArea: Boolean) {
                    if (samples.isEmpty()) return
                    val padded = if (samples.size < slots) {
                        List(slots - samples.size) { 0L } + samples
                    } else {
                        samples.takeLast(slots)
                    }
                    val strokePath = Path()
                    val fillPath = Path()
                    padded.forEachIndexed { idx, value ->
                        val x = idx * stepX
                        val ratio = (value.toFloat() / maxPeak.toFloat()).coerceIn(0f, 1f)
                        val y = h - (ratio * (h - 6f)) - 3f
                        if (idx == 0) {
                            strokePath.moveTo(x, y)
                            if (fillArea) {
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            }
                        } else {
                            strokePath.lineTo(x, y)
                            if (fillArea) fillPath.lineTo(x, y)
                        }
                    }
                    if (fillArea) {
                        fillPath.lineTo(w, h)
                        fillPath.close()
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    strokeColor.copy(alpha = 0.28f),
                                    strokeColor.copy(alpha = 0.02f)
                                ),
                                startY = 0f,
                                endY = h
                            )
                        )
                    }
                    drawPath(
                        path = strokePath,
                        color = strokeColor,
                        style = Stroke(
                            width = 2.6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                drawSeries(rxHistory, EmeraldProtected, fillArea = true)
                drawSeries(txHistory, ElectricCyan, fillArea = false)
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isAndroidTv) 12.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = primaryValue,
                style = (if (isAndroidTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall).copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = leftValue,
                style = (if (isAndroidTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall).copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (leftHighlight != null) FontWeight.Bold else FontWeight.Medium
                ),
                color = leftHighlight ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = rightValue,
                style = (if (isAndroidTv) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall).copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
