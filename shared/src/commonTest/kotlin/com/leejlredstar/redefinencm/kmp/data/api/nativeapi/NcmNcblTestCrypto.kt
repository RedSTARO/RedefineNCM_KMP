package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmCrypto

/** Non-cryptographic test double: common record tests must also run on the server-only Web target. */
internal class NcmNcblTestCrypto : NcmCrypto {
    var randomCalls = 0
        private set

    override fun secureRandom(size: Int): ByteArray {
        randomCalls++
        return ByteArray(size) { (it + 1).toByte() }
    }

    override fun md5(bytes: ByteArray): ByteArray = unused()
    override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = unused()
    override fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray = unused()
    override fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray = unused()
    override fun rsaRawEncrypt(data: ByteArray, publicKeyDer: ByteArray): ByteArray = unused()
    override fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray = unused()
    override fun x25519PublicKey(privateKey: ByteArray): ByteArray = unused()
    override fun x25519SharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray = unused()
    override fun aesGcmEncrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray = unused()
    override fun aesGcmDecrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray = unused()
    override fun chacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray = unused()
    override fun rsaModPow(data: ByteArray, exponent: ByteArray, modulus: ByteArray, outputSize: Int): ByteArray = unused()

    private fun unused(): Nothing = error("Record-only fixture must not encrypt")
}
