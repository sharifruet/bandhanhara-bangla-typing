# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projects

| Directory | Stack | Purpose |
|---|---|---|
| `web/` | Vite + React + TypeScript | Browser prototype for layout testing |
| `mobile/` | Expo (React Native) + TypeScript | Android & iOS app |

## Commands

### Web
```bash
cd web
npm run dev      # dev server (localhost:5173)
npm run build    # production build
npm run preview  # preview production build
npx tsc --noEmit # type-check only
```

### Mobile
```bash
cd mobile
npm run android  # run on Android emulator / device
npm run ios      # run on iOS simulator / device
npm run web      # run in browser via Expo
npx expo start   # start Expo dev server (choose platform interactively)
```

## Architecture

### Shared logic (duplicated between web/ and mobile/)
`data/keys.ts` — character layer definitions (Layer 1/2/3 key arrays with primary + longPress labels)  
`hooks/useLayerState.ts` — layer state machine: normal → oneshot → locked, handles one-shot return after char insert

Both projects share identical copies of these two files. If you change the key layout or layer logic, update both.

### Web (`web/src/`)
- `App.tsx` — text output area + copy button, wires keyboard callbacks
- `components/BanglaKeyboard.tsx` — renders the active layer grid + control row
- `components/Key.tsx` — single key with long-press timer (350ms) via pointer events
- `components//*.module.css` — CSS Modules, 4-column grid, `clamp()`-based sizing

### Mobile (`mobile/`)
- `App.tsx` — SafeAreaView, ScrollView output area, wires keyboard
- `components/BanglaKeyboard.tsx` — same structure as web but uses React Native `View`/`StyleSheet`
- `components/Key.tsx` — uses `TouchableOpacity` + `onLongPress` with `Vibration.vibrate(30)`

## Key Design Constraint

**4 columns max, minimum key height 56dp/pt, minimum font 24sp/pt.** This is the primary constraint — do not add more columns or reduce key size to fit more characters. Add a new layer row instead.

## Layer System

| Layer | Access | Content |
|---|---|---|
| 1 | Default | ~28 most-frequent chars, vowel signs, হসন্ত |
| 2 | Shift tap (one-shot) or double-tap (locked) | Remaining consonants, rare vowels, conjuncts |
| 3 | L3 key (stays open) | Rare/archaic chars, symbols, Bangla numerals |
