package com.bandhanhara.bangla

/**
 * Character layer definitions.
 *
 * Keep in sync with `web/src/data/keys.ts` and `mobile/data/keys.ts`.
 *
 * Long-press convention:
 *   consonant    → aspirated pair  (ক→খ, গ→ঘ, চ→ছ, জ→ঝ, ট→ঠ, ত→থ, দ→ধ, প→ফ, ব→ভ)
 *   nasal        → retroflex       (ন→ণ)
 *   sibilant     → variant         (স→শ)
 *   vowel        → long form       (অ→আ, ই→ঈ, উ→ঊ, এ→ঐ, ও→ঔ)
 *   vowel sign   → long form       (ি→ী, ু→ূ, ে→ৈ, ো→ৌ)
 *   Bangla digit → ASCII digit     (১→1)
 */
data class KeyDef(val primary: String, val longPress: String? = null)

/** Character keys per row. */
const val COLS = 6

/** Character rows per layer. Every layer is padded to this so the keyboard never changes height. */
const val CHAR_ROWS = 5

// ── Layer 1 — most frequent ───────────────────────────────────────────────────
val LAYER1_KEYS = listOf(
    // Row 1: stop consonants — tap = unaspirated, long-press = aspirated
    KeyDef("ক", "খ"), KeyDef("গ", "ঘ"), KeyDef("চ", "ছ"),
    KeyDef("জ", "ঝ"), KeyDef("ট", "ঠ"), KeyDef("ত", "থ"),
    // Row 2
    KeyDef("দ", "ধ"), KeyDef("ন", "ণ"), KeyDef("প", "ফ"),
    KeyDef("ব", "ভ"), KeyDef("ম"), KeyDef("য"),
    // Row 3
    KeyDef("র"), KeyDef("ল"), KeyDef("স", "শ"),
    KeyDef("হ"), KeyDef("ড", "ড়"), KeyDef("ঢ", "ঢ়"),
    // Row 4: vowels
    KeyDef("অ", "আ"), KeyDef("ই", "ঈ"), KeyDef("উ", "ঊ"),
    KeyDef("এ", "ঐ"), KeyDef("ও", "ঔ"), KeyDef("ঋ", "ৃ"),
    // Row 5: vowel signs + hasanta
    KeyDef("া"), KeyDef("ি", "ী"), KeyDef("ু", "ূ"),
    KeyDef("ে", "ৈ"), KeyDef("ো", "ৌ"), KeyDef("্"),
)

// ── Layer 2 — less frequent (⇧) ───────────────────────────────────────────────
val LAYER2_KEYS = listOf(
    // Row 1: rare consonants
    KeyDef("ঙ"), KeyDef("ঞ"), KeyDef("ষ"), KeyDef("ণ"), KeyDef("ৎ"), KeyDef("ঽ"),
    // Row 2: signs & nukta letters
    KeyDef("ং"), KeyDef("ঃ"), KeyDef("ঁ"), KeyDef("ঢ়"), KeyDef("ড়"), KeyDef("য়"),
    // Row 3: conjunct helpers
    KeyDef("ক্ষ"), KeyDef("জ্ঞ"), KeyDef("্র"), KeyDef("র্"), KeyDef("্য"), KeyDef("্ব"),
    // Rows 4–5: Bangla numerals, long-press → ASCII digits
    KeyDef("০", "0"), KeyDef("১", "1"), KeyDef("২", "2"), KeyDef("৩", "3"), KeyDef("৪", "4"), KeyDef("৫", "5"),
    KeyDef("৬", "6"), KeyDef("৭", "7"), KeyDef("৮", "8"), KeyDef("৯", "9"), KeyDef("৳", "₹"), KeyDef("%", "‰"),
)

// ── Layer 3 — symbols & punctuation (!?#) ─────────────────────────────────────
val LAYER3_KEYS = listOf(
    // Row 1: brackets
    KeyDef("(", "{"), KeyDef(")", "}"), KeyDef("[", "<"), KeyDef("]", ">"), KeyDef("{", "«"), KeyDef("}", "»"),
    // Row 2: quotes
    KeyDef("“"), KeyDef("”"), KeyDef("‘"), KeyDef("’"), KeyDef("\"", "`"), KeyDef("'", "´"),
    // Row 3: punctuation
    KeyDef(".", "…"), KeyDef(","), KeyDef(";"), KeyDef(":", "॥"), KeyDef("?", "¿"), KeyDef("!", "|"),
    // Row 4: signs & currency
    KeyDef("@"), KeyDef("#", "№"), KeyDef("৳", "₹"), KeyDef("$", "€"), KeyDef("%", "‰"), KeyDef("&", "§"),
    // Row 5: math & dashes
    KeyDef("-", "–"), KeyDef("_", "—"), KeyDef("+", "±"), KeyDef("=", "~"), KeyDef("/", "\\"), KeyDef("*", "^"),
)

/** The keys shown for [layer]. In numeric fields Layer 2 swaps digits so ASCII is the tap and Bangla the hold. */
fun keysFor(layer: Layer, numeric: Boolean): List<KeyDef> = when (layer) {
    Layer.L1 -> LAYER1_KEYS
    Layer.L2 -> if (!numeric) LAYER2_KEYS else LAYER2_KEYS.map { k ->
        val alt = k.longPress
        if (alt != null && alt.length == 1 && alt[0] in '0'..'9') KeyDef(alt, k.primary) else k
    }
    Layer.L3 -> LAYER3_KEYS
}
