# বাঁধনহারা বাংলা — Bandhanhara Bangla

A frequency-optimized Bangla keyboard for Android, designed around one principle: **big keys reduce typos**.

---

## Features

- **Aspirate-pair long-press** — tap ক for ক, hold for খ. Same for all consonant pairs (গ→ঘ, চ→ছ, জ→ঝ, ত→থ, দ→ধ, প→ফ, ব→ভ, স→শ…). No wasted keys for aspirated consonants.
- **Three layers** — Layer 1 for the most frequent characters, Layer 2 for less common consonants and numerals, Layer 3 for symbols and punctuation.
- **Key preview popup** — a large character preview appears above each key while pressed, and switches to the long-press character when it fires.
- **One-tap keyboard switch** — 🌐 switches straight to Samsung Keyboard or Gboard (or the next enabled keyboard). Hold 🌐 for the system picker.
- **6-column layout** — keys sized dynamically to fill the full screen width on any phone, capped at half the screen height.
- **Noto Sans Bengali font** — bundled, so Bangla renders identically on every phone.
- **Light and dark themes** — follows the system setting, and tints the navigation bar to match.
- **Smart enter key** — shows search, send, go, next, or done depending on the text field.
- **Numeric fields** — opens on the digit layer with ASCII digits on tap and Bangla digits on hold.
- **Fast editing** — backspace fires on touch and repeats while held, deleting one code point so a stray vowel sign comes off alone.
- **Two-thumb typing** — a second finger commits the first key immediately.
- **Word suggestions** — a strip above the keys shows three words. While typing it completes the current word, and after a space it predicts the next one. Tap a word to insert it with a space, and punctuation pulls that space back ("আমি যাও। "). It turns off in password, email and URL fields.
- **Learns from you** — it remembers the words, word pairs and three-word phrases you type, so a phrase you use often is predicted in full. Recent habits count more, and words you stop using fade out over about six weeks. A suggestion you pick and then delete is shown less after that word. It also learns the last word of a message sent with the app's own send button. Everything stays on the phone. The setup screen shows what it has learned, with a switch to pause learning and a button to clear it.
- **Setup screen** — the launcher icon opens a guided setup (enable, select, try it) with live status.

---

## Layout (Layer 1)

| Row | Keys |
|-----|------|
| 1 | ক(খ) গ(ঘ) চ(ছ) জ(ঝ) ট(ঠ) ত(থ) |
| 2 | দ(ধ) ন(ণ) প(ফ) ব(ভ) ম য |
| 3 | র ল স(শ) হ ড(ড়) ঢ(ঢ়) |
| 4 | অ(আ) ই(ঈ) উ(ঊ) এ(ঐ) ও(ঔ) ঋ(ৃ) |
| 5 | া ি(ী) ু(ূ) ে(ৈ) ো(ৌ) ্ |
| Control | ⇧ !?# 🌐 [space] ।(,) ⌫ ↵ |

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

Open the **Bandhanhara Bangla** app from the launcher and follow the three steps. Or do it by hand:

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
| Tap 🌐 on বাঁধনহারা | Switches directly to Samsung Keyboard or Gboard |
| Hold 🌐 on বাঁধনহারা | Shows the system keyboard picker |
| Long-press space on Samsung | Shows IME picker → select বাঁধনহারা to return |

---

## Development

Shared key layout and layer logic lives in:
- `web/src/data/keys.ts`
- `android-ime/app/src/main/kotlin/com/bandhanhara/bangla/Keys.kt`

If you change the key layout, update both files.

### Suggestion dictionary

The Android keyboard bundles `app/src/main/assets/dict_words.tsv` (30,000 words with frequencies) and `dict_bigrams.tsv` (word pairs for next-word prediction). Regenerate them with:

```bash
cd android-ime
python3 tools/build_dictionary.py
```

Data sources:
- Word frequencies: [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave, built from OpenSubtitles 2018, licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The derived `dict_words.tsv` is shared under the same license.
- Word pairs: Bengali sentences from [Tatoeba](https://tatoeba.org), licensed [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/).

---

## License

MIT
