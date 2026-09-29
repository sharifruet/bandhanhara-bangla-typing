<p align="center"><img src="assets/logo.svg" alt="বাঁধনহারা বাংলা — Bandhanhara Bangla" width="640"></p>

# বাঁধনহারা বাংলা — Bandhanhara Bangla

A frequency-optimized Bangla keyboard for Android, designed around one principle: **big keys reduce typos**.

---

## Features

- **Aspirate-pair long-press** — tap ক for ক, hold for খ. Same for all consonant pairs (গ→ঘ, চ→ছ, জ→ঝ, ত→থ, দ→ধ, প→ফ, ব→ভ, স→শ…). No wasted keys for aspirated consonants.
- **Three layers** — Layer 1 for the most frequent characters, Layer 2 for less common consonants and numerals, Layer 3 for symbols and punctuation.
- **Key preview popup** — a large character preview appears above each key while pressed, and switches to the long-press character when it fires.
- **বাংলা and English in one keyboard** — 🌐 switches to a full QWERTY layout with a number row, ⇧ for capitals (tap twice for caps lock), automatic capitals at the start of sentences, and its own English suggestions. Hold 🌐 to pick another keyboard app, or set 🌐 to switch keyboard apps in settings.
- **6-column layout** — keys sized dynamically to fill the full screen width on any phone, capped at half the screen height.
- **Noto Sans Bengali font** — bundled, so Bangla renders identically on every phone.
- **Themes and sizes** — light, dark or follow the system, three keyboard heights, and optional long-press hints and key previews.
- **Smart enter key** — shows search, send, go, next, or done depending on the text field.
- **Numeric fields** — opens on the digit layer with ASCII digits on tap and Bangla digits on hold.
- **Fast editing** — backspace fires on touch and repeats while held, speeding up to whole words. It deletes one code point at a time, so a stray vowel sign comes off alone, but a whole emoji at once. Drag along the space bar to move the cursor, and double-tap space to end a sentence with । or a full stop.
- **Two-thumb typing** — a second finger commits the first key immediately.
- **Word suggestions** — a strip above the keys shows three words. While typing it completes the current word, and after a space it predicts the next one. Tap a word to insert it with a space, and punctuation pulls that space back ("আমি যাও। "). It turns off in password, email and URL fields.
- **Learns from you** — it remembers the words, word pairs and three-word phrases you type, so a phrase you use often is predicted in full. Recent habits count more, and words you stop using fade out over about six weeks. A suggestion you pick and then delete is shown less after that word. It also learns the last word of a message sent with the app's own send button. Everything stays on the phone. The setup screen shows what it has learned, with a switch to pause learning and a button to clear it.
- **Emoji** — a full emoji panel with categories and a recently used tab. Hold !?# or use the toolbar to open it.
- **Clipboard** — text you copy shows up as a one-tap paste in the suggestion strip, and a clipboard panel keeps the last hour of copies in memory only.
- **Toolbar** — the ⊞ button in the suggestion strip opens emoji, clipboard, voice typing and settings.
- **Settings** — theme, height, 🌐 behaviour, suggestions, auto-space, double-space, auto-capitals, long-press delay, vibration, sound, key preview and learning, all in one screen. It is also reachable from the gear in Android's keyboard list.
- **Setup screen** — the launcher icon opens a guided setup (enable, select, try it) with live status.

---

## Layout (Layer 1)

| Row | Keys |
|-----|------|
| 1 | ক(খ) গ(ঘ) চ(ছ) জ(ঝ) ট(ঠ) ত(থ) য়(ং) |
| 2 | দ(ধ) ন(ণ) প(ফ) ব(ভ) ম য ষ(ঁ) |
| 3 | র ল স(শ) হ ড(ড়) ঢ(ঢ়) ্ |
| 4 | ⇄ + vowels অ(ঋ) আ ই(ঈ) উ(ঊ) এ(ঐ) ও(ঔ) ং(ঁ) **or** vowel signs া ি(ী) ু(ূ) ে(ৈ) ো(ৌ) ্র(ৃ) ্য |
| Control | ⇧ !?#(☺) 🌐 [space] ।(,) ⌫ ↵ |

