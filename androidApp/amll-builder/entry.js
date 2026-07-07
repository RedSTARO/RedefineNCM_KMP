/**
 * Entry point for the esbuild AMLL bundle.
 * Exports a global API that the Android WebView bridge can call.
 */
import { LyricPlayer } from "@applemusic-like-lyrics/core";
import { parseLrc } from "@applemusic-like-lyrics/lyric";

globalThis.AmllBridge = {
  LyricPlayer,
  parseLrc,
  player: null,

  /**
   * Initialize the lyric player and mount it into #amll-root.
   */
  init() {
    const root = document.getElementById("amll-root");
    if (!root) return false;

    this.player = new LyricPlayer();
    root.appendChild(this.player.getElement());

    globalThis.__amllDebug = { ready: true, lines: 0, time: 0 };

    // AMLL is driven by a requestAnimationFrame loop with real per-frame deltas;
    // setCurrentTime() only moves the target. Native position updates arrive
    // coarsely (~5/s), so the rAF loop owns the animation, not the native tick.
    let last = -1;
    const frame = (t) => {
      if (last < 0) last = t;
      this.player.update(t - last);
      last = t;
      requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
    return true;
  },

  /**
   * Load LRC lyrics text into the player.
   * @param {string} lrcText Raw LRC lyric text
   */
  loadLyrics(lrcText) {
    if (!this.player) return;
    const parsed = parseLrc(lrcText);
    this.player.setLyricLines(parsed);
    globalThis.AmllPage?.hideLoading?.();
    if (globalThis.__amllDebug) globalThis.__amllDebug.lines = parsed.length;
  },

  /**
   * Set the current playback time (milliseconds). Only moves the target —
   * the rAF loop started in init() advances the animation.
   * @param {number} timeMs Current position in ms
   */
  setTime(timeMs) {
    this.player?.setCurrentTime(timeMs);
    if (globalThis.__amllDebug) globalThis.__amllDebug.time = timeMs;
  },

  /**
   * Set the blurred album-art background image (a remote URL).
   * @param {string} url Artwork URL
   */
  setBackground(url) {
    const el = document.getElementById("bg");
    if (el && url) el.style.backgroundImage = 'url("' + url + '")';
  },

  /**
   * Set the overall theme: dark or light.
   * @param {"dark" | "light"} theme
   */
  setTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
  },
};
