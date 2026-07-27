const UNTIMED_LINE_DURATION_MS = 1_000;

export function parseUntimedLyrics(input) {
  const textLines = Array.isArray(input)
    ? input
    : String(input || "").split(/\r?\n/);

  return textLines
    .map((line) => String(line || "").trim())
    .filter((line) => line.length > 0)
    .map((line, index) => {
      const startTime = index * UNTIMED_LINE_DURATION_MS;
      const endTime = startTime + UNTIMED_LINE_DURATION_MS;
      return {
        startTime,
        endTime,
        words: [{
          startTime,
          endTime,
          word: line,
        }],
        translatedLyric: "",
        romanLyric: "",
        isBG: false,
        isDuet: false,
      };
    });
}

export function resolvePlaybackTime(timeMs, untimedMode) {
  if (untimedMode) return 0;
  return Math.max(0, Number(timeMs) || 0);
}
