import { readFile } from "node:fs/promises";
import { DOMParser } from "@xmldom/xmldom";
import { parseTTML } from "@applemusic-like-lyrics/lyric";
import { parseUntimedLyrics, resolvePlaybackTime } from "./untimed.js";

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

const untimed = parseUntimedLyrics([" 第一句 ", "", "第二句"]);
if (untimed.length !== 2) {
  throw new Error(`Untimed AMLL conversion returned ${untimed.length} lines`);
}
if (untimed[0].words?.[0]?.word !== "第一句" || untimed[1].words?.[0]?.word !== "第二句") {
  throw new Error("Untimed AMLL conversion lost lyric text or order");
}
if (
  untimed[0].endTime <= untimed[0].startTime ||
  untimed[0].words?.[0]?.endTime !== untimed[0].endTime ||
  untimed[1].startTime <= untimed[0].startTime
) {
  throw new Error("Untimed AMLL conversion did not create stable virtual layout times");
}
if (resolvePlaybackTime(12_345, true) !== 0 || resolvePlaybackTime(12_345, false) !== 12_345) {
  throw new Error("Untimed AMLL playback time is not frozen");
}
