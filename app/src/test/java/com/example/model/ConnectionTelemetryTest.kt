package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTelemetryTest {

    private val laServer = NordServer(
        id = 13084,
        name = "United States #13084",
        hostname = "us13084.nordvpn.com",
        stationIp = "198.51.100.12",
        loadPercent = 18,
        countryName = "United States",
        countryCode = "US",
        cityName = "Los Angeles",
        wireGuardPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    )

    @Test
    fun buildsConnectedWireGuardSnapshotWithLocationCodeAndLiveStats() {
        val session = VpnSessionState(
            status = TunnelStatus.CONNECTED,
            mode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
            connectedAtMillis = 1_000L,
            dnsQueriesForwarded = 2_450L,
            dnsCacheHits = 0L,
            bytesTx = 2L * 1024L * 1024L,
            bytesRx = 15L * 1024L * 1024L,
            primaryDns = "1.1.1.1",
            secondaryDns = "1.0.0.1",
            activeServer = laServer,
            splitMode = SplitTunnelMode.INCLUDE_ONLY_APPS,
            splitAppCount = 1,
            latencyMs = 34,
            isWireGuardVerified = true
        )

        val snap = ConnectionTelemetryFormatter.buildSnapshot(
            session = session,
            selectedMode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
            recommendedServer = laServer,
            splitMode = SplitTunnelMode.INCLUDE_ONLY_APPS,
            splitAppCount = 1,
            elapsedSeconds = 3_665L,
            measuredLatencyMs = 45,
            liveTxBytesPerSec = 512L * 1024L,
            liveRxBytesPerSec = 3L * 1024L * 1024L,
            networkTrace = NetworkTraceInfo(ip = "198.51.100.12", countryCode = "US", colo = "LAX")
        )

        assertTrue(snap.isConnected)
        assertEquals("CONNECTED • WIREGUARD", snap.statusLabel)
        assertEquals("#13084", snap.locationCode)
        assertEquals("Los Angeles, United States", snap.locationName)
        assertEquals("US", snap.countryCode)
        assertEquals("us13084.nordvpn.com", snap.hostname)
        assertEquals("198.51.100.12:51820", snap.endpoint)
        assertEquals("10.5.0.2/32", snap.tunnelAddress)
        assertEquals("WireGuard (NordLynx)", snap.protocolLabel)
        assertEquals("1.1.1.1 / 1.0.0.1", snap.dnsSummary)
        assertEquals("Include Only (1 app)", snap.splitSummary)
        assertEquals("34 ms", snap.latencyText)
        assertEquals("18%", snap.serverLoadText)
        assertEquals("01:01:05", snap.uptimeText)
        assertEquals("3.00 MB/s", snap.downloadRateText)
        assertEquals("512.0 KB/s", snap.uploadRateText)
        assertEquals("15.00 MB", snap.downloadTotalText)
        assertEquals("2.00 MB", snap.uploadTotalText)
        assertEquals("17.00 MB", snap.totalTrafficText)
        assertEquals("WG PACKETS", snap.packetsOrQueriesLabel)
        assertEquals("2.5K", snap.packetsOrQueriesCount)
        assertEquals("SERVER LOAD", snap.secondaryStatLabel)
        assertEquals("18%", snap.secondaryStatValue)
        assertEquals("198.51.100.12 (US) • LAX", snap.exitIpText)
    }

    @Test
    fun buildsDnsOnlySnapshotWithCacheHitsAndDirectEndpoint() {
        val session = VpnSessionState(
            status = TunnelStatus.CONNECTED,
            mode = ConnectionMode.CLOUDFLARE_ONLY,
            dnsQueriesForwarded = 412L,
            dnsCacheHits = 185L,
            bytesTx = 42_000L,
            bytesRx = 96_000L,
            primaryDns = "1.1.1.1",
            secondaryDns = "1.0.0.1",
            splitMode = SplitTunnelMode.ALL_APPS,
            latencyMs = 12,
            isWireGuardVerified = false
        )

        val snap = ConnectionTelemetryFormatter.buildSnapshot(
            session = session,
            selectedMode = ConnectionMode.CLOUDFLARE_ONLY,
            recommendedServer = laServer,
            splitMode = SplitTunnelMode.ALL_APPS,
            splitAppCount = 0,
            elapsedSeconds = 95L,
            measuredLatencyMs = 12,
            liveTxBytesPerSec = 512L,
            liveRxBytesPerSec = 2048L,
            networkTrace = null
        )

        assertTrue(snap.isConnected)
        assertEquals("CONNECTED • DNS", snap.statusLabel)
        assertEquals("1.1.1.1", snap.locationCode)
        assertEquals("1.1.1.1:53", snap.endpoint)
        assertEquals("10.215.173.1/32", snap.tunnelAddress)
        assertEquals("DNS QUERIES", snap.packetsOrQueriesLabel)
        assertEquals("412", snap.packetsOrQueriesCount)
        assertEquals("CACHE HITS", snap.secondaryStatLabel)
        assertEquals("185", snap.secondaryStatValue)
        assertEquals("01:35", snap.uptimeText)
    }

    @Test
    fun standbySnapshotShowsPointedLocationCodeAndZeroLiveRates() {
        val charlotteServer = laServer.copy(
            id = 12790,
            name = "United States #12790",
            hostname = "us12790.nordvpn.com",
            cityName = "Charlotte"
        )
        val snap = ConnectionTelemetryFormatter.buildSnapshot(
            session = VpnSessionState(status = TunnelStatus.DISCONNECTED),
            selectedMode = ConnectionMode.GOOGLE_PLUS_VPN,
            recommendedServer = charlotteServer,
            splitMode = SplitTunnelMode.EXCLUDE_APPS,
            splitAppCount = 3,
            elapsedSeconds = 0L,
            measuredLatencyMs = 28,
            liveTxBytesPerSec = 9_999L,
            liveRxBytesPerSec = 9_999L,
            networkTrace = null
        )

        assertFalse(snap.isConnected)
        assertEquals("STANDBY", snap.statusLabel)
        assertEquals("#12790", snap.locationCode)
        assertEquals("Charlotte, United States", snap.locationName)
        assertEquals("8.8.8.8 / 8.8.4.4", snap.dnsSummary)
        assertEquals("Exclude (3 apps)", snap.splitSummary)
        assertEquals("0 B/s", snap.downloadRateText)
        assertEquals("0 B/s", snap.uploadRateText)
        assertEquals("00:00", snap.uptimeText)
    }

    @Test
    fun formattersHandleEdgeValuesCleanly() {
        assertEquals("0 B", ConnectionTelemetryFormatter.formatBytes(-50L))
        assertEquals("512 B", ConnectionTelemetryFormatter.formatBytes(512L))
        assertEquals("1.50 GB", ConnectionTelemetryFormatter.formatBytes(1_610_612_736L))
        assertEquals("0 B/s", ConnectionTelemetryFormatter.formatRate(0L))
        assertEquals("1.50M", ConnectionTelemetryFormatter.formatCount(1_500_000L))
    }
}
