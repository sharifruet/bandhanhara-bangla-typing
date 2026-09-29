package com.bandhanhara.bangla

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Keyboard settings. Opened from the keyboard's toolbar (⚙), the setup screen, and the gear next
 * to the keyboard in the system's keyboard list. Changes apply the next time the keyboard opens.
 */
class SettingsActivity : Activity() {

    private val prefs by lazy { Prefs(this) }
    private val suggester by lazy { Suggester.get(this) }
    private lateinit var content: LinearLayout
    private lateinit var learnSummary: TextView
    private lateinit var learnWords: TextView

    private fun dp(v: Float) = (v * resources.displayMetrics.density).roundToInt()
    private val bangla by lazy { resources.getFont(R.font.noto_sans_bengali) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.setup_bg)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(28f))
        }
        scroll.addView(content)
        setContentView(scroll)
        // Edge-to-edge: pad for the status and navigation bars ourselves.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
        build()
    }

    override fun onResume() {
        super.onResume()
        suggester.whenReady(::refreshLearning)
    }

    private fun build() {
        // Title bar
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_left)
            setColorFilter(getColor(R.color.setup_text))
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            contentDescription = getString(android.R.string.cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        bar.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(dp(34f), dp(34f)).apply {
            marginStart = dp(4f); marginEnd = dp(12f)
        })
        bar.addView(text(getString(R.string.settings_title), 22f, R.color.setup_text, bold = true))
        content.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)))

        // Try it
        section(R.string.set_try).addView(EditText(this).apply {
            hint = getString(R.string.step3_hint)
            typeface = bangla
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(getColor(R.color.setup_text))
            setHintTextColor(getColor(R.color.setup_muted))
            background = getDrawable(R.drawable.bg_field)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines = 2
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f)
        })

        // Appearance
        section(R.string.set_appearance).apply {
            addView(choice(R.string.set_theme, listOf(R.string.set_theme_system, R.string.set_theme_light, R.string.set_theme_dark),
                { prefs.theme.ordinal }, { prefs.theme = ThemeMode.entries[it] }))
            addView(choice(R.string.set_height, listOf(R.string.set_height_short, R.string.set_height_normal, R.string.set_height_tall),
                { HEIGHTS.indexOfFirst { h -> h == prefs.heightScale }.coerceAtLeast(1) }, { prefs.heightScale = HEIGHTS[it] }))
            addView(toggle(R.string.set_hints, R.string.set_hints_sum, { prefs.showHints }, { prefs.showHints = it }))
        }

        // Typing
        section(R.string.set_typing).apply {
            addView(choice(R.string.set_globe, listOf(R.string.set_globe_language, R.string.set_globe_keyboard),
                { prefs.globeAction.ordinal }, { prefs.globeAction = GlobeAction.entries[it] }))
            addView(toggle(R.string.set_suggest, R.string.set_suggest_sum, { prefs.suggestions }, { prefs.suggestions = it }))
            addView(toggle(R.string.set_auto_space, R.string.set_auto_space_sum, { prefs.autoSpace }, { prefs.autoSpace = it }))
            addView(toggle(R.string.set_double_space, R.string.set_double_space_sum, { prefs.doubleSpacePeriod }, { prefs.doubleSpacePeriod = it }))
            addView(toggle(R.string.set_auto_caps, R.string.set_auto_caps_sum, { prefs.autoCaps }, { prefs.autoCaps = it }))
            addView(choice(R.string.set_long_press, listOf(R.string.set_long_press_short, R.string.set_long_press_normal, R.string.set_long_press_long),
                { LONG_PRESS.indexOf(prefs.longPressMs).coerceAtLeast(1) }, { prefs.longPressMs = LONG_PRESS[it] }))
        }

        // Feedback
        section(R.string.set_feedback).apply {
            addView(toggle(R.string.set_vibrate, null, { prefs.vibrate }, { prefs.vibrate = it }))
            addView(toggle(R.string.set_sound, null, { prefs.sound }, { prefs.sound = it }))
            addView(toggle(R.string.set_preview, R.string.set_preview_sum, { prefs.keyPreview }, { prefs.keyPreview = it }))
        }

        // Learning
        section(R.string.learn_title).apply {
            addView(text(getString(R.string.learn_desc), 14f, R.color.setup_muted))
            learnSummary = text("", 15f, R.color.setup_text, bold = true)
            addView(learnSummary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            learnWords = text("", 16f, R.color.setup_text)
            addView(learnWords)
            addView(toggle(R.string.learn_switch, null, { suggester.learningEnabled }, { suggester.learningEnabled = it }))
            addView(text(getString(R.string.learn_clear), 15f, R.color.accent, bold = true).apply {
                setPadding(0, dp(12f), 0, dp(6f))
                setOnClickListener { confirmClear() }
            })
        }

        // About
        section(R.string.set_about).apply {
            val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            addView(text(getString(R.string.set_version, version), 15f, R.color.setup_text))
            addView(text(getString(R.string.set_credits), 13f, R.color.setup_muted).apply { setPadding(0, dp(6f), 0, 0) })
            addView(text(getString(R.string.set_open_setup), 15f, R.color.accent, bold = true).apply {
                setPadding(0, dp(14f), 0, dp(4f))
                setOnClickListener { startActivity(Intent(this@SettingsActivity, SetupActivity::class.java)) }
            })
        }
    }

    // ── Building blocks ────────────────────────────────────────────────────────

    private fun text(s: String, sizeSp: Float, colorRes: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(getColor(colorRes))
        if (s.any { it in 'ঀ'..'৿' }) typeface = bangla
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** A titled card; returns its body to add rows to. */
    private fun section(titleRes: Int): LinearLayout {
        content.addView(text(getString(titleRes), 14f, R.color.accent, bold = true).apply {
            setPadding(dp(4f), dp(18f), 0, dp(8f))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        }
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun row(titleRes: Int, summary: String?, trailing: View?, onClick: (() -> Unit)?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(0, dp(6f), 0, dp(6f))
            if (onClick != null) {
                isClickable = true
                val tv = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { onClick() }
            }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(getString(titleRes), 16f, R.color.setup_text))
        if (summary != null) texts.addView(text(summary, 13f, R.color.setup_muted))
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) row.addView(trailing)
        return row
    }

    private fun toggle(titleRes: Int, summaryRes: Int?, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val sw = Switch(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, on -> set(on) }
        }
        return row(titleRes, summaryRes?.let(::getString), sw) { sw.toggle() }
    }

    private fun choice(titleRes: Int, options: List<Int>, get: () -> Int, set: (Int) -> Unit): View {
        lateinit var view: View
        fun build(): View = row(titleRes, getString(options[get()]), null) {
            AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(options.map { getString(it) }.toTypedArray(), get()) { d, which ->
                    set(which)
                    d.dismiss()
                    val parent = view.parent as LinearLayout
                    val i = parent.indexOfChild(view)
                    parent.removeViewAt(i)
                    view = build()
                    parent.addView(view, i)
                }
                .show()
        }
        view = build()
        return view
    }

    private fun refreshLearning() {
        val n = suggester.learnedWordCount
        if (n == 0) {
            learnSummary.setText(R.string.learn_none)
            learnWords.visibility = View.GONE
        } else {
            learnSummary.text = resources.getQuantityString(R.plurals.learn_count, n, n)
            learnWords.text = suggester.topLearnedWords(12).joinToString("  ·  ")
            learnWords.visibility = View.VISIBLE
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.learn_clear_confirm_title)
            .setMessage(R.string.learn_clear_confirm_body)
            .setPositiveButton(R.string.learn_clear_confirm_ok) { _, _ ->
                suggester.clearLearned()
                refreshLearning()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        val HEIGHTS = listOf(0.88f, 1.0f, 1.12f)
        val LONG_PRESS = listOf(250L, 320L, 450L)
    }
}
