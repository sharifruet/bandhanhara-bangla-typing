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

// Layer 2 — less frequent (6 columns × 5 rows)
export const LAYER2_KEYS: KeyDef[] = [
  // Row 1: rare consonants
  { primary: 'ঙ' }, { primary: 'ঞ' }, { primary: 'ষ' }, { primary: 'ণ' }, { primary: 'ৎ' }, { primary: 'ঽ' },
  // Row 2: marks & nasals
  { primary: 'ং' }, { primary: 'ঃ' }, { primary: 'ঁ' }, { primary: 'ঢ়' }, { primary: 'ড়' }, { primary: 'য়' },
  // Row 3: common conjunct components
  { primary: 'ক্ষ' }, { primary: 'জ্ঞ' }, { primary: '্র' }, { primary: 'র্' }, { primary: '্য' }, { primary: '্ব' },
  // Rows 4–5: Bangla numerals, long-press → ASCII digits
  { primary: '০', longPress: '0' }, { primary: '১', longPress: '1' }, { primary: '২', longPress: '2' },
  { primary: '৩', longPress: '3' }, { primary: '৪', longPress: '4' }, { primary: '৫', longPress: '5' },
  { primary: '৬', longPress: '6' }, { primary: '৭', longPress: '7' }, { primary: '৮', longPress: '8' },
  { primary: '৯', longPress: '9' }, { primary: '৳', longPress: '₹' }, { primary: '%', longPress: '‰' },
];

// Layer 3 — symbols & punctuation (6 columns × 5 rows)
export const LAYER3_KEYS: KeyDef[] = [
  // Row 1: brackets
  { primary: '(', longPress: '{' }, { primary: ')', longPress: '}' }, { primary: '[', longPress: '<' },
  { primary: ']', longPress: '>' }, { primary: '{', longPress: '«' }, { primary: '}', longPress: '»' },
  // Row 2: quotes
  { primary: '\u201C' }, { primary: '\u201D' }, { primary: '\u2018' }, { primary: '\u2019' },
  { primary: '"', longPress: '`' }, { primary: "'", longPress: '´' },
  // Row 3: punctuation
  { primary: '.', longPress: '…' }, { primary: ',' }, { primary: ';' },
  { primary: ':', longPress: '॥' }, { primary: '?', longPress: '¿' }, { primary: '!', longPress: '|' },
  // Row 4: signs & currency
  { primary: '@' }, { primary: '#', longPress: '№' }, { primary: '৳', longPress: '₹' },
  { primary: '$', longPress: '€' }, { primary: '%', longPress: '‰' }, { primary: '&', longPress: '§' },
  // Row 5: math & dashes
  { primary: '-', longPress: '–' }, { primary: '_', longPress: '—' }, { primary: '+', longPress: '±' },
  { primary: '=', longPress: '~' }, { primary: '/', longPress: '\\' }, { primary: '*', longPress: '^' },
];
