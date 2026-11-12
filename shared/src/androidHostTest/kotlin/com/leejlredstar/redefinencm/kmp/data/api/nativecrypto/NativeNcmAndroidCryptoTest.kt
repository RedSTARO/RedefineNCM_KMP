package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Exercises Android's actual provider, not the JVM actual, in the Android host test suite. */
class NativeNcmAndroidCryptoTest {
    @Test
    fun explicitProviderSupportsTheLegacyAndModernProtocolPrimitives() {
        val crypto = platformNcmCrypto()
        assertEquals("900150983cd24fb0d6963f7d28e17f72", crypto.md5("abc".encodeToByteArray()).toHex())
        val key = ByteArray(16) { (it + 11).toByte() }
        val data = "native protocol binary\u0000payload".encodeToByteArray()
        assertContentEquals(data, crypto.aesEcbDecrypt(crypto.aesEcbEncrypt(data, key), key))
        val nonce = ByteArray(12) { (it + 7).toByte() }
        val encrypted = crypto.aesGcmEncrypt(data, key, nonce)
        assertContentEquals(data, crypto.aesGcmDecrypt(encrypted, key, nonce))
        val alice = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes()
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", crypto.x25519PublicKey(alice).toHex())
        assertEquals(256, NcmProtocolCrypto(crypto).weapi("{}".encodeToByteArray()).encSecKey.length)
    }
}
