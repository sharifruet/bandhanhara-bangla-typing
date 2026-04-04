# বাঁধনহারা বাংলা — Bandhanhara Bangla

A frequency-optimized Bangla keyboard for Android, designed around one principle: **big keys reduce typos**.

---

## Features

- **Aspirate-pair long-press** — tap ক for ক, hold for খ. Same for all consonant pairs (গ→ঘ, চ→ছ, জ→ঝ, ত→থ, দ→ধ, প→ফ, ব→ভ, স→শ…). No wasted keys for aspirated consonants.
- **Three layers** — Layer 1 for the most frequent characters, Layer 2 for less common consonants and numerals, Layer 3 for symbols and punctuation.
- **iOS-style key popup** — a large character preview appears above each key while pressed, just like iOS.
- **One-tap keyboard switch** — 🌐 button switches directly to Samsung Keyboard (or any other keyboard via the system picker).
- **6-column layout** — keys sized dynamically to fill the full screen width on any phone.
- **Noto Sans Bengali font** — crisp Bangla rendering at all sizes.

---

## Layout (Layer 1)

| Row | Keys |
|-----|------|
| 1 | ক(খ) গ(ঘ) চ(ছ) জ(ঝ) ট(ঠ) ত(থ) |
| 2 | দ(ধ) ন(ণ) প(ফ) ব(ভ) ম য |
| 3 | র ল স(শ) হ ড(ড়) ঢ(ঢ়) |
| 4 | অ(আ) ই(ঈ) উ(ঊ) এ(ঐ) ও(ঔ) ঋ(ৃ) |
| 5 | া ি(ী) ু(ূ) ে(ৈ) ো(ৌ) ্ |
| Control | 🌐 ⇧ … [space] ⌫ ↵ |

`ক(খ)` = tap → ক, long-press → খ

---

## Projects

| Directory | Stack | Purpose |
|-----------|-------|---------|
| `android-ime/` | Kotlin | Native Android IME — installs as a system keyboard |
| `web/` | Vite + React + TypeScript | Browser prototype for layout testing |
| `mobile/` | Expo (React Native) | Mobile prototype app |

---

## Install on Android

### Requirements
- Android 8.0+ (API 26)
- Android Studio with SDK installed

### Build & install

```bash
cd android-ime

# Build
ANDROID_HOME=~/Library/Android/sdk \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew assembleDebug

# Install (phone connected via USB with USB debugging enabled)
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Enable as keyboard

1. **Settings → General management → Keyboard list and default**
2. Toggle on **বাঁধনহারা বাংলা**
3. Set as default keyboard

---

## Web Prototype

```bash
cd web
npm install
npm run dev        # opens at localhost:5173
```

The web prototype renders inside an iPhone 16 Pro Max shell on desktop. Open on your phone browser for real touch testing.

---

## How Switching Works

| Action | Result |
|--------|--------|
| Tap 🌐 on বাঁধনহারা | Switches directly to Samsung Keyboard |
| Long-press space on Samsung | Shows IME picker → select বাঁধনহারা to return |

---

## Development

Shared key layout and layer logic lives in:
- `web/src/data/keys.ts`
- `android-ime/app/src/main/kotlin/com/bandhanhara/bangla/Keys.kt`

If you change the key layout, update both files.

---

## License

MIT
