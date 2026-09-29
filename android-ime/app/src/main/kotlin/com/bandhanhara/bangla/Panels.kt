package com.bandhanhara.bangla

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Colours and sizes shared by the panels that replace the keys (emoji, clipboard). */
private class PanelStyle(val context: Context) {
    val density = context.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).roundToInt()
    val bg = context.getColor(R.color.kb_bg)
    val key = context.getColor(R.color.kb_key)
    val keyPressed = context.getColor(R.color.kb_key_pressed)
    val action = context.getColor(R.color.kb_action)
    val text = context.getColor(R.color.kb_text)
    val hint = context.getColor(R.color.kb_hint)
    val accent = context.getColor(R.color.kb_accent)
    val bangla = context.resources.getFont(R.font.noto_sans_bengali)

    fun rounded(color: Int, radiusDp: Float = 6f): Drawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * density }

    /** A key-like button: pressed state, feedback, and an optional repeat while held. */
    @SuppressLint("ClickableViewAccessibility")
    fun button(view: View, color: Int, repeat: Boolean = false, onTap: () -> Unit): View {
        view.background = rounded(color)
        view.isClickable = true
        val handler = Handler(Looper.getMainLooper())
        var repeater: Runnable? = null
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = rounded(keyPressed)
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (repeat) {
                        onTap()
                        repeater = object : Runnable {
                            override fun run() { onTap(); handler.postDelayed(this, 50) }
                        }.also { handler.postDelayed(it, 400) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = rounded(color)
                    repeater?.let { handler.removeCallbacks(it) }
                    repeater = null
                    if (!repeat && e.actionMasked == MotionEvent.ACTION_UP) onTap()
                }
            }
            true
        }
        return view
    }

    fun label(text: String, sizeSp: Float, color: Int = this.text) = TextView(context).apply {
        this.text = text
        typeface = bangla
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
    }

    fun icon(id: Int, tint: Int = text) = ImageView(context).apply {
        setImageResource(id)
        setColorFilter(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = dp(10f)
        setPadding(p, p, p, p)
    }
}

/** Bottom bar shared by the panels: [back to letters] [space] [⌫]. */
private fun bottomBar(
    s: PanelStyle, lettersLabel: String, onBack: () -> Unit, onSpace: (() -> Unit)?, onBackspace: (() -> Unit)?,
): LinearLayout = LinearLayout(s.context).apply {
    orientation = LinearLayout.HORIZONTAL
    val m = s.dp(3f)
    fun lp(weight: Float) = LinearLayout.LayoutParams(0, s.dp(46f), weight).apply { setMargins(m, m, m, m) }
    addView(s.button(s.label(lettersLabel, 17f), s.action, onTap = onBack), lp(1.4f))
    if (onSpace != null) addView(s.button(s.label(" ", 16f), s.key, onTap = onSpace), lp(4f))
    else addView(View(s.context), lp(4f))
    if (onBackspace != null) addView(s.button(s.icon(R.drawable.ic_backspace), s.action, repeat = true, onTap = onBackspace), lp(1.4f))
    else addView(View(s.context), lp(1.4f))
}

// ── Emoji ─────────────────────────────────────────────────────────────────────

/** Emoji data from assets/emoji.tsv, filtered to what this phone's font can draw. Loaded once. */
object EmojiData {
    const val CATEGORIES = 9
    /** Tab icons: recent, then the nine Unicode groups. */
    val TAB_ICONS = listOf("🕘", "😀", "👋", "🐻", "🍔", "🚗", "⚽", "💡", "❤️", "🏳️")

    @Volatile var byCategory: List<List<String>>? = null
        private set
    private val waiting = ArrayList<() -> Unit>()
    private var loading = false

    fun load(context: Context, onReady: () -> Unit) {
        byCategory?.let { onReady(); return }
        waiting += onReady
        if (loading) return
        loading = true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Executors.newSingleThreadExecutor().execute {
            val paint = Paint()
            val cats = List(CATEGORIES) { ArrayList<String>() }
            app.assets.open("emoji.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val cat = line.substring(0, tab).toIntOrNull() ?: continue
                    val e = line.substring(tab + 1)
                    if (cat in 0 until CATEGORIES && paint.hasGlyph(e)) cats[cat] += e
                }
            }
            main.post {
                byCategory = cats
                waiting.forEach { it() }
                waiting.clear()
            }
        }
    }
}

