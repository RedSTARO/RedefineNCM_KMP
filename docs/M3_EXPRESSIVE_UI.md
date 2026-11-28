# Material 3 Expressive UI

The shared Compose route stack serves Android, iOS, Desktop, and Web. This redesign makes the
music or task the visual focus rather than tinting each entire page. It does not add a route,
change a provider capability, or change how playback, downloads, or sign-in work.

## From Material 3

- The content pane is `surface`; the navigation region is `surfaceContainer` at every width.
  `secondaryContainer`/`onSecondaryContainer` mark the selected destination; primary roles mark
  the most important action; error feedback uses the error role pair. A tested artwork palette
  is used when a surface explicitly represents a track, album or other collection.
- Use both typography sets. For example, the small app bar uses `titleLarge`; in-content page
  headings use `headlineLargeEmphasized`. The emphasized role changes weight and tracking, not
  size. Both sets carry the same platform font and locale mapping.
- Segmented lists use 16 dp outside corners, 4 dp at inner joints, and a 2 dp gap (Compose List
  tokens). Compact content gutters are 16 dp and wider gutters are 24 dp. Keep normal Material
  control sizes and a 48 dp minimum target. Use Material’s native shape morphs where practical.
- Use spatial springs for geometry and effects springs for color/opacity. Routine tabs use an
  effects-only transition, pushed screens travel a short distance, and the Home search pill
  connects to its editor with a container transform. Use Multi-browse Carousel when the visual
  content supports masked peeks, with a way to browse every item without a swipe. Status is not
  conveyed by tint alone.

## App decisions

The app starts neutral, can follow the cover currently playing, and still supports Android’s
optional wallpaper color. The player keeps its artwork-led presentation, shapes and type;
real window constraints determine how much room the artwork gets.
The 32 dp Material radius is assigned to feature panels, not to every surface. Art may tint a detail
header or the immersive player; most list rows do not extract their own background color. Body
pane widths, when a supporting pane enters, and the travel distances and use of snap under
reduced motion are product choices, not Material component requirements. Widths are measured
inside the available pane after navigation and safe edges. An occluding hinge still takes
precedence over every layout choice.

| Routes | Focus / narrow-to-wide behavior |
| --- | --- |
| For You | Daily Picks is the artwork-led hero, with contrasting cover silhouettes and a compact track carousel. Recommended Playlists uses horizontal capsule covers in a Multi-browse carousel and has a vertical alternative to see every playlist. On both rails, captions fit the moving mask and sit over a bottom scrim, not a separate badge. The feed and artwork scroll behind the status area while foreground controls are clipped to the safe region. Recognition remains actionable when signed out. |
| Search | When the Home pill is visible, switching between Home and Search connects it to the pinned editor with a container transform. Only the pill or an explicit search shortcut requests the keyboard. The editable field itself is laid out at its final bounds; the IME resizes only the scrollable body. |
| Me | Keep cached identity and level visible, bound the profile wallpaper, and place device shortcuts before playlist groups. |
| Daily Picks, playlist, album, artist | Give cover art, metadata and primary actions an adaptive header, with a smaller presentation in short windows. The playlist split button keeps Play All primary and opens Download All and Save as Local Playlist where available. |
| Local library, local playlist | Make creation/import discoverable; show local tracks in the same segmented list language as online tracks. |
| Now Playing, lyrics | Android opens playback in a full-window layer, independent of browse navigation. In a stacked pane, measure the control deck before sizing the cover; only genuinely short panes or large type need to scroll. Keep the quotation-mark lyrics action and the connected island's 3.6-second auto-hide. |
| Downloads, recognition | Surface current work first. Use status chips in wide downloads and a status selector in narrow panes. Recognition keeps the listen, cancel and retry controls next to their state. |
| Settings, Accounts, Login, Licenses | Keep navigation stable; offer a settings category picker or a supporting index, reflow account configuration and login forms in place, and show license text in a bounded reading pane. |

The mobile floating toolbar or measured compact dock and the wide rails share navigation
roles, not a fixed footprint. The desktop playback strip, mini player and playback sheets
share player emphasis. Web boot and in-page lyrics use compatible neutral surfaces; the iOS
Live Activity and the desktop lyric window remain native companions, not alternate app routes.

## Verification checklist

- Walk each tab and pushed route, including loading, cached, empty and failure states. From
  Home, record the search field's container transform and its return. Check that the editor lands
  once and stays at the same bounds with the IME up or down. The draft, cursor and manual IME
  dismissal should survive resizing; a Home search opens the IME, a plain tab switch does not.
- With loaded artwork, drag each Home gallery from start to end: confirm medium cards retain
  readable captions on the bottom scrim, peeks change width, cards tap through, and all items
  have a non-swipe path. Scroll the Daily Picks heading behind the status bar and back; the
  title and actions must reappear while artwork still scrolls behind the status icons.
- Use compact portrait, wide, short and narrow split-screen, and (on Android) a fold or hinge.
  Look for cut-off actions and double player entrances. Try light, dark, larger fonts and
  English, Chinese and Japanese. Keep a playlist, queue or editor open through a resize.
- Exercise the primary action and the secondary menu on catalog and download lists; verify a
  playing row still identifies itself without color. Open lyrics and check seek and collapse
  separately. See [Android adaptive QA](ANDROID_ADAPTIVE_QA.md) for the full fold matrix.
- Run common/JVM and Android host tests, build Android/Desktop/WASM, and run the Web boot tests.
  An iOS build and Dynamic Island/Lock Screen visual check need macOS/Xcode and compatible hardware.

Design references: [Material layout](https://m3.material.io/foundations/layout/overview),
[lists](https://m3.material.io/components/lists/overview),
[typography](https://m3.material.io/styles/typography/overview), and
[motion](https://m3.material.io/styles/motion/overview).
