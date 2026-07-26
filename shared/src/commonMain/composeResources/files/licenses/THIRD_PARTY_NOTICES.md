# Third-party notices

## Apple Music-like Lyrics

The embedded lyric renderer and lyric/TTML parser are built from
[`amll-dev/applemusic-like-lyrics`](https://github.com/amll-dev/applemusic-like-lyrics):

- `@applemusic-like-lyrics/core` 0.5.2
- `@applemusic-like-lyrics/lyric` 1.0.2
- transitively, `@applemusic-like-lyrics/ttml` 1.0.1

These packages declare `AGPL-3.0-only`. The version and integrity-pinned published package
artifacts used as build inputs are described by
`androidApp/amll-builder/package-lock.json`; `npm ci` retrieves those artifacts before building
the local bridge in `entry.js`. Immutable upstream preferred-source revisions are
[`fd7ec2d`](https://github.com/amll-dev/applemusic-like-lyrics/tree/fd7ec2d597daa2a66a37ca5f3214d6757ec17cfa)
for Core 0.5.2 / Lyric 1.0.2 and
[`36e5703`](https://github.com/amll-dev/applemusic-like-lyrics/tree/36e57035a735596479abe943f78846b8d1e78afc)
for TTML 1.0.1. This project's integration/build source is
`androidApp/amll-builder/entry.js`, `androidApp/amll-builder/build.js`, and
`shared/src/commonMain/amllAssets/amll/player.html`; generated `bundle.js` and `style.css` are
committed under the same asset directory. The full license text is in
[`LICENSES/AGPL-3.0-only.txt`](LICENSES/AGPL-3.0-only.txt) and is also shipped as a common
application resource. Esbuild preserves the legal comments it discovers for bundled transitive
JavaScript dependencies, but that generated footer is not a substitute for a project-wide
distribution license audit.

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