/** The emoji panel: category tabs, a scrolling grid, and the bottom bar. */
class EmojiPanel(
    context: Context,
    private val prefs: Prefs,
    lettersLabel: String,
    private val onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onBack: () -> Unit,
) : LinearLayout(context) {

    private val s = PanelStyle(context)
    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val grid = GridView(context).apply {
        columnWidth = s.dp(46f)
        numColumns = GridView.AUTO_FIT
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        verticalSpacing = 0
        selector = s.rounded(s.keyPressed, 10f)
        isVerticalScrollBarEnabled = false
    }
    private val empty = s.label(context.getString(R.string.emoji_recent_empty), 14f, s.hint)
    private var current: List<String> = emptyList()
    private var category = 1
    private val adapter by lazy { emojiAdapter() }

    init {
        orientation = VERTICAL
        setBackgroundColor(s.bg)
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, s.dp(44f)))
        val body = FrameLayout(context)
        body.addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomBar(s, lettersLabel, onBack, onSpace, onBackspace))

        EmojiData.TAB_ICONS.forEachIndexed { i, icon ->
            val t = s.label(icon, 20f)
            t.setOnClickListener { select(i) }
            tabs.addView(t, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { v, _, pos, _ ->
            val e = current.getOrNull(pos) ?: return@setOnItemClickListener
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            remember(e)
            onEmoji(e)
        }
        // Open on recents if there are any.
        category = if (prefs.recentEmoji.isNotEmpty()) 0 else 1
        EmojiData.load(context) { select(category) }
        select(category)
    }

    private fun select(i: Int) {
        category = i
        for (t in 0 until tabs.childCount) {
            tabs.getChildAt(t).background = if (t == i) s.rounded(ColorUtils.setAlphaComponent(s.accent, 0x33), 10f) else null
        }
        current = if (i == 0) prefs.recentEmoji else EmojiData.byCategory?.getOrNull(i - 1).orEmpty()
        empty.visibility = if (i == 0 && current.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
    }

    private fun remember(e: String) {
        prefs.recentEmoji = (listOf(e) + prefs.recentEmoji.filter { it != e }).take(MAX_RECENT)
    }

    private fun emojiAdapter() = object : BaseAdapter() {
        override fun getCount() = current.size
        override fun getItem(position: Int) = current[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.dp(48f))
            }
            v.text = current[position]
            return v
        }
    }

    private companion object {
        const val MAX_RECENT = 40
    }
}

// ── Clipboard ─────────────────────────────────────────────────────────────────

/** Recently copied text, kept in memory only and forgotten after an hour. */
class ClipHistory {
    private class Clip(val text: String, val time: Long)
    private val clips = ArrayList<Clip>()

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        clips.removeAll { it.text == t }
        clips.add(0, Clip(t, System.currentTimeMillis()))
        while (clips.size > MAX) clips.removeAt(clips.lastIndex)
    }

    fun items(): List<String> {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        clips.removeAll { it.time < cutoff }
        return clips.map { it.text }
    }

    /** The newest clip, if it was copied within the last [withinMs]. */
    fun fresh(withinMs: Long): String? = clips.firstOrNull()?.takeIf { System.currentTimeMillis() - it.time < withinMs }?.text

    fun remove(text: String) { clips.removeAll { it.text == text } }
    fun clear() = clips.clear()

    private companion object {
        const val MAX = 20
        const val KEEP_MS = 60 * 60 * 1000L
    }
}

/** The clipboard panel: tap a clip to paste it. */
class ClipboardPanel(
    context: Context,
    private val history: ClipHistory,
    lettersLabel: String,
    private val onPaste: (String) -> Unit,
    onBack: () -> Unit,
) : LinearLayout(context) {

    private val s = PanelStyle(context)
    private val list = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(s.bg)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(s.dp(14f), 0, s.dp(6f), 0)
        }
        header.addView(
            s.label(context.getString(R.string.clipboard_title), 15f, s.hint).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL },
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f),
        )
        header.addView(
            s.label(context.getString(R.string.clipboard_clear), 14f, s.accent).apply {
                setPadding(s.dp(12f), 0, s.dp(12f), 0)
                setOnClickListener { history.clear(); refresh() }
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, s.dp(44f)))
        addView(ScrollView(context).apply { addView(list) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomBar(s, lettersLabel, onBack, null, null))
        refresh()
    }

    fun refresh() {
        list.removeAllViews()
        val items = history.items()
        if (items.isEmpty()) {
            list.addView(
                s.label(context.getString(R.string.clipboard_empty), 14f, s.hint).apply { setPadding(s.dp(24f), s.dp(32f), s.dp(24f), s.dp(32f)) },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
            )
            return
        }
        for (text in items) {
            val row = TextView(context).apply {
                this.text = text
                typeface = s.bangla
                setTextColor(s.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(s.dp(14f), s.dp(10f), s.dp(14f), s.dp(10f))
                background = s.rounded(s.key, 10f)
                setOnClickListener { onPaste(text) }
                setOnLongClickListener { history.remove(text); refresh(); true }
            }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(s.dp(8f), s.dp(4f), s.dp(8f), s.dp(4f))
            })
        }
    }
}
