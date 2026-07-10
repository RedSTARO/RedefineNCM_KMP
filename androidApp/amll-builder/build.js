/**
 * Build the AMLL bundle for the WebView lyric player.
 *
 * Usage:
 *   cd androidApp/amll-builder
 *   npm install
 *   npm run build
 *
 * Outputs bundle.js (AMLL engine, IIFE → globalThis.AmllBridge) and style.css
 * into the single common asset root used by Android and Desktop. player.html is
 * hand-maintained alongside those generated assets.
 */
import * as esbuild from "esbuild";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const ASSET_DIR = fileURLToPath(
  new URL("../../shared/src/commonMain/amllAssets/amll/", import.meta.url),
);
mkdirSync(ASSET_DIR, { recursive: true });

await esbuild.build({
  entryPoints: ["./entry.js"],
  bundle: true,
  // IIFE (classic script), not ESM: Android WebView blocks dynamic import()
  // and <script type="module"> over file:// URLs, but a classic <script src>
  // loads fine. The bundle exposes its API via globalThis.AmllBridge.
  format: "iife",
  target: ["chrome91"],
  outfile: `${ASSET_DIR}/bundle.js`,
  sourcemap: false,
  minify: true,
});
console.log(`→ bundle written to ${ASSET_DIR}/bundle.js`);

const rawCss = readFileSync(
  "node_modules/@applemusic-like-lyrics/core/dist/style.css",
  "utf8",
);
const css = await esbuild.transform(rawCss, {
  loader: "css",
  target: ["chrome91"],
  minify: false,
});
writeFileSync(`${ASSET_DIR}/style.css`, css.code);
console.log(`→ style.css compiled to ${ASSET_DIR}/style.css`);
