# AGENTS.md — instructions for AI coding agents

> 🇸🇦 العربية: [AGENTS.ar.md](AGENTS.ar.md) · Human docs: [README.md](README.md) · Design spec: [docs/HIG-tvOS.md](docs/HIG-tvOS.md)

This file is the operating manual for any LLM agent (Claude, GPT, Gemini, Cursor, Codex…)
working in this repo. Read it fully before your first edit. To rebuild the whole system from scratch, follow
[docs/MASTER-PROMPT.md](docs/MASTER-PROMPT.md).

---

## 0. Mission

Make an Android TV box look, feel and behave **exactly like Apple tvOS**. The owner judges every
change by one question: *would this pass as Apple-made?* Any of the following is an instant
rejection:

- Material Design components (`androidx.compose.material*`), ripples or elevation-heavy Android
  looks
- Generic "AI template" styling: navy gradients, coloured borders on every card, uniform cards
  with no hierarchy, decorative colour
- Invented data (fake numbers, placeholder content, "updated 2 min ago" that isn't true)
- Anything that lags on the target box

## 1. Non-negotiables

1. **The HIG is binding.** `docs/HIG-tvOS.md` holds the rules and numbers (safe area 80/60 pt,
   tvOS type scale, grid widths, tab bar 68 pt tall and 46 pt from the top, five focus states,
   parallax, Liquid Glass). **Units: dp = tvOS pt ÷ 2** (1920×1080 at 320 dpi).
2. **Use the design kit**, never raw values: `design/Tv.kt` (TvType, TvColors, TvDims, TvShapes,
   TvMotion), `design/LiquidGlass.kt`, `design/Components.kt`. For a new token, add it to the kit
   (or to the surface's own token object) with a comment saying why.
3. **Zero idle frames.** At rest the screen must render nothing. No `rememberInfiniteTransition`
   on always-visible UI, and no per-frame loops. Animate via `graphicsLayer` or the draw phase,
   with one-shot springs or tweens. Verify with gfxinfo (§5).
4. **Real data only.** TMDB, Open-Meteo, Apple's Aerial feed and the public Instagram profile.
   When data is missing, hide the element.
5. **Layout direction.** The box runs an Arabic (RTL) locale. tvOS surfaces are forced **LTR**
   (`CompositionLocalProvider(LocalLayoutDirection provides Ltr)`). The Arabic prayer dashboard
   is deliberately **RTL** and uses Arabic-Indic digits.
6. **Secrets.** The TMDB token lives only in `local.properties` → `BuildConfig.TMDB_READ_TOKEN`.
   Never commit it, never print it, never add other keys to source.

## 2. Stack & commands

- Kotlin 2.3, AGP 8.13, Compose BOM 2026.05, Media3 1.8, Coil 3, OkHttp 4. minSdk 26, targetSdk 34.
- JDK 21: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Any other `java`
  on the PATH may be Java 8 and will break Gradle.
- Build + test: `./gradlew -q :app:testReleaseUnitTest :app:assembleRelease`
- **Install only the release APK** on the box. Debuggable Compose is too slow there.
- Parallel installs on one emulator: `-PidSuffix=.yourname` gives you a separate applicationId.

## 3. Architecture map

| Package | Owns | Key entry points |
|---|---|---|
| `design/` | Shared tvOS kit (do not fork it per screen) | `TvType`, `TvDims`, `LiquidGlass.liquidGlass()` |
| `ui/`, `home/`, `HomeViewModel.kt`, `MainActivity.kt` | Home launcher: tab bar, hero carousel, rows, Apps grid, focus | `HomeScreen`, `HomeViewModel.onDirection/onSelect`, `ContentRepository` |
| `search/` | Search app + product page | `SearchActivity` (extras `tmdb_id`, `tmdb_type` open a product page directly) |
| `settings/` | Settings app | `PagesDevice.kt` / `PagesLauncher.kt` build `PageSpec`s; `SettingsIcons.kt` draws the tiles |
| `system/` | Accessibility service in process `:system`: Home-key router, Control Center, App Switcher overlays | `SystemUiService`, `HomeKeyRouter` (600 ms hold / 350 ms double press) |
| `aerial/` | Aerial screensaver (`AerialDreamService`) | `AerialCatalogue`, `AerialPlayback`, `AerialNetwork` (Apple root CA, host-scoped) |
| `dashboard/` | Prayer-times screensaver over Aerial (`PrayerDashboardDream`) | `DashboardScreen`, `DashboardData` (weather + Instagram), `ArabicText` |
| `prayer/` | Offline prayer astronomy | `PrayerTimes.forDay`, `PrayerConfig.BaghdadJafari`, `PrayerSchedule` |
| `tmdb/` | TMDB v3 client | `trending`, `onProvider`, `search`, `details`, `logoPath`, `image()` |
| `prefs/` | `LauncherPrefs` (one process-wide `StateFlow`) | `LauncherPrefs.get(ctx).update { … }` |

Navigation on Home is a **pure state machine** in `HomeViewModel` + `home/HomeNav.kt`. Compose
focus is not used there. Keys arrive via `onPreviewKeyEvent` → `KeyRouter` → VM. Settings and
Search use Compose focus (`focusRequester`, `clickable`).

## 4. Design rules in practice

- **Focus = scale + lift + illumination** (white platter, black text, shadow). Never colour alone.
  On press, dip briefly (the HIG "highlighted" state).
- **Liquid Glass** (`liquidGlass()`) for navigation chrome, the tab bar, Control Center and idle
  buttons. Focused buttons turn white.
- **Grids**: 5-column app grid at 160×96 dp with 20 dp gaps and 40 dp side margins.
  Poster rows use the 7-column width. Leave room for the focus scale so a focused item never
  overlaps its neighbours.
- **Type**: SF Pro / SF Arabic through `TvType` (exact HIG sizes, weights and tracking). Large
  numerals use SemiBold/Bold. Secondary text is shown as white at lower opacity (78/58/40 %),
  never grey-on-grey.
- **Motion**: springs from `TvMotion`. Tab switch = fade out → fade in of pages that are already
  composed; the tab pill slides. Nothing loops at rest.
- Before any visual work, run the `anti-slop-design` process if your harness has it: fix a
  direction, freeze tokens, verify against screenshots.

## 5. Device workflow (the target box)

```bash
S=<adb serial or ip:5555>
adb -s $S install -r app/build/outputs/apk/release/app-release.apk
# Reinstall unbinds the accessibility service — always re-enable:
adb -s $S shell 'settings put secure enabled_accessibility_services com.hamad.tvhome/com.hamad.tvhome.system.SystemUiService; settings put secure accessibility_enabled 1'
adb -s $S shell input keyevent KEYCODE_HOME
adb -s $S exec-out screencap -p > /tmp/shot.png      # then look at it
```

Performance check (required for any UI change):

```bash
adb -s $S shell 'dumpsys gfxinfo com.hamad.tvhome reset; sleep 5; dumpsys gfxinfo com.hamad.tvhome | grep -E "Total frames|Janky|90th|99th"'
# idle target: ~0 frames · navigation target: janky < 15 %, p90 ≤ 20 ms
```

Traps that look like bugs but aren't:

| Trap | What to do |
|---|---|
| `am force-stop com.hamad.tvhome` silently turns accessibility off | Re-enable it as above |
| Accessibility service enabled but `dumpsys accessibility` shows `services:{}` | A stale binding: `adb reboot` |
| `adb shell input keyevent` never reaches the Home-key router | Injected keys skip the a11y filter. Test long/double Home on the real remote, or with `uinput` on an emulator |
| Screenshots of the screensaver show black video | Hardware video plane; only the overlay is captured. Use an emulator to see video frames |
| Clock shows 2017 after a power loss | `settings put global ntp_server 162.159.200.123` |
| Shell `am start` can't open `SettingsActivity` / `SearchActivity` | They aren't exported. Open them through the Home tab bar (Search icon / gear) |

## 6. Multi-agent rules (learned the hard way)

- Work in isolated git worktrees; **each agent owns explicit files or packages** and edits nothing else.
- **Before merging any branch: `git diff --stat master <branch>`.** One agent once committed a
  stale worktree and silently reverted 38 files of other agents' work.
- Never uninstall, disable or remove packages on the owner's device unless explicitly asked.
- Only one agent drives the real box at a time; others use an emulator with `-PidSuffix`.
- Another live session may be editing the same repo. If files changed minutes ago, coordinate
  before you write.

## 7. Testing

- Unit tests live in `app/src/test/…` (JUnit 4). Any pure logic change needs tests: navigation,
  scrolling, formatting, astronomy, parsing.
- Prayer times are checked against Aladhan (`method=0`) to within 1 minute. If you change the
  angles or the city, update the reference values in `PrayerTimesTest`.
- UI is verified on the device by screenshot plus gfxinfo numbers, reported honestly in your summary.

## 8. Recipes

| Task | Where |
|---|---|
| Add a Home row | `home/ContentRepository.kt` (data) + `ui/Rows.kt` (render); keep HIG row widths |
| Add a Settings row/page | `settings/Pages*.kt` → return `Item`/`Header` in a `PageSpec`; only real, working actions |
| Change the prayer city/method | `prayer/PrayerTimes.kt` → new `PrayerConfig`; update tests |
| Change the Instagram account | `dashboard/PrayerDashboardDream.kt` → `INSTAGRAM_HANDLE` |
| New screensaver | Add a `DreamService` + manifest `<service>` with `BIND_DREAM_SERVICE` + an `xml/…_dream.xml` |
| New Control Center tile | `system/SystemControls.kt` (action) + `system/ControlCenter.kt` (tile); hide it if its intent doesn't resolve |

## 9. Commits & reporting

- Conventional commits: `feat:`, `fix:`, `perf:`, `docs:`, `chore:`; one concern per commit.
- Your final report must be short and scannable: what changed, what you verified (numbers and
  screenshots), what is still broken. Never claim a check you didn't run.
