package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.example.model.ActivePeerTunnelSummary
import com.example.model.NordServer
import com.example.vpn.crypto.WireGuardCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

data class PeerTunnelSpec(
    val routeKey: String,
    val countryId: Int,
    val server: NordServer,
    val appPackages: List<String>,
    val appUids: Set<Int>,
    val isDefaultPeer: Boolean
)

/**
 * Multi-Peer Pure-Kotlin WireGuard Engine (Android 10+ / API 29+).
 *
 * Maintains one default NordLynx peer plus N simultaneous per-app country peers over a single
 * Android VpnService TUN interface. Outgoing IPv4 TCP/UDP packets are classified by owning app
 * UID via ConnectivityManager.getConnectionOwnerUid(), cached per socket 4-tuple, and encrypted
 * through each app's assigned country WireGuard session (e.g., App 1 -> India, App 2 -> US).
 */
class MultiPeerWireGuardEngine(
    private val vpnService: VpnService,
    private val localPrivateKeyBase64: String,
    private val peerSpecs: List<PeerTunnelSpec>,
    private val scope: CoroutineScope,
    private val onStatsFlushed: (
        packetsDelta: Long,
        txBytesDelta: Long,
        rxBytesDelta: Long,
        peerSummaries: List<ActivePeerTunnelSummary>
    ) -> Unit
) {

    private class PeerSlot(
        val spec: PeerTunnelSpec,
        val endpointAddress: InetAddress,
        val socket: DatagramSocket,
        val session: WireGuardSession
    ) {
        val lastSendMillis = AtomicLong(System.currentTimeMillis())
        val totalTxBytes = AtomicLong(0L)
        val totalRxBytes = AtomicLong(0L)
    }

    @Volatile
    private var running = false
    private val slots = mutableListOf<PeerSlot>()
    private var defaultSlot: PeerSlot? = null
    private var routeTable: AppPeerRouteTable<PeerSlot>? = null

    private var txJob: Job? = null
    private val rxJobs = mutableListOf<Job>()
    private var maintenanceJob: Job? = null
    private val tunWriteLock = Any()

    private val pendingPackets = AtomicLong(0L)
    private val pendingTxBytes = AtomicLong(0L)
    private val pendingRxBytes = AtomicLong(0L)
    private val lastFlushMillis = AtomicLong(0L)

    fun performInitialHandshakes(): Result<Pair<Int, List<ActivePeerTunnelSummary>>> = runCatching {
        val privBytes = WireGuardCrypto.decodeBase64(localPrivateKeyBase64)
        require(privBytes.size == 32) { "Invalid NordLynx private key length" }
        require(peerSpecs.isNotEmpty()) { "At least one WireGuard peer is required" }

        var defaultRttMs = 40
        val uidMap = HashMap<Int, PeerSlot>()

        for (spec in peerSpecs) {
            val peerPub = WireGuardCrypto.decodeBase64(spec.server.wireGuardPublicKey)
            require(peerPub.size == 32) { "Invalid server public key for ${spec.server.hostname}" }
            val targetAddr = InetAddress.getByName(spec.server.stationIp)
            val socket = DatagramSocket()
            check(vpnService.protect(socket)) { "Failed to protect WireGuard UDP socket (${spec.server.hostname})" }
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.receiveBufferSize = 256 * 1024
            socket.sendBufferSize = 256 * 1024

            val wgSession = WireGuardSession(localPrivateKey = privBytes, peerPublicKey = peerPub)
            val rtt = handshakeSinglePeer(socket, targetAddr, wgSession, spec.server.hostname)
            if (rtt == null) {
                runCatching { socket.close() }
                if (spec.isDefaultPeer) {
                    throw IllegalStateException(
                        "WireGuard handshake timed out (${spec.server.hostname}:51820)"
                    )
                }
                continue
            }

            socket.soTimeout = 0
            val slot = PeerSlot(
                spec = spec,
                endpointAddress = targetAddr,
                socket = socket,
                session = wgSession
            )
            slots.add(slot)
            if (spec.isDefaultPeer) {
                defaultSlot = slot
                defaultRttMs = rtt
            }
            spec.appUids.forEach { uid -> uidMap[uid] = slot }
        }

        val primary = defaultSlot ?: slots.firstOrNull()
            ?: throw IllegalStateException("No WireGuard peers completed handshake")
        defaultSlot = primary
        routeTable = AppPeerRouteTable(defaultPeer = primary, uidToPeerMap = uidMap)

        defaultRttMs to buildPeerSummaries()
    }.onFailure {
        stop()
    }

    private fun handshakeSinglePeer(
        socket: DatagramSocket,
        targetAddr: InetAddress,
        wgSession: WireGuardSession,
        hostname: String
    ): Int? {
        val recvBuf = ByteArray(256)
        val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
        val startNs = System.nanoTime()

        for (attempt in 1..MAX_HANDSHAKE_ATTEMPTS) {
            val initMsg = wgSession.createHandshakeInitiation()
            runCatching {
                socket.send(DatagramPacket(initMsg, initMsg.size, targetAddr, WG_PORT))
            }.onFailure { return null }

            try {
                socket.receive(recvPacket)
                val len = recvPacket.length
                when {
                    len == 92 && recvBuf[0] == WireGuardSession.TYPE_HANDSHAKE_RESP -> {
                        if (wgSession.consumeHandshakeResponse(recvBuf, len)) {
                            return ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                        }
                    }
                    len == 64 && recvBuf[0] == WireGuardSession.TYPE_COOKIE_REPLY -> {
                        wgSession.consumeCookieReply(recvBuf, len)
                    }
                }
            } catch (_: Exception) {
                if (attempt == MAX_HANDSHAKE_ATTEMPTS) {
                    return null
                }
            }
        }
        return null
    }

    fun startDataLoops(tunFd: ParcelFileDescriptor) {
        val router = routeTable ?: return
        val fallback = defaultSlot ?: return
        val cm = vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        running = true

        val tunInput = FileInputStream(tunFd.fileDescriptor)
        val tunOutput = FileOutputStream(tunFd.fileDescriptor)

        // Single TX loop reading TUN packets and multiplexing them across country WireGuard peers
        txJob = scope.launch(Dispatchers.IO) {
            val readBuf = ByteArray(2048)
            while (running && isActive) {
                val len = try {
                    tunInput.read(readBuf)
                } catch (_: Exception) {
                    break
                }
                if (len <= 0) continue

                val ipVersion = (readBuf[0].toInt() ushr 4) and 0x0F
                if (ipVersion != 4) continue // Drop IPv6 to prevent IPv6 leaks

                val targetSlot = resolveTargetSlot(readBuf, len, router, fallback, cm)
                val encrypted = targetSlot.session.encryptTransportPacket(readBuf, len) ?: continue
                try {
                    targetSlot.socket.send(
                        DatagramPacket(encrypted, encrypted.size, targetSlot.endpointAddress, WG_PORT)
                    )
                    targetSlot.lastSendMillis.set(System.currentTimeMillis())
                    targetSlot.totalTxBytes.addAndGet(len.toLong())
                    pendingPackets.incrementAndGet()
                    pendingTxBytes.addAndGet(len.toLong())
                    flushStats(force = false)
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }

        // Dedicated RX loop per active WireGuard peer
        for (slot in slots) {
            val job = scope.launch(Dispatchers.IO) {
                val recvBuf = ByteArray(2560)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                while (running && isActive) {
                    try {
                        slot.socket.receive(recvPacket)
                    } catch (_: Exception) {
                        break
                    }
                    val len = recvPacket.length
                    if (len < 32) continue

                    when (recvBuf[0]) {
                        WireGuardSession.TYPE_TRANSPORT_DATA -> {
                            val plaintext = slot.session.decryptTransportPacket(recvBuf, len) ?: continue
                            if (plaintext.isNotEmpty()) {
                                try {
                                    synchronized(tunWriteLock) {
                                        tunOutput.write(plaintext)
                                    }
                                    slot.totalRxBytes.addAndGet(plaintext.size.toLong())
                                    pendingRxBytes.addAndGet(plaintext.size.toLong())
                                    flushStats(force = false)
                                } catch (_: Exception) {
                                    if (!running) break
                                }
                            }
                        }
                        WireGuardSession.TYPE_HANDSHAKE_RESP -> {
                            slot.session.consumeHandshakeResponse(recvBuf, len)
                        }
                        WireGuardSession.TYPE_COOKIE_REPLY -> {
                            if (slot.session.consumeCookieReply(recvBuf, len)) {
                                val retryInit = slot.session.createHandshakeInitiation()
                                runCatching {
                                    slot.socket.send(
                                        DatagramPacket(retryInit, retryInit.size, slot.endpointAddress, WG_PORT)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            rxJobs.add(job)
        }

        // Shared 25s keepalive + 120s zero-downtime rekey across all peers
        maintenanceJob = scope.launch(Dispatchers.IO) {
            while (running && isActive) {
                delay(5_000L)
                if (!running) break
                val now = System.currentTimeMillis()
                for (slot in slots) {
                    val keys = slot.session.activeKeys ?: continue
                    if (now - keys.establishedAtMillis >= REKEY_AFTER_MS) {
                        val initMsg = slot.session.createHandshakeInitiation()
                        runCatching {
                            slot.socket.send(
                                DatagramPacket(initMsg, initMsg.size, slot.endpointAddress, WG_PORT)
                            )
                            slot.lastSendMillis.set(now)
                        }
                    } else if (now - slot.lastSendMillis.get() >= KEEPALIVE_INTERVAL_MS) {
                        val keepalive = slot.session.encryptTransportPacket(ByteArray(0), 0)
                        if (keepalive != null) {
                            runCatching {
                                slot.socket.send(
                                    DatagramPacket(keepalive, keepalive.size, slot.endpointAddress, WG_PORT)
                                )
                                slot.lastSendMillis.set(now)
                            }
                        }
                    }
                }
                flushStats(force = false)
            }
        }
    }

    private fun resolveTargetSlot(
        packet: ByteArray,
        length: Int,
        router: AppPeerRouteTable<PeerSlot>,
        fallback: PeerSlot,
        cm: ConnectivityManager?
    ): PeerSlot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || cm == null) return fallback
        val tuple = Ipv4PacketInspector.extractFlowTuple(packet, length) ?: return fallback
        return router.routeFlow(tuple.flowKey) {
            runCatching {
                val osProto = when (tuple.protocol) {
                    Ipv4PacketInspector.PROTO_TCP -> OsConstants.IPPROTO_TCP
                    Ipv4PacketInspector.PROTO_UDP -> OsConstants.IPPROTO_UDP
                    else -> return@runCatching -1
                }
                val local = InetSocketAddress(InetAddress.getByAddress(tuple.srcIpBytes), tuple.srcPort)
                val remote = InetSocketAddress(InetAddress.getByAddress(tuple.dstIpBytes), tuple.dstPort)
                cm.getConnectionOwnerUid(osProto, local, remote)
            }.getOrDefault(-1)
        }
    }

    fun buildPeerSummaries(): List<ActivePeerTunnelSummary> = slots.map { slot ->
        ActivePeerTunnelSummary(
            routeKey = slot.spec.routeKey,
            countryId = slot.spec.countryId,
            countryName = slot.spec.server.countryName,
            countryCode = slot.spec.server.countryCode,
            cityName = slot.spec.server.cityName,
            serverCodeTag = slot.spec.server.serverCodeTag,
            hostname = slot.spec.server.hostname,
            stationIp = slot.spec.server.stationIp,
            loadPercent = slot.spec.server.loadPercent,
            appPackages = slot.spec.appPackages,
            isDefaultPeer = slot.spec.isDefaultPeer,
            bytesTx = slot.totalTxBytes.get(),
            bytesRx = slot.totalRxBytes.get()
        )
    }

    fun stop() {
        running = false
        maintenanceJob?.cancel()
        maintenanceJob = null
        txJob?.cancel()
        txJob = null
        rxJobs.forEach { it.cancel() }
        rxJobs.clear()
        slots.forEach { slot ->
            runCatching { slot.socket.close() }
        }
        flushStats(force = true)
        slots.clear()
        defaultSlot = null
        routeTable = null
    }

    private fun flushStats(force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastFlushMillis.get()
        if (!force && now - last < STATS_FLUSH_INTERVAL_MS) return
        if (force || lastFlushMillis.compareAndSet(last, now)) {
            val pkts = pendingPackets.getAndSet(0L)
            val tx = pendingTxBytes.getAndSet(0L)
            val rx = pendingRxBytes.getAndSet(0L)
            if (pkts > 0L || tx > 0L || rx > 0L) {
                onStatsFlushed(pkts, tx, rx, buildPeerSummaries())
            }
        }
    }

    companion object {
        private const val WG_PORT = 51820
        private const val HANDSHAKE_TIMEOUT_MS = 2_500
        private const val MAX_HANDSHAKE_ATTEMPTS = 3
        private const val KEEPALIVE_INTERVAL_MS = 25_000L
        private const val REKEY_AFTER_MS = 120_000L
        private const val STATS_FLUSH_INTERVAL_MS = 500L
    }
}
