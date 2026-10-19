# tvOS HIG — the binding spec for this launcher

Distilled from every page of developer.apple.com/design/human-interface-guidelines that has tvOS
guidance (102 pages crawled 2026-10-19). Units: tvOS points at 1080p = px; on the box
1 dp = 2 px, so **dp = pt / 2**.

## Fundamentals (Designing for tvOS)
- Viewed from 8+ feet: large, legible, cinematic. Edge-to-edge artwork, subtle fluid animation.
- Embrace the focus system: it gently highlights and expands items so people always know where they are.
- People sink into one experience for hours — the home screen should feel calm, not busy.

## Layout
- Safe area: **80 pt sides, 60 pt top/bottom** → 40 dp / 30 dp.
- Grids (unfocused width / horizontal spacing / min vertical spacing):
  2-col 860/40/100 · 3-col 560 · 4-col 410 · 5-col 320 · 6-col 260 · 7-col 217 · 8-col 184 · 9-col 160 (all 40 pt h-spacing, ≥100 pt v-spacing).
- Titled rows need extra vertical space: previous row bottom → title centre, and title bottom → row top.
- Consistent spacing everywhere. **Partially hidden offscreen items must be the same width on both sides.**
- Padding between focusables must absorb the focus scale — a focused item never overlaps important content.

## Tab bar (navigation)
- **Height 68 pt (34 dp); top edge 46 pt (23 dp) from the screen top** — fixed values.
- Translucent bar; **only the selected tab is opaque**. When the bar has focus, the selected tab gets a drop shadow.
- May contain button icons such as **Search and Settings**.
- Too many items → right-side fade truncation (and left fade when scrolled).
- On single-main-view tabs (TV app: Watch Now, Movies, TV Shows) **the bar scrolls offscreen with content**.
  Split-view tabs (Library, Settings) keep it pinned.
- **Menu/Back always returns focus to the tab bar at the top of the page.**

## Focus & selection (five states)
| State | Look |
|---|---|
| Unfocused | less prominent |
| Focused | elevated to the foreground, illuminated, animated (parallax sway + shine) |
| Highlighted (pressed) | instant feedback — e.g. briefly invert colours / dip and animate |
| Selected | chosen state (filled heart, checkmark) |
| Unavailable | inactive, not focusable |
- Never use colour alone to show focus — **scale and responsive motion are primary**.
- Supply assets at the larger focused size so focused items stay sharp.
- Focus moves in the same direction as the gesture; don't change focus without user action.
- Lists/collections: full highlight, not rings. Text/search fields: focus ring.
- Full-screen content shows no focus.

## Images & parallax
- Focused element: elevated, **gently sways**, surface **illuminates/shines**. "Parallax is designed to be almost unnoticeable" — keep it subtle.
- **After a period of inactivity, out-of-focus content dims and the focused element expands.**
- Layered images: 2–5 layers (foreground text/characters, middle shadows/effects, opaque background). Text in the foreground. Leave a safe zone — foreground layers crop more when scaled.
- App icons: 800×480 layered, rectangular with concentric rounded corners, safe zone inside.

## Materials
- **Liquid Glass**: navigation elements, Top Shelf, Control Center. Image views and buttons adopt Liquid Glass **when they gain focus**.
- Standard materials (ultra-thin → thick) structure the content layer; dark-scheme overlays for partial obscuring.

## Color & appearance
- Limited palette that coordinates with the brand; colour defers to content.
- Dark Mode is the norm on tvOS; respect it.

## Typography (SF Pro; NY allowed)
Title1 76/96 Medium · Title2 57/66 · Title3 48/56 · Headline 38/46 Medium · Subtitle1 38/46 Regular ·
Callout 31/38 Medium · Body 29/36 Medium · Caption1 25/32 Medium · Caption2 23/30 Medium.
Emphasized = Bold (Subtitle1 → Medium). Tracking per the HIG tvOS table (implemented in `design/Tv.kt`).

## Top Shelf
- Carousel actions / details: full-screen video & images, **title** (+ optional subtitle), primary **Play** + **More Info**; details layout adds summary/metadata and an attribution like "Featured on My App" above the title near the top.
- Feature new content; no ads or prices.
- Static fallback: system flips and blurs the image to 1920 wide; not focusable, never imply interactivity.
- Sectioned row sizes (actual / focused / unfocused): poster 2:3 404×608 / 380×570 / 333×570 · square 608 / 570 / 500 · 16:9 908×512 / 852×479 / 782×440.
- Scrolling inset banner: 3–8 images, 1940×692 actual, 1740×620 focused, 1740×560 unfocused, auto-scroll on a timer until focused.

## Scroll views
- No scroll indicators. The system scrolls automatically to keep the focused item visible.

## Remotes
- Swipe = move focus; press = activate / go deeper; **Back = parent screen; at the top level → Home**; hold Back → Home from anywhere; Play/Pause = media.
- Use standard gestures; don't repurpose standard buttons.

## Audio
- tvOS plays sound only when people initiate it; no sounds for alerts/notifications. Navigation clicks are a user setting.

## Playing video
- Overlays above video stay small, short-lived, translucent SDR — avoid image retention.

## Launching
- The launch screen is static and is replaced quickly by the first screen.
