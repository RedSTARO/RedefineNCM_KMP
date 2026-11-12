# Third-party notices

RedefineNCM KMP itself is licensed under the GNU Affero General Public License, version 3 only
(`AGPL-3.0-only`); the full text is [`LICENSE`](LICENSE) and, identically,
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt). The complete source of every
release is this repository at the release tag, with the `AMLL_Jetpack_Compose` submodule at the
commit that tag pins, so anyone can rebuild the application, including against modified versions
of the LGPL components listed below. This file lists the third-party components the application
contains or uses and their terms.

## Apple Music-like Lyrics renderer

The application ships one AMLL renderer on every platform: native Compose,
provided by the standalone
[AMLL_Jetpack_Compose](https://github.com/RedSTARO/AMLL_Jetpack_Compose) library
(Gradle coordinate `com.leejlredstar.amll:amll-compose`). The Legacy WebView renderer and its
bundled HTML/JS/CSS page were removed on 2026-09-06.

It is based on:

- `@applemusic-like-lyrics/core` version `0.5.2`
- `@applemusic-like-lyrics/lyric` version `1.0.2`
- `@applemusic-like-lyrics/ttml` version `1.0.1`

Upstream project: [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)

Copyright (C) the Apple Music-like Lyrics contributors.

The Kotlin lyric parser, optimizer, timeline, layout, spring, interlude,
word-mask, emphasis, reduced-motion behavior and artwork background live in the
`com.leejlredstar.amll:amll-compose` library and are a platform adaptation
distributed under the GNU Affero General Public License, version 3 only
(`AGPL-3.0-only`). They were modified for Kotlin Multiplatform and Compose
Multiplatform on 2026-07-27 and extracted into their own repository on
2026-09-06. The dynamic-cover platform leaf and the song-details surface remain
in this repository under the same terms.

The symbol-level source-to-Kotlin mapping is documented in
[`docs/AMLL_NATIVE_TRANSLATION.md`](docs/AMLL_NATIVE_TRANSLATION.md). The
bundled full license text is
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt);
the canonical upstream text is available at
<https://www.gnu.org/licenses/agpl-3.0.txt>.

