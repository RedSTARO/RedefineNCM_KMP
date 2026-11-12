@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BIGNUM
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_CTX_free
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_CTX_new
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_bin2bn
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_bn2binpad
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_clear_free
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_mod_exp
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.BN_new
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_CIPHER_CTX_free
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_CIPHER_CTX_new
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_EncryptFinal_ex
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_EncryptInit_ex
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_EncryptUpdate
import dev.whyoleg.cryptography.providers.openssl3.internal.cinterop.EVP_chacha20
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

actual fun platformNcmCrypto(): NcmCrypto = IosNcmCrypto

/**
 * OpenSSL3 is supplied and statically linked by cryptography-provider-openssl3-prebuilt. Its
 * published cinterop declarations are public despite their package name; raw ChaCha20/BN are
 * used only for NCBL, which cryptography-kotlin's high-level algorithm API does not expose.
 */
private object IosNcmCrypto : ProviderNcmCrypto(CryptographyProvider.Openssl3) {
    override fun chacha20(data: ByteArray, key: ByteArray, nonce: ByteArray, counter: UInt): ByteArray {
        checkChacha20(data, key, nonce, counter)
        if (data.isEmpty()) return byteArrayOf()
        // EVP_chacha20's state is counter64 || nonce64. Encoding counter32 || nonce96 produces
        // exactly the IETF state, provided counter32 never wraps (checked on all targets above).
        val iv = ByteArray(16)
        repeat(4) { iv[it] = (counter shr (it * 8)).toByte() }
        nonce.copyInto(iv, 4)
        val context = checkNotNull(EVP_CIPHER_CTX_new()) { "Cannot allocate OpenSSL cipher context" }
        try {
            val cipher = checkNotNull(EVP_chacha20()) { "OpenSSL ChaCha20 is unavailable" }
            return memScoped {
                val moved = alloc<IntVar>()
                val output = ByteArray(data.size + 64)
                key.usePinned { keyPin ->
                    iv.usePinned { ivPin ->
                        check(EVP_EncryptInit_ex(context, cipher, null,
                            keyPin.addressOf(0).reinterpret(), ivPin.addressOf(0).reinterpret()) == 1) {
                            "Cannot initialize OpenSSL ChaCha20"
                        }
                    }
                }
                val total = output.usePinned { outputPin ->
                    data.usePinned { inputPin ->
                        check(EVP_EncryptUpdate(context, outputPin.addressOf(0).reinterpret(), moved.ptr,
                            inputPin.addressOf(0).reinterpret(), data.size) == 1) { "OpenSSL ChaCha20 failed" }
                    }
                    val updated = moved.value
                    check(EVP_EncryptFinal_ex(context, outputPin.addressOf(updated).reinterpret(), moved.ptr) == 1) {
                        "OpenSSL ChaCha20 finalization failed"
                    }
                    updated + moved.value
                }
                check(total == data.size) { "Unexpected ChaCha20 output length" }
                output.copyOf(total)
            }
        } finally {
            EVP_CIPHER_CTX_free(context)
        }
    }

    override fun rsaModPow(
        data: ByteArray,
        exponent: ByteArray,
        modulus: ByteArray,
        outputSize: Int,
    ): ByteArray {
        checkModPow(exponent, modulus, outputSize)
        val context = checkNotNull(BN_CTX_new()) { "Cannot allocate OpenSSL BN context" }
        try {
            return withBignum(data) { base ->
                withBignum(exponent) { power ->
                    withBignum(modulus) { modulo ->
                        val result = checkNotNull(BN_new()) { "Cannot allocate OpenSSL BIGNUM" }
                        try {
                            check(BN_mod_exp(result, base, power, modulo, context) == 1) {
                                "OpenSSL modular exponentiation failed"
                            }
                            ByteArray(outputSize).also { output ->
                                output.usePinned { pin ->
                                    require(BN_bn2binpad(result, pin.addressOf(0).reinterpret(), outputSize) == outputSize) {
                                        "Modular result exceeds output size"
                                    }
                                }
                            }
                        } finally {
                            BN_clear_free(result)
                        }
                    }
                }
            }
        } finally {
            BN_CTX_free(context)
        }
    }

    private inline fun <T> withBignum(bytes: ByteArray, action: (CPointer<BIGNUM>) -> T): T {
        val value = if (bytes.isEmpty()) byteArrayOf(0) else bytes
        val number = value.usePinned { pin ->
            checkNotNull(BN_bin2bn(pin.addressOf(0).reinterpret(), value.size, null)) {
                "Cannot import OpenSSL BIGNUM"
            }
        }
        try {
            return action(number)
        } finally {
            BN_clear_free(number)
        }
    }
}