`ক(খ)` = tap → ক, long-press → খ

The 7th column holds the most-used characters that are not plain consonants, by frequency in real text: ্ hasanta (4.1 % of characters), so every যুক্তাক্ষর is consonant + ্ + consonant on the first screen; then য় (1.7 %) and ষ (0.4 %), with ং and ঁ on long-press.

In the sign row, য-ফলা (্য, 1.1 %) and র-ফলা (্র, 0.6 %) are single keys, where they are needed right after a consonant; the rarer ৃ is held on ্র. In the vowel row, আ has its own key, and ং (hold for ঁ) is there because it usually follows a vowel sign, as in বাংলা. ঋ, which almost never appears, is held on অ.

Row 4 switches by itself: right after a consonant it shows vowel signs (কা, কি…), and everywhere else it shows full vowels (অ, ই…). The ⇄ key on its left flips it by hand until the next letter. The Android keyboard shows this merged row; the web and Expo prototypes still show vowels and signs as two rows.

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
| Tap 🌐 on বাঁধনহারা | Switches between বাংলা and English (or to your other keyboard app, if set in settings) |
| Hold 🌐 on বাঁধনহারা | Shows the system keyboard picker |
| Long-press space on Samsung | Shows IME picker → select বাঁধনহারা to return |

---

## Development

Shared key layout and layer logic lives in:
- `web/src/data/keys.ts`
- `android-ime/app/src/main/kotlin/com/bandhanhara/bangla/Keys.kt`

If you change the key layout, update both files.

### Brand assets

The mascot is a happy green bird writing অ with a bamboo fountain pen, on warm cream. The reference artwork is `assets/new-logo.jpg`. One script redraws it as vector art and produces every brand asset: `assets/icon.svg`, `assets/logo.svg`, `assets/mascot.svg`, the Android launcher, themed and splash icons, the in-app logo, the Play Store images in `android-ime/store/`, the web favicon, and the Expo app icons.

```bash
pip install fonttools uharfbuzz resvg-py
python3 android-ime/tools/build_brand.py
```

Edit the script, not the generated files.

### Suggestion dictionary and emoji

The Android keyboard bundles, for Bangla and English (`_en`), `app/src/main/assets/dict_words*.tsv` (30,000 words each, with frequencies) and `dict_bigrams*.tsv` (word pairs for next-word prediction), plus `emoji.tsv`. Regenerate them with:

```bash
cd android-ime
python3 tools/build_dictionary.py
python3 tools/build_emoji.py
```

Data sources:
- Word frequencies: [FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave, built from OpenSubtitles 2018, licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The derived `dict_words.tsv` is shared under the same license.
- Word pairs: Bengali and English sentences from [Tatoeba](https://tatoeba.org), licensed [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/).
- Emoji: [Unicode emoji-test.txt](https://www.unicode.org/Public/emoji/15.1/emoji-test.txt). Emoji the phone cannot draw are hidden at runtime.

---

## Publishing to Google Play

The release build is signed with an upload key kept in `android-ime/keystore/`. That folder is ignored by git and exists only on this computer, so **back up both files in it somewhere safe**, such as a password manager. Google re-signs the app with its own key (Play App Signing). If the upload key is ever lost, request an upload-key reset in Play Console.

```bash
cd android-ime
ANDROID_HOME=~/Library/Android/sdk \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab   (upload this to Play Console)
```

Before each new upload, raise `versionCode` in `android-ime/app/build.gradle.kts`, and change `versionName` to match. Store texts, the data-safety answers and the list of images to upload are in [android-ime/store/listing.md](android-ime/store/listing.md). The privacy policy is in [docs/privacy-policy.md](docs/privacy-policy.md).

## License

MIT