Immutable upstream preferred-source revisions are
[`fd7ec2d`](https://github.com/amll-dev/applemusic-like-lyrics/tree/fd7ec2d597daa2a66a37ca5f3214d6757ec17cfa)
for Core 0.5.2 / Lyric 1.0.2 and
[`36e5703`](https://github.com/amll-dev/applemusic-like-lyrics/tree/36e57035a735596479abe943f78846b8d1e78afc)
for TTML 1.0.1.

This root document is the canonical notice manifest. Canonical full license
texts live under [`LICENSES/`](LICENSES/). Compose application resources ship
an exact copy of this manifest plus the AGPL-3.0-only, Apache-2.0 and SIL OFL 1.1 texts;
Desktop distributions additionally stage the complete `LICENSES/` directory.

## Noto Sans SC (Web font)

The Web build includes `shared/src/wasmJsMain/composeResources/font/noto_sans_sc_variable.ttf`
so the Compose canvas can render Chinese without depending on installed system fonts.
Its embedded font metadata identifies version `2.004-H2` and SIL Open Font License 1.1.

Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.

The copyright notice and complete license are bundled in
[`LICENSES/OFL-1.1.txt`](LICENSES/OFL-1.1.txt) and the app's font-license page.
The text is copied from the font's [Google Fonts license file](https://github.com/google/fonts/blob/main/ofl/notosanssc/OFL.txt).
The font retains its own license.

## AMLL TTML database

TTML files are retrieved at runtime from
[`amll-dev/amll-ttml-db`](https://github.com/amll-dev/amll-ttml-db) and its listed mirrors.
The repository uses CC0-1.0 for contributors' original database work. Its README notes that data
originating elsewhere continues to be governed by the original provider's terms; this notice does
not relicense lyric text.

## xmlutil

Common TTML parsing uses `io.github.pdvrieze.xmlutil:core` 0.91.3, licensed under
the Apache License 2.0. The full license text is in
[`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt) and is also shipped as a common application
resource.

## Desktop dynamic-cover video decoding

Desktop/JVM distributions include these direct runtime components:

- JavaCV `1.5.13`
- JavaCPP `1.5.13`
- JavaCPP Presets for FFmpeg `8.0.1-1.5.13`
- FFmpeg `8.0.1` native libraries from the matching non-`-gpl` platform
  classifier

Upstream projects:

- <https://github.com/bytedeco/javacv/tree/1.5.13>
- <https://github.com/bytedeco/javacpp/tree/1.5.13>
- <https://github.com/bytedeco/javacpp-presets/tree/1.5.13/ffmpeg>
- <https://github.com/FFmpeg/FFmpeg/tree/n8.0.1>

Copyright (C) 2009-2025 Samuel Audet and the JavaCV contributors.

Copyright (C) 2011-2025 Samuel Audet and the JavaCPP contributors.

Copyright (C) 2013-2025 Samuel Audet and the JavaCPP Presets contributors.

The JavaCV, JavaCPP, and JavaCPP Presets Java layers are offered under either
the Apache License, Version 2.0, or the GNU GPL version 2 or later with the
Classpath exception. This distribution uses the Apache-2.0 option. Its complete
text is bundled at
[`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt).
The three `1.5.13` source tags contain the same complete dual-license file; a
verbatim local copy is bundled at
[`LICENSES/Bytedeco-1.5.13-LICENSE.txt`](LICENSES/Bytedeco-1.5.13-LICENSE.txt).

The selected FFmpeg native artifacts deliberately do not use the separately
published `-gpl` classifiers. The JavaCPP Presets `1.5.13` build recipe enables
`--enable-version3` for the standard artifact and adds `--enable-gpl` only for
an `-gpl` extension. Consequently the bundled standard FFmpeg libraries are
licensed under the GNU Lesser General Public License, version 3 or any later
version (`LGPL-3.0-or-later`). The complete LGPL v3 additional terms and the
GNU GPL v3 terms they incorporate are bundled at:

- [`LICENSES/LGPL-3.0-or-later.txt`](LICENSES/LGPL-3.0-or-later.txt)
- [`LICENSES/GPL-3.0.txt`](LICENSES/GPL-3.0.txt)

FFmpeg's own license explanation, including its notices for differently
licensed source files and external-library combinations, is reproduced at
[`LICENSES/FFmpeg-8.0.1-LICENSE.md`](LICENSES/FFmpeg-8.0.1-LICENSE.md).
The exact corresponding source and native build recipe are available from the
FFmpeg `n8.0.1` and JavaCPP Presets `1.5.13` links above.

`FFmpegFrameGrabber` also contains code based on FFmpeg sample code with this
preserved notice:

Copyright (c) 2001 Fabrice Bellard

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Open Orpheus audio fingerprint implementation

The native Kotlin audio-fingerprint implementation is derived from the
`src/main/afp.ts` implementation in
[YUCLing/open-orpheus](https://github.com/YUCLing/open-orpheus), commit
`021984fb7ad35393efe5dde4e3b1666c3d14c972`.

Copyright 2026 YUCLing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Beat This! beat and downbeat tracker

Smart song transitions find beats and downbeats with the `small0` checkpoint of
[CPJKU/beat_this](https://github.com/CPJKU/beat_this) (Foscarin, Schlüter, Widmer, "Beat This!
Accurate Beat Tracking Without DBN Postprocessing", ISMIR 2024), commit
`b95c8ab0c58c2d9fcfd40508ae8dffbc05ac4f5c`. The model files
(`beat_this_small0_t750.onnx` and its LiteRT and Core ML conversions) are made from that
checkpoint by `tools/automix-model`. The web app serves the ONNX file with its own pages; the
Android and desktop apps do not ship the model and download it from this repository the first
time smart transitions need it. The Kotlin log-mel front end, the chunk split and
aggregation, and the minimal peak picking are ports of `beat_this.preprocessing`,
`beat_this.inference` and `beat_this.model.postprocessor`. The model's transformer is adapted
upstream from Phil Wang's BS-RoFormer, also under the MIT License.

Copyright (c) 2024 Institute of Computational Perception, JKU Linz, Austria

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## ONNX Runtime

The desktop app runs the beat model with [ONNX Runtime](https://github.com/microsoft/onnxruntime)
through its Java binding (`com.microsoft.onnxruntime:onnxruntime` 1.24.3). The Windows package
ships the DirectML build of `onnxruntime.dll` and `onnxruntime_providers_shared.dll` from the
`Microsoft.ML.OnnxRuntime.DirectML` 1.24.4 NuGet package. That build loads `DirectML.dll` from
Windows itself; the app does not ship DirectML.

The web app bundles ONNX Runtime Web (`onnxruntime-web` 1.30.0) and its WebAssembly, loaded
only when smart transitions first need the model. Parts of that bundle carry Google LLC's
Apache License 2.0 notices, reproduced in the `.LICENSE.txt` files next to the bundle; the full
Apache License text is [`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt).

Copyright (c) Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## LiteRT

The Android app runs the beat model with [LiteRT](https://github.com/google-ai-edge/LiteRT)
(`com.google.ai.edge.litert:litert` 2.2.0 and `litert-api` 2.2.0), Google's on-device
inference runtime, under the Apache License 2.0
([`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt)). The APK carries its native libraries
`libLiteRt.so`, `libLiteRtClGlAccelerator.so` and `liblitert_jni.so`. Those libraries are built
from many open-source components under Apache, MIT, BSD and other licences; LiteRT's own notice
file for them is reproduced as
[`LICENSES/LiteRT-2.2.0-THIRD_PARTY_NOTICE.txt`](LICENSES/LiteRT-2.2.0-THIRD_PARTY_NOTICE.txt).

Copyright 2025 Google LLC.

## Native NetEase protocol adaptation

The Android, iOS and Desktop/JVM NetEase client adapts endpoint mappings, parameter
encoding, response shaping, login bootstrap and encrypted request envelopes from
[NeteaseCloudMusicApiEnhanced/api-enhanced, revision
`4a7d557326d16d2f1e5172ee69c9471eb3a50173`](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/tree/4a7d557326d16d2f1e5172ee69c9471eb3a50173).
The source references are its `module/` route files and `util/request.js`,
`util/option.js`, `util/crypto.js`, `util/xeapiKey.js` and `util/config.json`.
These parts were adapted to Kotlin/KMP on 2026-11-13. Native targets do not ship
or execute the upstream Node/JavaScript server or its npm dependencies.

The upstream root license is MIT (`MIT`). Its complete text follows and is also
preserved at
[`LICENSES/NeteaseCloudMusicApiEnhanced-4a7d557-MIT.txt`](LICENSES/NeteaseCloudMusicApiEnhanced-4a7d557-MIT.txt),
from the [pinned original](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/4a7d557326d16d2f1e5172ee69c9471eb3a50173/LICENSE).
The NCBL reporting code has an additional provenance described separately below.

The MIT License (MIT)

Copyright (c) 2013-2022 Binaryify

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

### NCBL playback reporting provenance

The pinned upstream `util/ncbl.js` and `module/scrobble_v1.js` identify their
source as [folltoshe/netease-report-listen-song](https://github.com/folltoshe/netease-report-listen-song).
That project's author is folltoshe; its
[`LICENSE` at revision `02ea43d7ffd4deb05eb4a48e824e9458b792ed0b`](https://github.com/folltoshe/netease-report-listen-song/blob/02ea43d7ffd4deb05eb4a48e824e9458b792ed0b/LICENSE)
is the GNU Affero General Public License, version 3. The NCBL record layout,
PLV/PLD payload construction and upload orchestration are adapted through the
pinned api-enhanced source, not relicensed as MIT. They were modified for
Kotlin/KMP on 2026-11-13 and are distributed here under AGPL version 3, consistent
with the application's `AGPL-3.0-only` license. The full license is
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt), also available in the app's
license page. Cryptographic primitives are supplied by the libraries below,
not by the upstream's handwritten JavaScript implementations.

## QRCode-Kotlin

Local NetEase login QR generation uses
[`io.github.g0dkar:qrcode-kotlin` 4.5.0](https://github.com/g0dkar/qrcode-kotlin/tree/v4.5.0),
under the MIT License (`MIT`). The complete
[upstream license](https://github.com/g0dkar/qrcode-kotlin/blob/v4.5.0/LICENSE)
is reproduced here and in
[`LICENSES/QRCode-Kotlin-4.5.0-MIT.txt`](LICENSES/QRCode-Kotlin-4.5.0-MIT.txt).

MIT License

Copyright (c) 2021 Rafael Madureira Lins de Araújo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

### QR Code Generator reference implementation

QRCode-Kotlin identifies its encoder as a Kotlin adaptation of
[Kazuhiko Arase's QR Code Generator](https://github.com/kazuhikoarase/qrcode-generator).
The reference implementation's
[MIT notice](https://github.com/kazuhikoarase/qrcode-generator/blob/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/LICENSE)
is retained here and in
[`LICENSES/QRCode-Generator-MIT.txt`](LICENSES/QRCode-Generator-MIT.txt).

MIT License

Copyright (c) 2009 Kazuhiko Arase

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## cryptography-kotlin

Native protocol cryptography uses
[whyoleg/cryptography-kotlin 0.6.0](https://github.com/whyoleg/cryptography-kotlin/tree/0.6.0):
`dev.whyoleg.cryptography:cryptography-core`, the JDK provider on Android/Desktop,
and `cryptography-provider-openssl3-prebuilt` on iOS, together with their
supporting library modules. These Kotlin components are licensed under the
Apache License 2.0 (`Apache-2.0`).

Copyright (c) 2023-2026 Oleg Yukhnevich.

The [upstream license](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/LICENSE)
is the full Apache License 2.0, already bundled at
[`LICENSES/Apache-2.0.txt`](LICENSES/Apache-2.0.txt) and as an application resource.
The separately bundled OpenSSL native library retains its own notice below.

## Bouncy Castle

Android and Desktop/JVM protocol primitives also use
[`org.bouncycastle:bcprov-jdk18on` 1.87](https://github.com/bcgit/bc-java/tree/r1rv87),
under the MIT License (`MIT`). The
[release's original license](https://github.com/bcgit/bc-java/blob/r1rv87/LICENSE.html)
is preserved verbatim at
[`LICENSES/Bouncy-Castle-1.87-LICENSE.html`](LICENSES/Bouncy-Castle-1.87-LICENSE.html).
Its complete text is reproduced below without the HTML markup so it is readable
in the application's license page.

MIT License

Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc. (https://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.

## OpenSSL bundled by the iOS cryptography provider

The iOS `cryptography-provider-openssl3-prebuilt` **0.6.0** artifact includes
OpenSSL **3.6.0**, licensed under the Apache License 2.0 (`Apache-2.0`). This is
not Apple's system TLS library, and it is not the separate
`cryptography-provider-openssl3-prebuilt-nativebuilds` artifact.

The [0.6.0 prebuilt-provider build](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/cryptography-providers/openssl3/prebuilt/build.gradle.kts)
selects `openssl.v3_6`; its
[pinned build configuration](https://github.com/whyoleg/cryptography-kotlin/blob/0.6.0/build-logic/src/main/kotlin/ckbuild.use-openssl.gradle.kts)
resolves the [whyoleg/openssl-builds 3.6.0 release](https://github.com/whyoleg/openssl-builds/releases/tag/3.6.0).
The published iOS ARM64 cinterop archive contains `libcrypto.a` identifying itself
as `OpenSSL 3.6.0 1 Oct 2025`; its SHA-256 is
`28c4f52c65932c1a11ef0436a247e4e5a1699ce36feaa2034f69ce09167b4fd7`, matching the
[published module metadata](https://repo.maven.apache.org/maven2/dev/whyoleg/cryptography/cryptography-provider-openssl3-prebuilt-iosarm64/0.6.0/cryptography-provider-openssl3-prebuilt-iosarm64-0.6.0.module).

The [OpenSSL 3.6.0 source](https://github.com/openssl/openssl/tree/openssl-3.6.0)
and the prebuilt release above provide its source and build recipe. The release's
[original Apache-2.0 text](https://github.com/openssl/openssl/blob/openssl-3.6.0/LICENSE.txt)
is preserved at [`LICENSES/OpenSSL-3.6.0-LICENSE.txt`](LICENSES/OpenSSL-3.6.0-LICENSE.txt);
the complete Apache License 2.0 is also available in the app's license page.
The [upstream README](https://github.com/openssl/openssl/blob/openssl-3.6.0/README.md)
retains these copyright notices:

Copyright (c) 1998-2025 The OpenSSL Project Authors

Copyright (c) 1995-1998 Eric A. Young, Tim J. Hudson

All rights reserved.
