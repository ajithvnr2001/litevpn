package com.example.vpn.crypto

import java.security.SecureRandom

/**
 * Pure-Kotlin, zero-dependency cryptographic primitives for WireGuard
 * (Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s):
 * - X25519 (RFC 7748)
 * - BLAKE2s-256, Keyed BLAKE2s, HMAC-BLAKE2s, HKDF-BLAKE2s (RFC 7693)
 * - ChaCha20, HChaCha20, Poly1305, ChaCha20-Poly1305 AEAD, XChaCha20-Poly1305 (RFC 8439)
 * - TAI64N timestamp & Base64 key decoding
 */
object WireGuardCrypto {

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    fun generatePrivateKey(): ByteArray {
        val k = randomBytes(32)
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()
        return k
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        val basepoint = ByteArray(32).apply { this[0] = 9 }
        return x25519(privateKey, basepoint)
    }

    // -------------------------------------------------------------------------
    // X25519 (RFC 7748) — 16-limb TweetNaCl/Montgomery ladder over GF(2^255-19)
    // -------------------------------------------------------------------------

    fun x25519(scalar: ByteArray, uCoord: ByteArray): ByteArray {
        require(scalar.size == 32 && uCoord.size == 32)
        val z = scalar.copyOf()
        z[0] = (z[0].toInt() and 248).toByte()
        z[31] = ((z[31].toInt() and 127) or 64).toByte()

        val x = LongArray(16)
        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        val d = LongArray(16)
        val e = LongArray(16)
        val f = LongArray(16)

        unpack25519(x, uCoord)
        for (i in 0 until 16) b[i] = x[i]
        a[0] = 1
        d[0] = 1

        for (i in 254 downTo 0) {
            val r = ((z[i ushr 3].toInt() ushr (i and 7)) and 1).toLong()
            sel25519(a, b, r)
            sel25519(c, d, r)
            add25519(e, a, c)
            sub25519(a, a, c)
            add25519(c, b, d)
            sub25519(b, b, d)
            mul25519(d, e, e)
            mul25519(f, a, a)
            mul25519(a, c, a)
            mul25519(c, b, e)
            add25519(e, a, c)
            sub25519(a, a, c)
            mul25519(b, a, a)
            sub25519(c, d, f)
            mul25519(a, c, CONST_121665)
            add25519(a, a, d)
            mul25519(c, c, a)
            mul25519(a, d, f)
            mul25519(d, b, x)
            mul25519(b, e, e)
            sel25519(a, b, r)
            sel25519(c, d, r)
        }

        inv25519(c, c)
        mul25519(a, a, c)
        val out = ByteArray(32)
        pack25519(out, a)
        return out
    }

