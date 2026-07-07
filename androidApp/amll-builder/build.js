/**
 * Build the AMLL bundle for the WebView lyric player.
 *
 * Usage:
 *   cd androidApp/amll-builder
 *   npm install
 *   npm run build
 *
 * Outputs three files — bundle.js (AMLL engine, IIFE → globalThis.AmllBridge),
 * style.css (AMLL core stylesheet, REQUIRED), player.html (host page) — into BOTH
 * platform asset roots:
 *   - androidApp/src/main/assets/amll/        (Android WebView, file:///android_asset)
 *   - shared/src/jvmMain/resources/amll/      (Desktop system WebView, extracted to temp at runtime)
 * player.html is hand-maintained in the Android dir and mirrored to the desktop dir here.
 */
import * as esbuild from "esbuild";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";

const ANDROID_DIR = "../src/main/assets/amll";
const DESKTOP_DIR = "../../shared/src/jvmMain/resources/amll";

await esbuild.build({
  entryPoints: ["./entry.js"],
  bundle: true,
  // IIFE (classic script), not ESM: Android WebView blocks dynamic import()
  // and <script type="module"> over file:// URLs, but a classic <script src>
  // loads fine. The bundle exposes its API via globalThis.AmllBridge.
  format: "iife",
  target: ["chrome91"],
  outfile: `${ANDROID_DIR}/bundle.js`,
  sourcemap: false,
  minify: true,
});
console.log(`→ bundle written to ${ANDROID_DIR}/bundle.js`);

const rawCss = readFileSync(
  "node_modules/@applemusic-like-lyrics/core/dist/style.css",
  "utf8",
);
const css = await esbuild.transform(rawCss, {
  loader: "css",
  target: ["chrome91"],
  minify: false,
});
writeFileSync(`${ANDROID_DIR}/style.css`, css.code);
console.log(`→ style.css compiled to ${ANDROID_DIR}/style.css`);

// Mirror all three assets to the desktop (jvmMain) resources root.
mkdirSync(DESKTOP_DIR, { recursive: true });
for (const f of ["bundle.js", "style.css", "player.html"]) {
  copyFileSync(`${ANDROID_DIR}/${f}`, `${DESKTOP_DIR}/${f}`);
}
console.log(`→ assets mirrored to ${DESKTOP_DIR}/`);
