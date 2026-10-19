# MASTER-PROMPT.md — one copyable prompt that rebuilds everything

> 🇸🇦 العربية: [MASTER-PROMPT.ar.md](MASTER-PROMPT.ar.md) · Step-by-step version: [PROMPTS.md](PROMPTS.md)

Copy the block below (use the copy button at its top-right) and paste it into your AI coding
agent: Claude Code, Cursor, Codex, Windsurf, Gemini CLI… It contains the full specification of
tvhome. The agent builds it phase by phase and verifies each phase on your device.
Replace the values in `<ANGLE_BRACKETS>` first.

```text
You are a senior Android TV engineer and an Apple-level product designer. Build "tvhome": a complete Apple TV (tvOS 26) experience for an ordinary Android TV box — launcher, Top Shelf, Search, Settings, Control Center, App Switcher, Aerial screensaver and a prayer-times screensaver. Work autonomously, phase by phase, verifying every phase on the real device before moving on.

═══════════════════════════════════════════════
0. CONTEXT & CONSTRAINTS
═══════════════════════════════════════════════
- Target device: Android TV box reachable over adb at <BOX_ADB_SERIAL_OR_IP:5555>. Assume the worst case: Android 9 (API 28), 2 GB RAM, low-end GPU GPU, armeabi-v7a, 1920x1080 at 320 dpi (1 dp = 2 px), Arabic RTL system locale, no RTC battery.
- Stack: Kotlin 2.x + Jetpack Compose (foundation only), single :app module, package com.hamad.tvhome (or <YOUR_PACKAGE>), minSdk 26, targetSdk 34, compileSdk 36. Libraries: androidx core/activity-compose, Compose BOM, Media3 ExoPlayer, Coil 3 (+ okhttp network), OkHttp 4, kotlinx-coroutines, JUnit 4. NO Material / material3 / tv-material components.
- Build: JDK 21 (Android Studio JBR). Always install the RELEASE build (R8 minify + shrink, signed with the debug key); debuggable Compose is too slow on this hardware.
- Secrets: TMDB read token only in git-ignored local.properties (TMDB_READ_TOKEN) → BuildConfig field. Never commit or print it.
- Real data only. Never invent numbers or placeholder content; hide an element when its data is unavailable.

═══════════════════════════════════════════════
1. DESIGN SYSTEM (binding)
═══════════════════════════════════════════════
First crawl every page of Apple's Human Interface Guidelines that contains tvOS guidance (JSON endpoints: https://developer.apple.com/tutorials/data/design/human-interface-guidelines/<page>.json, starting from human-interface-guidelines.json and following references). Distill the rules and exact numbers into docs/HIG-tvOS.md. Then build design/ as the single source of truth:
- Units: dp = tvOS pt / 2.
- Safe area 80 pt sides / 60 pt top-bottom → 40 dp / 30 dp.
- tvOS text styles (pt → sp = /2): Title1 76/96 Medium, Title2 57/66, Title3 48/56, Headline 38/46 Medium, Subtitle1 38/46 Regular, Callout 31/38 Medium, Body 29/36 Medium, Caption1 25/32, Caption2 23/30; emphasized = Bold; apply the HIG tvOS tracking table.
- Fonts: SF Pro Display/Text + SF Arabic (or open alternatives such as Inter + IBM Plex Sans Arabic for public distribution).
- Grids: 5-col 320 pt wide (160 dp), 40 pt gaps (20 dp), ≥100 pt (50 dp) between rows; 4-col 410, 7-col 217 (posters). App icons 5:3 (800x480 layout) with concentric rounded corners.
- Focus states: unfocused / focused (scale up, lift with shadow, white platter + black text, subtle shine) / highlighted on press (brief dip) / selected / unavailable. Never colour-only focus.
- Liquid Glass material (translucent body, top sheen, bright top-left rim) for navigation chrome, tab bar, Control Center, idle buttons; focused buttons turn white.
- Dark appearance: white labels with opacity hierarchy (100/78/58/40 %), system accent colours only where semantic.
- Motion: springs (damping ~0.75–0.9), one-shot only. NOTHING may animate while the screen is at rest.

═══════════════════════════════════════════════
2. HOME LAUNCHER (MainActivity, HOME + LEANBACK_LAUNCHER)
═══════════════════════════════════════════════
- Apple TV app layout. Floating Liquid Glass capsule tab bar, 68 pt tall, top edge 46 pt from the screen top: [Search icon] Home · Movies · TV Shows · Apps [Settings gear]; clock at top-right. Only the selected tab is opaque; ONE white focus pill that slides (spring) between items, and one sliding selected-tab pill. The bar hides when you scroll into rows and returns at the top. Back always returns focus to the tab bar; Back on the Home tab bar does nothing; the Home key returns to the Home tab.
- Home tab: full-bleed hero carousel (TMDB trending + titles on installed providers: Netflix 8, Prime 9, Disney+ 337, Apple TV+ 350; region from settings, fallback US). Hero shows attribution ("Featured on Netflix" / "Trending"), TMDB title logo (fallback Title1 text), meta line ("TV Show · 2024 · Drama"), 2-line overview, page dots, buttons Play (opens provider app or YouTube trailer search) and More Info (opens the Search product page with extras tmdb_id + tmdb_type). Auto-advance every ~8 s only while the hero/bar is focused, for at most one loop. Hero scrims baked into the bitmap off the main thread.
- Rows below the hero: Your Apps (5:3 app tiles), Trending Now (16:9 cards), Top on Netflix, Top on Prime Video (only if installed), Popular Movies, Popular TV Shows (2:3 posters). Row titles; focused card lifts and shows its title; offscreen partial cards symmetric.
- Movies / TV Shows tabs: hero + trending, top rated and 4–5 genre rows (TMDB discover). Selecting a card opens the product page.
- Apps tab: 5-column tvOS grid of all launchable apps (leanback + launcher categories, banner or generated icon plate), Settings and Search tiles. Hold OK → jiggle mode, D-pad moves the app, OK finishes; Menu/Play-Pause or hold OK in jiggle → tvOS action sheet: Move, Hide App, App Info, Uninstall (removable apps only), Cancel. Order persisted.
- After 4 s without input: one 700 ms "inactivity" animation (siblings dim, focused item grows slightly), then zero frames.
- Tab pages stay composed once built and all tabs are prefetched ~1.5 s after start, so a tab switch is an alpha fade only (no composition spike).
- Navigation is a pure state machine in the ViewModel (no Compose focus on Home); unit-test grid navigation and row scrolling.
- Optional soft navigation click (user setting).

═══════════════════════════════════════════════
3. SEARCH APP (SearchActivity, not exported)
═══════════════════════════════════════════════
- tvOS linear keyboard: one row "space a–z ⌫" with a lifted focus pill, mode row abc / ABC / 123 / #+=, search field with a focus ring. Down enters results, Up returns.
- Results (300 ms debounce, stale requests cancelled): Top Results, Apps (installed, matching label), Movies, TV Shows (TMDB posters, HIG widths). Empty query → "Popular Movies and TV Shows".
- Product page: full-bleed backdrop with left gradient, title logo, "Movie · 2024 · Sci-Fi · ★ 7.8" (real TMDB rating), overview, buttons Watch Trailer (YouTube TV search intent, web fallback) and Open Netflix if installed. Deep link: extras tmdb_id/tmdb_type open the page directly; Back then returns to Home.

═══════════════════════════════════════════════
4. SETTINGS APP (SettingsActivity, not exported)
═══════════════════════════════════════════════
- Layout: page title centered at top; left column = large Apple app-icon tile per section (squircle, system colour, white glyph: General grey gear, Video & Audio blue TV, Network blue Wi-Fi, Apps cyan grid, Home Screen indigo, Screen Saver landscape, Remotes graphite) with a caption below; right column = list of 46 dp rows, 19 sp labels, value + chevron, focused row = white lifted platter. Pages push/pop with a short slide; focus remembered per page; tvOS uses "On/Off" values instead of switches; pickers show a checkmark.
- Root shows live values (display format "1080p 60Hz", current screensaver name, network type/SSID) and the device name + Android version under the icon.
- Sections (only rows whose intents resolve; only real values): General (About: name, model, Android version, patch, storage, resolution; Language; Date & Time; Keyboard), Video and Audio (format, audio output, navigation clicks), Screen Saver (type picker over installed dreams, start after, preview via com.android.systemui/.Somnambulator, show location, show clock, prayer times, 4K), Home Screen (Top Shelf autoplay, content region picker, reset layout with a tvOS alert, how to restore the stock launcher), Remotes and Devices, Apps (per app: open, hide on Home, app info, uninstall, version), Network (connection, signal, IP, router, DNS, configure Wi-Fi), System (software update, accessibility, Android settings).
- Writes secure/system settings only if WRITE_SECURE_SETTINGS / WRITE_SETTINGS are granted; otherwise opens the matching Android settings screen.

═══════════════════════════════════════════════
5. CONTROL CENTER + APP SWITCHER (AccessibilityService, process ":system")
═══════════════════════════════════════════════
- Key filter (flagRequestFilterKeyEvents): hold Home ≥600 ms → Control Center; double-press Home within 350 ms → App Switcher; single press → performGlobalAction(HOME). Put the routing logic in a pure, unit-tested class. Run the service in its own process so key filtering never waits on the launcher UI thread.
- Overlays: TYPE_ACCESSIBILITY_OVERLAY windows hosting ComposeView with hand-rolled Lifecycle/SavedState/ViewModelStore owners; Back/Home closes.
- Control Center: right-side Liquid Glass panel springing in over a scrim; big clock + date; 2-column tiles: Sleep (GLOBAL_ACTION_LOCK_SCREEN), Screen Saver (Somnambulator), Network (real state), Bluetooth, Search, Settings; wide Volume tile (Left/Right changes STREAM_MUSIC). Only tiles whose intents resolve.
- App Switcher: dimmed full screen, horizontal carousel of large app cards (banner art) from UsageStatsManager (last 3 days) with a Home card; Left/Right moves, OK opens, Up = close (killBackgroundProcesses + remove card).

═══════════════════════════════════════════════
6. AERIAL SCREENSAVER (DreamService)
═══════════════════════════════════════════════
- Catalogue: Apple's tvOS feed https://sylvan.apple.com/Aerials/resources-16.tar (fallback -15) → entries.json + localized caption strings (points of interest); parse the tar/plist yourself; cache on disk, refresh weekly; bundle a fallback list.
- TLS: sylvan.apple.com chains to "Apple Root CA" which Android lacks — bundle that CA and trust it ONLY for apple.com hosts (never disable validation).
- Playback: Media3 ExoPlayer with an OkHttp data source + SimpleCache (~400 MB or ¼ free space), SurfaceView, shuffle without repeats, 1 s fade through black, skip on error, precache next clip; 1080p H.264 by default, 4K HEVC optional.
- Overlay: location/POI caption bottom-left (fades in ~6 s), clock top-right; offline → slow dark ambient gradient + clock.

═══════════════════════════════════════════════
7. PRAYER-TIMES SCREENSAVER (DreamService over the Aerial video)
═══════════════════════════════════════════════
- Calculation offline (PrayTimes algorithm) for <CITY, lat, lon, UTC offset> with method <e.g. Shia Jafari: Fajr 16°, Maghrib 4°, Isha 14° | Sunni MWL: 18°/17°, Maghrib at sunset>; unit-test against api.aladhan.com to ±1 minute for two dates.
- Layout (Arabic, RTL, Arabic-Indic digits, correct Arabic plural agreement: ساعة/ساعتان/٣ ساعات/١١ ساعة, same for minutes and days): no cards or borders on the top band — white type on soft top/bottom scrims over the video.
  · Top-right block: big clock ("٧:٥٢" + ص/م), "الاثنين، ١٩ أكتوبر · ٨ جمادى الأولى ١٤٤٨ هـ" (ICU Islamic Umm al-Qura), weather line (city, icon, temperature, condition) from Open-Meteo.
  · Top-left block (mirrors the right one line-for-line): Instagram followers for @<INSTAGRAM_HANDLE> from the public profile og:description (logged-out Instagram only gives a rounded figure like "14K"; show "١٤ ألف متابع"), handle with an outline Instagram glyph, posts count or a 24 h change line from an hourly history.
  · Bottom: row title "مواقيت الصلاة · <city>" and a tvOS shelf of five glass tiles (Fajr, Dhuhr, Asr, Maghrib, Isha) each with a line glyph (dawn, sun, low sun, sunset, crescent+stars), name and time; passed prayers dim; the NEXT prayer is the focused tile — wider (weight ~1.7), white, black type, shadow — containing its time, "بعد ٣ ساعات و٤٨ دقيقة" and a progress capsule from the previous prayer. For 20 minutes after a prayer starts the tile says "حان الآن".
- Updates once a minute only (data: weather 30 min, Instagram 30 min); burn-in guard: the whole layout drifts a few dp per minute. Hide the prayer content if the system clock is unset (year < 2025).
- Offline: ambient background; prayer times still shown.

═══════════════════════════════════════════════
8. DEVICE SETUP (do it for me over adb)
═══════════════════════════════════════════════
install -r the release APK; cmd package set-home-activity <pkg>/.MainActivity; pm disable-user --user 0 com.google.android.tvlauncher (tell me how to undo); enable the accessibility service (settings put secure enabled_accessibility_services … + accessibility_enabled 1) — re-enable after every reinstall/force-stop, reboot if dumpsys shows services:{}; appops GET_USAGE_STATS + WRITE_SETTINGS allow; pm grant WRITE_SECURE_SETTINGS; set screensaver_components to the prayer dream; if the box has no RTC and uses Private DNS, set ntp_server to 162.159.200.123. Ask before uninstalling or removing any package.

═══════════════════════════════════════════════
9. PERFORMANCE & VERIFICATION (every phase)
═══════════════════════════════════════════════
- Measure with `dumpsys gfxinfo <pkg>`: home idle ≈ 0 frames / ~0 % CPU; navigation < 15 % janky, p90 ≤ 20 ms; tab switch worst frame < 30 ms. Report before/after numbers.
- Screenshot every screen with `adb exec-out screencap -p` and look at it; compare against tvOS. Note: video planes are invisible in box screenshots (use an emulator to check video), and `adb input keyevent` does not reach the accessibility key filter (test Home gestures on the real remote).
- Keep RTL locale in mind: force LTR on tvOS surfaces, RTL on the Arabic prayer screen.
- Unit tests for: grid navigation, row scroll, Home-key router, recents ordering, Aerial parsing, prayer astronomy, prayer schedule, Arabic formatting.

═══════════════════════════════════════════════
10. PROCESS RULES
═══════════════════════════════════════════════
- Order: (1) skeleton launcher + device setup → (2) HIG crawl + design kit → (3) Home → (4) performance pass → (5) Search, Settings, Control Center/App Switcher, Aerial (in parallel if you can spawn sub-agents: separate git worktrees, each owns only its package, run `git diff --stat main <branch>` before every merge) → (6) prayer screensaver → (7) polish → (8) docs.
- Conventional commits, one concern each. Never claim a check you did not run. End each phase with a short report: what changed, measured numbers, screenshots, known gaps.
- Deliver docs: README.md (+ Arabic), AGENTS.md (+ Arabic) for future AI agents, docs/HIG-tvOS.md.
- Quality bar: if a screen would not pass as Apple-made, it is not done.
```

---

### How to use it in the video

1. Show the empty folder and the box's stock launcher.
2. Paste the prompt and let the agent work through the phases.
3. Between phases, give short corrections. These are the real ones used:
   *"colours look generic — make it Apple TV style"*, *"tab switching isn't smooth"*,
   *"improve the prayers design"*, *"improve settings UI"*.
4. Finish on the box: Home → Search → Settings → hold Home (Control Center) → wait for the prayer screensaver.
