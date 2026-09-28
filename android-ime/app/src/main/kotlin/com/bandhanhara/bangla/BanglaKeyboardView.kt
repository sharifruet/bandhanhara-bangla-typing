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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Layer { L1, L2, L3 }
enum class ShiftMode { NORMAL, ONESHOT, LOCKED }
enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, DONE }

/**
 * The keyboard: one custom-drawn View.
 *
 * Six character columns × five character rows, plus a control row. Every size derives from the
 * view width (capped by screen height in landscape / on short screens) so keys are always as large
 * as the screen allows. Character keys commit on release, backspace fires on press and repeats,
 * long-press (320 ms) commits the key's alternate. Two-thumb typing is supported: a second finger
 * going down commits the first key immediately.
 */
class BanglaKeyboardView(context: Context) : View(context) {

    // ── Callbacks, wired by the IME service ────────────────────────────────────
    var onChar: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onSwitchIme: (() -> Unit)? = null
    var onShowImePicker: (() -> Unit)? = null
    var onSuggestion: ((String) -> Unit)? = null

    // ── Layer / editor state ───────────────────────────────────────────────────
    private var layer = Layer.L1
    private var shiftMode = ShiftMode.NORMAL
    private var enterAction = EnterAction.NEWLINE
    private var numericMode = false

    /** Current suggestions, best first. Shown in the strip as [2nd] [1st] [3rd]. */
    private var suggestions: List<String> = emptyList()

    // ── Key model ──────────────────────────────────────────────────────────────
    private enum class Action { SHIFT, SYMBOLS, GLOBE, SPACE, BACKSPACE, ENTER, SUGGESTION }

    /**
     * A laid-out key. [rect] is what is drawn; [hit] is the touch target and includes the gaps.
     * For suggestion cells, [slot] is the index into [suggestions].
     */
    private class KeyItem(val rect: RectF, val hit: RectF, val def: KeyDef?, val action: Action?, val slot: Int = -1)

    private class ControlKey(val action: Action?, val def: KeyDef?, val units: Int)

    // ⇧ | !?# | 🌐 | ␣ ␣ | । | ⌫ | ↵   — 8 units across the width of 6 character keys
    private val controlRow = listOf(
        ControlKey(Action.SHIFT, null, 1),
        ControlKey(Action.SYMBOLS, null, 1),
        ControlKey(Action.GLOBE, null, 1),
        ControlKey(Action.SPACE, null, 2),
        ControlKey(null, KeyDef("।", ","), 1),
        ControlKey(Action.BACKSPACE, null, 1),
        ControlKey(Action.ENTER, null, 1),
    )
    private val controlUnits = controlRow.sumOf { it.units }
    private val keys = ArrayList<KeyItem>(SUGGESTION_SLOTS + CHAR_ROWS * COLS + controlRow.size)

