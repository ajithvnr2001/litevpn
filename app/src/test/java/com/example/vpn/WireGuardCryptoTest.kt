package com.example.vpn

import com.example.vpn.crypto.WireGuardCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies pure-Kotlin WireGuard cryptography against official RFC 7748 (X25519),
 * RFC 7693 (BLAKE2s-256), RFC 8439 (ChaCha20-Poly1305), and full Noise_IKpsk2 handshake
 * + transport packet round-trip tests.
 */
class WireGuardCryptoTest {

    @Test
    fun `RFC 7748 X25519 official test vectors match bit-for-bit`() {
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val expectedAlicePub = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        assertArrayEquals(expectedAlicePub, WireGuardCrypto.publicKeyFromPrivate(alicePriv))

        val bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val expectedBobPub = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        assertArrayEquals(expectedBobPub, WireGuardCrypto.publicKeyFromPrivate(bobPriv))

        val expectedShared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        assertArrayEquals(expectedShared, WireGuardCrypto.x25519(alicePriv, expectedBobPub))
        assertArrayEquals(expectedShared, WireGuardCrypto.x25519(bobPriv, expectedAlicePub))
    }

    @Test
    fun `RFC 7693 BLAKE2s-256 official test vectors match bit-for-bit`() {
        val emptyExpected = hex("69217a3079908094e11121d042354a7c1f55b6482ca1a51e1b250dfd1ed0eef9")
        assertArrayEquals(emptyExpected, WireGuardCrypto.blake2s(ByteArray(0), 32))

        val abcExpected = hex("508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982")
        assertArrayEquals(abcExpected, WireGuardCrypto.blake2s("abc".toByteArray(Charsets.US_ASCII), 32))
    }

