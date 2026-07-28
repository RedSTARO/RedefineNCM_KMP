/**
 * Build the AMLL bundle for the WebView lyric player.
 *
 * Usage:
 *   cd androidApp/amll-builder
 *   npm ci
 *   npm run build
 *
 * Outputs bundle.js, style.css, and the runtime dependency licenses into the
 * common asset root used by Android and Desktop. player.html is maintained
 * alongside those generated assets.
 */
import * as esbuild from "esbuild";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  writeFileSync,
} from "node:fs";
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

const packageLock = JSON.parse(readFileSync("package-lock.json", "utf8"));
const runtimePackages = Object.entries(packageLock.packages ?? {})
  .filter(([packagePath, metadata]) => (
    packagePath.startsWith("node_modules/") &&
    metadata.dev !== true &&
    existsSync(packagePath)
  ))
  .map(([packagePath]) => {
    const packageJson = JSON.parse(
      readFileSync(`${packagePath}/package.json`, "utf8"),
    );
    const licenseTexts = readdirSync(packagePath)
      .filter((name) => /^(license|copying|notice)([-._].*)?$/i.test(name))
      .sort((left, right) => left.localeCompare(right))
      .map((name) => ({
        name,
        text: readFileSync(`${packagePath}/${name}`, "utf8").trim(),
      }));
    const overridePath =
      `license-overrides/${packageJson.name.replaceAll("/", "__")}.txt`;
    if (licenseTexts.length === 0 && existsSync(overridePath)) {
      licenseTexts.push({
        name: "project-maintained license override",
        text: readFileSync(overridePath, "utf8").trim(),
      });
    }
    if (licenseTexts.length === 0) {
      throw new Error(
        `Runtime dependency ${packageJson.name}@${packageJson.version} ` +
        "does not include a license file or a project-maintained override",
      );
    }
    return {
      name: packageJson.name,
      version: packageJson.version,
      license: packageJson.license ?? "Unspecified",
      licenseTexts,
    };
  })
  .sort((left, right) => left.name.localeCompare(right.name));

const thirdPartyLicenses = runtimePackages.map((dependency) => {
  const heading = [
    "=".repeat(80),
    `${dependency.name}@${dependency.version}`,
    `Declared license: ${dependency.license}`,
    "=".repeat(80),
  ].join("\n");
  const texts = dependency.licenseTexts.map(({ name, text }) => (
    `--- ${name} ---\n${text}`
  )).join("\n\n");
  return `${heading}\n\n${texts}`;
}).join("\n\n");

writeFileSync(
  `${ASSET_DIR}/THIRD_PARTY_LICENSES.txt`,
  `${thirdPartyLicenses}\n`,
);
console.log(
  `→ THIRD_PARTY_LICENSES.txt generated for ${runtimePackages.length} runtime packages`,
);
