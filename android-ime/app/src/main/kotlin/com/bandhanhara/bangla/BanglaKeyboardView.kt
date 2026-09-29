package com.bandhanhara.bangla

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Layer { L1, L2, L3 }
enum class ShiftMode { NORMAL, ONESHOT, LOCKED }
enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, DONE }

/**
 * The keyboard: one custom-drawn View.
 *
 * Layout, top to bottom:
 *  - a strip: toolbar button + three word suggestions (or a clipboard chip, or the toolbar:
 *    emoji · clipboard · voice · settings),
 *  - character rows: Bangla 6 × 5, English QWERTY with a number row, or the shared symbol layer,
 *  - a control row: ⇧ · !?# · 🌐 · space · ।/. · ⌫ · ↵.
 *
 * The character area has the same height in every layout, so switching language or layer never
 * makes the keyboard jump. Sizes derive from the view width (and a cap on screen height) times the
 * user's height setting.
 *
 * Gestures: long-press for a key's alternate; hold ⌫ to repeat (speeding up to whole words);
 * drag along the space bar to move the cursor; a second finger commits the first key at once.
 */
class BanglaKeyboardView(context: Context, private val prefs: Prefs) : View(context) {

    // ── Callbacks, wired by the IME service ────────────────────────────────────
    var onChar: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onDeleteWord: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onGlobe: (() -> Unit)? = null
    var onShowImePicker: (() -> Unit)? = null
    var onSuggestion: ((String) -> Unit)? = null
    var onCursorMove: ((Int) -> Unit)? = null
    var onEmoji: (() -> Unit)? = null
    var onClipboard: (() -> Unit)? = null
    var onVoice: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onPasteClip: (() -> Unit)? = null

    // ── Keyboard state ─────────────────────────────────────────────────────────
    var language = Language.BANGLA
        private set
    private var layer = Layer.L1
    private var shiftMode = ShiftMode.NORMAL
    /** ⇧ was turned on automatically (English sentence start), not by the user. */
    private var autoShifted = false
    private var enterAction = EnterAction.NEWLINE
    private var numericMode = false
    /** Bangla vowel row: showing vowel signs (true) or full vowels (false). */
    private var vowelSigns = false
    /** The user flipped the vowel row by hand; automatic switching waits until the next character. */
    private var vowelManual = false

    private var suggestions: List<String> = emptyList()
    private var clipChip: String? = null
    private var toolbarOpen = false

    // Settings snapshot (the service rebuilds the view when appearance settings change).
    private val showHints = prefs.showHints
    private val showPreview = prefs.keyPreview
    private val longPressMs = prefs.longPressMs
    private val heightScale = prefs.heightScale

    // ── Key model ──────────────────────────────────────────────────────────────
    private enum class Action {
        SHIFT, SYMBOLS, GLOBE, SPACE, BACKSPACE, ENTER, VOWEL_TOGGLE,
        SUGGESTION, CLIP_CHIP, TOOLBAR_TOGGLE, TOOL_EMOJI, TOOL_CLIPBOARD, TOOL_VOICE, TOOL_SETTINGS,
    }

    /**
     * A laid-out key. [rect] is what is drawn; [hit] is the touch target (includes the gaps).
     * [slot] is the suggestion rank for suggestion cells; [textSize] the label size for character keys.
     */
    private class KeyItem(
        val rect: RectF, val hit: RectF, val def: KeyDef?, val action: Action?,
        val slot: Int = -1, val textSize: Float = 0f,
    )

    private val keys = ArrayList<KeyItem>(64)

    // ── Metrics (px) ───────────────────────────────────────────────────────────
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val gapX = dp(2.5f)
    private val gapY = dp(2.5f)
    private val padX = dp(3f)
    private val padTop = dp(4f)
    private val padBottom = dp(3f)
    private val radius = dp(6f)
    private val shadowDy = dp(1f)
    private val stripH = dp(38f)
    private var keyH = 0f          // height of a key in the 5-row Bangla grid
    private var unitW = 0f         // one unit of the control row
    private var fontSize = 0f
    private var hintSize = 0f
    private var labelSize = 0f
    private var iconSize = 0f
    private var labelBaseline = 0f
    private var suggestionSize = 0f
    var bottomInset = 0
        private set

