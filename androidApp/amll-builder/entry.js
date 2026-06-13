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
  },

  /**
   * Set current playback time (milliseconds).
   * @param {number} timeMs Current position in ms
   * @param {number} delta Frame delta for animation
   */
  setTime(timeMs, delta = 0) {
    if (!this.player) return;
    this.player.setCurrentTime(timeMs);
    this.player.update(delta);
  },

  /**
   * Set the overall theme: dark or light.
   * @param {"dark" | "light"} theme
   */
  setTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
  },
};
