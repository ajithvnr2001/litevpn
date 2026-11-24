package com.example.vpn

data class Ipv4Cidr(
    val address: String,
    val prefixLength: Int
)

/**
 * Pure-Kotlin IPv4 CIDR calculator for Local Network (LAN) Bypass.
 *
 * - On Android 13+ (API 33+), SwiftTunnelVpnService uses native VpnService.Builder.excludeRoute()
 *   with [excludedLanSubnets] while keeping 0.0.0.0/0.
 * - On Android 7–12 (API 24–32, including many Android TV boxes), SwiftTunnelVpnService adds
 *   [publicInternetRoutes] (exact complement of 0.0.0.0/0 minus private LAN/link-local/multicast)
 *   so Chromecast, Plex/Jellyfin, SMB/NAS, and ADB over Wi-Fi bypass the VPN cleanly while 100%
 *   of public internet traffic routes through WireGuard.
 */
object LanBypassRouteCalculator {

    val excludedLanSubnets: List<Ipv4Cidr> = listOf(
        Ipv4Cidr("10.0.0.0", 8),         // RFC 1918 Class A LAN
        Ipv4Cidr("127.0.0.0", 8),        // Loopback
        Ipv4Cidr("169.254.0.0", 16),     // Link-local (mDNS/Bonjour)
        Ipv4Cidr("172.16.0.0", 12),      // RFC 1918 Class B LAN
        Ipv4Cidr("192.168.0.0", 16),     // RFC 1918 Class C Home/TV LAN
        Ipv4Cidr("224.0.0.0", 4),        // Multicast (Chromecast/DLNA/SSDP)
        Ipv4Cidr("240.0.0.0", 4)         // Reserved & 255.255.255.255 broadcast
    )

    val publicInternetRoutes: List<Ipv4Cidr> by lazy {
        computeComplementRoutes(excludedLanSubnets)
    }

    fun computeComplementRoutes(excluded: List<Ipv4Cidr>): List<Ipv4Cidr> {
        val excludedRanges = excluded
            .map { cidrToRange(it) }
            .sortedBy { it.first }

        // Merge overlapping/adjacent excluded ranges
        val merged = ArrayList<Pair<Long, Long>>()
        for (range in excludedRanges) {
            if (merged.isEmpty() || range.first > merged.last().second + 1L) {
                merged.add(range)
            } else {
                val prev = merged.removeAt(merged.lastIndex)
                merged.add(prev.first to maxOf(prev.second, range.second))
            }
        }

        val allowed = ArrayList<Pair<Long, Long>>()
        var cursor = 0L
        val maxIpv4 = 0xFFFFFFFFL
        for ((exStart, exEnd) in merged) {
            if (exStart > cursor) {
                allowed.add(cursor to (exStart - 1L))
            }
            if (exEnd >= maxIpv4) {
                cursor = maxIpv4 + 1L
                break
            }
            cursor = maxOf(cursor, exEnd + 1L)
        }
        if (cursor <= maxIpv4) {
            allowed.add(cursor to maxIpv4)
        }

        val result = ArrayList<Ipv4Cidr>()
        for ((start, end) in allowed) {
            rangeToCidrs(start, end, result)
        }
        return result
    }

    fun ipv4ToLong(ip: String): Long {
        val parts = ip.trim().split('.')
        require(parts.size == 4) { "Invalid IPv4 address: $ip" }
        var acc = 0L
        for (part in parts) {
            val octet = part.toLong()
            require(octet in 0L..255L) { "Invalid IPv4 octet: $part" }
            acc = (acc shl 8) or octet
        }
        return acc
    }

    fun longToIpv4(value: Long): String {
        val a = (value ushr 24) and 0xFFL
        val b = (value ushr 16) and 0xFFL
        val c = (value ushr 8) and 0xFFL
        val d = value and 0xFFL
        return "$a.$b.$c.$d"
    }

    fun containsIp(routes: List<Ipv4Cidr>, ip: String): Boolean {
        val target = ipv4ToLong(ip)
        return routes.any { cidr ->
            val (start, end) = cidrToRange(cidr)
            target in start..end
        }
    }

    private fun cidrToRange(cidr: Ipv4Cidr): Pair<Long, Long> {
        val ip = ipv4ToLong(cidr.address)
        val hostBits = 32 - cidr.prefixLength
        val mask = if (cidr.prefixLength == 0) 0L else (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
        val start = ip and mask
        val size = if (hostBits == 32) 0x1_0000_0000L else (1L shl hostBits)
        return start to (start + size - 1L)
    }

    private fun rangeToCidrs(startIp: Long, endIp: Long, out: MutableList<Ipv4Cidr>) {
        var current = startIp
        while (current <= endIp) {
            var maxBlockBits = if (current == 0L) 32 else java.lang.Long.numberOfTrailingZeros(current)
            val remaining = endIp - current + 1L
            while (maxBlockBits > 0 && (1L shl maxBlockBits) > remaining) {
                maxBlockBits--
            }
            val prefix = 32 - maxBlockBits
            out.add(Ipv4Cidr(longToIpv4(current), prefix))
            current += (1L shl maxBlockBits)
        }
    }
}
