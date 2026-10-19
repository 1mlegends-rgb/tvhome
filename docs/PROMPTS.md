# PROMPTS.md — rebuild this project with an LLM, step by step

> 🇸🇦 العربية: [PROMPTS.ar.md](PROMPTS.ar.md) · **One-shot version:** [MASTER-PROMPT.md](MASTER-PROMPT.md)

This is the prompt playbook behind tvhome. Paste each prompt into your coding agent (Claude
Code, Cursor, Codex…) **in order**. Each one is a single focused phase. Wait for the phase to be
verified on the device before sending the next.

**Golden rules to put in every session**

- "Follow `AGENTS.md` and `docs/HIG-tvOS.md`. Real data only. Zero idle frames. No Material
  components."
- "Verify on the real box with a screenshot and `dumpsys gfxinfo`. Report numbers, not adjectives."
- Short feedback works best. Say *what* is wrong ("colours look navy and generic", "tab
  switching stutters") and let the agent measure *why*.

---

### Phase 0 — Find the device
```
Find my Android TV box on the network/adb and tell me its Android version, resolution, CPU ABI, RAM and current launcher.
```

### Phase 1 — Skeleton launcher
```
Create a native Kotlin + Jetpack Compose Android TV launcher (package com.hamad.tvhome, minSdk 26).
It must register as HOME + LEANBACK_LAUNCHER, list installed apps with their banners, and run fast on a 2 GB Android 9 box.
Build a release APK (R8, debug-signed), install it, set it as the default home, and disable the stock launcher reversibly.
```

### Phase 2 — The design system from the HIG
```
Read every page of https://developer.apple.com/design/human-interface-guidelines that has tvOS guidance (use the JSON endpoints under /tutorials/data/design/human-interface-guidelines/).
Distill the rules and exact numbers into docs/HIG-tvOS.md, then build a design kit in design/: tvOS type scale (dp = pt/2), safe area 80/60pt, grid specs, focus states, Liquid Glass material, SF Pro fonts.
```

### Phase 3 — Home like the Apple TV app
```
Rebuild the home as the Apple TV app (tvOS 26): floating Liquid Glass tab bar (Search · Home · Movies · TV Shows · Apps · Settings, 68pt tall, 46pt from the top) with a clock; a full-bleed hero carousel from TMDB (title logo, metadata, Play + More Info); rows for Your Apps, Trending, Top on Netflix/Prime, Popular Movies/TV.
Movies and TV Shows tabs get genre rows; the Apps tab is the 5-column tvOS grid with jiggle-to-reorder.
```

### Phase 4 — Performance pass
```
Measure the home on the box with dumpsys gfxinfo (idle and while navigating). Idle must render 0 frames and use ~0% CPU; navigation must stay under 15% janky.
Remove every infinite animation, pre-bake hero scrims, cap image decode sizes, and keep tab pages composed so a tab switch never composes a whole page in one frame. Report before/after numbers.
```

### Phase 5 — System surfaces (one agent each, in parallel worktrees)
```
A) Control Center (hold Home) + App Switcher (double-press Home) as an accessibility service with overlay windows, in its own process. Only real, working tiles.
B) Aerial screensaver: Apple's real tvOS aerial feed (resources .tar → entries.json + localized captions), ExoPlayer with disk cache, tvOS location captions + clock, TLS for Apple's root CA scoped to Apple hosts.
C) tvOS Settings app: section icon tiles on the left, a focusable list on the right, every row backed by real system state.
D) tvOS Search: linear keyboard, app + TMDB results, product page with trailer.
Each agent owns only its package; verify with `git diff --stat master` before merging.
```

### Phase 6 — Prayer times screensaver
```
When the box is idle, show Baghdad prayer times (Shia/Jafari: Fajr 16°, Maghrib 4°, Isha 14°), computed offline and unit-tested against Aladhan.
Design it Apple-TV style over the Aerial video: no cards or borders, white type hierarchy, the five prayers as a tvOS shelf where the next prayer is the focused white tile with a countdown and progress bar; clock, Gregorian + Hijri date and weather top-right; Instagram followers for @<handle> top-left.
Arabic, RTL, Arabic-Indic digits, correct Arabic plurals. Hide the card if the clock is unset.
```

### Phase 7 — Polish loops (use as needed)
```
The tab switching stutters — measure the spike and make it smooth; slide the focus pill instead of fading it.
Improve the Settings UI: Apple app-icon tiles per section, larger living-room rows, live values on the root page.
The design looks generic — redo it in Apple TV style following AGENTS.md §4.
```

### Phase 8 — Docs
```
Write README.md + AGENTS.md + docs/PROMPTS.md in English and Arabic: build, install via adb, remote map, configuration, architecture, data sources, performance numbers, tests, uninstall and troubleshooting.
```

---

**Tip for the video:** for each phase, show the prompt, the agent's measured result (gfxinfo
numbers or a screenshot), and the next short correction. That loop is what makes it look Apple-made.
