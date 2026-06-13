/**
 * Build the AMLL + PixiJS bundle for the WebView lyric player.
 *
 * Usage:
 *   cd androidApp/amll-builder
 *   npm install
 *   npm run build
 *
 * Output: ../src/main/assets/amll/bundle.js
 */
import * as esbuild from "esbuild";

await esbuild.build({
  entryPoints: ["./entry.js"],
  bundle: true,
  format: "esm",
  target: ["chrome91"],
  outfile: "../src/main/assets/amll/bundle.js",
  sourcemap: false,
  minify: true,
});

console.log("→ bundle written to ../src/main/assets/amll/bundle.js");
