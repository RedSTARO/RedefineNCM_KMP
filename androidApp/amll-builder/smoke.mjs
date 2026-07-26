import { readFile } from "node:fs/promises";
import { DOMParser } from "@xmldom/xmldom";
import { parseTTML } from "@applemusic-like-lyrics/lyric";

globalThis.DOMParser = DOMParser;

const fixtureUrl = new URL("./fixtures/basic.ttml", import.meta.url);
const ttml = await readFile(fixtureUrl, "utf8");
const parsed = parseTTML(ttml);
const lines = Array.isArray(parsed?.lines) ? parsed.lines : [];

if (lines.length !== 1) {
  throw new Error(`Official AMLL parseTTML returned ${lines.length} lines`);
}
if (lines[0].words?.map((word) => word.word).join("") !== "AMLL TTML") {
  throw new Error("Official AMLL parseTTML lost word content");
}
if (lines[0].translatedLyric !== "逐字歌词" || lines[0].romanLyric !== "AMLL TTML") {
  throw new Error("Official AMLL parseTTML lost lyric supplements");
}
