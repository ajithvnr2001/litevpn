package com.example.vpn

import com.example.vpn.crypto.WireGuardCrypto
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * WireGuard Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s protocol handshake and transport session.
 * Wire-compatible with NordVPN NordLynx servers on UDP port 51820.
 */
class WireGuardSession(
    private val localPrivateKey: ByteArray,
    private val peerPublicKey: ByteArray,
    private val presharedKey: ByteArray = ByteArray(32)
) {
    private val localPublicKey: ByteArray = WireGuardCrypto.publicKeyFromPrivate(localPrivateKey)
    private val mac1Key: ByteArray = WireGuardCrypto.hash(LABEL_MAC1, peerPublicKey)
    private val cookieKey: ByteArray = WireGuardCrypto.hash(LABEL_COOKIE, peerPublicKey)

    private val indexGenerator = AtomicInteger((WireGuardCrypto.readLE32(WireGuardCrypto.randomBytes(4), 0) and 0x7FFFFFFF) + 1)

    // Pending handshake state
    private val handshakeLock = Any()
    private var pendingLocalIndex: Int = 0
    private var pendingEphemeralPrivate: ByteArray? = null
    private var pendingChainingKey: ByteArray? = null
    private var pendingHash: ByteArray? = null
    private var lastSentMac1: ByteArray? = null
    private var lastCookie: ByteArray? = null
    private var cookieExpiresAtMillis: Long = 0L

    // Active transport session state
    class TransportKeys(
        val localIndex: Int,
        val remoteIndex: Int,
        val sendKey: ByteArray,
        val recvKey: ByteArray,
        val establishedAtMillis: Long = System.currentTimeMillis()
    ) {
        val sendCounter = AtomicLong(0L)
        private val replayLock = Any()
        private var maxRecvCounter: Long = -1L
        private var replayBitmap: Long = 0L

        fun acceptCounter(counter: Long): Boolean = synchronized(replayLock) {
            if (counter < 0L) return false
            if (maxRecvCounter < 0L) {
                maxRecvCounter = counter
                replayBitmap = 1L
                return true
            }
            if (counter > maxRecvCounter) {
                val shift = counter - maxRecvCounter
                replayBitmap = if (shift >= 64L) 1L else ((replayBitmap shl shift.toInt()) or 1L)
                maxRecvCounter = counter
                return true
            }
            val diff = maxRecvCounter - counter
            if (diff >= 64L) return false
            val bit = 1L shl diff.toInt()
            if ((replayBitmap and bit) != 0L) return false
            replayBitmap = replayBitmap or bit
            true
        }
    }

    @Volatile
    var activeKeys: TransportKeys? = null
        private set

    @Volatile
    private var previousKeys: TransportKeys? = null

    fun createHandshakeInitiation(
        ephemeralPrivateOverride: ByteArray? = null,
        timestampOverride: ByteArray? = null
    ): ByteArray = synchronized(handshakeLock) {
        val localIdx = indexGenerator.incrementAndGet()
        val ePriv = ephemeralPrivateOverride?.copyOf() ?: WireGuardCrypto.generatePrivateKey()
        val ePub = WireGuardCrypto.publicKeyFromPrivate(ePriv)

        var ck = INITIAL_CHAINING_KEY.copyOf()
        var h = WireGuardCrypto.hash(INITIAL_HASH, peerPublicKey)

        // Ephemeral
        ck = WireGuardCrypto.kdf1(ck, ePub)
        h = WireGuardCrypto.hash(h, ePub)

        // es = DH(Epriv_i, Spub_r)
        val es = WireGuardCrypto.x25519(ePriv, peerPublicKey)
        val (ck2, k1) = WireGuardCrypto.kdf2(ck, es)
        ck = ck2
        val encryptedStatic = WireGuardCrypto.aeadEncrypt(
            key = k1,
            nonce12 = ZERO_NONCE,
            plaintext = localPublicKey,
            aad = h
        )
        h = WireGuardCrypto.hash(h, encryptedStatic)

        // ss = DH(Spriv_i, Spub_r)
        val ss = WireGuardCrypto.x25519(localPrivateKey, peerPublicKey)
        val (ck3, k2) = WireGuardCrypto.kdf2(ck, ss)
        ck = ck3
        val timestamp = timestampOverride ?: WireGuardCrypto.tai64nNow()
        val encryptedTimestamp = WireGuardCrypto.aeadEncrypt(
            key = k2,
            nonce12 = ZERO_NONCE,
            plaintext = timestamp,
            aad = h
        )
        h = WireGuardCrypto.hash(h, encryptedTimestamp)

        val msg = ByteArray(148)
        msg[0] = TYPE_HANDSHAKE_INIT
        WireGuardCrypto.writeLE32(msg, 4, localIdx)
        ePub.copyInto(msg, 8)
        encryptedStatic.copyInto(msg, 40)
        encryptedTimestamp.copyInto(msg, 88)

        val mac1 = WireGuardCrypto.mac(mac1Key, msg, 116)
        mac1.copyInto(msg, 116)

        val cookie = lastCookie
        if (cookie != null && System.currentTimeMillis() < cookieExpiresAtMillis) {
            val mac2 = WireGuardCrypto.mac(cookie, msg, 132)
            mac2.copyInto(msg, 132)
        }

        pendingLocalIndex = localIdx
        pendingEphemeralPrivate = ePriv
        pendingChainingKey = ck
        pendingHash = h
        lastSentMac1 = mac1

        msg
    }

    fun consumeHandshakeResponse(packet: ByteArray, length: Int = packet.size): Boolean = synchronized(handshakeLock) {
        if (length != 92 || packet[0] != TYPE_HANDSHAKE_RESP) return false

        val remoteIdx = WireGuardCrypto.readLE32(packet, 4)
        val receiverIdx = WireGuardCrypto.readLE32(packet, 8)
        if (receiverIdx != pendingLocalIndex) return false

        val ePriv = pendingEphemeralPrivate ?: return false
        var ck = pendingChainingKey ?: return false
        var h = pendingHash ?: return false

        val peerEphemeralPub = packet.copyOfRange(12, 44)
        val encryptedNothing = packet.copyOfRange(44, 60)

        ck = WireGuardCrypto.kdf1(ck, peerEphemeralPub)
        h = WireGuardCrypto.hash(h, peerEphemeralPub)

        // ee = DH(Epriv_i, Epub_r)
        val ee = WireGuardCrypto.x25519(ePriv, peerEphemeralPub)
        ck = WireGuardCrypto.kdf1(ck, ee)

        // se = DH(Spriv_i, Epub_r)
        val se = WireGuardCrypto.x25519(localPrivateKey, peerEphemeralPub)
        ck = WireGuardCrypto.kdf1(ck, se)

        // psk
        val (ckFinal, tau, k3) = WireGuardCrypto.kdf3(ck, presharedKey)
        h = WireGuardCrypto.hash(h, tau)

        val decrypted = WireGuardCrypto.aeadDecrypt(
            key = k3,
            nonce12 = ZERO_NONCE,
            ciphertextAndTag = encryptedNothing,
            aad = h
        ) ?: return false
        if (decrypted.isNotEmpty()) return false

        val (sendKey, recvKey) = WireGuardCrypto.kdf2(ckFinal, ByteArray(0))
        previousKeys = activeKeys
        activeKeys = TransportKeys(
            localIndex = receiverIdx,
            remoteIndex = remoteIdx,
            sendKey = sendKey,
            recvKey = recvKey
        )

        pendingEphemeralPrivate = null
        pendingChainingKey = null
        pendingHash = null
        true
    }

    fun consumeCookieReply(packet: ByteArray, length: Int = packet.size): Boolean = synchronized(handshakeLock) {
        if (length != 64 || packet[0] != TYPE_COOKIE_REPLY) return false
        val receiverIdx = WireGuardCrypto.readLE32(packet, 4)
        if (receiverIdx != pendingLocalIndex) return false
        val mac1 = lastSentMac1 ?: return false
        val nonce24 = packet.copyOfRange(8, 32)
        val encryptedCookie = packet.copyOfRange(32, 64)
        val cookie = WireGuardCrypto.xAeadDecrypt(
            key = cookieKey,
            nonce24 = nonce24,
            ciphertextAndTag = encryptedCookie,
            aad = mac1
        ) ?: return false
        lastCookie = cookie
        cookieExpiresAtMillis = System.currentTimeMillis() + 120_000L
        true
    }

    fun encryptTransportPacket(plaintext: ByteArray, length: Int = plaintext.size): ByteArray? {
        val keys = activeKeys ?: return null
        val counter = keys.sendCounter.getAndIncrement()

        // Pad to 16-byte multiple per WireGuard spec (empty length = keepalive)
        val padded = if (length == 0) {
            ByteArray(0)
        } else {
            val paddedLen = (length + 15) and 15.inv()
            ByteArray(paddedLen).also { plaintext.copyInto(it, 0, 0, length) }
        }

        val encrypted = WireGuardCrypto.aeadEncrypt(
            key = keys.sendKey,
            nonce12 = WireGuardCrypto.wireGuardNonce(counter),
            plaintext = padded
        )

        val packet = ByteArray(16 + encrypted.size)
        packet[0] = TYPE_TRANSPORT_DATA
        WireGuardCrypto.writeLE32(packet, 4, keys.remoteIndex)
        WireGuardCrypto.writeLE64(packet, 8, counter)
        encrypted.copyInto(packet, 16)
        return packet
    }

    fun decryptTransportPacket(packet: ByteArray, length: Int = packet.size): ByteArray? {
        if (length < 32 || packet[0] != TYPE_TRANSPORT_DATA) return null
        val receiverIdx = WireGuardCrypto.readLE32(packet, 4)
        val keys = when {
            activeKeys?.localIndex == receiverIdx -> activeKeys
            previousKeys?.localIndex == receiverIdx -> previousKeys
            else -> null
        } ?: return null

        val counter = WireGuardCrypto.readLE64(packet, 8)
        if (!keys.acceptCounter(counter)) return null

        val ciphertextAndTag = packet.copyOfRange(16, length)
        val decrypted = WireGuardCrypto.aeadDecrypt(
            key = keys.recvKey,
            nonce12 = WireGuardCrypto.wireGuardNonce(counter),
            ciphertextAndTag = ciphertextAndTag
        ) ?: return null

        if (decrypted.isEmpty()) return ByteArray(0) // Keepalive

        // Strip trailing WireGuard padding using the IP header length
        val ipVersion = (decrypted[0].toInt() ushr 4) and 0x0F
        return when (ipVersion) {
            4 -> {
                if (decrypted.size < 20) return null
                val ipTotalLen = ((decrypted[2].toInt() and 0xFF) shl 8) or (decrypted[3].toInt() and 0xFF)
                if (ipTotalLen in 20..decrypted.size) decrypted.copyOf(ipTotalLen) else decrypted
            }
            6 -> {
                if (decrypted.size < 40) return null
                val payloadLen = ((decrypted[4].toInt() and 0xFF) shl 8) or (decrypted[5].toInt() and 0xFF)
                val ipTotalLen = 40 + payloadLen
                if (ipTotalLen in 40..decrypted.size) decrypted.copyOf(ipTotalLen) else decrypted
            }
            else -> decrypted
        }
    }

    companion object {
        const val TYPE_HANDSHAKE_INIT: Byte = 1
        const val TYPE_HANDSHAKE_RESP: Byte = 2
        const val TYPE_COOKIE_REPLY: Byte = 3
        const val TYPE_TRANSPORT_DATA: Byte = 4

        private val CONSTRUCTION = "Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray(Charsets.US_ASCII)
        private val IDENTIFIER = "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray(Charsets.US_ASCII)
        private val LABEL_MAC1 = "mac1----".toByteArray(Charsets.US_ASCII)
        private val LABEL_COOKIE = "cookie--".toByteArray(Charsets.US_ASCII)
        private val ZERO_NONCE = ByteArray(12)

        val INITIAL_CHAINING_KEY: ByteArray = WireGuardCrypto.hash(CONSTRUCTION)
        val INITIAL_HASH: ByteArray = WireGuardCrypto.hash(INITIAL_CHAINING_KEY, IDENTIFIER)
    }
}
