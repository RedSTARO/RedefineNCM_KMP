/**
 * Build the AMLL bundle for the WebView lyric player.
 *
 * Usage:
 *   cd androidApp/amll-builder
 *   npm install
 *   npm run build
 *
 * Outputs (to ../src/main/assets/amll/):
 *   - bundle.js  : the AMLL engine (IIFE, exposes globalThis.AmllBridge)
 *   - style.css  : AMLL core stylesheet (REQUIRED — the DOM lyric player has no
 *                  styles of its own; without it lyric lines render invisibly)
 */
import * as esbuild from "esbuild";
import { copyFileSync } from "node:fs";

const OUT_DIR = "../src/main/assets/amll";

await esbuild.build({
  entryPoints: ["./entry.js"],
  bundle: true,
  // IIFE (classic script), not ESM: Android WebView blocks dynamic import()
  // and <script type="module"> over file:// URLs, but a classic <script src>
  // loads fine. The bundle exposes its API via globalThis.AmllBridge.
  format: "iife",
  target: ["chrome91"],
  outfile: `${OUT_DIR}/bundle.js`,
  sourcemap: false,
  minify: true,
});
console.log(`→ bundle written to ${OUT_DIR}/bundle.js`);

copyFileSync(
  "node_modules/@applemusic-like-lyrics/core/dist/style.css",
  `${OUT_DIR}/style.css`,
);
console.log(`→ style.css copied to ${OUT_DIR}/style.css`);