    // ── Metrics (px) ───────────────────────────────────────────────────────────
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val gapX = dp(2.5f)
    private val gapY = dp(3f)
    private val padX = dp(3f)
    private val padTop = dp(4f)
    private val padBottom = dp(3f)
    private val radius = dp(6f)
    private val shadowDy = dp(1f)
    private val stripH = dp(42f)
    private var keyW = 0f
    private var keyH = 0f
    private var unitW = 0f
    private var fontSize = 0f
    private var hintSize = 0f
    private var labelSize = 0f
    private var iconSize = 0f
    private var textBaseline = 0f
    private var labelBaseline = 0f
    private var bottomInset = 0

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
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1f)
    }
    private var suggestionSize = 0f

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

    private val spaceLabel = context.getString(R.string.kb_space_label)
    private val symbolsLabel = context.getString(R.string.kb_symbols_label)
    private val lettersLabel = context.getString(R.string.kb_letters_label)

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    // ── Touch state ────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var activePointerId = -1
    private var pressed: KeyItem? = null
    private var longPressFired = false
    private var backspaceFiredOnDown = false
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null

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
        // On edge-to-edge systems the IME window extends under the navigation bar; pad for it.
        setOnApplyWindowInsetsListener { _, insets ->
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
            insets
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
        }
        rebuildKeys()
    }

    /** Replace the words shown in the suggestion strip (best first, up to three). */
    fun setSuggestions(words: List<String>) {
        val next = words.take(SUGGESTION_SLOTS)
        if (next == suggestions) return
        suggestions = next
        invalidate()
    }

    /** Drop any in-progress press (keyboard hidden, field lost, …). */
    fun cancelInteraction() {
        cancelTimers()
        hidePreview()
        pressed = null
        activePointerId = -1
        longPressFired = false
        invalidate()
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private fun computeMetrics(w: Int) {
        val dm = resources.displayMetrics
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val innerW = w - padX * 2
        keyW = innerW / COLS - gapX * 2
        unitW = innerW / controlUnits - gapX * 2

        // Never let the keyboard eat more than 44 % of the screen (58 % in landscape).
        val maxTotal = dm.heightPixels * (if (landscape) 0.58f else 0.44f)
        val maxKeyH = (maxTotal - stripH - padTop - padBottom) / ROWS - gapY * 2
        keyH = min(keyW * 0.8f, maxKeyH).coerceAtLeast(dp(34f))

        suggestionSize = dp(19f)
        suggestionPaint.textSize = suggestionSize
        dividerPaint.color = ColorUtils.setAlphaComponent(cHint, 0x55)

        fontSize = keyH * 0.54f
        hintSize = max(dp(9f), fontSize * 0.36f)
        labelSize = fontSize * 0.55f
        iconSize = (keyH * 0.42f).coerceIn(dp(16f), dp(30f))

        textPaint.textSize = fontSize
        hintPaint.textSize = hintSize
        labelPaint.textSize = labelSize
        textBaseline = baselineOffset(textPaint)
        labelBaseline = baselineOffset(labelPaint)
    }

    private fun contentHeight() = stripH + padTop + ROWS * (keyH + gapY * 2) + padBottom

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

        // Suggestion strip: three equal cells across the top. Best guess sits in the middle.
        val cellW = w / SUGGESTION_SLOTS
        for (cell in 0 until SUGGESTION_SLOTS) {
            val r = RectF(cell * cellW, 0f, (cell + 1) * cellW, stripH)
            keys += KeyItem(RectF(r).apply { inset(dp(4f), dp(4f)) }, r, null, Action.SUGGESTION, SLOT_FOR_CELL[cell])
        }

        var top = stripH + padTop
        for (row in keysFor(layer, numericMode).chunked(COLS)) {
            var left = padX
            for (def in row) {
                keys += keyItem(left, top, keyW, def, null)
                left += keyW + gapX * 2
            }
            top += keyH + gapY * 2
        }
        var left = padX
        for (c in controlRow) {
            val cw = unitW * c.units + gapX * 2 * (c.units - 1)
            keys += keyItem(left, top, cw, c.def, c.action)
            left += cw + gapX * 2
        }
        // Edge keys also catch touches in the outer padding.
        val bottomEdge = contentHeight()
        for (k in keys) {
            if (k.action == Action.SUGGESTION) continue
            if (k.hit.left <= padX + 0.5f) k.hit.left = 0f
            if (k.hit.right >= w - padX - 0.5f) k.hit.right = w
            if (k.hit.top <= stripH + padTop + 0.5f) k.hit.top = stripH
            if (k.hit.bottom >= bottomEdge - padBottom - 0.5f) k.hit.bottom = bottomEdge + bottomInset
        }
        invalidate()
    }

    private fun keyItem(left: Float, top: Float, w: Float, def: KeyDef?, action: Action?) = KeyItem(
        rect = RectF(left + gapX, top + gapY, left + gapX + w, top + gapY + keyH),
        hit = RectF(left, top, left + w + gapX * 2, top + keyH + gapY * 2),
        def = def,
        action = action,
    )

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

    private fun drawSuggestion(canvas: Canvas, k: KeyItem) {
        // Thin dividers between cells.
        if (k.hit.left > 0f) {
            val inset = stripH * 0.28f
            canvas.drawLine(k.hit.left, inset, k.hit.left, stripH - inset, dividerPaint)
        }
        val word = suggestions.getOrNull(k.slot) ?: return
        val r = k.rect
        if (k === pressed) {
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
        canvas.drawText(word, r.centerX(), r.centerY() + baselineOffset(suggestionPaint), suggestionPaint)
    }

    private fun drawKey(canvas: Canvas, k: KeyItem) {
        if (k.action == Action.SUGGESTION) {
            drawSuggestion(canvas, k)
            return
        }
        val isPressed = k === pressed
        val r = k.rect
        val accent = when (k.action) {
            Action.SHIFT -> layer == Layer.L2
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
            canvas.drawText(def.primary, r.centerX(), r.centerY() + textBaseline, textPaint)
            val hint = def.longPress
            if (hint != null) {
                hintPaint.color = if (accent) cOnAccent else cHint
                canvas.drawText(hint, r.right - dp(4.5f), r.top + hintSize * 1.15f, hintPaint)
            }
            return
        }
        when (k.action) {
            Action.SHIFT -> drawIcon(
                canvas,
                when {
                    layer == Layer.L2 && shiftMode == ShiftMode.LOCKED -> icShiftLocked
                    layer == Layer.L2 -> icShiftFilled
                    else -> icShift
                },
                r, fg,
            )
            Action.SYMBOLS -> drawLabel(canvas, if (layer == Layer.L3) lettersLabel else symbolsLabel, r, fg)
            Action.GLOBE -> drawIcon(canvas, icGlobe, r, fg)
            Action.SPACE -> drawLabel(canvas, spaceLabel, r, cHint)
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
            Action.SUGGESTION, null -> Unit
        }
    }

    private fun drawIcon(canvas: Canvas, d: Drawable, r: RectF, tint: Int) {
        d.setTint(tint)
        val s = iconSize.roundToInt()
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
        if (k.def != null) showPreview(k, k.def.primary) else hidePreview()
        val hasAlternate = k.def?.longPress != null || k.action == Action.GLOBE
        if (hasAlternate) {
            val r = Runnable { fireLongPress(k) }
            longPressRunnable = r
            handler.postDelayed(r, LONG_PRESS_MS)
        }
    }

    private fun movePress(x: Float, y: Float) {
        if (longPressFired) return
        val k = keyAt(x, y)
        if (k !== pressed) press(k)
    }

    private fun fireLongPress(k: KeyItem) {
        longPressFired = true
        longPressRunnable = null
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val alt = k.def?.longPress
        when {
            alt != null -> {
                showPreview(k, alt)
                commitChar(alt)
            }
            k.action == Action.GLOBE -> onShowImePicker?.invoke()
        }
    }

    private fun finishPress(commit: Boolean) {
        cancelTimers()
        val k = pressed
        val fired = longPressFired
        val firedOnDown = backspaceFiredOnDown
        pressed = null
        longPressFired = false
        backspaceFiredOnDown = false
        hidePreview()
        invalidate()
        if (k == null || !commit || fired) return
        val def = k.def
        when {
            def != null -> commitChar(def.primary)
            k.action == Action.SUGGESTION -> suggestions.getOrNull(k.slot)?.let { word ->
                onSuggestion?.invoke(word)
                if (shiftMode == ShiftMode.ONESHOT) setLayer(Layer.L1, ShiftMode.NORMAL)
            }
            k.action == Action.BACKSPACE -> if (!firedOnDown) onBackspace?.invoke()
            k.action != null -> handleAction(k.action)
        }
    }

    private fun commitChar(text: String) {
        onChar?.invoke(text)
        if (shiftMode == ShiftMode.ONESHOT) setLayer(Layer.L1, ShiftMode.NORMAL)
    }

    private fun handleAction(action: Action) {
        when (action) {
            Action.SHIFT -> when {
                layer == Layer.L2 && shiftMode == ShiftMode.ONESHOT -> setLayer(Layer.L2, ShiftMode.LOCKED)
                layer == Layer.L2 -> setLayer(Layer.L1, ShiftMode.NORMAL)
                else -> setLayer(Layer.L2, ShiftMode.ONESHOT)
            }
            Action.SYMBOLS ->
                if (layer == Layer.L3) setLayer(Layer.L1, ShiftMode.NORMAL) else setLayer(Layer.L3, ShiftMode.NORMAL)
            Action.GLOBE -> onSwitchIme?.invoke()
            Action.SPACE -> commitChar(" ")
            Action.ENTER -> onEnter?.invoke()
            Action.BACKSPACE, Action.SUGGESTION -> Unit // handled in finishPress / on press
        }
    }

    private fun feedback(k: KeyItem) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        audio?.playSoundEffect(
            when (k.action) {
                Action.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                Action.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                Action.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
        )
    }

    private fun startRepeat() {
        val r = object : Runnable {
            override fun run() {
                onBackspace?.invoke()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
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
        val h = (keyH * 1.3f).roundToInt()
        preview.show(text, fontSize * 1.65f)
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
        const val ROWS = CHAR_ROWS + 1
        const val SUGGESTION_SLOTS = 3
        /** Cell (left → right) to suggestion rank: best in the middle, like most keyboards. */
        val SLOT_FOR_CELL = intArrayOf(1, 0, 2)
        const val LONG_PRESS_MS = 320L
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 45L
    }
}

/** Offset from a box centre to the baseline that visually centres a typical consonant. */
private fun baselineOffset(p: Paint): Float {
    val b = Rect()
    p.getTextBounds(REF_GLYPH, 0, REF_GLYPH.length, b)
    return -(b.top + b.bottom) / 2f
}

private const val REF_GLYPH = "ক"

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
    private var baseline = 0f

    init {
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = radius
            setStroke(strokeWidth.roundToInt().coerceAtLeast(1), border)
        }
    }

    fun show(text: String, textSize: Float) {
        this.text = text
        if (paint.textSize != textSize) {
            paint.textSize = textSize
            baseline = baselineOffset(paint)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(text, width / 2f, height / 2f + baseline, paint)
    }
}