    private val CONST_121665 = LongArray(16).apply {
        this[0] = 0xDB41L
        this[1] = 1L
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) {
            o[i] = ((n[2 * i].toInt() and 0xFF) or ((n[2 * i + 1].toInt() and 0xFF) shl 8)).toLong()
        }
        o[15] = o[15] and 0x7FFFL
    }

    private fun carry25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            o[(i + 1) * (if (i < 15) 1 else 0)] += c - 1 + (if (i == 15) 37L * (c - 1) else 0L)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Long) {
        val c = -(b and 1L)
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun add25519(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub25519(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul25519(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38L * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        carry25519(o)
        carry25519(o)
    }

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            mul25519(c, c, c)
            if (a != 2 && a != 4) mul25519(c, c, i)
        }
        for (k in 0 until 16) o[k] = c[k]
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = LongArray(16)
        val t = n.copyOf()
        carry25519(t)
        carry25519(t)
        carry25519(t)
        repeat(2) {
            m[0] = t[0] - 0xFFEDL
            for (i in 1 until 15) {
                m[i] = t[i] - 0xFFFFL - ((m[i - 1] shr 16) and 1L)
                m[i - 1] = m[i - 1] and 0xFFFFL
            }
            m[15] = t[15] - 0x7FFFL - ((m[14] shr 16) and 1L)
            val b = (m[15] shr 16) and 1L
            m[14] = m[14] and 0xFFFFL
            sel25519(t, m, 1L - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xFFL).toByte()
            o[2 * i + 1] = ((t[i] shr 8) and 0xFFL).toByte()
        }
    }

    // -------------------------------------------------------------------------
    // BLAKE2s (RFC 7693), HMAC-BLAKE2s, and WireGuard HKDF (KDF1/KDF2/KDF3)
    // -------------------------------------------------------------------------

    private val BLAKE2S_IV = intArrayOf(
        0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
        0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19
    )

    private val BLAKE2S_SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    fun blake2s(data: ByteArray, outLen: Int = 32, key: ByteArray = ByteArray(0)): ByteArray {
        require(outLen in 1..32 && key.size <= 32)
        val h = BLAKE2S_IV.copyOf()
        h[0] = h[0] xor (0x01010000 or (key.size shl 8) or outLen)

        val buf = ByteArray(64)
        var bufLen = 0
        var t0 = 0L

        fun compress(last: Boolean) {
            val v = IntArray(16)
            for (i in 0 until 8) {
                v[i] = h[i]
                v[i + 8] = BLAKE2S_IV[i]
            }
            v[12] = v[12] xor (t0 and 0xFFFFFFFFL).toInt()
            v[13] = v[13] xor ((t0 ushr 32) and 0xFFFFFFFFL).toInt()
            if (last) v[14] = v[14].inv()

            val m = IntArray(16) { idx -> readLE32(buf, idx * 4) }

            fun g(a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
                v[a] = v[a] + v[b] + x
                v[d] = Integer.rotateRight(v[d] xor v[a], 16)
                v[c] = v[c] + v[d]
                v[b] = Integer.rotateRight(v[b] xor v[c], 12)
                v[a] = v[a] + v[b] + y
                v[d] = Integer.rotateRight(v[d] xor v[a], 8)
                v[c] = v[c] + v[d]
                v[b] = Integer.rotateRight(v[b] xor v[c], 7)
            }

            for (round in 0 until 10) {
                val s = BLAKE2S_SIGMA[round]
                g(0, 4, 8, 12, m[s[0]], m[s[1]])
                g(1, 5, 9, 13, m[s[2]], m[s[3]])
                g(2, 6, 10, 14, m[s[4]], m[s[5]])
                g(3, 7, 11, 15, m[s[6]], m[s[7]])
                g(0, 5, 10, 15, m[s[8]], m[s[9]])
                g(1, 6, 11, 12, m[s[10]], m[s[11]])
                g(2, 7, 8, 13, m[s[12]], m[s[13]])
                g(3, 4, 9, 14, m[s[14]], m[s[15]])
            }

            for (i in 0 until 8) {
                h[i] = h[i] xor v[i] xor v[i + 8]
            }
        }

        if (key.isNotEmpty()) {
            key.copyInto(buf, 0)
            bufLen = 64
        }

        for (b in data) {
            if (bufLen == 64) {
                t0 += 64L
                compress(false)
                bufLen = 0
            }
            buf[bufLen++] = b
        }

        t0 += bufLen.toLong()
        for (i in bufLen until 64) buf[i] = 0
        compress(true)

        val out = ByteArray(outLen)
        for (i in 0 until outLen) {
            out[i] = ((h[i ushr 2] ushr (8 * (i and 3))) and 0xFF).toByte()
        }
        return out
    }

    fun hash(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val merged = ByteArray(total)
        var off = 0
        for (p in parts) {
            p.copyInto(merged, off)
            off += p.size
        }
        return blake2s(merged, 32)
    }

    fun mac(key: ByteArray, data: ByteArray, length: Int = data.size): ByteArray {
        val slice = if (length == data.size) data else data.copyOf(length)
        return blake2s(slice, 16, key)
    }

    fun hmacBlake2s(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val block = ByteArray(64)
        val k = if (key.size > 64) blake2s(key, 32) else key
        k.copyInto(block, 0)

        val ipad = ByteArray(64) { i -> (block[i].toInt() xor 0x36).toByte() }
        val opad = ByteArray(64) { i -> (block[i].toInt() xor 0x5C).toByte() }

        val inner = hash(ipad, *parts)
        return hash(opad, inner)
    }

    fun kdf1(chainingKey: ByteArray, input: ByteArray): ByteArray {
        val prk = hmacBlake2s(chainingKey, input)
        return hmacBlake2s(prk, byteArrayOf(0x01))
    }

    fun kdf2(chainingKey: ByteArray, input: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmacBlake2s(chainingKey, input)
        val t1 = hmacBlake2s(prk, byteArrayOf(0x01))
        val t2 = hmacBlake2s(prk, t1, byteArrayOf(0x02))
        return t1 to t2
    }

    fun kdf3(chainingKey: ByteArray, input: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val prk = hmacBlake2s(chainingKey, input)
        val t1 = hmacBlake2s(prk, byteArrayOf(0x01))
        val t2 = hmacBlake2s(prk, t1, byteArrayOf(0x02))
        val t3 = hmacBlake2s(prk, t2, byteArrayOf(0x03))
        return Triple(t1, t2, t3)
    }

    // -------------------------------------------------------------------------
    // ChaCha20, HChaCha20, Poly1305, ChaCha20-Poly1305 AEAD (RFC 8439)
    // -------------------------------------------------------------------------

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }

    private fun chacha20Block(key: ByteArray, counter: Int, nonce12: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646E
        state[2] = 0x79622D32
        state[3] = 0x6B206574
        for (i in 0 until 8) state[4 + i] = readLE32(key, i * 4)
        state[12] = counter
        state[13] = readLE32(nonce12, 0)
        state[14] = readLE32(nonce12, 4)
        state[15] = readLE32(nonce12, 8)

        val working = state.copyOf()
        repeat(10) {
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)
            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }

        val out = ByteArray(64)
        for (i in 0 until 16) {
            writeLE32(out, i * 4, working[i] + state[i])
        }
        return out
    }

    fun hchacha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val s = IntArray(16)
        s[0] = 0x61707865
        s[1] = 0x3320646E
        s[2] = 0x79622D32
        s[3] = 0x6B206574
        for (i in 0 until 8) s[4 + i] = readLE32(key, i * 4)
        for (i in 0 until 4) s[12 + i] = readLE32(nonce16, i * 4)

        repeat(10) {
            quarterRound(s, 0, 4, 8, 12)
            quarterRound(s, 1, 5, 9, 13)
            quarterRound(s, 2, 6, 10, 14)
            quarterRound(s, 3, 7, 11, 15)
            quarterRound(s, 0, 5, 10, 15)
            quarterRound(s, 1, 6, 11, 12)
            quarterRound(s, 2, 7, 8, 13)
            quarterRound(s, 3, 4, 9, 14)
        }

        val out = ByteArray(32)
        for (i in 0 until 4) writeLE32(out, i * 4, s[i])
        for (i in 0 until 4) writeLE32(out, 16 + i * 4, s[12 + i])
        return out
    }

    private fun chacha20Xor(key: ByteArray, initialCounter: Int, nonce12: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var counter = initialCounter
        var offset = 0
        while (offset < input.size) {
            val block = chacha20Block(key, counter++, nonce12)
            val chunk = minOf(64, input.size - offset)
            for (i in 0 until chunk) {
                out[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += chunk
        }
        return out
    }

    // Poly1305 (RFC 8439) using 5x26-bit limbs (Donna style — zero BigInteger allocations!)
    private fun readLE32U(buf: ByteArray, offset: Int): Long =
        readLE32(buf, offset).toLong() and 0xFFFFFFFFL

    private fun poly1305Mac(otk: ByteArray, msg: ByteArray): ByteArray {
        val r0 = readLE32U(otk, 0) and 0x3FFFFFFL
        val r1 = (readLE32U(otk, 3) ushr 2) and 0x3FFFF03L
        val r2 = (readLE32U(otk, 6) ushr 4) and 0x3FFC0FFL
        val r3 = (readLE32U(otk, 9) ushr 6) and 0x3F03FFFL
        val r4 = (readLE32U(otk, 12) ushr 8) and 0x00FFFFFL

        val s1 = r1 * 5L
        val s2 = r2 * 5L
        val s3 = r3 * 5L
        val s4 = r4 * 5L

        var h0 = 0L
        var h1 = 0L
        var h2 = 0L
        var h3 = 0L
        var h4 = 0L

        val block = ByteArray(17)
        var offset = 0
        while (offset < msg.size) {
            val chunk = minOf(16, msg.size - offset)
            block.fill(0)
            msg.copyInto(block, 0, offset, offset + chunk)
            block[chunk] = 1

            h0 += readLE32U(block, 0) and 0x3FFFFFFL
            h1 += (readLE32U(block, 3) ushr 2) and 0x3FFFFFFL
            h2 += (readLE32U(block, 6) ushr 4) and 0x3FFFFFFL
            h3 += (readLE32U(block, 9) ushr 6) and 0x3FFFFFFL
            h4 += (readLE32U(block, 12) ushr 8) or ((block[16].toLong() and 0xFFL) shl 24)

            val d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1
            val d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            var c = d0 ushr 26; h0 = d0 and 0x3FFFFFFL
            val t1 = d1 + c; c = t1 ushr 26; h1 = t1 and 0x3FFFFFFL
            val t2 = d2 + c; c = t2 ushr 26; h2 = t2 and 0x3FFFFFFL
            val t3 = d3 + c; c = t3 ushr 26; h3 = t3 and 0x3FFFFFFL
            val t4 = d4 + c; c = t4 ushr 26; h4 = t4 and 0x3FFFFFFL
            h0 += c * 5L; c = h0 ushr 26; h0 = h0 and 0x3FFFFFFL
            h1 += c

            offset += chunk
        }

        // Full carry
        var c = h1 ushr 26; h1 = h1 and 0x3FFFFFFL
        h2 += c; c = h2 ushr 26; h2 = h2 and 0x3FFFFFFL
        h3 += c; c = h3 ushr 26; h3 = h3 and 0x3FFFFFFL
        h4 += c; c = h4 ushr 26; h4 = h4 and 0x3FFFFFFL
        h0 += c * 5L; c = h0 ushr 26; h0 = h0 and 0x3FFFFFFL
        h1 += c

        // Compute h + -p
        var g0 = h0 + 5L; c = g0 ushr 26; g0 = g0 and 0x3FFFFFFL
        var g1 = h1 + c; c = g1 ushr 26; g1 = g1 and 0x3FFFFFFL
        var g2 = h2 + c; c = g2 ushr 26; g2 = g2 and 0x3FFFFFFL
        var g3 = h3 + c; c = g3 ushr 26; g3 = g3 and 0x3FFFFFFL
        var g4 = h4 + c - (1L shl 26)

        val mask = (g4 ushr 63) - 1L
        g0 = g0 and mask; g1 = g1 and mask; g2 = g2 and mask; g3 = g3 and mask; g4 = g4 and mask
        val invMask = mask.inv()
        h0 = (h0 and invMask) or g0
        h1 = (h1 and invMask) or g1
        h2 = (h2 and invMask) or g2
        h3 = (h3 and invMask) or g3
        h4 = (h4 and invMask) or g4

        // Pack h into 4x32-bit words
        var f0 = (h0 or (h1 shl 26)) and 0xFFFFFFFFL
        var f1 = ((h1 ushr 6) or (h2 shl 20)) and 0xFFFFFFFFL
        var f2 = ((h2 ushr 12) or (h3 shl 14)) and 0xFFFFFFFFL
        var f3 = ((h3 ushr 18) or (h4 shl 8)) and 0xFFFFFFFFL

        // Add s = otk[16..31]
        f0 += readLE32U(otk, 16)
        f1 += readLE32U(otk, 20) + (f0 ushr 32)
        f2 += readLE32U(otk, 24) + (f1 ushr 32)
        f3 += readLE32U(otk, 28) + (f2 ushr 32)

        val tag = ByteArray(16)
        writeLE32(tag, 0, f0.toInt())
        writeLE32(tag, 4, f1.toInt())
        writeLE32(tag, 8, f2.toInt())
        writeLE32(tag, 12, f3.toInt())
        return tag
    }

    private fun buildPoly1305Input(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val aadPad = (16 - (aad.size % 16)) % 16
        val ctPad = (16 - (ciphertext.size % 16)) % 16
        val total = aad.size + aadPad + ciphertext.size + ctPad + 16
        val out = ByteArray(total)
        var pos = 0
        aad.copyInto(out, pos); pos += aad.size + aadPad
        ciphertext.copyInto(out, pos); pos += ciphertext.size + ctPad
        writeLE64(out, pos, aad.size.toLong()); pos += 8
        writeLE64(out, pos, ciphertext.size.toLong())
        return out
    }

    fun wireGuardNonce(counter: Long): ByteArray {
        val nonce = ByteArray(12)
        writeLE64(nonce, 4, counter)
        return nonce
    }

    fun aeadEncrypt(key: ByteArray, nonce12: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val otk = chacha20Block(key, 0, nonce12).copyOf(32)
        val ciphertext = chacha20Xor(key, 1, nonce12, plaintext)
        val tag = poly1305Mac(otk, buildPoly1305Input(aad, ciphertext))
        return ciphertext + tag
    }

    fun aeadDecrypt(key: ByteArray, nonce12: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        if (ciphertextAndTag.size < 16) return null
        val ctLen = ciphertextAndTag.size - 16
        val ciphertext = ciphertextAndTag.copyOf(ctLen)
        val receivedTag = ciphertextAndTag.copyOfRange(ctLen, ciphertextAndTag.size)
        val otk = chacha20Block(key, 0, nonce12).copyOf(32)
        val expectedTag = poly1305Mac(otk, buildPoly1305Input(aad, ciphertext))
        if (!constantTimeEquals(receivedTag, expectedTag)) return null
        return chacha20Xor(key, 1, nonce12, ciphertext)
    }

    fun xAeadDecrypt(key: ByteArray, nonce24: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        require(nonce24.size == 24)
        val subkey = hchacha20(key, nonce24.copyOf(16))
        val nonce12 = ByteArray(12)
        nonce24.copyInto(nonce12, 4, 16, 24)
        return aeadDecrypt(subkey, nonce12, ciphertextAndTag, aad)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // -------------------------------------------------------------------------
    // TAI64N Timestamp & Byte Helpers
    // -------------------------------------------------------------------------

    fun tai64nNow(): ByteArray {
        val millis = System.currentTimeMillis()
        val secs = (millis / 1000L) + 4611686018427387914L // 2^62 + 10
        val nanos = ((millis % 1000L) * 1_000_000L).toInt()
        val out = ByteArray(12)
        writeBE64(out, 0, secs)
        writeBE32(out, 8, nanos)
        return out
    }

    fun readLE32(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    fun writeLE32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    fun readLE64(buf: ByteArray, offset: Int): Long =
        (readLE32(buf, offset).toLong() and 0xFFFFFFFFL) or
            ((readLE32(buf, offset + 4).toLong() and 0xFFFFFFFFL) shl 32)

    fun writeLE64(buf: ByteArray, offset: Int, value: Long) {
        writeLE32(buf, offset, (value and 0xFFFFFFFFL).toInt())
        writeLE32(buf, offset + 4, ((value ushr 32) and 0xFFFFFFFFL).toInt())
    }

    private fun writeBE32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeBE64(buf: ByteArray, offset: Int, value: Long) {
        writeBE32(buf, offset, ((value ushr 32) and 0xFFFFFFFFL).toInt())
        writeBE32(buf, offset + 4, (value and 0xFFFFFFFFL).toInt())
    }

    private val B64_TABLE = IntArray(128) { -1 }.apply {
        for (c in 'A'..'Z') this[c.code] = c - 'A'
        for (c in 'a'..'z') this[c.code] = c - 'a' + 26
        for (c in '0'..'9') this[c.code] = c - '0' + 52
        this['+'.code] = 62
        this['/'.code] = 63
    }

    fun decodeBase64(input: String): ByteArray {
        val clean = input.trim().filter { !it.isWhitespace() }
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var acc = 0
        var bits = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = if (ch.code < 128) B64_TABLE[ch.code] else -1
            require(v >= 0) { "Invalid Base64 character" }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((acc ushr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
