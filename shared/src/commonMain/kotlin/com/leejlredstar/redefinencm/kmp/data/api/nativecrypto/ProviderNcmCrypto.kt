@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.MD5
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import dev.whyoleg.cryptography.random.CryptographyRandom

/** All cryptographic operations here delegate to cryptography-kotlin's native provider. */
internal abstract class ProviderNcmCrypto(private val provider: CryptographyProvider) : NcmCrypto {
    override fun secureRandom(size: Int): ByteArray {
        require(size >= 0) { "Negative random byte count" }
        return CryptographyRandom.nextBytes(size)
    }

    override fun md5(bytes: ByteArray): ByteArray = provider.get(MD5).hasher().hashBlocking(bytes)

    override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        checkAesKey(key)
        require(iv.size == 16) { "AES-CBC requires a 16-byte IV" }
        return provider.get(AES.CBC).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher(padding = true).encryptWithIvBlocking(iv, data)
    }

    override fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        checkAesKey(key)
        return provider.get(AES.ECB).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher(padding = true).encryptBlocking(data)
    }

    override fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        checkAesKey(key)
        require(data.isNotEmpty() && data.size % 16 == 0) { "Invalid AES-ECB ciphertext length" }
        return provider.get(AES.ECB).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher(padding = true).decryptBlocking(data)
    }

    override fun rsaRawEncrypt(data: ByteArray, publicKeyDer: ByteArray): ByteArray =
        provider.get(RSA.RAW).publicKeyDecoder(SHA256)
            .decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, publicKeyDer)
            .encryptor().encryptBlocking(data)

    override fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray =
        provider.get(HMAC).keyDecoder(SHA256).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
            .signatureGenerator().generateSignatureBlocking(data)

    override fun x25519PublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "X25519 requires a 32-byte private key" }
        return provider.get(XDH).privateKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArrayBlocking(XDH.PrivateKey.Format.RAW, privateKey)
            .getPublicKeyBlocking().encodeToByteArrayBlocking(XDH.PublicKey.Format.RAW)
    }

    override fun x25519SharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32 && publicKey.size == 32) { "Invalid X25519 key length" }
        val xdh = provider.get(XDH)
        val secret = xdh.privateKeyDecoder(XDH.Curve.X25519)
            .decodeFromByteArrayBlocking(XDH.PrivateKey.Format.RAW, privateKey)
            .sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(
                xdh.publicKeyDecoder(XDH.Curve.X25519)
                    .decodeFromByteArrayBlocking(XDH.PublicKey.Format.RAW, publicKey),
            )
        // RFC 7748 permits this check; reject low-order public keys consistently across providers.
        require(secret.size == 32 && secret.any { it != 0.toByte() }) { "Invalid X25519 shared secret" }
        return secret
    }

    override fun aesGcmEncrypt(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        checkAesKey(key)
        require(nonce.size == 12) { "AES-GCM requires a 12-byte nonce" }
        return provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher().encryptWithIvBlocking(nonce, data, associatedData)
    }

    override fun aesGcmDecrypt(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        checkAesKey(key)
        require(nonce.size == 12 && data.size >= 16) { "Invalid AES-GCM sealed data" }
        return provider.get(AES.GCM).keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
            .cipher().decryptWithIvBlocking(nonce, data, associatedData)
    }
}
