# Third-party notices

## Apple Music-like Lyrics native Compose port

The native Kotlin lyric parser, optimizer, timeline, layout, spring, interlude,
word-mask, and emphasis implementation is a platform adaptation of:

- `@applemusic-like-lyrics/core` version `0.5.2`
- `@applemusic-like-lyrics/lyric` version `1.0.2`
- `@applemusic-like-lyrics/ttml` version `1.0.1`
- the AMLL host page formerly shipped in this repository

Upstream project: [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)

Copyright (C) the Apple Music-like Lyrics contributors.

The adapted implementation is distributed under the GNU Affero General Public
License, version 3 only (`AGPL-3.0-only`). It was modified for Kotlin
Multiplatform and Compose Multiplatform on 2026-07-27. The source-to-Kotlin
mapping is documented in
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
