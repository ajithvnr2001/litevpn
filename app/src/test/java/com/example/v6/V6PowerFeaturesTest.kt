package com.example.v6

import com.example.data.AppConfigBackup
import com.example.data.ConfigBackupCodec
import com.example.model.ConnectionMode
import com.example.model.ConnectionTelemetryFormatter
import com.example.model.DnsPreset
import com.example.model.NordServer
import com.example.model.SavedVpnProfile
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import com.example.model.VpnSessionState
import com.example.model.isValidIpv4Address
import com.example.model.resolveEffectiveDns
import com.example.vpn.LanBypassRouteCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V6PowerFeaturesTest {

    @Test
    fun lanBypassComplementRoutesExcludeAllPrivateAndMulticastIps() {
        val publicRoutes = LanBypassRouteCalculator.publicInternetRoutes
        assertTrue("Complement route table should be non-empty", publicRoutes.isNotEmpty())
        assertTrue("Every route prefix must be within 0..32", publicRoutes.all { it.prefixLength in 0..32 })

        val mustBypassLanIps = listOf(
            "10.0.0.1",
            "10.255.255.254",
            "127.0.0.1",
            "169.254.1.1",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.0.1",
            "192.168.1.50",
            "224.0.0.251", // mDNS / Chromecast
            "239.255.255.250", // SSDP / DLNA
            "255.255.255.255"
        )
        for (ip in mustBypassLanIps) {
            assertFalse("LAN/multicast IP $ip must NOT be in public VPN routes", LanBypassRouteCalculator.containsIp(publicRoutes, ip))
            assertTrue("LAN/multicast IP $ip MUST be in excluded subnets", LanBypassRouteCalculator.containsIp(LanBypassRouteCalculator.excludedLanSubnets, ip))
        }
    }

    @Test
    fun lanBypassComplementRoutesIncludeAllPublicInternetIps() {
        val publicRoutes = LanBypassRouteCalculator.publicInternetRoutes
        val mustTunnelPublicIps = listOf(
            "1.1.1.1",
            "1.0.0.1",
            "8.8.8.8",
            "9.9.9.9",
            "94.140.14.14",
            "103.86.96.100",
            "142.250.190.46",
            "172.15.255.255", // Just below 172.16.0.0/12
            "172.32.0.1",     // Just above 172.16.0.0/12
            "192.167.255.255",
            "192.169.0.1",
            "198.51.100.12"
        )
        for (ip in mustTunnelPublicIps) {
            assertTrue("Public IP $ip MUST route through VPN", LanBypassRouteCalculator.containsIp(publicRoutes, ip))
        }
    }

    @Test
    fun ipv4ValidatorAndDnsPresetResolverWorkAccurately() {
        assertTrue(isValidIpv4Address("1.1.1.1"))
        assertTrue(isValidIpv4Address("94.140.14.14"))
        assertTrue(isValidIpv4Address("0.0.0.0"))
        assertTrue(isValidIpv4Address("255.255.255.255"))

        assertFalse(isValidIpv4Address(""))
        assertFalse(isValidIpv4Address("999.1.1.1"))
        assertFalse(isValidIpv4Address("01.2.3.4"))
        assertFalse(isValidIpv4Address("1.2.3"))
        assertFalse(isValidIpv4Address("1.2.3.4.5"))
        assertFalse(isValidIpv4Address("dns.google"))

        assertEquals(
            "94.140.14.14" to "94.140.15.15",
            resolveEffectiveDns(ConnectionMode.CLOUDFLARE_PLUS_VPN, DnsPreset.ADGUARD_ADS, "", "")
        )
        assertEquals(
            "9.9.9.9" to "149.112.112.112",
            resolveEffectiveDns(ConnectionMode.GOOGLE_ONLY, DnsPreset.QUAD9_SECURITY, "", "")
        )
        assertEquals(
            "45.90.28.10" to "45.90.30.10",
            resolveEffectiveDns(ConnectionMode.CLOUDFLARE_PLUS_VPN, DnsPreset.CUSTOM, "45.90.28.10", "45.90.30.10")
        )
        // Invalid custom DNS falls back cleanly to active routing mode DNS
        assertEquals(
            "8.8.8.8" to "8.8.8.8",
            resolveEffectiveDns(ConnectionMode.GOOGLE_PLUS_VPN, DnsPreset.CUSTOM, "invalid-ip", "")
        )
    }

    @Test
    fun configBackupCodecRoundTripsCleanlyAndExcludesSecrets() {
        val profile = SavedVpnProfile(
            id = "tv-us-1",
            name = "TV Streaming + AdGuard",
            modeId = ConnectionMode.CLOUDFLARE_PLUS_VPN.id,
            dnsPresetId = DnsPreset.ADGUARD_ADS.id,
            customDnsPrimary = "",
            customDnsSecondary = "",
            countryId = 228,
            cityName = "Los Angeles",
            pinnedServerHostname = "us13084.nordvpn.com",
            pinnedServerCode = "#13084",
            splitModeId = SplitTunnelMode.INCLUDE_ONLY_APPS.id,
            splitPackages = setOf("com.brave.browser", "com.netflix.ninja"),
            lanBypassEnabled = true,
            mtu = 1360
        )
        val original = AppConfigBackup(
            connectionModeId = ConnectionMode.GOOGLE_PLUS_VPN.id,
            dnsPresetId = DnsPreset.CUSTOM.id,
            customDnsPrimary = "45.90.28.10",
            customDnsSecondary = "45.90.30.10",
            selectedCountryId = 228,
            selectedCityName = "Charlotte",
            pinnedServerHostname = "us12790.nordvpn.com",
            favoriteCountryIds = setOf(2, 228),
            splitModeId = SplitTunnelMode.EXCLUDE_APPS.id,
            excludedPackages = setOf("com.google.android.apps.chromecast.app"),
            includedPackages = setOf("com.brave.browser"),
            autoConnectOnBoot = true,
            lanBypassEnabled = true,
            autoFailoverEnabled = true,
            customMtu = 1400,
            savedProfiles = listOf(profile)
        )

        val encoded = ConfigBackupCodec.encode(original)
        assertTrue(encoded.contains("\"secretsExcluded\": true"))
        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("privateKey", ignoreCase = true))

        val decoded = ConfigBackupCodec.decode(encoded).getOrThrow()
        assertEquals(original.connectionModeId, decoded.connectionModeId)
        assertEquals(original.dnsPresetId, decoded.dnsPresetId)
        assertEquals("45.90.28.10", decoded.customDnsPrimary)
        assertEquals("45.90.30.10", decoded.customDnsSecondary)
        assertEquals(228, decoded.selectedCountryId)
        assertEquals("Charlotte", decoded.selectedCityName)
        assertEquals("us12790.nordvpn.com", decoded.pinnedServerHostname)
        assertEquals(setOf(2, 228), decoded.favoriteCountryIds)
        assertEquals(SplitTunnelMode.EXCLUDE_APPS.id, decoded.splitModeId)
        assertEquals(original.excludedPackages, decoded.excludedPackages)
        assertEquals(original.includedPackages, decoded.includedPackages)
        assertTrue(decoded.lanBypassEnabled)
        assertTrue(decoded.autoFailoverEnabled)
        assertEquals(1400, decoded.customMtu)
        assertEquals(1, decoded.savedProfiles.size)
        assertEquals("TV Streaming + AdGuard", decoded.savedProfiles.first().name)
        assertEquals("#13084", decoded.savedProfiles.first().pinnedServerCode)
        assertEquals(1360, decoded.savedProfiles.first().mtu)
    }

    @Test
    fun configBackupCodecClampsOutOfRangeMtuAndSanitizesBadDns() {
        val raw = """
            {
              "schemaVersion": 1,
              "connectionModeId": "cf_vpn",
              "dnsPresetId": "custom",
              "customDnsPrimary": "999.999.1.1",
              "customDnsSecondary": "not_an_ip",
              "customMtu": 9000,
              "savedProfiles": [
                {
                  "id": "p1",
                  "name": "Low MTU Profile",
                  "modeId": "cf_vpn",
                  "mtu": 500
                }
              ]
            }
        """.trimIndent()

        val decoded = ConfigBackupCodec.decode(raw).getOrThrow()
        assertEquals("", decoded.customDnsPrimary)
        assertEquals("", decoded.customDnsSecondary)
        assertEquals(ConfigBackupCodec.MAX_MTU, decoded.customMtu)
        assertEquals(ConfigBackupCodec.MIN_MTU, decoded.savedProfiles.first().mtu)
    }

    @Test
    fun telemetryFormatterReflectsAdGuardLanBypassAndCustomMtu() {
        val server = NordServer(
            id = 13084,
            name = "United States #13084",
            hostname = "us13084.nordvpn.com",
            stationIp = "198.51.100.12",
            loadPercent = 14,
            countryName = "United States",
            countryCode = "US",
            cityName = "Los Angeles",
            wireGuardPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        )
        val snap = ConnectionTelemetryFormatter.buildSnapshot(
            session = VpnSessionState(status = TunnelStatus.DISCONNECTED),
            selectedMode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
            recommendedServer = server,
            splitMode = SplitTunnelMode.ALL_APPS,
            splitAppCount = 0,
            elapsedSeconds = 0L,
            measuredLatencyMs = 24,
            liveTxBytesPerSec = 0L,
            liveRxBytesPerSec = 0L,
            networkTrace = null,
            dnsPreset = DnsPreset.ADGUARD_ADS,
            lanBypassEnabled = true,
            customMtu = 1360
        )

        assertEquals("94.140.14.14 / 94.140.15.15", snap.dnsSummary)
        assertEquals("LAN Bypass ON", snap.lanBypassText)
        assertEquals("MTU 1360", snap.mtuText)
    }
}
