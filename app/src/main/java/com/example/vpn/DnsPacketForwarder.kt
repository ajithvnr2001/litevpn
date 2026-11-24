package com.example.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Battery- and memory-optimized IPv4 UDP/53 DNS packet forwarder for Android VpnService:
 * - Bounded 4-worker coroutine pool over a drop-oldest channel (no thread/coroutine explosion).
 * - In-memory 256-entry LRU DNS cache (45s TTL) answering repeat lookups with zero radio wakeups.
 * - Batched telemetry flushes (at most once per 500ms) to minimize CPU wakeups & UI recompositions.
 */
class DnsPacketForwarder(
    private val vpnService: VpnService,
    private val tunFd: ParcelFileDescriptor,
    private val primaryDnsIp: String,
    private val secondaryDnsIp: String,
    private val scope: CoroutineScope,
    private val onStatsFlushed: (forwardedDelta: Long, cacheHitDelta: Long, txDelta: Long, rxDelta: Long) -> Unit
) {

    private class DnsTask(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayload: ByteArray
    )

    private class CacheEntry(
        val responseBytes: ByteArray,
        val expiresAtMillis: Long
    )

    @Volatile
    private var running = true
    private val writeLock = Any()
    private val cacheLock = Any()

    private val dnsCache = object : LinkedHashMap<String, CacheEntry>(CACHE_CAPACITY + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > CACHE_CAPACITY
    }

    private val taskChannel = Channel<DnsTask>(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val pendingForwarded = AtomicLong(0L)
    private val pendingCacheHits = AtomicLong(0L)
    private val pendingTxBytes = AtomicLong(0L)
    private val pendingRxBytes = AtomicLong(0L)
    private val lastFlushMillis = AtomicLong(0L)

    fun start() {
        val inputStream = FileInputStream(tunFd.fileDescriptor)
        val outputStream = FileOutputStream(tunFd.fileDescriptor)
        val primaryAddress = InetAddress.getByName(primaryDnsIp)
        val secondaryAddress = InetAddress.getByName(secondaryDnsIp)

        // Launch a fixed, bounded pool of 4 worker coroutines for upstream UDP resolution
        repeat(WORKER_COUNT) {
            scope.launch(Dispatchers.IO) {
                for (task in taskChannel) {
                    if (!running) break
                    handleDnsTask(task, primaryAddress, secondaryAddress, outputStream)
                }
            }
        }

        val buffer = ByteArray(2048)

        while (running && scope.isActive) {
            val length = try {
                inputStream.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length < 28) continue

            val version = (buffer[0].toInt() ushr 4) and 0x0F
            if (version != 4) continue

            val ihlBytes = (buffer[0].toInt() and 0x0F) * 4
            if (ihlBytes < 20 || length < ihlBytes + 8) continue

            val protocol = buffer[9].toInt() and 0xFF
            if (protocol != 17) continue // UDP only

            val dstPort = ((buffer[ihlBytes + 2].toInt() and 0xFF) shl 8) or
                (buffer[ihlBytes + 3].toInt() and 0xFF)
            if (dstPort != 53) continue

            val srcPort = ((buffer[ihlBytes].toInt() and 0xFF) shl 8) or
                (buffer[ihlBytes + 1].toInt() and 0xFF)
            val udpLength = ((buffer[ihlBytes + 4].toInt() and 0xFF) shl 8) or
                (buffer[ihlBytes + 5].toInt() and 0xFF)
            val dnsOffset = ihlBytes + 8
            val dnsLength = (udpLength - 8).coerceAtMost(length - dnsOffset)
            if (dnsLength <= 12) continue

            val task = DnsTask(
                srcIp = buffer.copyOfRange(12, 16),
                dstIp = buffer.copyOfRange(16, 20),
                srcPort = srcPort,
                dstPort = dstPort,
                dnsPayload = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLength)
            )
            taskChannel.trySend(task)
        }
        flushStats(force = true)
    }

    fun stop() {
        running = false
        taskChannel.close()
        flushStats(force = true)
        synchronized(cacheLock) {
            dnsCache.clear()
        }
    }

    private fun handleDnsTask(
        task: DnsTask,
        primaryAddress: InetAddress,
        secondaryAddress: InetAddress,
        outputStream: FileOutputStream
    ) {
        val cacheKey = buildCacheKey(task.dnsPayload)
        val cachedReply = lookupCache(cacheKey, task.dnsPayload)

        val replyPayload: ByteArray
        val fromCache: Boolean
        if (cachedReply != null) {
            replyPayload = cachedReply
            fromCache = true
        } else {
            val targetDns = resolveTargetAddress(task.dstIp, primaryAddress, secondaryAddress)
            val firstReply = queryUpstream(targetDns, task.dnsPayload)
            replyPayload = firstReply
                ?: (if (targetDns != secondaryAddress) queryUpstream(secondaryAddress, task.dnsPayload) else null)
                ?: return
            fromCache = false
            storeCache(cacheKey, replyPayload)
        }

        val responsePacket = buildIpv4UdpPacket(
            srcIp = task.dstIp,
            dstIp = task.srcIp,
            srcPort = task.dstPort,
            dstPort = task.srcPort,
            payload = replyPayload
        )

        synchronized(writeLock) {
            if (!running) return
            try {
                outputStream.write(responsePacket)
            } catch (_: Exception) {
                return
            }
        }

        pendingForwarded.incrementAndGet()
        if (fromCache) pendingCacheHits.incrementAndGet()
        pendingTxBytes.addAndGet(task.dnsPayload.size.toLong())
        pendingRxBytes.addAndGet(replyPayload.size.toLong())
        flushStats(force = false)
    }

    private fun flushStats(force: Boolean) {
        val now = System.currentTimeMillis()
        val last = lastFlushMillis.get()
        if (!force && now - last < STATS_FLUSH_INTERVAL_MS && pendingForwarded.get() < 8L) {
            return
        }
        if (force || lastFlushMillis.compareAndSet(last, now)) {
            val fwd = pendingForwarded.getAndSet(0L)
            val hits = pendingCacheHits.getAndSet(0L)
            val tx = pendingTxBytes.getAndSet(0L)
            val rx = pendingRxBytes.getAndSet(0L)
            if (fwd > 0L || hits > 0L) {
                onStatsFlushed(fwd, hits, tx, rx)
            }
        }
    }

    private fun buildCacheKey(queryPayload: ByteArray): String? {
        // Key by everything after the 2-byte Transaction ID & flags (question section)
        if (queryPayload.size < 14) return null
        val sb = StringBuilder((queryPayload.size - 12) * 2)
        for (i in 12 until queryPayload.size) {
            val b = queryPayload[i].toInt() and 0xFF
            sb.append(HEX_CHARS[b ushr 4])
            sb.append(HEX_CHARS[b and 0x0F])
        }
        return sb.toString()
    }

    private fun lookupCache(key: String?, queryPayload: ByteArray): ByteArray? {
        if (key == null) return null
        val now = System.currentTimeMillis()
        val entry = synchronized(cacheLock) {
            val found = dnsCache[key]
            if (found != null && found.expiresAtMillis > now) {
                found
            } else {
                if (found != null) dnsCache.remove(key)
                null
            }
        } ?: return null

        val copy = entry.responseBytes.copyOf()
        if (copy.size >= 2 && queryPayload.size >= 2) {
            copy[0] = queryPayload[0]
            copy[1] = queryPayload[1]
        }
        return copy
    }

    private fun storeCache(key: String?, responsePayload: ByteArray) {
        if (key == null || responsePayload.size < 12) return
        // Only cache NOERROR responses (RCODE == 0)
        val rcode = responsePayload[3].toInt() and 0x0F
        if (rcode != 0) return
        val expiresAt = System.currentTimeMillis() + CACHE_TTL_MS
        synchronized(cacheLock) {
            dnsCache[key] = CacheEntry(responsePayload.copyOf(), expiresAt)
        }
    }

    private fun resolveTargetAddress(
        dstIp: ByteArray,
        primaryAddress: InetAddress,
        secondaryAddress: InetAddress
    ): InetAddress {
        val secondaryBytes = secondaryAddress.address
        return if (dstIp.contentEquals(secondaryBytes)) secondaryAddress else primaryAddress
    }

    private fun queryUpstream(dnsAddress: InetAddress, queryPayload: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!vpnService.protect(socket)) return null
                socket.soTimeout = 2_500
                val sendPacket = DatagramPacket(queryPayload, queryPayload.size, dnsAddress, 53)
                socket.send(sendPacket)

                val recvBuffer = ByteArray(1500)
                val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                socket.receive(recvPacket)
                recvBuffer.copyOf(recvPacket.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpLen = 8 + payload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(packet)

        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte())
        buf.putShort(0.toShort())
        buf.put(srcIp)
        buf.put(dstIp)

        val checksum = computeIpv4Checksum(packet, ipHeaderLen)
        packet[10] = ((checksum ushr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()

        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0.toShort())

        buf.put(payload)
        return packet
    }

    private fun computeIpv4Checksum(header: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            val high = header[i].toInt() and 0xFF
            val low = if (i + 1 < length) header[i + 1].toInt() and 0xFF else 0
            sum += (high shl 8) or low
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    companion object {
        private const val WORKER_COUNT = 4
        private const val CACHE_CAPACITY = 256
        private const val CACHE_TTL_MS = 45_000L
        private const val STATS_FLUSH_INTERVAL_MS = 500L
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
