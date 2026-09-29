package com.bandhanhara.bangla

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

/** The keyboard's two built-in languages. */
enum class Language(val code: String) { BANGLA("bn"), ENGLISH("en") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What a tap on 🌐 does. A long-press always opens the system keyboard picker. */
enum class GlobeAction { SWITCH_LANGUAGE, SWITCH_KEYBOARD }

/**
 * User settings, shared by the keyboard service and the settings screen.
 * Everything has a sensible default, so a fresh install works without visiting settings.
 */
class Prefs(context: Context) {

    val sp: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Appearance
    var theme: ThemeMode
        get() = enumOr(sp.getString(K_THEME, null), ThemeMode.SYSTEM)
        set(v) = sp.edit().putString(K_THEME, v.name).apply()
    /** Key-height multiplier: 0.88 short, 1.0 normal, 1.12 tall. */
    var heightScale: Float
        get() = sp.getFloat(K_HEIGHT, 1.0f)
        set(v) = sp.edit().putFloat(K_HEIGHT, v).apply()
    var showHints: Boolean
        get() = sp.getBoolean(K_HINTS, true)
        set(v) = sp.edit().putBoolean(K_HINTS, v).apply()

    // Typing
    var suggestions: Boolean
        get() = sp.getBoolean(K_SUGGEST, true)
        set(v) = sp.edit().putBoolean(K_SUGGEST, v).apply()
    var autoSpace: Boolean
        get() = sp.getBoolean(K_AUTO_SPACE, true)
        set(v) = sp.edit().putBoolean(K_AUTO_SPACE, v).apply()
    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean(K_DOUBLE_SPACE, true)
        set(v) = sp.edit().putBoolean(K_DOUBLE_SPACE, v).apply()
    var autoCaps: Boolean
        get() = sp.getBoolean(K_AUTO_CAPS, true)
        set(v) = sp.edit().putBoolean(K_AUTO_CAPS, v).apply()
    var longPressMs: Long
        get() = sp.getLong(K_LONG_PRESS, 320L)
        set(v) = sp.edit().putLong(K_LONG_PRESS, v).apply()
    var globeAction: GlobeAction
        get() = enumOr(sp.getString(K_GLOBE, null), GlobeAction.SWITCH_LANGUAGE)
        set(v) = sp.edit().putString(K_GLOBE, v.name).apply()

    // Feedback
    var vibrate: Boolean
        get() = sp.getBoolean(K_VIBRATE, true)
        set(v) = sp.edit().putBoolean(K_VIBRATE, v).apply()
    var sound: Boolean
        get() = sp.getBoolean(K_SOUND, false)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()
    var keyPreview: Boolean
        get() = sp.getBoolean(K_PREVIEW, true)
        set(v) = sp.edit().putBoolean(K_PREVIEW, v).apply()

    // State
    var language: Language
        get() = enumOr(sp.getString(K_LANGUAGE, null), Language.BANGLA)
        set(v) = sp.edit().putString(K_LANGUAGE, v.name).apply()
    var recentEmoji: List<String>
        get() = sp.getString(K_RECENT_EMOJI, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(v) = sp.edit().putString(K_RECENT_EMOJI, v.joinToString("\n")).apply()

    /** Settings that change how the keyboard looks; changing one means rebuilding the view. */
    fun appearanceKey() = "$theme|$heightScale|$showHints|$keyPreview"

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val FILE = "bandhanhara"
        const val K_THEME = "theme"
        const val K_HEIGHT = "height_scale"
        const val K_HINTS = "show_hints"
        const val K_SUGGEST = "suggestions"
        const val K_AUTO_SPACE = "auto_space"
        const val K_DOUBLE_SPACE = "double_space_period"
        const val K_AUTO_CAPS = "auto_caps"
        const val K_LONG_PRESS = "long_press_ms"
        const val K_GLOBE = "globe_action"
        const val K_VIBRATE = "vibrate"
        const val K_SOUND = "sound"
        const val K_PREVIEW = "key_preview"
        const val K_LANGUAGE = "language"
        const val K_RECENT_EMOJI = "recent_emoji"
    }
}

/** A context whose resources resolve light or dark colours according to the user's theme choice. */
fun themedContext(base: Context, mode: ThemeMode): Context {
    if (mode == ThemeMode.SYSTEM) return base
    val config = Configuration(base.resources.configuration)
    val night = if (mode == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    return base.createConfigurationContext(config)
}
