# Native NetEase protocol cryptography

Reference: [`util/crypto.js`](https://raw.githubusercontent.com/NeteaseCloudMusicApiEnhanced/api-enhanced/4a7d557326d16d2f1e5172ee69c9471eb3a50173/util/crypto.js) and [`util/xeapiKey.js`](https://raw.githubusercontent.com/NeteaseCloudMusicApiEnhanced/api-enhanced/4a7d557326d16d2f1e5172ee69c9471eb3a50173/util/xeapiKey.js), pinned to `4a7d557326d16d2f1e5172ee69c9471eb3a50173`.

## Provider choice and verified coordinates

The application implements the protocol framing, not cryptographic algorithms. The provider matrix and tagged source were checked against cryptography-kotlin **0.6.0**. Its 0.5.0 version does not offer the XDH API needed here. There is no pure-Kotlin algorithm provider in cryptography-kotlin; adding `optimal` does not fill algorithm gaps on every target.

| Source set | Dependency | Backend used explicitly |
| --- | --- | --- |
| commonMain | `dev.whyoleg.cryptography:cryptography-core:0.6.0` | Algorithm API; includes independent secure-random module transitively |
| androidMain, jvmMain | `dev.whyoleg.cryptography:cryptography-provider-jdk:0.6.0` | `CryptographyProvider.JDK(BouncyCastleProvider())` |
| androidMain, jvmMain | `org.bouncycastle:bcprov-jdk18on:1.87` | App-owned BC instance; no global JCA replacement or ServiceLoader dependency |
| iosMain | `dev.whyoleg.cryptography:cryptography-provider-openssl3-prebuilt:0.6.0` | Statically supplied OpenSSL 3 provider, no additional Swift bridge |
| wasmJsMain | none | All native-crypto operations deliberately throw; Web uses the existing HTTP server |

BC's coordinate and version are published in [Maven Central metadata](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml). cryptography-kotlin 0.6.0 publishes [OpenSSL iOS API elements](https://repo.maven.apache.org/maven2/dev/whyoleg/cryptography/cryptography-provider-openssl3-api-iosarm64/0.6.0/cryptography-provider-openssl3-api-iosarm64-0.6.0.module) including its public C-interop declaration artifact. The actual iOS cinterop KLIB was inspected with `klib dump-metadata`: `EVP_chacha20`, cipher-context functions and BN functions are `public external`, despite their `internal.cinterop` package name. This avoids a new project-owned cinterop definition. These APIs are pinned by the library version.

The [published support matrix](https://whyoleg.github.io/cryptography-kotlin/primitives/operations/) distinguishes providers, not merely targets:

| Required operation | JVM / Android BC | iOS OpenSSL3 | Why not WebCrypto / Apple alone? |
| --- | --- | --- | --- |
| Secure random | Platform `SecureRandom` via cryptography-random | `CCRandomGenerateBytes` via cryptography-random | Independent of default algorithm-provider registration |
| MD5 | JCA/BC | OpenSSL | Not available in WebCrypto |
| AES-CBC / ECB, PKCS#7 | JCA/BC | OpenSSL | ECB missing from WebCrypto and CryptoKit |
| RSA raw, modulus-sized block | JCA/BC | OpenSSL | Missing from WebCrypto and CryptoKit |
| HMAC-SHA256 | JCA/BC | OpenSSL | Supported, but does not fill the other gaps |
| X25519 raw import, public derivation, agreement | JCA/BC (including Android API 24) | OpenSSL | CommonCrypto does not expose X25519; browser support varies |
| AES-GCM, 128-bit tag | JCA/BC | OpenSSL | CommonCrypto provider does not offer GCM |
| IETF ChaCha20, explicit block counter | BC `ChaCha7539Engine` | OpenSSL `EVP_chacha20` | Not the high-level ChaCha20-Poly1305 AEAD; not falsely substituted |
| NCBL unsigned modular exponentiation | `java.math.BigInteger.modPow` | OpenSSL `BN_mod_exp` | RSA key import may reject the legacy 256-bit modulus; direct BN is required |

For raw OpenSSL ChaCha20, `counter32LE || nonce96` creates the same initial 16-word state as RFC 8439. Counter wrap is rejected before encryption on every platform, so OpenSSL's legacy 64-bit counter can never carry into the first nonce word. This is byte-layout adaptation over OpenSSL, not an implementation of ChaCha rounds.

MD5, AES-ECB and unpadded RSA are insecure choices for new protocol design; they exist here solely because the upstream wire protocol requires them. This package must not be reused as a general cryptography toolkit.

## Contracts

* All primitive and envelope functions are synchronous; callers choose their own coroutine dispatcher.
* `weapi(jsonUtf8)` and `eapi(path,jsonUtf8)` consume **exact serialized request JSON UTF-8 bytes**. They do not parse or reorder it.
* WEAPI uses secure, unbiased rejection-sampled base62 keys instead of upstream `Math.random`. Reversed 16-byte secrets are left-zero-padded to the 128-byte RSA block. `params` is standard padded base64, `encSecKey` is 256 lower-case hex characters.
* EAPI hashes `nobody || path || use || json || md5forencrypt`, frames with `-36cd479b6b5-`, encrypts with AES-128-ECB/PKCS#7, and returns upper-case hex.
* `buildXeapiPlaintext` receives an **already encoded URI** and an insertion-ordered map of **already JS-String-coerced values**. It reproduces URLSearchParams's UTF-8 form escaping, removes `e_r` from the body and appends `e_r=true` to the raw query. Unlike a JSON body, non-form content types carry the URL-encoded body as base64.
* `xeapi(plaintextUtf8,state,os,sessionKey,sessionId)` consumes those plaintext-envelope JSON bytes. `state.publicKey` is base64 of a raw 32-byte X25519 key. An optional session key is upstream's literal UTF-8 string bytes, **not base64 decoded**, and must be 16/24/32 bytes. `C` is unpadded base64url; `S` and `R` are standard padded base64.
* `S` is `ephemeralPublic32 || nonce12 || ciphertext || GCM-tag16`. The KDF is exactly upstream's HMAC extract/expand sequence. Its sign key is the **literal base64-looking ASCII string**, not decoded bytes.
* `decryptEapiResponse` / `decryptXeapiResponse` consume raw ciphertext and return **decrypted bytes, possibly gzip**. Both use `eapiKey`, never the request's dynamic key. Decompression and JSON parsing are the transport's responsibility.
* `decryptXeapiPublicKey` consumes a base64 string and returns JSON bytes decrypted using the static 256-bit XEAPI key. Transport code owns public-key validation and carrying forward a previous `sk` when allowed.
* NCBL's `chacha20` has a 12-byte nonce and a `UInt` initial block counter; output is raw ciphertext without tag. `rsaModPow` receives unsigned big-endian base, exponent and modulus and returns exactly `outputSize` bytes with leading zeros.
* No unsupported native algorithm has a stub. Web's explicit exception is intentional while Web stays server-backed.

## Offline verification

`generate-vectors.cjs` under the JVM-test `nativecrypto` package uses only Node's built-in `crypto`, `zlib`, `vm` and assertion facilities. Run it with `node`, optionally passing the path to the pinned upstream `util/crypto.js`. It generates WEAPI/EAPI known answers with Node AES/RSA and executes the pinned upstream's actual XEAPI, HMAC, registration decrypt and compressed-response functions in a VM. The VM injects deterministic randomness and X25519 private seeds; no dependency installation, network request or upstream server is involved.

The deterministic stream is `00,01,02,...` across calls. Normal XEAPI consumes dynamic key `00..0f`, mask `10..1f`, ephemeral seed `20..3f`, and GCM nonce `40..4b` (76 bytes). Session-key XEAPI skips the first random call and consumes 60 bytes. WEAPI's first 16 random bytes select `abcdefghijklmnop`. This fixed entropy exists only in tests; production always uses the native secure RNG.

`NcmProtocolCryptoTest` asserts full ciphertexts, not just round trips. `NcmCryptoPrimitiveTest` adds published RFC 1321, RFC 4231, RFC 7748, RFC 8439 and NIST AES/GCM answers, padding boundaries, authenticated-decryption failures and invalid-input checks. `XeapiPlaintextTest` is common and exercises URLSearchParams escaping and JSON field insertion order, including the non-form branch.

Relevant test selection: `:shared:jvmTest --tests '*nativecrypto*'`. JVM, Android, Wasm compilation and iOS compilation/link validation are coordinated by the Lead; this task does not start a competing Gradle process. The Windows host cannot prove iOS execution behavior; the iOS adapter still requires a macOS/Xcode run of the same known answers before claiming runtime verification there.
