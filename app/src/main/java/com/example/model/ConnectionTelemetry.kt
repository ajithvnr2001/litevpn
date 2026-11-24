package com.example.model

data class ConnectionDetailsSnapshot(
    val isConnected: Boolean,
    val statusLabel: String,
    val locationCode: String,
    val locationName: String,
    val flagEmoji: String,
    val countryCode: String,
    val hostname: String,
    val endpoint: String,
    val tunnelAddress: String,
    val protocolLabel: String,
    val dnsSummary: String,
    val splitSummary: String,
    val latencyText: String,
    val serverLoadText: String,
    val uptimeText: String,
    val downloadRateText: String,
    val uploadRateText: String,
    val downloadTotalText: String,
    val uploadTotalText: String,
    val totalTrafficText: String,
    val packetsOrQueriesLabel: String,
    val packetsOrQueriesCount: String,
    val secondaryStatLabel: String,
    val secondaryStatValue: String,
    val exitIpText: String,
    val lanBypassText: String,
    val mtuText: String
)

object ConnectionTelemetryFormatter {

    fun buildSnapshot(
        session: VpnSessionState,
        selectedMode: ConnectionMode,
        recommendedServer: NordServer?,
        splitMode: SplitTunnelMode,
        splitAppCount: Int,
        elapsedSeconds: Long,
        measuredLatencyMs: Int?,
        liveTxBytesPerSec: Long,
        liveRxBytesPerSec: Long,
        networkTrace: NetworkTraceInfo?,
        dnsPreset: DnsPreset = DnsPreset.MODE_DEFAULT,
        customDnsPrimary: String = "",
        customDnsSecondary: String = "",
        lanBypassEnabled: Boolean = false,
        customMtu: Int = 1280
    ): ConnectionDetailsSnapshot {
        val isConnected = session.status == TunnelStatus.CONNECTED
        val effectiveMode = if (isConnected) session.mode else selectedMode
        val effectiveServer = if (effectiveMode.usesVpn) {
            (if (isConnected) session.activeServer else null) ?: recommendedServer
        } else {
            null
        }
        val effectiveSplitMode = if (isConnected) session.splitMode else splitMode
        val effectiveSplitCount = if (isConnected) session.splitAppCount else splitAppCount
        val effectiveLatency = (if (isConnected) session.latencyMs else null) ?: measuredLatencyMs
        val effectiveLanBypass = if (isConnected) session.lanBypassEnabled else lanBypassEnabled
        val effectiveMtu = if (isConnected) session.mtu else customMtu
        val (primaryDns, secondaryDns) = if (isConnected) {
            session.primaryDns to session.secondaryDns
        } else {
            resolveEffectiveDns(effectiveMode, dnsPreset, customDnsPrimary, customDnsSecondary)
        }

        val statusLabel = when (session.status) {
            TunnelStatus.CONNECTED -> if (session.isWireGuardVerified) "CONNECTED • WIREGUARD" else "CONNECTED • DNS"
            TunnelStatus.CONNECTING -> "HANDSHAKING"
            TunnelStatus.DISCONNECTING -> "DISCONNECTING"
            TunnelStatus.ERROR -> "ERROR"
            TunnelStatus.DISCONNECTED -> "STANDBY"
        }

        val locationCode = when {
            effectiveServer != null -> effectiveServer.serverCodeTag
            !effectiveMode.usesVpn -> primaryDns
            else -> "AUTO"
        }

        val locationName = when {
            effectiveServer != null -> effectiveServer.locationLabel
            !effectiveMode.usesVpn -> {
                if (dnsPreset == DnsPreset.MODE_DEFAULT) "${effectiveMode.dnsProviderLabel} Direct"
                else "${dnsPreset.title} Direct"
            }
            else -> "Fastest NordLynx Server"
        }

        val flagEmoji = effectiveServer?.flagEmoji ?: "\uD83C\uDF10"
        val countryCode = effectiveServer?.countryCode?.uppercase().orEmpty()
        val hostname = effectiveServer?.hostname ?: effectiveMode.shortTitle
        val endpoint = when {
            effectiveServer != null -> "${effectiveServer.stationIp}:51820"
            else -> "$primaryDns:53"
        }
        val tunnelAddress = if (effectiveMode.usesVpn) "10.5.0.2/32" else "10.215.173.1/32"
        val protocolLabel = if (effectiveMode.usesVpn) {
            "WireGuard (NordLynx)"
        } else {
            "DNS Tunnel ($primaryDns)"
        }
        val dnsSummary = if (primaryDns == secondaryDns) primaryDns else "$primaryDns / $secondaryDns"

        val splitSummary = when (effectiveSplitMode) {
            SplitTunnelMode.ALL_APPS -> "All Apps"
            SplitTunnelMode.EXCLUDE_APPS -> {
                val noun = if (effectiveSplitCount == 1) "app" else "apps"
                "Exclude ($effectiveSplitCount $noun)"
            }
            SplitTunnelMode.INCLUDE_ONLY_APPS -> {
                val noun = if (effectiveSplitCount == 1) "app" else "apps"
                "Include Only ($effectiveSplitCount $noun)"
            }
        }

        val packetsLabel = if (effectiveMode.usesVpn) "WG PACKETS" else "DNS QUERIES"
        val packetsCount = if (isConnected) formatCount(session.dnsQueriesForwarded) else "0"
        val secondaryLabel = if (effectiveMode.usesVpn) "SERVER LOAD" else "CACHE HITS"
        val secondaryValue = if (effectiveMode.usesVpn) {
            effectiveServer?.let { "${it.loadPercent}%" } ?: "—"
        } else {
            if (isConnected) formatCount(session.dnsCacheHits) else "0"
        }

        val exitIpText = networkTrace?.let { t ->
            if (t.ip.isNotBlank()) {
                buildString {
                    append(t.ip)
                    if (t.countryCode.isNotBlank()) append(" (${t.countryCode})")
                    if (t.colo.isNotBlank()) append(" • ${t.colo}")
                }
            } else {
                null
            }
        } ?: "—"

        return ConnectionDetailsSnapshot(
            isConnected = isConnected,
            statusLabel = statusLabel,
            locationCode = locationCode,
            locationName = locationName,
            flagEmoji = flagEmoji,
            countryCode = countryCode,
            hostname = hostname,
            endpoint = endpoint,
            tunnelAddress = tunnelAddress,
            protocolLabel = protocolLabel,
            dnsSummary = dnsSummary,
            splitSummary = splitSummary,
            latencyText = effectiveLatency?.let { "$it ms" } ?: "—",
            serverLoadText = effectiveServer?.let { "${it.loadPercent}%" } ?: "—",
            uptimeText = if (isConnected) formatDuration(elapsedSeconds) else "00:00",
            downloadRateText = if (isConnected) formatRate(liveRxBytesPerSec) else "0 B/s",
            uploadRateText = if (isConnected) formatRate(liveTxBytesPerSec) else "0 B/s",
            downloadTotalText = formatBytes(session.bytesRx),
            uploadTotalText = formatBytes(session.bytesTx),
            totalTrafficText = formatBytes(session.bytesTx + session.bytesRx),
            packetsOrQueriesLabel = packetsLabel,
            packetsOrQueriesCount = packetsCount,
            secondaryStatLabel = secondaryLabel,
            secondaryStatValue = secondaryValue,
            exitIpText = exitIpText,
            lanBypassText = if (effectiveLanBypass) "LAN Bypass ON" else "Strict Tunnel",
            mtuText = "MTU $effectiveMtu"
        )
    }

    fun formatDuration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        val s = safe % 60L
        return if (h > 0L) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> "%.1f KB".format(safe / 1024.0)
            safe < 1024L * 1024L * 1024L -> "%.2f MB".format(safe / (1024.0 * 1024.0))
            else -> "%.2f GB".format(safe / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatRate(bytesPerSec: Long): String {
        val safe = bytesPerSec.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B/s"
            safe < 1024L * 1024L -> "%.1f KB/s".format(safe / 1024.0)
            else -> "%.2f MB/s".format(safe / (1024.0 * 1024.0))
        }
    }

    fun formatCount(count: Long): String {
        val safe = count.coerceAtLeast(0L)
        return when {
            safe < 1_000L -> safe.toString()
            safe < 1_000_000L -> "%.1fK".format(safe / 1_000.0)
            else -> "%.2fM".format(safe / 1_000_000.0)
        }
    }
}
