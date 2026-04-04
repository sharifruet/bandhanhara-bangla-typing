# Bangla Keyboard — Requirements Document

## Overview

A frequency-optimized Bangla keyboard, initially prototyped as a web app, then shipped as native Android and iOS apps. The keyboard organizes characters across three layers based on usage frequency and uses aspirate-pair long-press to maximize key size while keeping all characters reachable.

---

## Goals

- **Maximize key size** to minimize typos — big keys are the single most important design constraint
- Reduce average keystrokes per word for Bangla text via aspirate-pair long-press
- Intuitive layer switching that doesn't interrupt typing flow
- Web prototype (Vite + React) to validate layout on real phones before native build
- Native apps for Android and iOS (Expo / React Native)

---

## Tech Stack

| Project | Stack |
|---|---|
| `web/` | Vite + React + TypeScript |
| `mobile/` | Expo (React Native) + TypeScript |

Shared between both: `data/keys.ts` (character layer definitions) and `hooks/useLayerState.ts` (layer state machine). Changes to the key layout must be applied to both copies.

---

## Character Layer System

### Layer 1 — Default (6 columns × 5 rows)

Aspirated consonants are **not** separate keys — they are the long-press variant of their unaspirated base (see Aspirate-Pair Long-Press below).

| Row | Keys |
|---|---|
| 1 — stop consonants | ক(খ) গ(ঘ) চ(ছ) জ(ঝ) ট(ঠ) ত(থ) |
| 2 — more consonants | দ(ধ) ন(ণ) প(ফ) ব(ভ) ম য |
| 3 — remaining consonants | র ল স(শ) হ ড(ড়) ঢ(ঢ়) |
| 4 — vowels | অ(আ) ই(ঈ) উ(ঊ) এ(ঐ) ও(ঔ) ঋ(ৃ) |
| 5 — vowel signs | া ি(ী) ু(ূ) ে(ৈ) ো(ৌ) ্ |

Notation: `ক(খ)` means tap → ক, long-press → খ.

**Control row:** ⇧ (layer switch) | । (long-press → ,) | [space] | ⌫ | ↵  
**Layer 3 row:** full-width toggle button for Layer 3

### Layer 2 — Less Frequent (6 columns)

Activated by ⇧. One-shot by default (returns to L1 after one character); double-tap ⇧ to lock.

- Row 1: ঙ ঞ ষ ণ ৎ ঽ
- Row 2: ং ঃ ঁ ঢ় ড় য়
- Row 3: ক্ষ জ্ঞ ্র র্ ্য ্ব
- Row 4–5: Bangla numerals ০–৯, ৳ %

### Layer 3 — Rare / Symbols (6 columns)

Stays open until explicitly closed via the toggle button.

- Paired punctuation: ( ) " " ' '
- Symbols: @ # - – … / * & ^ ~ ? !

---

## Aspirate-Pair Long-Press

Bangla consonants come in unaspirated/aspirated pairs. Long-pressing the base consonant gives the aspirated form. This removes ~11 keys from Layer 1, keeping the grid spacious.

| Tap | Long-press | Tap | Long-press |
|---|---|---|---|
| ক (ka) | খ (kha) | দ (da) | ধ (dha) |
| গ (ga) | ঘ (gha) | প (pa) | ফ (pha) |
| চ (ca) | ছ (cha) | ব (ba) | ভ (bha) |
| জ (ja) | ঝ (jha) | স (sa) | শ (śa) |
| ট (ṭa) | ঠ (ṭha) | ন (na) | ণ (ṇa) |
| ত (ta) | থ (tha) | ড (ḍa) | ড় (ṛa) |

The same pattern applies to vowels (অ→আ, ই→ঈ, উ→ঊ, এ→ঐ, ও→ঔ) and vowel signs (ি→ী, ু→ূ, ে→ৈ, ো→ৌ).

Long-press delay: **320ms** (usability-tested starting point).

---

## Layer Switching Behavior

| Action | Effect |
|---|---|
| Tap ⇧ (from L1) | One-shot Layer 2 — inserts one char then returns to L1 |
| Tap ⇧ again (while in one-shot) | Lock Layer 2 |
| Tap ⇧ (while L2 locked) | Return to L1 |
| Tap L3 button | Open Layer 3 (stays open) |
| Tap L3 button again | Return to L1 |

