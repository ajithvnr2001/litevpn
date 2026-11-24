package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Full-tunnel NordLynx (WireGuard UDP:51820) userspace engine running over Android's VpnService.
 * Routes all IPv4 traffic (0.0.0.0/0) for allowed/non-excluded apps through the selected NordVPN
 * server with real ChaCha20-Poly1305 encryption, automatic keepalives, and 120s zero-downtime rekey.
 */
class WireGuardTunnelEngine(
    private val vpnService: VpnService,
    private val endpointHost: String,
    private val endpointPort: Int = 51820,
    private val localPrivateKeyBase64: String,
    private val peerPublicKeyBase64: String,
    private val scope: CoroutineScope,
    private val onStatsFlushed: (packetsDelta: Long, txBytesDelta: Long, rxBytesDelta: Long) -> Unit
) {

    @Volatile
    private var running = false
    private var udpSocket: DatagramSocket? = null
    private var session: WireGuardSession? = null
    private var endpointAddress: InetAddress? = null

    private var txJob: Job? = null
    private var rxJob: Job? = null
    private var keepaliveJob: Job? = null

    private val pendingPackets = AtomicLong(0L)
    private val pendingTxBytes = AtomicLong(0L)
    private val pendingRxBytes = AtomicLong(0L)
    private val lastFlushMillis = AtomicLong(0L)
    private val lastSendMillis = AtomicLong(0L)

    /**
     * Performs the real WireGuard Noise_IKpsk2 handshake with the NordVPN server BEFORE
     * establishing the TUN route so unreachable servers or invalid keys fail cleanly.
     */
    fun performInitialHandshake(): Result<Int> = runCatching {
        val privBytes = WireGuardCrypto.decodeBase64(localPrivateKeyBase64)
        val pubBytes = WireGuardCrypto.decodeBase64(peerPublicKeyBase64)
        require(privBytes.size == 32) { "Invalid NordLynx private key length" }
        require(pubBytes.size == 32) { "Invalid Nord server public key length" }

        val targetAddr = InetAddress.getByName(endpointHost)
        endpointAddress = targetAddr

        val socket = DatagramSocket()
        check(vpnService.protect(socket)) { "Failed to protect WireGuard UDP socket" }
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        socket.receiveBufferSize = 256 * 1024
        socket.sendBufferSize = 256 * 1024
        udpSocket = socket

        val wgSession = WireGuardSession(localPrivateKey = privBytes, peerPublicKey = pubBytes)
        session = wgSession

        val recvBuf = ByteArray(256)
        val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
        val startNs = System.nanoTime()

        for (attempt in 1..MAX_HANDSHAKE_ATTEMPTS) {
            val initMsg = wgSession.createHandshakeInitiation()
            socket.send(DatagramPacket(initMsg, initMsg.size, targetAddr, endpointPort))
            lastSendMillis.set(System.currentTimeMillis())

            try {
                socket.receive(recvPacket)
                val len = recvPacket.length
                when {
                    len == 92 && recvBuf[0] == WireGuardSession.TYPE_HANDSHAKE_RESP -> {
                        if (wgSession.consumeHandshakeResponse(recvBuf, len)) {
                            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                            // Remove socket read timeout for non-polling blocking RX loop
                            socket.soTimeout = 0
                            return@runCatching rttMs
                        }
                    }
                    len == 64 && recvBuf[0] == WireGuardSession.TYPE_COOKIE_REPLY -> {
                        wgSession.consumeCookieReply(recvBuf, len)
                        // Immediately loop to resend with MAC2 cookie
                    }
                }
            } catch (_: Exception) {
                if (attempt == MAX_HANDSHAKE_ATTEMPTS) {
                    throw IllegalStateException(
                        "WireGuard handshake timed out ($endpointHost:$endpointPort). Check your Nord API key or try another server."
                    )
                }
            }
        }
        throw IllegalStateException("WireGuard handshake failed with $endpointHost")
    }.onFailure {
        stop()
    }

    fun startDataLoops(tunFd: ParcelFileDescriptor) {
        val socket = udpSocket ?: return
        val wgSession = session ?: return
        val targetAddr = endpointAddress ?: return
        running = true

        val tunInput = FileInputStream(tunFd.fileDescriptor)
        val tunOutput = FileOutputStream(tunFd.fileDescriptor)

        // TX loop: TUN -> WireGuard Encrypt -> UDP
        txJob = scope.launch(Dispatchers.IO) {
            val readBuf = ByteArray(2048)
            while (running && isActive) {
                val len = try {
                    tunInput.read(readBuf)
                } catch (_: Exception) {
                    break
                }
                if (len <= 0) continue

                // NordLynx is IPv4-only; silently drop outbound IPv6 packets to prevent IPv6 leaks
                // and force instant Happy Eyeballs fallback to IPv4 over the Albania/Nord tunnel.
                val ipVersion = (readBuf[0].toInt() ushr 4) and 0x0F
                if (ipVersion != 4) continue

                val encrypted = wgSession.encryptTransportPacket(readBuf, len) ?: continue
                try {
                    socket.send(DatagramPacket(encrypted, encrypted.size, targetAddr, endpointPort))
                    lastSendMillis.set(System.currentTimeMillis())
                    pendingPackets.incrementAndGet()
                    pendingTxBytes.addAndGet(len.toLong())
                    flushStats(force = false)
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }

        // RX loop: UDP -> WireGuard Decrypt -> TUN
        rxJob = scope.launch(Dispatchers.IO) {
            val recvBuf = ByteArray(2560)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            while (running && isActive) {
                try {
                    socket.receive(recvPacket)
                } catch (_: Exception) {
                    break
                }
                val len = recvPacket.length
                if (len < 32) continue

                when (recvBuf[0]) {
                    WireGuardSession.TYPE_TRANSPORT_DATA -> {
                        val plaintext = wgSession.decryptTransportPacket(recvBuf, len) ?: continue
                        if (plaintext.isNotEmpty()) {
                            try {
                                tunOutput.write(plaintext)
                                pendingRxBytes.addAndGet(plaintext.size.toLong())
                                flushStats(force = false)
                            } catch (_: Exception) {
                                if (!running) break
                            }
                        }
                    }
                    WireGuardSession.TYPE_HANDSHAKE_RESP -> {
                        wgSession.consumeHandshakeResponse(recvBuf, len)
                    }
                    WireGuardSession.TYPE_COOKIE_REPLY -> {
                        if (wgSession.consumeCookieReply(recvBuf, len)) {
                            val retryInit = wgSession.createHandshakeInitiation()
                            runCatching {
                                socket.send(DatagramPacket(retryInit, retryInit.size, targetAddr, endpointPort))
                            }
                        }
                    }
                }
            }
        }

        // Battery-friendly Keepalive (25s) & Automatic Rekey (120s) loop
        keepaliveJob = scope.launch(Dispatchers.IO) {
            while (running && isActive) {
                delay(5_000L)
                if (!running) break
                val now = System.currentTimeMillis()
                val keys = wgSession.activeKeys ?: continue

                // Rekey at 120s (well before WireGuard 180s REJECT_AFTER_TIME)
                if (now - keys.establishedAtMillis >= REKEY_AFTER_MS) {
                    val initMsg = wgSession.createHandshakeInitiation()
                    runCatching {
                        socket.send(DatagramPacket(initMsg, initMsg.size, targetAddr, endpointPort))
                        lastSendMillis.set(now)
                    }
                } else if (now - lastSendMillis.get() >= KEEPALIVE_INTERVAL_MS) {
                    val keepalive = wgSession.encryptTransportPacket(ByteArray(0), 0)
                    if (keepalive != null) {
                        runCatching {
                            socket.send(DatagramPacket(keepalive, keepalive.size, targetAddr, endpointPort))
                            lastSendMillis.set(now)
                        }
                    }
                }
                flushStats(force = false)
            }
        }
    }

    fun stop() {
        running = false
        keepaliveJob?.cancel()
        keepaliveJob = null
        txJob?.cancel()
        txJob = null
        rxJob?.cancel()
        rxJob = null
        runCatching { udpSocket?.close() }
        udpSocket = null
        flushStats(force = true)
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
                onStatsFlushed(pkts, tx, rx)
            }
        }
    }

    companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 2_500
        private const val MAX_HANDSHAKE_ATTEMPTS = 4
        private const val KEEPALIVE_INTERVAL_MS = 25_000L
        private const val REKEY_AFTER_MS = 120_000L
        private const val STATS_FLUSH_INTERVAL_MS = 500L
    }
}
