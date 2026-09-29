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

/** Rows of the 6-column character layers (Layer 2, symbols). Layer 1 shows 4: its vowel row switches. */
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

// ── English (QWERTY) — tap ⇧ for one capital, twice for caps lock ──────────────
private fun row(keys: String, alts: String) = keys.mapIndexed { i, c -> KeyDef(c.toString(), alts.getOrNull(i)?.toString()) }

val ENGLISH_ROWS: List<List<KeyDef>> = listOf(
    row("1234567890", "!@#$%^&*()"),
    row("qwertyuiop", "%^~|[]<>{}"),
    row("asdfghjkl", "@#&*-+=()"),
    row("zxcvbnm", "_$\"':;/"),
)

private val ENGLISH_ROWS_UPPER = ENGLISH_ROWS.map { r -> r.map { k -> KeyDef(k.primary.uppercase(), k.longPress) } }

/**
 * The rows of keys for the current state.
 *  - Bangla: 6 columns × 5 rows on every layer.
 *  - English: QWERTY with a number row (10/10/9/7); ⇧ gives capitals; !?# shares the symbol layer.
 *  - Number fields get the digit layer (ASCII digits on tap) whatever the language.
 */
fun rowsFor(language: Language, layer: Layer, numeric: Boolean, vowelSigns: Boolean = false): List<List<KeyDef>> = when {
    numeric -> keysFor(Layer.L2, numeric = true).chunked(COLS)
    layer == Layer.L3 -> LAYER3_KEYS.chunked(COLS)
    language == Language.ENGLISH -> if (layer == Layer.L2) ENGLISH_ROWS_UPPER else ENGLISH_ROWS
    layer == Layer.L1 -> CONSONANT_ROWS + listOf(listOf(VOWEL_TOGGLE) + if (vowelSigns) SIGN_ROW else VOWEL_ROW)
    else -> keysFor(layer, numeric = false).chunked(COLS)
}

// ── Layer 1 on the keyboard: three consonant rows and ONE vowel row ────────────
// The vowel row shows either full vowels (অ ই উ …) or vowel signs (া ি ু …). It switches by
// itself — signs right after a consonant, vowels everywhere else — and the ⇄ key flips it by hand.

/** Marker for the vowel/sign switch key at the start of the vowel row. */
const val VOWEL_TOGGLE_ID = "\uE000vowel-toggle"
val VOWEL_TOGGLE = KeyDef(VOWEL_TOGGLE_ID)

/**
 * A 7th key on each consonant row brings the most-used characters that used to need ⇧ onto the
 * first screen (frequencies from the bundled word list): ্ hasanta 4.1 % — always one tap away
 * for যুক্তাক্ষর — then য় 1.7 % and ষ 0.4 %, with ং and ঁ (0.2 % each) on long-press.
 */
private val CONSONANT_EXTRAS = listOf(KeyDef("য়", "ং"), KeyDef("ষ", "ঁ"), KeyDef("্"))

private val CONSONANT_ROWS = LAYER1_KEYS.take(18).chunked(COLS).zip(CONSONANT_EXTRAS) { row, extra -> row + extra }
/**
 * The switching vowel row, ordered by frequency in the bundled word list.
 * Signs (after a consonant): া 11.4 %, ে 9.1 %, ি 5.9 %, ো 2.2 %, ু 2.2 %, then the ফলা that used to
 * take two taps — ্য 1.1 %, ্র 0.6 % — with the rare ৃ (0.08 %) held on ্র, its "r" sibling.
 * Vowels (elsewhere): আ gets its own key (আমি, আমার…); ং / ঁ usually follow a vowel sign (বাংলা,
 * হাঁ), when this row is showing vowels; ঋ (≈ 0 %) is held on অ.
 */
private val VOWEL_ROW = listOf(
    KeyDef("অ", "ঋ"), KeyDef("আ"), KeyDef("ই", "ঈ"), KeyDef("উ", "ঊ"),
    KeyDef("এ", "ঐ"), KeyDef("ও", "ঔ"), KeyDef("ং", "ঁ"),
)
private val SIGN_ROW = listOf(
    KeyDef("া"), KeyDef("ি", "ী"), KeyDef("ু", "ূ"), KeyDef("ে", "ৈ"),
    KeyDef("ো", "ৌ"), KeyDef("্র", "ৃ"), KeyDef("্য"),
)

/**
 * Should the vowel row show vowel signs? Yes when the text before the cursor ends in a consonant
 * (a nukta or a conjunct still counts), because a consonant is what a vowel sign attaches to.
 * After a vowel, a vowel sign, hasanta, a space or anything else, full vowels are more useful.
 */
fun wantsVowelSign(before: CharSequence): Boolean {
    var i = before.length - 1
    if (i >= 0 && before[i] == '\u09BC') i-- // nukta belongs to the consonant before it (ড় ঢ় য়)
    if (i < 0) return false
    val c = before[i]
    return c in '\u0995'..'\u09B9' || c == '\u09DC' || c == '\u09DD' || c == '\u09DF'
}

/** The keys shown for [layer]. In numeric fields Layer 2 swaps digits so ASCII is the tap and Bangla the hold. */
fun keysFor(layer: Layer, numeric: Boolean): List<KeyDef> = when (layer) {
    Layer.L1 -> LAYER1_KEYS
    Layer.L2 -> if (!numeric) LAYER2_KEYS else LAYER2_KEYS.map { k ->
        val alt = k.longPress
        if (alt != null && alt.length == 1 && alt[0] in '0'..'9') KeyDef(alt, k.primary) else k
    }
    Layer.L3 -> LAYER3_KEYS
}