---

## Layout Design Principles

1. **6 columns, dynamic key size** — Key width = `(screenWidth - padding) / 6`. Key height = `keyWidth × 0.95`. Font size = `keyHeight × 0.48`. Keys are always as large as the screen allows.
2. **Aspirate pairs eliminate duplicate keys** — No separate keys for aspirated consonants on L1. Long-press hint shown in the top-right corner of each key.
3. **Vowel signs on L1** — কার signs combine with nearly every word; must always be one tap away.
4. **হসন্ত (্) on L1** — Required for conjunct formation; never buried in a layer.
5. **No scroll in output area** — The text display is fixed-height; no scrolling. Keeps focus on typing.
6. **No accidental layer switch** — ⇧ requires a deliberate tap; no swipe-triggered layer change.
7. **Long-press hints** — Secondary character shown small in the top-right corner; must not compete visually with the primary label.

---

## Web Prototype

**Stack:** Vite + React + TypeScript  
**Purpose:** Test layout and key sizing on real phones via browser before native build.

### Phone Shell (Desktop)
On desktop, the app renders inside a simulated iPhone 16 Pro Max shell (430 × 932 logical px) centered on a dark background, with a Dynamic Island pill and side-button decoration. Scale is computed in JS:
```
scale = min(viewportW × 0.95 / 430, viewportH × 0.95 / 932)
```
On a real phone browser (viewport ≤ 500px wide), the shell is hidden and the app goes full-screen.

### Key Sizing (Web)
- Grid: `grid-template-columns: repeat(6, 1fr)`
- Key height: `clamp(20px, 5.5vw, 28px)` for font; keys fill the grid cell height
- Font: Noto Sans Bengali loaded via Google Fonts

### Run
```bash
cd web && npm run dev        # dev server at localhost:5173
cd web && npm run build      # production build
```

---

## Mobile App (Expo / React Native)

**Stack:** Expo SDK 54, React Native 0.81, TypeScript  
**Targets:** iOS 16+, Android 8.0+ (API 26+)  
**Current phase:** Prototype app (Expo Go compatible). Native IME/keyboard extension is out of scope for v1.

### Key Sizing (Mobile)
```ts
keyWidth  = (screenWidth - H_PADDING * 2 - KEY_GAP * 2 * COLS) / COLS
keyHeight = keyWidth * 0.95
fontSize  = keyHeight * 0.48
```
All sizes are computed from `useWindowDimensions` at runtime — no hardcoded dp values.

### Font
Noto Sans Bengali loaded via `expo-font` / `useFonts`. Font file bundled at `assets/fonts/NotoSansBengali-Regular.ttf`. App shows splash screen until font is ready.

### Haptics
`expo-haptics` — `ImpactFeedbackStyle.Light` on long-press. Works on both iOS and Android.

### Long-press bug fix
`Pressable.onPress` fires after `onLongPress` completes. Fixed with a `longPressedRef` flag: if long-press fired, the subsequent `onPress` is swallowed.

### Safe Area
`react-native-safe-area-context` — `useSafeAreaInsets` applied as `paddingBottom` on the keyboard so it clears the iOS home indicator and Android gesture bar.

### Run
```bash
cd mobile && npx expo start   # then press i (iOS) or a (Android)
cd mobile && npm run ios
cd mobile && npm run android
```

---

## Out of Scope (v1)

- System-wide IME / keyboard extension (Android InputMethodService, iOS UIInputViewController)
- Autocomplete / word prediction
- Voice input
- Transliteration mode (e.g., typing "ami" → আমি)
- Cloud sync of custom layouts
- Tablet-optimized split layout
- Dark mode

---

## Open Questions

1. Should Layer 2 default to one-shot or locked? (User research needed.)
2. Long-press delay: 320ms is the starting point — needs usability testing with native Bangla typists.
3. Should ং and ঃ be promoted to Layer 1? (Currently on Layer 2; they appear in many common words.)
4. Should the web prototype log keystroke sequences for layout analysis ("researcher mode")?
