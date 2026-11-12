package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/** Published RFC/NIST known answers, in addition to the independent protocol envelope vectors. */
class NcmCryptoPrimitiveTest {
    private val crypto = platformNcmCrypto()

    @Test
    fun rfc1321Md5() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", crypto.md5(byteArrayOf()).toHex())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", crypto.md5("abc".encodeToByteArray()).toHex())
    }

    @Test
    fun rfc4231HmacSha256() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            crypto.hmacSha256("Hi There".encodeToByteArray(), ByteArray(20) { 0x0b }).toHex(),
        )
    }

    @Test
    fun nistAesCbcFirstBlockAndPkcs7FullPaddingBlock() {
        val key = "2b7e151628aed2a6abf7158809cf4f3c".hexToBytes()
        val iv = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val plaintext = "6bc1bee22e409f96e93d7e117393172a".hexToBytes()
        val ciphertext = crypto.aesCbcEncrypt(plaintext, key, iv)
        assertEquals(32, ciphertext.size)
        assertEquals("7649abac8119b246cee98e9b12e9197d", ciphertext.copyOfRange(0, 16).toHex())
        assertEquals(16, crypto.aesCbcEncrypt(byteArrayOf(), key, iv).size)
    }

    @Test
    fun nistAesEcbFirstBlockAndPkcs7RoundTripsBinaryAtAllKeySizes() {
        val key = "000102030405060708090a0b0c0d0e0f".hexToBytes()
        val plaintext = "00112233445566778899aabbccddeeff".hexToBytes()
        assertEquals(
            "69c4e0d86a7b0430d8cdb78070b4c55a",
            crypto.aesEcbEncrypt(plaintext, key).copyOfRange(0, 16).toHex(),
        )
        for (size in listOf(16, 24, 32)) {
            val activeKey = ByteArray(size) { it.toByte() }
            for (length in listOf(0, 1, 15, 16, 17, 31, 32, 256)) {
                val data = ByteArray(length) { it.toByte() }
                val sealed = crypto.aesEcbEncrypt(data, activeKey)
                assertEquals((length / 16 + 1) * 16, sealed.size)
                assertContentEquals(data, crypto.aesEcbDecrypt(sealed, activeKey))
            }
        }
    }

    @Test
    fun nistAesGcmReturnsCiphertextAndTagWithoutNonce() {
        val key = ByteArray(16)
        val iv = ByteArray(12)
        // Encrypt FIRST. whyoleg pools the JCA Cipher by algorithm, and BC remembers its last
        // key/nonce even after decryption; decrypting the empty NIST vector before this call
        // therefore triggers BC's correct nonce-reuse guard. No provider behavior is bypassed.
        val sealed = crypto.aesGcmEncrypt(ByteArray(16), key, iv)
        assertEquals("0388dace60b6a392f328c2b971b2fe78ab6e47d42cec13bdf53a67b21257bddf", sealed.toHex())
        assertContentEquals(byteArrayOf(), crypto.aesGcmDecrypt("58e2fccefa7e3061367f1d57a4e7455a".hexToBytes(), key, iv))
        assertContentEquals(ByteArray(16), crypto.aesGcmDecrypt(sealed, key, iv))
        val corrupted = sealed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFails { crypto.aesGcmDecrypt(corrupted, key, iv) }
        val aadIv = iv.copyOf().also { it[it.lastIndex] = 1 }
        val withAad = crypto.aesGcmEncrypt("binary\u0000payload".encodeToByteArray(), key, aadIv, byteArrayOf(1, 2, 3))
        assertContentEquals("binary\u0000payload".encodeToByteArray(), crypto.aesGcmDecrypt(withAad, key, aadIv, byteArrayOf(1, 2, 3)))
        assertFails { crypto.aesGcmDecrypt(withAad, key, aadIv, byteArrayOf(1, 2, 4)) }
    }

    @Test
    fun rfc7748X25519KeyDerivationAndAgreement() {
        val alice = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes()
        val alicePublic = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a".hexToBytes()
        val bob = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb".hexToBytes()
        val bobPublic = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f".hexToBytes()
        assertContentEquals(alicePublic, crypto.x25519PublicKey(alice))
        assertContentEquals(bobPublic, crypto.x25519PublicKey(bob))
        val shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742".hexToBytes()
        assertContentEquals(shared, crypto.x25519SharedSecret(alice, bobPublic))
        assertContentEquals(shared, crypto.x25519SharedSecret(bob, alicePublic))
        assertFails { crypto.x25519SharedSecret(alice, ByteArray(32)) }
    }

    @Test
    fun rfc8439ChaCha20BlockOneUsesIetf96BitNonce() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = "000000090000004a00000000".hexToBytes()
        assertEquals(
            "10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e" +
                "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e",
            crypto.chacha20(ByteArray(64), key, nonce, 1u).toHex(),
        )
        val data = ByteArray(150) { it.toByte() }
        val sealed = crypto.chacha20(data, key, nonce, 17u)
        assertContentEquals(data, crypto.chacha20(sealed, key, nonce, 17u))
        assertContentEquals(byteArrayOf(), crypto.chacha20(byteArrayOf(), key, nonce, 0u))
    }

    @Test
    fun modularExponentiationUsesUnsignedBigEndianAndFixedOutputLength() {
        // 4^13 mod 497 = 445; a leading zero is significant to the output width, not the input.
        assertEquals("000001bd", crypto.rsaModPow(byteArrayOf(4), byteArrayOf(13), "01f1".hexToBytes(), 4).toHex())
        // Unsigned input 0x80, not negative -128; 128^1 mod 257 = 128.
        assertEquals("0080", crypto.rsaModPow("80".hexToBytes(), byteArrayOf(1), "0101".hexToBytes(), 2).toHex())
        assertEquals("0000", crypto.rsaModPow(byteArrayOf(), byteArrayOf(1), "0101".hexToBytes(), 2).toHex())
        assertFailsWith<IllegalArgumentException> {
            crypto.rsaModPow("0100".hexToBytes(), byteArrayOf(1), "0101".hexToBytes(), 1)
        }
    }

    @Test
    fun invalidPrimitiveParametersDoNotFallBack() {
        assertFailsWith<IllegalArgumentException> { crypto.secureRandom(-1) }
        assertEquals(0, crypto.secureRandom(0).size)
        assertEquals(37, crypto.secureRandom(37).size)
        assertFailsWith<IllegalArgumentException> { crypto.aesEcbEncrypt(byteArrayOf(), ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { crypto.aesEcbDecrypt(byteArrayOf(), ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { crypto.aesCbcEncrypt(byteArrayOf(), ByteArray(16), ByteArray(15)) }
        assertFailsWith<IllegalArgumentException> { crypto.aesGcmEncrypt(byteArrayOf(), ByteArray(16), ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { crypto.x25519PublicKey(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { crypto.chacha20(byteArrayOf(), ByteArray(31), ByteArray(12), 0u) }
        assertFailsWith<IllegalArgumentException> { crypto.chacha20(byteArrayOf(), ByteArray(32), ByteArray(8), 0u) }
        assertFailsWith<IllegalArgumentException> { crypto.chacha20(ByteArray(65), ByteArray(32), ByteArray(12), UInt.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { crypto.rsaModPow(byteArrayOf(1), byteArrayOf(1), ByteArray(1), 1) }
    }
}
