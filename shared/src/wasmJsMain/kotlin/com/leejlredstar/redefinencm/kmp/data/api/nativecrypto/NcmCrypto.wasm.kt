package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

actual fun platformNcmCrypto(): NcmCrypto = ServerOnlyWebNcmCrypto

/** Native crypto is never used by the browser transport. Do not pretend WebCrypto has ECB/RAW. */
private object ServerOnlyWebNcmCrypto : NcmCrypto {
    private fun unavailable(): Nothing =
        throw UnsupportedOperationException("Native NetEase cryptography is not used on Web; use the HTTP server transport")

    override fun secureRandom(size: Int): ByteArray = unavailable()
    override fun md5(bytes: ByteArray): ByteArray = unavailable()
    override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = unavailable()
    override fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray = unavailable()
    override fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray = unavailable()
    override fun rsaRawEncrypt(data: ByteArray, publicKeyDer: ByteArray): ByteArray = unavailable()
    override fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray = unavailable()
    override fun x25519PublicKey(privateKey: ByteArray): ByteArray = unavailable()
    override fun x25519SharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray = unavailable()
    override fun aesGcmEncrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray = unavailable()
    override fun aesGcmDecrypt(data: ByteArray, key: ByteArray, nonce: ByteArray, associatedData: ByteArray): ByteArray = unavailable()
    override fun chacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray = unavailable()
    override fun rsaModPow(data: ByteArray, exponent: ByteArray, modulus: ByteArray, outputSize: Int): ByteArray = unavailable()
}
