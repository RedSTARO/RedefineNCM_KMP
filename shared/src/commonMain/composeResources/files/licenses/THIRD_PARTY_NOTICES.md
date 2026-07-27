# Third-party notices

## Apple Music-like Lyrics renderers

The application ships two user-selectable AMLL renderers on Android and
Windows x64: the recommended Legacy WebView renderer and a Native Compose
renderer intended for lower-end devices. Platforms without the Legacy host
always use Native Compose.

Both implementations are based on:

- `@applemusic-like-lyrics/core` version `0.5.2`
- `@applemusic-like-lyrics/lyric` version `1.0.2`
- `@applemusic-like-lyrics/ttml` version `1.0.1`

Upstream project: [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)

Copyright (C) the Apple Music-like Lyrics contributors.

### Native Compose renderer

The native Kotlin lyric parser, optimizer, timeline, layout, spring, interlude,
word-mask, emphasis, reduced-motion behavior, artwork background, dynamic-cover
presentation, and song-details surface are a platform adaptation distributed
under the GNU Affero General Public License, version 3 only
(`AGPL-3.0-only`). It was modified for Kotlin Multiplatform and Compose
Multiplatform on 2026-07-27. The source-to-Kotlin mapping is documented in
[`docs/AMLL_NATIVE_TRANSLATION.md`](docs/AMLL_NATIVE_TRANSLATION.md). The
bundled full license text is
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt);
the canonical upstream text is available at
<https://www.gnu.org/licenses/agpl-3.0.txt>.

### Legacy WebView renderer

The Legacy renderer loads the repository's `player.html`, generated
`bundle.js`, and generated `style.css` in Android System WebView or Windows
WebView2. Preferred source and reproducible build inputs are:

- `androidApp/amll-builder/package.json`
- `androidApp/amll-builder/package-lock.json`
- `androidApp/amll-builder/build.js`
- `androidApp/amll-builder/entry.js`
- `shared/src/commonMain/amllAssets/amll/player.html`

Run `npm ci`, `npm test`, and `npm run build` in
`androidApp/amll-builder` to reproduce `bundle.js`, `style.css`, and the
generated JavaScript runtime notices. Complete notices for every non-development
package in the locked runtime graph are shipped beside the Web assets at
[`shared/src/commonMain/amllAssets/amll/THIRD_PARTY_LICENSES.txt`](shared/src/commonMain/amllAssets/amll/THIRD_PARTY_LICENSES.txt).

Immutable upstream preferred-source revisions are
[`fd7ec2d`](https://github.com/amll-dev/applemusic-like-lyrics/tree/fd7ec2d597daa2a66a37ca5f3214d6757ec17cfa)
for Core 0.5.2 / Lyric 1.0.2 and
[`36e5703`](https://github.com/amll-dev/applemusic-like-lyrics/tree/36e57035a735596479abe943f78846b8d1e78afc)
for TTML 1.0.1.

The Windows host uses the native library embedded by
`com.github.webview.webview_java:core` at commit
`da910b62589c36961d50fa8895d79e3b3d792b78`. Its Java SDK and parent Webview
components are MIT-licensed. The exact upstream license for that pinned commit
is bundled at
[`LICENSES/webview-java-da910b-LICENSE.md`](LICENSES/webview-java-da910b-LICENSE.md).
The Gradle dependency is non-transitive; no Casterlabs Commons runtime is
included through this coordinate.

This root document is the canonical notice manifest. Canonical full license
texts live under [`LICENSES/`](LICENSES/). Compose application resources ship
an exact copy of this manifest plus the AGPL-3.0-only and Apache-2.0 texts;
the Legacy Web assets ship their generated JavaScript notices; Desktop
distributions additionally stage the complete `LICENSES/` directory.

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

## Desktop MP3 playback runtime

The resolved `:shared:jvmRuntimeClasspath` contains the following MP3 playback
components. `mp3spi` is the declared Desktop/JVM dependency; JLayer and
Tritonus Share are its transitive runtime dependencies:

- `com.googlecode.soundlibs:mp3spi:1.9.5.4`
- `com.googlecode.soundlibs:jlayer:1.0.1.4`
- `com.googlecode.soundlibs:tritonus-share:0.3.7.4`

The binary JAR manifest of each artifact declares
`Bundle-License: http://www.opensource.org/licenses/lgpl-2.1.php`. Their
inherited `com.googlecode.soundlibs:soundlibs:1.4` parent POM likewise names
the “LGPL 2.1 license”. The Java files in the corresponding source JARs use
the older name “GNU Library General Public License” and state “either version
2 of the License, or (at your option) any later version”. These are two
separate pieces of upstream evidence; this notice records both and does not
make a legal determination that one supersedes the other. The complete texts
are included at:

- [`LICENSES/LGPL-2.1.txt`](LICENSES/LGPL-2.1.txt)
- [`LICENSES/GNU-Library-GPL-2.0-or-later.txt`](LICENSES/GNU-Library-GPL-2.0-or-later.txt)

The upstream artifact evidence and corresponding source are:

- MP3SPI `1.9.5.4`:
  [POM](https://repo1.maven.org/maven2/com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4.pom),
  [source JAR](https://repo1.maven.org/maven2/com/googlecode/soundlibs/mp3spi/1.9.5.4/mp3spi-1.9.5.4-sources.jar)
- JLayer `1.0.1.4`:
  [POM](https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4.pom),
  [source JAR](https://repo1.maven.org/maven2/com/googlecode/soundlibs/jlayer/1.0.1.4/jlayer-1.0.1.4-sources.jar)
- Tritonus Share `0.3.7.4`:
  [POM](https://repo1.maven.org/maven2/com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4.pom),
  [source JAR](https://repo1.maven.org/maven2/com/googlecode/soundlibs/tritonus-share/0.3.7.4/tritonus-share-0.3.7.4-sources.jar)
- Soundlibs `1.4`
  [parent POM](https://repo1.maven.org/maven2/com/googlecode/soundlibs/soundlibs/1.4/soundlibs-1.4.pom)

The MP3SPI and JLayer POMs identify JavaZoom as original author and Patrik
Duditš as packager. JLayer source files preserve, among others,
`Copyright (C) 1993, 1994 Tobias Bading` and
`Copyright (c) 1991 MPEG/audio software simulation group, All Rights
Reserved`. The Tritonus Share POM identifies Florian Bomers as original
author and Patrik Duditš as packager. Its source headers preserve copyrights
of Matthias Pfisterer and Florian Bomers across 1999–2006.
`TAudioFileReader.java` also preserves
`Copyright (C) 1988-1991 Apple Computer, Inc.` and identifies Malcolm Slaney
and Ken Turkowski as implementers of the incorporated conversion routines.

Two files in Tritonus Share,
`org/tritonus/share/sampled/FloatInputStream.java` and
`org/tritonus/share/sampled/FloatSampleInput.java`, carry this separate
two-clause BSD-style notice:

Copyright (c) 2006 by Florian Bomers <http://www.bomers.de>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

- Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

Gradle initially resolves these components as separate JARs. The current
Desktop release build then enables ProGuard optimization and obfuscation with
`joinOutputJars = true`, so the packaged release does not retain these three
artifacts as separately replaceable, original JARs. This notice and the source
links above do not claim that replacement or relinking requirements have been
satisfied. Public GitHub Release publication remains blocked by the repository
license-approval gate unless the project owner records the project license and
Corresponding Source scope and explicitly sets `RELEASE_LICENSE_APPROVED=true`;
adding this notice does not lift that gate.

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
