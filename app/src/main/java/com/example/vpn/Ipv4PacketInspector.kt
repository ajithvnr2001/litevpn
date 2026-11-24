package com.example.vpn

import java.util.concurrent.ConcurrentHashMap

data class FlowKey(
    val protocol: Int,
    val srcPort: Int,
    val dstIpInt: Int,
    val dstPort: Int
)

data class Ipv4FlowTuple(
    val protocol: Int,
    val srcIpBytes: ByteArray,
    val dstIpBytes: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val flowKey: FlowKey
)

/**
 * Pure-Kotlin IPv4 packet inspector and UID-to-peer flow router.
 *
 * Extracts TCP/UDP 4-tuples from raw TUN IPv4 packets so MultiPeerWireGuardEngine can look up
 * the owning app UID via ConnectivityManager.getConnectionOwnerUid() on Android 10+ and cache
 * the target country WireGuard peer per active socket flow.
 */
object Ipv4PacketInspector {

    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    fun extractFlowTuple(packet: ByteArray, length: Int): Ipv4FlowTuple? {
        if (length < 20 || packet.size < length) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTO_TCP && protocol != PROTO_UDP) return null
        if (length < ihl + 4) return null

        val srcIp = byteArrayOf(packet[12], packet[13], packet[14], packet[15])
        val dstIp = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        val dstIpInt = ((packet[16].toInt() and 0xFF) shl 24) or
            ((packet[17].toInt() and 0xFF) shl 16) or
            ((packet[18].toInt() and 0xFF) shl 8) or
            (packet[19].toInt() and 0xFF)

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        return Ipv4FlowTuple(
            protocol = protocol,
            srcIpBytes = srcIp,
            dstIpBytes = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            flowKey = FlowKey(
                protocol = protocol,
                srcPort = srcPort,
                dstIpInt = dstIpInt,
                dstPort = dstPort
            )
        )
    }
}

/**
 * Thread-safe UID -> Peer lookup with bounded flow-key caching so only the first packet of a
 * TCP/UDP connection pays for Android's ConnectivityManager.getConnectionOwnerUid() syscall.
 */
class AppPeerRouteTable<P : Any>(
    private val defaultPeer: P,
    uidToPeerMap: Map<Int, P>,
    private val maxCachedFlows: Int = 2048
) {
    private val uidPeers = ConcurrentHashMap<Int, P>(uidToPeerMap)
    private val flowCache = ConcurrentHashMap<FlowKey, P>()

    fun routeFlow(flowKey: FlowKey, resolveUid: () -> Int): P {
        flowCache[flowKey]?.let { return it }
        val uid = resolveUid()
        val target = if (uid > 0) uidPeers[uid] ?: defaultPeer else defaultPeer
        if (flowCache.size >= maxCachedFlows) {
            flowCache.clear()
        }
        flowCache[flowKey] = target
        return target
    }

    fun fallbackPeer(): P = defaultPeer

    fun cachedFlowCount(): Int = flowCache.size
}
