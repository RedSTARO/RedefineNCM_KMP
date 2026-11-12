package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

/**
 * Cryptographic primitives needed by the upstream NetEase protocols, not a general-purpose API.
 *
 * CBC/ECB use PKCS#7 padding (including a full padding block for block-aligned input). GCM uses
 * a 12-byte nonce and returns ciphertext followed by its 16-byte authentication tag, without IV.
 * X25519 keys and shared secrets are raw 32-byte RFC 7748 values. RSA values are unsigned,
 * big-endian bytes; [rsaRawEncrypt] takes a complete, modulus-sized, zero-left-padded block and
 * an RFC 5280 SubjectPublicKeyInfo DER public key. No method silently substitutes an algorithm.
 *
 * MD5, ECB and unpadded RSA are retained solely for compatibility with the existing wire format.
 */
interface NcmCrypto {
    fun secureRandom(size: Int): ByteArray
    fun md5(bytes: ByteArray): ByteArray
    fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray
    fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray
    fun rsaRawEncrypt(data: ByteArray, publicKeyDer: ByteArray): ByteArray
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray
    fun x25519PublicKey(privateKey: ByteArray): ByteArray
    fun x25519SharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray
    fun aesGcmEncrypt(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray = byteArrayOf(),
    ): ByteArray
    fun aesGcmDecrypt(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray = byteArrayOf(),
    ): ByteArray

    /** IETF ChaCha20: key32, nonce12, an unsigned 32-bit initial block counter; no Poly1305. */
    fun chacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray

    /** Modular exponentiation for the upstream NCBL key wrap; fixed-width, zero-left-padded output. */
    fun rsaModPow(
        data: ByteArray,
        exponent: ByteArray,
        modulus: ByteArray,
        outputSize: Int,
    ): ByteArray
}

/** Native targets use mature cryptographic providers. Web deliberately retains its HTTP server. */
expect fun platformNcmCrypto(): NcmCrypto

internal fun checkAesKey(key: ByteArray) {
    require(key.size == 16 || key.size == 24 || key.size == 32) { "Invalid AES key length" }
}

internal fun checkChacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt) {
    require(key.size == 32) { "ChaCha20 requires a 32-byte key" }
    require(nonce.size == 12) { "ChaCha20 requires a 12-byte nonce" }
    // OpenSSL's raw ChaCha20 and the IETF variant have the same state until the low counter
    // overflows. Reject that boundary on every target instead of reusing/carrying into a nonce.
    require(data.size.toLong() <= (0x1_0000_0000L - counter.toLong()) * 64L) {
        "ChaCha20 block counter would overflow"
    }
}

internal fun checkModPow(exponent: ByteArray, modulus: ByteArray, outputSize: Int) {
    require(exponent.isNotEmpty()) { "Missing modular exponent" }
    require(modulus.any { it != 0.toByte() }) { "Invalid modular modulus" }
    require(outputSize > 0) { "Invalid modular output size" }
}
