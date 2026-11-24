package com.example.v7

import com.example.data.AppConfigBackup
import com.example.data.ConfigBackupCodec
import com.example.model.AppRouteRule
import com.example.model.ConnectionMode
import com.example.model.DnsPreset
import com.example.model.SplitTunnelMode
import com.example.vpn.AppPeerRouteTable
import com.example.vpn.FlowKey
import com.example.vpn.Ipv4PacketInspector
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V7MultiAppTunnelTest {

    private fun buildIpv4TransportPacket(
        protocol: Int,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        ihlWords: Int = 5
    ): ByteArray {
        val headerLen = ihlWords * 4
        val packet = ByteArray(headerLen + 20)
        packet[0] = ((4 shl 4) or (ihlWords and 0x0F)).toByte()
        packet[9] = protocol.toByte()
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)
        packet[headerLen] = ((srcPort ushr 8) and 0xFF).toByte()
        packet[headerLen + 1] = (srcPort and 0xFF).toByte()
        packet[headerLen + 2] = ((dstPort ushr 8) and 0xFF).toByte()
        packet[headerLen + 3] = (dstPort and 0xFF).toByte()
        return packet
    }

    @Test
    fun `extractFlowTuple parses standard IPv4 TCP and UDP headers`() {
        val tcpPacket = buildIpv4TransportPacket(
            protocol = Ipv4PacketInspector.PROTO_TCP,
            srcIp = byteArrayOf(10, 5, 0, 2),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 54321,
            dstPort = 443
        )
        val tcpFlow = Ipv4PacketInspector.extractFlowTuple(tcpPacket, tcpPacket.size)
        assertNotNull(tcpFlow)
        assertEquals(Ipv4PacketInspector.PROTO_TCP, tcpFlow!!.protocol)
        assertEquals(54321, tcpFlow.srcPort)
        assertEquals(443, tcpFlow.dstPort)
        assertEquals("10.5.0.2", InetAddress.getByAddress(tcpFlow.srcIpBytes).hostAddress)
        assertEquals("1.1.1.1", InetAddress.getByAddress(tcpFlow.dstIpBytes).hostAddress)

        val udpPacket = buildIpv4TransportPacket(
            protocol = Ipv4PacketInspector.PROTO_UDP,
            srcIp = byteArrayOf(10, 5, 0, 2),
            dstIp = byteArrayOf(8, 8, 8, 8),
            srcPort = 41000,
            dstPort = 53
        )
        val udpFlow = Ipv4PacketInspector.extractFlowTuple(udpPacket, udpPacket.size)
        assertNotNull(udpFlow)
        assertEquals(Ipv4PacketInspector.PROTO_UDP, udpFlow!!.protocol)
        assertEquals(41000, udpFlow.srcPort)
        assertEquals(53, udpFlow.dstPort)
    }

    @Test
    fun `extractFlowTuple handles IPv4 header with IP options (IHL=6)`() {
        val packetWithOptions = buildIpv4TransportPacket(
            protocol = Ipv4PacketInspector.PROTO_TCP,
            srcIp = byteArrayOf(10, 5, 0, 2),
            dstIp = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34),
            srcPort = 60001,
            dstPort = 80,
            ihlWords = 6
        )
        val flow = Ipv4PacketInspector.extractFlowTuple(packetWithOptions, packetWithOptions.size)
        assertNotNull(flow)
        assertEquals(60001, flow!!.srcPort)
        assertEquals(80, flow.dstPort)
    }

    @Test
    fun `extractFlowTuple ignores ICMP, IPv6, and truncated packets`() {
        val icmpPacket = buildIpv4TransportPacket(
            protocol = Ipv4PacketInspector.PROTO_ICMP,
            srcIp = byteArrayOf(10, 5, 0, 2),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 0,
            dstPort = 0
        )
        assertNull(Ipv4PacketInspector.extractFlowTuple(icmpPacket, icmpPacket.size))

        val ipv6Packet = ByteArray(48)
        ipv6Packet[0] = (6 shl 4).toByte()
        assertNull(Ipv4PacketInspector.extractFlowTuple(ipv6Packet, ipv6Packet.size))

        val truncated = ByteArray(18)
        truncated[0] = 0x45
        assertNull(Ipv4PacketInspector.extractFlowTuple(truncated, truncated.size))
    }

    @Test
    fun `AppPeerRouteTable routes distinct app UIDs to simultaneous country peers and caches flows`() {
        val uidIndia = 10101
        val uidUs = 10202
        val uidOther = 10303

        val table = AppPeerRouteTable(
            defaultPeer = "DEFAULT_ALBANIA",
            uidToPeerMap = mapOf(
                uidIndia to "INDIA_PEER",
                uidUs to "US_PEER"
            ),
            maxCachedFlows = 64
        )

        val braveFlow = FlowKey(
            protocol = Ipv4PacketInspector.PROTO_TCP,
            srcPort = 50001,
            dstIpInt = 0x01010101,
            dstPort = 443
        )
        val netflixFlow = FlowKey(
            protocol = Ipv4PacketInspector.PROTO_TCP,
            srcPort = 50002,
            dstIpInt = 0x08080808,
            dstPort = 443
        )
        val systemFlow = FlowKey(
            protocol = Ipv4PacketInspector.PROTO_UDP,
            srcPort = 50003,
            dstIpInt = 0x09090909,
            dstPort = 53
        )

        var lookupCount = 0
        fun resolve(uid: Int): Int {
            lookupCount++
            return uid
        }

        // First packets trigger UID resolution
        assertEquals("INDIA_PEER", table.routeFlow(braveFlow) { resolve(uidIndia) })
        assertEquals("US_PEER", table.routeFlow(netflixFlow) { resolve(uidUs) })
        assertEquals("DEFAULT_ALBANIA", table.routeFlow(systemFlow) { resolve(uidOther) })
        assertEquals(3, lookupCount)

        // Subsequent packets on same TCP/UDP 4-tuples hit cache with 0 extra UID lookups
        assertEquals("INDIA_PEER", table.routeFlow(braveFlow) { resolve(uidIndia) })
        assertEquals("US_PEER", table.routeFlow(netflixFlow) { resolve(uidUs) })
        assertEquals("DEFAULT_ALBANIA", table.routeFlow(systemFlow) { resolve(uidOther) })
        assertEquals(3, lookupCount)
        assertEquals(3, table.cachedFlowCount())
    }

    @Test
    fun `AppPeerRouteTable enforces bounded flow cache eviction`() {
        val table = AppPeerRouteTable(
            defaultPeer = "DEFAULT",
            uidToPeerMap = mapOf(10001 to "IN_PEER"),
            maxCachedFlows = 10
        )

        for (port in 1000 until 1025) {
            val flow = FlowKey(
                protocol = Ipv4PacketInspector.PROTO_TCP,
                srcPort = port,
                dstIpInt = 0x01010101,
                dstPort = 443
            )
            assertEquals("IN_PEER", table.routeFlow(flow) { 10001 })
        }

        assertTrue("Cache size should remain bounded", table.cachedFlowCount() <= 10)
    }

    @Test
    fun `ConfigBackupCodec v7 round-trips per-app multi-location rules without secrets`() {
        val rules = listOf(
            AppRouteRule(
                packageName = "com.brave.browser",
                countryId = 100,
                countryName = "India",
                countryCode = "IN"
            ),
            AppRouteRule(
                packageName = "com.netflix.ninja",
                countryId = 228,
                countryName = "United States",
                countryCode = "US",
                cityName = "Los Angeles",
                pinnedServerHostname = "us13084.nordvpn.com",
                pinnedServerCode = "#13084"
            )
        )

        val backup = AppConfigBackup(
            connectionModeId = ConnectionMode.CLOUDFLARE_PLUS_VPN.id,
            dnsPresetId = DnsPreset.ADGUARD_ADS.id,
            customDnsPrimary = "",
            customDnsSecondary = "",
            selectedCountryId = 2,
            selectedCityName = "Tirana",
            pinnedServerHostname = null,
            favoriteCountryIds = setOf(2, 100, 228),
            splitModeId = SplitTunnelMode.ALL_APPS.id,
            excludedPackages = emptySet(),
            includedPackages = emptySet(),
            autoConnectOnBoot = true,
            lanBypassEnabled = true,
            autoFailoverEnabled = true,
            customMtu = 1400,
            savedProfiles = emptyList(),
            appRouteRules = rules
        )

        val json = ConfigBackupCodec.encode(backup)
        assertTrue(json.contains("\"secretsExcluded\": true"))
        assertTrue(json.contains("com.brave.browser"))
        assertTrue(json.contains("com.netflix.ninja"))

        val decoded = ConfigBackupCodec.decode(json).getOrThrow()
        assertEquals(2, decoded.appRouteRules.size)
        assertEquals("IN", decoded.appRouteRules[0].countryCode)
        assertEquals("India", decoded.appRouteRules[0].countryName)
        assertEquals("US", decoded.appRouteRules[1].countryCode)
        assertEquals("Los Angeles", decoded.appRouteRules[1].cityName)
        assertEquals("#13084", decoded.appRouteRules[1].pinnedServerCode)
    }
}