    @Test
    fun `RFC 8439 ChaCha20-Poly1305 AEAD official test vector matches bit-for-bit`() {
        val key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
        val nonce = hex("070000004041424344454647")
        val aad = hex("50515253c0c1c2c3c4c5c6c7")
        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
            .toByteArray(Charsets.US_ASCII)

        val expectedCt = hex(
            "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6" +
                "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b36" +
                "92ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc" +
                "3ff4def08e4b7a9de576d26586cec64b6116"
        )
        val expectedTag = hex("1ae10b594f09e26a7e902ecbd0600691")

        val encrypted = WireGuardCrypto.aeadEncrypt(key, nonce, plaintext, aad)
        assertArrayEquals(expectedCt + expectedTag, encrypted)

        val decrypted = WireGuardCrypto.aeadDecrypt(key, nonce, encrypted, aad)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `Full WireGuard Noise_IKpsk2 handshake and Type 4 transport round-trip succeeds`() {
        val clientPriv = WireGuardCrypto.generatePrivateKey()
        val clientPub = WireGuardCrypto.publicKeyFromPrivate(clientPriv)
        val serverPriv = WireGuardCrypto.generatePrivateKey()
        val serverPub = WireGuardCrypto.publicKeyFromPrivate(serverPriv)

        val clientSession = WireGuardSession(localPrivateKey = clientPriv, peerPublicKey = serverPub)
        val initMsg = clientSession.createHandshakeInitiation()
        assertEquals(148, initMsg.size)
        assertEquals(WireGuardSession.TYPE_HANDSHAKE_INIT, initMsg[0])

        // Simulate real WireGuard Responder (NordLynx server) processing Type 1 & replying Type 2
        val clientSenderIdx = WireGuardCrypto.readLE32(initMsg, 4)
        val clientEpub = initMsg.copyOfRange(8, 40)
        val encStatic = initMsg.copyOfRange(40, 88)
        val encTimestamp = initMsg.copyOfRange(88, 116)
        val mac1 = initMsg.copyOfRange(116, 132)

        // Verify MAC1
        val expectedMac1 = WireGuardCrypto.mac(
            WireGuardCrypto.hash("mac1----".toByteArray(Charsets.US_ASCII), serverPub),
            initMsg,
            116
        )
        assertArrayEquals(expectedMac1, mac1)

        var ck = WireGuardSession.INITIAL_CHAINING_KEY.copyOf()
        var h = WireGuardCrypto.hash(WireGuardSession.INITIAL_HASH, serverPub)
        ck = WireGuardCrypto.kdf1(ck, clientEpub)
        h = WireGuardCrypto.hash(h, clientEpub)

        val es = WireGuardCrypto.x25519(serverPriv, clientEpub)
        val (ck2, k1) = WireGuardCrypto.kdf2(ck, es)
        ck = ck2
        val decryptedClientPub = WireGuardCrypto.aeadDecrypt(k1, ByteArray(12), encStatic, h)
        assertNotNull(decryptedClientPub)
        assertArrayEquals(clientPub, decryptedClientPub)
        h = WireGuardCrypto.hash(h, encStatic)

        val ss = WireGuardCrypto.x25519(serverPriv, decryptedClientPub!!)
        val (ck3, k2) = WireGuardCrypto.kdf2(ck, ss)
        ck = ck3
        val decryptedTs = WireGuardCrypto.aeadDecrypt(k2, ByteArray(12), encTimestamp, h)
        assertNotNull(decryptedTs)
        assertEquals(12, decryptedTs!!.size)
        h = WireGuardCrypto.hash(h, encTimestamp)

        // Server builds Type 2 Handshake Response
        val serverEpriv = WireGuardCrypto.generatePrivateKey()
        val serverEpub = WireGuardCrypto.publicKeyFromPrivate(serverEpriv)
        val serverSenderIdx = 0x12345678

        ck = WireGuardCrypto.kdf1(ck, serverEpub)
        h = WireGuardCrypto.hash(h, serverEpub)
        ck = WireGuardCrypto.kdf1(ck, WireGuardCrypto.x25519(serverEpriv, clientEpub))
        ck = WireGuardCrypto.kdf1(ck, WireGuardCrypto.x25519(serverEpriv, clientPub))
        val (ckFinal, tau, k3) = WireGuardCrypto.kdf3(ck, ByteArray(32))
        h = WireGuardCrypto.hash(h, tau)
        val encNothing = WireGuardCrypto.aeadEncrypt(k3, ByteArray(12), ByteArray(0), h)

        val respMsg = ByteArray(92)
        respMsg[0] = WireGuardSession.TYPE_HANDSHAKE_RESP
        WireGuardCrypto.writeLE32(respMsg, 4, serverSenderIdx)
        WireGuardCrypto.writeLE32(respMsg, 8, clientSenderIdx)
        serverEpub.copyInto(respMsg, 12)
        encNothing.copyInto(respMsg, 44)

        val (serverRecvKey, serverSendKey) = WireGuardCrypto.kdf2(ckFinal, ByteArray(0))

        // Client consumes Type 2
        assertTrue(clientSession.consumeHandshakeResponse(respMsg))

        // Verify bidirectional transport encryption (dummy 28-byte IPv4 packet)
        val fakeIpv4 = ByteArray(28).apply {
            this[0] = 0x45.toByte()
            this[2] = 0x00.toByte()
            this[3] = 28.toByte()
            this[9] = 17.toByte()
            this[20] = 0xAB.toByte()
            this[27] = 0xCD.toByte()
        }
        val wirePacket = clientSession.encryptTransportPacket(fakeIpv4)!!
        assertEquals(WireGuardSession.TYPE_TRANSPORT_DATA, wirePacket[0])
        assertEquals(serverSenderIdx, WireGuardCrypto.readLE32(wirePacket, 4))

        // Server decrypts client's transport packet
        val counter = WireGuardCrypto.readLE64(wirePacket, 8)
        val serverDecrypted = WireGuardCrypto.aeadDecrypt(
            serverRecvKey,
            WireGuardCrypto.wireGuardNonce(counter),
            wirePacket.copyOfRange(16, wirePacket.size)
        )
        assertNotNull(serverDecrypted)
        assertArrayEquals(fakeIpv4, serverDecrypted!!.copyOf(28))

        // Server encrypts response packet -> Client decrypts & strips WireGuard padding
        val serverEncrypted = WireGuardCrypto.aeadEncrypt(
            serverSendKey,
            WireGuardCrypto.wireGuardNonce(0L),
            ByteArray(32).also { fakeIpv4.copyInto(it) } // padded to 32 bytes
        )
        val downlink = ByteArray(16 + serverEncrypted.size).apply {
            this[0] = WireGuardSession.TYPE_TRANSPORT_DATA
            WireGuardCrypto.writeLE32(this, 4, clientSenderIdx)
            WireGuardCrypto.writeLE64(this, 8, 0L)
            serverEncrypted.copyInto(this, 16)
        }
        val clientDecrypted = clientSession.decryptTransportPacket(downlink)
        assertNotNull(clientDecrypted)
        assertArrayEquals(fakeIpv4, clientDecrypted)
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