    // ── Colours ────────────────────────────────────────────────────────────────
    private val cBg = context.getColor(R.color.kb_bg)
    private val cKey = context.getColor(R.color.kb_key)
    private val cKeyPressed = context.getColor(R.color.kb_key_pressed)
    private val cAction = context.getColor(R.color.kb_action)
    private val cActionPressed = context.getColor(R.color.kb_action_pressed)
    private val cText = context.getColor(R.color.kb_text)
    private val cHint = context.getColor(R.color.kb_hint)
    private val cShadow = context.getColor(R.color.kb_key_shadow)
    private val cAccent = context.getColor(R.color.kb_accent)
    private val cAccentPressed = ColorUtils.blendARGB(cAccent, Color.BLACK, 0.18f)
    private val cOnAccent = context.getColor(R.color.kb_on_accent)
    private val cPreview = context.getColor(R.color.kb_preview)
    private val cPreviewBorder = context.getColor(R.color.kb_preview_border)
    private val cChip = ColorUtils.setAlphaComponent(cAccent, 0x2A)

    // ── Paint & assets ─────────────────────────────────────────────────────────
    private val bangla: Typeface = resources.getFont(R.font.noto_sans_bengali)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bangla; textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bangla; textAlign = Paint.Align.RIGHT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bangla; textAlign = Paint.Align.CENTER
    }
    private val suggestionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bangla; textAlign = Paint.Align.CENTER
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = bangla; textAlign = Paint.Align.LEFT
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }

    private fun icon(id: Int): Drawable = context.getDrawable(id)!!.mutate()
    private val icBackspace = icon(R.drawable.ic_backspace)
    private val icReturn = icon(R.drawable.ic_return)
    private val icShift = icon(R.drawable.ic_shift)
    private val icShiftFilled = icon(R.drawable.ic_shift_filled)
    private val icShiftLocked = icon(R.drawable.ic_shift_locked)
    private val icGlobe = icon(R.drawable.ic_globe)
    private val icSearch = icon(R.drawable.ic_search)
    private val icSend = icon(R.drawable.ic_send)
    private val icCheck = icon(R.drawable.ic_check)
    private val icArrowForward = icon(R.drawable.ic_arrow_forward)
    private val icApps = icon(R.drawable.ic_apps)
    private val icChevronLeft = icon(R.drawable.ic_chevron_left)
    private val icEmoji = icon(R.drawable.ic_emoji)
    private val icClipboard = icon(R.drawable.ic_clipboard)
    private val icMic = icon(R.drawable.ic_mic)
    private val icSettings = icon(R.drawable.ic_settings)

    private val symbolsLabel = context.getString(R.string.kb_symbols_label)

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    // ── Touch state ────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var activePointerId = -1
    private var pressed: KeyItem? = null
    private var longPressFired = false
    private var backspaceFiredOnDown = false
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private var downX = 0f
    private var cursorMode = false
    private var cursorAnchorX = 0f

    // ── Key preview popup ──────────────────────────────────────────────────────
    private val preview = KeyPreview(context, bangla, cPreview, cText, cPreviewBorder, radius, dp(1f))
    private val previewWindow = PopupWindow(
        preview, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        isTouchable = false
        isFocusable = false
        isClippingEnabled = false
        elevation = dp(8f)
        animationStyle = 0
    }

    init {
        isHapticFeedbackEnabled = true
        language = prefs.language
        // On edge-to-edge systems the IME window extends under the navigation bar; pad for it.
        setOnApplyWindowInsetsListener { _, insets ->
            applyBottomInset(insets)
            insets
        }
    }

    /**
     * A view swapped in with setInputView() (after a settings change) isn't sent the window insets
     * again, so read them from the window as soon as we're attached.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootWindowInsets?.let(::applyBottomInset)
        requestApplyInsets()
    }

    private fun applyBottomInset(insets: WindowInsets) {
        // The IME's own nav bar (hide chevron + switcher) is a tappable element taller than the
        // gesture-bar inset, so take whichever is larger.
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            max(
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsets.Type.tappableElement()).bottom,
            )
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        val clamped = bottom.coerceIn(0, dp(56f).roundToInt())
        if (clamped != bottomInset) {
            bottomInset = clamped
            requestLayout()
        }
    }

    // ── Public API (used by the service) ───────────────────────────────────────

    /** Called whenever a field gains focus. */
    fun configure(enterAction: EnterAction, numeric: Boolean, resetLayer: Boolean) {
        this.enterAction = enterAction
        numericMode = numeric
        if (resetLayer) {
            layer = if (numeric) Layer.L2 else Layer.L1
            shiftMode = if (numeric) ShiftMode.LOCKED else ShiftMode.NORMAL
            autoShifted = false
            toolbarOpen = false
            vowelManual = false
        }
        rebuildKeys()
    }

    fun setLanguage(lang: Language) {
        if (lang == language) return
        language = lang
        layer = Layer.L1
        shiftMode = ShiftMode.NORMAL
        autoShifted = false
        rebuildKeys()
    }

    /** English sentence-start capitals: the service says whether the next letter should be a capital. */
    fun setAutoCaps(on: Boolean) {
        if (language != Language.ENGLISH || numericMode) return
        if (on && layer == Layer.L1) {
            autoShifted = true
            setLayer(Layer.L2, ShiftMode.ONESHOT)
        } else if (!on && autoShifted && layer == Layer.L2 && shiftMode == ShiftMode.ONESHOT) {
            autoShifted = false
            setLayer(Layer.L1, ShiftMode.NORMAL)
        }
    }

    /**
     * The service says what the text before the cursor wants: vowel signs after a consonant, full
     * vowels otherwise. Ignored while the user has flipped the row by hand.
     */
    fun setAutoVowelSigns(signs: Boolean) {
        if (vowelManual || signs == vowelSigns) return
        vowelSigns = signs
        if (language == Language.BANGLA && layer == Layer.L1 && !numericMode) rebuildKeys()
    }

    /** Replace the words shown in the suggestion strip (best first, up to three). */
    fun setSuggestions(words: List<String>) {
        val next = words.take(SUGGESTION_SLOTS)
        if (next == suggestions) return
        suggestions = next
        invalidate()
    }

    /** Show (or hide, with null) a "paste what you just copied" chip in the strip. */
    fun setClipChip(text: String?) {
        if (text == clipChip) return
        val layoutChanged = (text == null) != (clipChip == null)
        clipChip = text
        if (layoutChanged) rebuildKeys() else invalidate()
    }

    fun closeToolbar() {
        if (!toolbarOpen) return
        toolbarOpen = false
        rebuildKeys()
    }

    /** Drop any in-progress press (keyboard hidden, field lost, …). */
    fun cancelInteraction() {
        cancelTimers()
        hidePreview()
        pressed = null
        activePointerId = -1
        longPressFired = false
        cursorMode = false
        invalidate()
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private fun computeMetrics(w: Int) {
        val dm = resources.displayMetrics
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val innerW = w - padX * 2
        val keyW = innerW / COLS - gapX * 2
        unitW = innerW / CONTROL_UNITS - gapX * 2

        // Never let the keyboard eat more than ~38 % of the screen (52 % in landscape).
        val maxTotal = dm.heightPixels * (if (landscape) 0.52f else 0.38f) * heightScale
        val maxKeyH = (maxTotal - stripH - padTop - padBottom) / (CHAR_AREA + 1) - gapY * 2
        keyH = min(keyW * 0.66f * heightScale, maxKeyH).coerceAtLeast(dp(34f))

        suggestionSize = dp(19f)
        chipPaint.textSize = dp(15f)
        dividerPaint.color = ColorUtils.setAlphaComponent(cHint, 0x55)

        fontSize = keyH * 0.58f
        hintSize = max(dp(9f), fontSize * 0.36f)
        labelSize = fontSize * 0.55f
        iconSize = (keyH * 0.42f).coerceIn(dp(16f), dp(30f))

        hintPaint.textSize = hintSize
        labelPaint.textSize = labelSize
        labelBaseline = baselineOffset(labelPaint, "ক")
    }

    private fun contentHeight() = stripH + padTop + (CHAR_AREA + 1) * (keyH + gapY * 2) + padBottom

    /** Total height of the keyboard, used by the emoji and clipboard panels to match it. */
    val keyboardHeight: Int get() = (contentHeight() + bottomInset).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        computeMetrics(w)
        setMeasuredDimension(w, (contentHeight() + bottomInset).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeMetrics(w)
        rebuildKeys()
    }

    private fun rebuildKeys() {
        keys.clear()
        if (width == 0) {
            invalidate()
            return
        }
        val w = width.toFloat()
        buildStrip(w)

        // Character rows share a fixed area, whatever their count.
        val rows = rowsFor(language, layer, numericMode, vowelSigns)
        val area = CHAR_AREA * (keyH + gapY * 2)
        val pitch = area / rows.size
        val rowKeyH = pitch - gapY * 2
        val maxCols = max(COLS, rows.maxOf { it.size })
        // English staggers its shorter rows (QWERTY); every other layout fills each row edge to edge.
        val stagger = language == Language.ENGLISH && layer != Layer.L3 && !numericMode
        var top = stripH + padTop
        for (row in rows) {
            // The vowel/sign switch key is narrower than a letter so the vowel row fits 7 letters.
            val weights = row.map { if (it.primary == VOWEL_TOGGLE_ID) TOGGLE_WEIGHT else 1f }
            val units = if (stagger) maxCols.toFloat() else weights.sum()
            val unitW = (w - padX * 2) / units
            val size = min(fontSize * (rowKeyH / keyH).coerceAtMost(1.1f), (unitW - gapX * 2) * 0.62f)
            var left = padX + (units - weights.sum()) * unitW / 2
            row.forEachIndexed { i, def ->
                val toggle = def.primary == VOWEL_TOGGLE_ID
                val colW = unitW * weights[i]
                val keyW = colW - gapX * 2
                val k = KeyItem(
                    rect = RectF(left + gapX, top + gapY, left + gapX + keyW, top + gapY + rowKeyH),
                    hit = RectF(left, top, left + colW, top + pitch),
                    def = if (toggle) null else def,
                    action = if (toggle) Action.VOWEL_TOGGLE else null,
                    textSize = size,
                )
                // Rows narrower than the widest one: the end keys also catch the empty margins.
                if (i == 0) k.hit.left = 0f
                if (i == row.lastIndex) k.hit.right = w
                keys += k
                left += colW
            }
            top += pitch
        }

        // Control row
        var left = padX
        for ((action, units) in CONTROL_ROW) {
            val cw = unitW * units + gapX * 2 * (units - 1)
            val def = if (action == null) punctuationKey() else null
            keys += KeyItem(
                rect = RectF(left + gapX, top + gapY, left + gapX + cw, top + gapY + keyH),
                hit = RectF(left, top, left + cw + gapX * 2, top + keyH + gapY * 2),
                def = def, action = action, textSize = fontSize,
            )
            left += cw + gapX * 2
        }

        // Edge keys also catch touches in the outer padding.
        val bottomEdge = contentHeight()
        for (k in keys) {
            if (k.hit.top < stripH) continue
            if (k.hit.left <= padX + 0.5f) k.hit.left = 0f
            if (k.hit.right >= w - padX - 0.5f) k.hit.right = w
            if (k.hit.top <= stripH + padTop + 0.5f) k.hit.top = stripH
            if (k.hit.bottom >= bottomEdge - padBottom - 0.5f) k.hit.bottom = bottomEdge + bottomInset
        }
        invalidate()
    }

    private fun buildStrip(w: Float) {
        fun cell(l: Float, r: Float, action: Action, slot: Int = -1) {
            val hit = RectF(l, 0f, r, stripH)
            keys += KeyItem(RectF(hit).apply { inset(dp(4f), dp(4f)) }, hit, null, action, slot)
        }
        cell(0f, stripH, Action.TOOLBAR_TOGGLE)
        val l = stripH
        when {
            toolbarOpen -> {
                val tools = listOf(Action.TOOL_EMOJI, Action.TOOL_CLIPBOARD, Action.TOOL_VOICE, Action.TOOL_SETTINGS)
                val cw = (w - l) / tools.size
                tools.forEachIndexed { i, a -> cell(l + i * cw, l + (i + 1) * cw, a) }
            }
            clipChip != null -> cell(l, w - dp(8f), Action.CLIP_CHIP)
            else -> {
                val cw = (w - l) / SUGGESTION_SLOTS
                for (c in 0 until SUGGESTION_SLOTS) cell(l + c * cw, l + (c + 1) * cw, Action.SUGGESTION, SLOT_FOR_CELL[c])
            }
        }
    }

    private fun punctuationKey() = if (language == Language.ENGLISH) KeyDef(".", ",") else KeyDef("।", ",")

    private fun setLayer(newLayer: Layer, newMode: ShiftMode) {
        if (layer == newLayer && shiftMode == newMode) return
        layer = newLayer
        shiftMode = newMode
        rebuildKeys()
    }

    // ── Drawing ────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        for (k in keys) drawKey(canvas, k)
    }

    private fun drawStripCell(canvas: Canvas, k: KeyItem) {
        val r = k.rect
        val isPressed = k === pressed
        when (k.action) {
            Action.SUGGESTION -> {
                if (k.hit.left > stripH + 1f) {
                    val inset = stripH * 0.28f
                    canvas.drawLine(k.hit.left, inset, k.hit.left, stripH - inset, dividerPaint)
                }
                val word = suggestions.getOrNull(k.slot) ?: return
                if (isPressed) {
                    fillPaint.color = cKeyPressed
                    canvas.drawRoundRect(r, radius, radius, fillPaint)
                }
                // Shrink long words to fit the cell rather than cutting them off.
                val maxW = r.width() - dp(8f)
                suggestionPaint.textSize = suggestionSize
                val tw = suggestionPaint.measureText(word)
                if (tw > maxW) suggestionPaint.textSize = suggestionSize * maxW / tw
                suggestionPaint.color = cText
                suggestionPaint.isFakeBoldText = k.slot == 0
                canvas.drawText(word, r.centerX(), r.centerY() + baselineOffset(suggestionPaint, refGlyph(word)), suggestionPaint)
            }
            Action.CLIP_CHIP -> {
                val text = clipChip ?: return
                fillPaint.color = if (isPressed) ColorUtils.setAlphaComponent(cAccent, 0x44) else cChip
                canvas.drawRoundRect(r, r.height() / 2, r.height() / 2, fillPaint)
                val s = (r.height() * 0.55f).roundToInt()
                icClipboard.setTint(cAccent)
                val il = (r.left + dp(10f)).roundToInt()
                val it = (r.centerY() - s / 2f).roundToInt()
                icClipboard.setBounds(il, it, il + s, it + s)
                icClipboard.draw(canvas)
                chipPaint.color = cText
                val start = il + s + dp(8f)
                val shown = TextUtils.ellipsize(
                    text.replace('\n', ' '), android.text.TextPaint(chipPaint), r.right - start - dp(12f), TextUtils.TruncateAt.END,
                ).toString()
                canvas.drawText(shown, start, r.centerY() + baselineOffset(chipPaint, "Hক"), chipPaint)
            }
            else -> {
                if (isPressed) {
                    fillPaint.color = cKeyPressed
                    canvas.drawRoundRect(r, radius, radius, fillPaint)
                }
                val d = when (k.action) {
                    Action.TOOLBAR_TOGGLE -> if (toolbarOpen) icChevronLeft else icApps
                    Action.TOOL_EMOJI -> icEmoji
                    Action.TOOL_CLIPBOARD -> icClipboard
                    Action.TOOL_VOICE -> icMic
                    Action.TOOL_SETTINGS -> icSettings
                    else -> return
                }
                drawIcon(canvas, d, r, cHint, dp(22f))
            }
        }
    }

    private fun drawKey(canvas: Canvas, k: KeyItem) {
        if (k.hit.top < stripH && k.hit.bottom <= stripH + 0.5f) {
            drawStripCell(canvas, k)
            return
        }
        val isPressed = k === pressed
        val r = k.rect
        val accent = when (k.action) {
            Action.SHIFT -> layer == Layer.L2 && !numericMode
            Action.ENTER -> enterAction != EnterAction.NEWLINE
            else -> false
        }
        val light = k.action == null || k.action == Action.SPACE
        val bg = when {
            accent -> if (isPressed) cAccentPressed else cAccent
            light -> if (isPressed) cKeyPressed else cKey
            else -> if (isPressed) cActionPressed else cAction
        }
        val fg = if (accent) cOnAccent else cText

        if (!isPressed) {
            fillPaint.color = cShadow
            canvas.drawRoundRect(r.left, r.top + shadowDy, r.right, r.bottom + shadowDy, radius, radius, fillPaint)
        }
        fillPaint.color = bg
        canvas.drawRoundRect(r, radius, radius, fillPaint)

        val def = k.def
        if (def != null) {
            textPaint.color = fg
            textPaint.textSize = k.textSize
            canvas.drawText(def.primary, r.centerX(), r.centerY() + baselineOffset(textPaint, refGlyph(def.primary)), textPaint)
            val hint = def.longPress
            if (hint != null && showHints) {
                hintPaint.color = if (accent) cOnAccent else cHint
                hintPaint.textSize = min(hintSize, r.width() * 0.3f)
                canvas.drawText(hint, r.right - dp(4f), r.top + hintPaint.textSize * 1.15f, hintPaint)
            }
            return
        }
        when (k.action) {
            Action.SHIFT -> drawIcon(
                canvas,
                when {
                    layer == Layer.L2 && shiftMode == ShiftMode.LOCKED && !numericMode -> icShiftLocked
                    layer == Layer.L2 && !numericMode -> icShiftFilled
                    else -> icShift
                },
                r, fg,
            )
            Action.SYMBOLS -> {
                drawLabel(canvas, if (layer == Layer.L3) lettersLabel() else symbolsLabel, r, fg)
                if (showHints) {
                    hintPaint.color = cHint
                    hintPaint.textSize = hintSize
                    canvas.drawText("☺", r.right - dp(4f), r.top + hintSize * 1.15f, hintPaint)
                }
            }
            Action.GLOBE -> drawIcon(canvas, icGlobe, r, fg)
            Action.VOWEL_TOGGLE -> {
                // Shows what a tap switches to: signs (◌া) or vowels (অ). Tinted while flipped by hand.
                textPaint.color = if (vowelManual) cAccent else fg
                textPaint.textSize = min(k.textSize * 0.92f, k.rect.width() * 0.5f)
                val label = if (vowelSigns) "অ" else "\u25CCা"
                canvas.drawText(label, r.centerX(), r.centerY() + baselineOffset(textPaint, "ক"), textPaint)
                if (showHints) {
                    hintPaint.color = cHint
                    hintPaint.textSize = min(hintSize, r.width() * 0.3f)
                    canvas.drawText("⇄", r.right - dp(4f), r.top + hintPaint.textSize * 1.15f, hintPaint)
                }
            }
            Action.SPACE -> drawLabel(canvas, if (language == Language.ENGLISH) "English" else "বাংলা", r, cHint)
            Action.BACKSPACE -> drawIcon(canvas, icBackspace, r, fg)
            Action.ENTER -> drawIcon(
                canvas,
                when (enterAction) {
                    EnterAction.SEARCH -> icSearch
                    EnterAction.SEND -> icSend
                    EnterAction.DONE -> icCheck
                    EnterAction.GO, EnterAction.NEXT -> icArrowForward
                    EnterAction.NEWLINE -> icReturn
                },
                r, fg,
            )
            else -> Unit
        }
    }

    private fun lettersLabel() = if (language == Language.ENGLISH) "ABC" else "কখগ"

    private fun drawIcon(canvas: Canvas, d: Drawable, r: RectF, tint: Int, size: Float = iconSize) {
        d.setTint(tint)
        val s = size.roundToInt()
        val l = (r.centerX() - s / 2f).roundToInt()
        val t = (r.centerY() - s / 2f).roundToInt()
        d.setBounds(l, t, l + s, t + s)
        d.draw(canvas)
    }

    private fun drawLabel(canvas: Canvas, text: String, r: RectF, color: Int) {
        labelPaint.color = color
        canvas.drawText(text, r.centerX(), r.centerY() + labelBaseline, labelPaint)
    }

    // ── Touch ──────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPress(event.getPointerId(0), event.x, event.y)

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger: commit the first key now, start tracking the new one.
                finishPress(commit = true)
                val i = event.actionIndex
                beginPress(event.getPointerId(i), event.getX(i), event.getY(i))
            }

            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(activePointerId)
                if (i >= 0) movePress(event.getX(i), event.getY(i))
            }

            MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    finishPress(commit = true)
                    activePointerId = -1
                }

            MotionEvent.ACTION_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) finishPress(commit = true)
                activePointerId = -1
            }

            MotionEvent.ACTION_CANCEL -> {
                finishPress(commit = false)
                activePointerId = -1
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): KeyItem? {
        for (k in keys) if (k.hit.contains(x, y)) return k
        return null
    }

    private fun beginPress(pointerId: Int, x: Float, y: Float) {
        activePointerId = pointerId
        longPressFired = false
        cursorMode = false
        downX = x
        val k = keyAt(x, y)
        press(k)
        if (k == null) return
        feedback(k)
        backspaceFiredOnDown = k.action == Action.BACKSPACE
        if (backspaceFiredOnDown) {
            onBackspace?.invoke()
            startRepeat()
        }
    }

    /** Make [k] the highlighted key (or none), (re)starting its long-press timer. */
    private fun press(k: KeyItem?) {
        cancelTimers()
        pressed = k
        backspaceFiredOnDown = false
        invalidate()
        if (k == null) {
            hidePreview()
            return
        }
        if (k.def != null && showPreview) showPreview(k, k.def.primary) else hidePreview()
        val hasAlternate = k.def?.longPress != null || k.action == Action.GLOBE || k.action == Action.SYMBOLS
        if (hasAlternate) {
            val r = Runnable { fireLongPress(k) }
            longPressRunnable = r
            handler.postDelayed(r, longPressMs)
        }
    }

    private fun movePress(x: Float, y: Float) {
        if (longPressFired) return
        val p = pressed
        // Drag along the space bar: move the cursor one character per step.
        if (p?.action == Action.SPACE) {
            if (!cursorMode && abs(x - downX) > dp(16f)) {
                cursorMode = true
                cursorAnchorX = downX
                cancelTimers()
            }
            if (cursorMode) {
                val step = dp(11f)
                val steps = ((x - cursorAnchorX) / step).toInt()
                if (steps != 0) {
                    onCursorMove?.invoke(steps)
                    cursorAnchorX += steps * step
                    if (prefs.vibrate) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                return
            }
        }
        if (p?.action == Action.BACKSPACE && backspaceFiredOnDown) return // keep repeating even if the finger drifts
        val k = keyAt(x, y)
        if (k !== p) press(k)
    }

    private fun fireLongPress(k: KeyItem) {
        longPressFired = true
        longPressRunnable = null
        if (prefs.vibrate) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val alt = k.def?.longPress
        when {
            alt != null -> {
                showPreview(k, alt)
                commitChar(alt)
            }
            k.action == Action.GLOBE -> onShowImePicker?.invoke()
            k.action == Action.SYMBOLS -> onEmoji?.invoke()
        }
    }

    private fun finishPress(commit: Boolean) {
        cancelTimers()
        val k = pressed
        val fired = longPressFired
        val firedOnDown = backspaceFiredOnDown
        val wasCursor = cursorMode
        pressed = null
        longPressFired = false
        backspaceFiredOnDown = false
        cursorMode = false
        hidePreview()
        invalidate()
        if (k == null || !commit || fired || wasCursor) return
        val def = k.def
        when {
            def != null -> commitChar(def.primary)
            k.action == Action.BACKSPACE -> if (!firedOnDown) onBackspace?.invoke()
            k.action != null -> handleAction(k)
        }
    }

    private fun commitChar(text: String) {
        onChar?.invoke(text)
        if (shiftMode == ShiftMode.ONESHOT) {
            autoShifted = false
            setLayer(Layer.L1, ShiftMode.NORMAL)
        }
        // Switch the vowel row right away for the next key, without waiting for the editor.
        vowelManual = false
        setAutoVowelSigns(wantsVowelSign(text))
    }

    private fun handleAction(k: KeyItem) {
        when (k.action) {
            Action.SHIFT -> {
                autoShifted = false
                when {
                    numericMode -> Unit
                    layer == Layer.L2 && shiftMode == ShiftMode.ONESHOT -> setLayer(Layer.L2, ShiftMode.LOCKED)
                    layer == Layer.L2 -> setLayer(Layer.L1, ShiftMode.NORMAL)
                    else -> setLayer(Layer.L2, ShiftMode.ONESHOT)
                }
            }
            Action.SYMBOLS -> when {
                layer == Layer.L3 && numericMode -> setLayer(Layer.L2, ShiftMode.LOCKED)
                layer == Layer.L3 -> setLayer(Layer.L1, ShiftMode.NORMAL)
                else -> setLayer(Layer.L3, ShiftMode.NORMAL)
            }
            Action.GLOBE -> onGlobe?.invoke()
            Action.VOWEL_TOGGLE -> {
                vowelSigns = !vowelSigns
                vowelManual = true
                rebuildKeys()
            }
            Action.SPACE -> commitChar(" ")
            Action.ENTER -> onEnter?.invoke()
            Action.SUGGESTION -> suggestions.getOrNull(k.slot)?.let { word ->
                onSuggestion?.invoke(word)
                if (shiftMode == ShiftMode.ONESHOT) setLayer(Layer.L1, ShiftMode.NORMAL)
            }
            Action.CLIP_CHIP -> onPasteClip?.invoke()
            Action.TOOLBAR_TOGGLE -> {
                toolbarOpen = !toolbarOpen
                rebuildKeys()
            }
            Action.TOOL_EMOJI -> { closeToolbar(); onEmoji?.invoke() }
            Action.TOOL_CLIPBOARD -> { closeToolbar(); onClipboard?.invoke() }
            Action.TOOL_VOICE -> { closeToolbar(); onVoice?.invoke() }
            Action.TOOL_SETTINGS -> { closeToolbar(); onSettings?.invoke() }
            Action.BACKSPACE, null -> Unit
        }
    }

    private fun feedback(k: KeyItem) {
        if (prefs.vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound) {
            audio?.playSoundEffect(
                when (k.action) {
                    Action.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                    Action.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    Action.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                },
                0.5f,
            )
        }
    }

    /** Held ⌫: characters at first, then whole words once it has been held a while. */
    private fun startRepeat() {
        var count = 0
        val r = object : Runnable {
            override fun run() {
                count++
                if (count > WORD_DELETE_AFTER) {
                    onDeleteWord?.invoke()
                    handler.postDelayed(this, WORD_REPEAT_INTERVAL_MS)
                } else {
                    onBackspace?.invoke()
                    handler.postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, REPEAT_DELAY_MS)
    }

    private fun cancelTimers() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ── Preview popup ──────────────────────────────────────────────────────────

    private fun showPreview(k: KeyItem, text: String) {
        val w = max(k.rect.width() * 1.35f, dp(48f)).roundToInt()
        val h = (k.rect.height() * 1.3f).roundToInt()
        preview.show(text, min(k.textSize * 1.65f, w * 0.8f), refGlyph(text))
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val x = (loc[0] + k.rect.centerX() - w / 2f).roundToInt()
        val y = (loc[1] + k.rect.top - h - dp(4f)).roundToInt()
        try {
            if (previewWindow.isShowing) {
                previewWindow.update(x, y, w, h)
            } else {
                previewWindow.width = w
                previewWindow.height = h
                previewWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            }
        } catch (_: WindowManager.BadTokenException) {
            // Window went away mid-press; the preview is decoration only.
        }
    }

    private fun hidePreview() {
        if (previewWindow.isShowing) previewWindow.dismiss()
    }

    override fun onDetachedFromWindow() {
        cancelInteraction()
        super.onDetachedFromWindow()
    }

    private companion object {
        /**
         * Height of the character area, in rows of the base key height. Layer 1 (4 rows) gets keys
         * 10 % taller than the base; the 5-row layers (⇧, symbols) 12 % shorter.
         */
        const val CHAR_AREA = 4.4f
        /** Width of the vowel/sign switch key, in letter-key widths. */
        const val TOGGLE_WEIGHT = 0.7f
        const val SUGGESTION_SLOTS = 3
        /** Cell (left → right) to suggestion rank: best in the middle, like most keyboards. */
        val SLOT_FOR_CELL = intArrayOf(1, 0, 2)
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 45L
        const val WORD_DELETE_AFTER = 18
        const val WORD_REPEAT_INTERVAL_MS = 160L

        // ⇧ | !?# | 🌐 | ␣ ␣ | ।/. | ⌫ | ↵   — null = the punctuation key
        val CONTROL_ROW = listOf(
            Action.SHIFT to 1, Action.SYMBOLS to 1, Action.GLOBE to 1, Action.SPACE to 2,
            null to 1, Action.BACKSPACE to 1, Action.ENTER to 1,
        )
        val CONTROL_UNITS = CONTROL_ROW.sumOf { it.second }
    }
}

/** A glyph whose height represents [label], so labels of one script share a baseline. */
private fun refGlyph(label: String): String {
    val c = label.firstOrNull() ?: return "ক"
    return when {
        c in 'a'..'z' -> "x"
        c in 'A'..'Z' || c in '0'..'9' -> "H"
        else -> "ক"
    }
}

private val baselineCache = HashMap<String, Float>()

/** Offset from a box centre to the baseline that visually centres [ref] at the paint's size. */
private fun baselineOffset(p: Paint, ref: String): Float {
    val key = "$ref|${p.textSize}|${p.typeface?.hashCode()}"
    return baselineCache.getOrPut(key) {
        val b = Rect()
        p.getTextBounds(ref, 0, ref.length, b)
        -(b.top + b.bottom) / 2f
    }
}

/** The floating character preview shown above a pressed key. */
private class KeyPreview(
    context: Context,
    typeface: Typeface,
    bg: Int,
    fg: Int,
    border: Int,
    radius: Float,
    strokeWidth: Float,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        color = fg
        textAlign = Paint.Align.CENTER
    }
    private var text = ""
    private var ref = "ক"

    init {
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = radius
            setStroke(strokeWidth.roundToInt().coerceAtLeast(1), border)
        }
    }

    fun show(text: String, textSize: Float, ref: String) {
        this.text = text
        this.ref = ref
        paint.textSize = textSize
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(text, width / 2f, height / 2f + baselineOffset(paint, ref), paint)
    }
}
