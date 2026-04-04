// Character layer definitions
// Layer 1: most frequent, Layer 2: less frequent, Layer 3: rare
//
// Long-press convention for consonants:
//   unaspirated → aspirated  (ক→খ, গ→ঘ, চ→ছ, জ→ঝ, ট→ঠ, ত→থ, দ→ধ, প→ফ, ব→ভ)
//   base nasal  → retroflex  (ন→ণ)
//   sibilant    → variants   (স→শ)

export type Layer = 1 | 2 | 3;

export interface KeyDef {
  primary: string;    // character shown on key
  longPress?: string; // revealed on long-press
}

// Layer 1 — 6 columns × 5 rows
// Aspirated consonants (খ ঘ ছ ঝ ঠ থ ধ ফ ভ শ ণ) are NOT separate keys —
// they are the long-press variant of their unaspirated base.
export const LAYER1_KEYS: KeyDef[] = [
  // Row 1: stop consonants — unaspirated, long-press → aspirated
  { primary: 'ক', longPress: 'খ' },
  { primary: 'গ', longPress: 'ঘ' },
  { primary: 'চ', longPress: 'ছ' },
  { primary: 'জ', longPress: 'ঝ' },
  { primary: 'ট', longPress: 'ঠ' },
  { primary: 'ত', longPress: 'থ' },

  // Row 2: more consonants
  { primary: 'দ', longPress: 'ধ' },
  { primary: 'ন', longPress: 'ণ' },
  { primary: 'প', longPress: 'ফ' },
  { primary: 'ব', longPress: 'ভ' },
  { primary: 'ম' },
  { primary: 'য' },

  // Row 3: remaining consonants
  { primary: 'র' },
  { primary: 'ল' },
  { primary: 'স', longPress: 'শ' },
  { primary: 'হ' },
  { primary: 'ড', longPress: 'ড়' },
  { primary: 'ঢ', longPress: 'ঢ়' },

  // Row 4: vowels — short, long-press → long form
  { primary: 'অ', longPress: 'আ' },
  { primary: 'ই', longPress: 'ঈ' },
  { primary: 'উ', longPress: 'ঊ' },
  { primary: 'এ', longPress: 'ঐ' },
  { primary: 'ও', longPress: 'ঔ' },
  { primary: 'ঋ', longPress: 'ৃ' },

  // Row 5: vowel signs + essential marks
  { primary: 'া' },
  { primary: 'ি', longPress: 'ী' },
  { primary: 'ু', longPress: 'ূ' },
  { primary: 'ে', longPress: 'ৈ' },
  { primary: 'ো', longPress: 'ৌ' },
  { primary: '্' },
];

// Layer 2 — less frequent (6 columns × 4 rows)
export const LAYER2_KEYS: KeyDef[] = [
  // Row 1: rare consonants
  { primary: 'ঙ' }, { primary: 'ঞ' }, { primary: 'ষ' }, { primary: 'ণ' }, { primary: 'ৎ' }, { primary: 'ঽ' },
  // Row 2: marks & nasals
  { primary: 'ং' }, { primary: 'ঃ' }, { primary: 'ঁ' }, { primary: 'ঢ়' }, { primary: 'ড়' }, { primary: 'য়' },
  // Row 3: common conjunct components
  { primary: 'ক্ষ' }, { primary: 'জ্ঞ' }, { primary: '্র' }, { primary: 'র্' }, { primary: '্য' }, { primary: '্ব' },
  // Row 4: Bangla numerals
  { primary: '০' }, { primary: '১' }, { primary: '২' }, { primary: '৩' }, { primary: '৪' }, { primary: '৫' },
  { primary: '৬' }, { primary: '৭' }, { primary: '৮' }, { primary: '৯' }, { primary: '৳' }, { primary: '%' },
];

// Layer 3 — rare / symbols (6 columns)
export const LAYER3_KEYS: KeyDef[] = [
  { primary: '(' }, { primary: ')' }, { primary: '"' }, { primary: '"' }, { primary: '\'' }, { primary: '\'' },
  { primary: '@' }, { primary: '#' }, { primary: '-' }, { primary: '–' }, { primary: '…' }, { primary: '/' },
  { primary: '*' }, { primary: '&' }, { primary: '^' }, { primary: '~' }, { primary: '?' }, { primary: '!' },
];
