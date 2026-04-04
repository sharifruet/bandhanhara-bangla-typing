package com.bandhanhara.bangla

data class KeyDef(val primary: String, val longPress: String? = null)

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
    // Row 5: vowel signs
    KeyDef("া"), KeyDef("ি", "ী"), KeyDef("ু", "ূ"),
    KeyDef("ে", "ৈ"), KeyDef("ো", "ৌ"), KeyDef("্"),
)

val LAYER2_KEYS = listOf(
    KeyDef("ঙ"), KeyDef("ঞ"), KeyDef("ষ"), KeyDef("ণ"), KeyDef("ৎ"), KeyDef("ঽ"),
    KeyDef("ং"), KeyDef("ঃ"), KeyDef("ঁ"), KeyDef("ঢ়"), KeyDef("ড়"), KeyDef("য়"),
    KeyDef("ক্ষ"), KeyDef("জ্ঞ"), KeyDef("্র"), KeyDef("র্"), KeyDef("্য"), KeyDef("্ব"),
    KeyDef("০"), KeyDef("১"), KeyDef("২"), KeyDef("৩"), KeyDef("৪"), KeyDef("৫"),
    KeyDef("৬"), KeyDef("৭"), KeyDef("৮"), KeyDef("৯"), KeyDef("৳"), KeyDef("%"),
)

val LAYER3_KEYS = listOf(
    KeyDef("("), KeyDef(")"), KeyDef("\u201C"), KeyDef("\u201D"), KeyDef("'"), KeyDef("'"),
    KeyDef("@"), KeyDef("#"), KeyDef("-"), KeyDef("–"), KeyDef("…"), KeyDef("/"),
    KeyDef("*"), KeyDef("&"), KeyDef("^"), KeyDef("~"), KeyDef("?"), KeyDef("!"),
)

const val COLS = 6
