package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import java.math.BigInteger
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.provider.BouncyCastleProvider

actual fun platformNcmCrypto(): NcmCrypto = AndroidNcmCrypto

// Explicit, app-owned BC supports X25519 on minSdk 24 without using or replacing Android's
// bundled historical provider. Every algorithm comes from the mature provider, not app code.
private object AndroidNcmCrypto : ProviderNcmCrypto(CryptographyProvider.JDK(BouncyCastleProvider())) {
    override fun chacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray {
        checkChacha20(data, key, nonce, counter)
        val cipher = ChaCha7539Engine()
        cipher.init(true, ParametersWithIV(KeyParameter(key), nonce))
        cipher.seekTo(counter.toLong() * 64L)
        return ByteArray(data.size).also { output -> cipher.processBytes(data, 0, data.size, output, 0) }
    }

    override fun rsaModPow(
        data: ByteArray,
        exponent: ByteArray,
        modulus: ByteArray,
        outputSize: Int,
    ): ByteArray {
        checkModPow(exponent, modulus, outputSize)
        val base = if (data.isEmpty()) BigInteger.ZERO else BigInteger(1, data)
        val value = base.modPow(BigInteger(1, exponent), BigInteger(1, modulus)).toByteArray()
        val unsigned = if (value.size > 1 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value
        require(unsigned.size <= outputSize) { "Modular result exceeds output size" }
        return ByteArray(outputSize).also { unsigned.copyInto(it, outputSize - unsigned.size) }
    }
}
