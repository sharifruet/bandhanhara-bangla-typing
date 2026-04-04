package com.bandhanhara.bangla

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

enum class Layer { L1, L2, L3 }
enum class ShiftMode { NORMAL, ONESHOT, LOCKED }

class BanglaKeyboardView(context: Context) : View(context) {

    var onChar: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onSwitchIME: (() -> Unit)? = null

    // — Layer state —
    private var layer = Layer.L1
    private var shiftMode = ShiftMode.NORMAL

    // — Sizing (computed in onSizeChanged) —
    private var keyW = 0f
    private var keyH = 0f
    private var fontSize = 0f
    private var hintSize = 0f
    private val gap = 5f.dp
    private val hPad = 4f.dp

    // — Paint —
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827") }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6b7280") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        maskFilter = BlurMaskFilter(2f.dp, BlurMaskFilter.Blur.NORMAL)
    }

    // — Touch state —
    private var pressedIndex: Int = -1
    private var pressedIsAction = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false

    // — Popup preview —
    private var popupWindow: PopupWindow? = null

    // — Key rects (built in onSizeChanged / onDraw) —
    private data class KeyRect(val rect: RectF, val def: KeyDef?, val actionTag: String?)
    private val keyRects = mutableListOf<KeyRect>()

    init {
        keyPaint.color = Color.WHITE
        actionPaint.color = Color.parseColor("#AEB6BF")
        pressedPaint.color = Color.parseColor("#C8CDD2")
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for shadow blur
    }

    // — dp helper —
    private val Float.dp get() = this * resources.displayMetrics.density
    private val Int.dp get() = this.toFloat() * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        keyW = (w - hPad * 2 - gap * 2 * COLS) / COLS
        keyH = keyW * 0.95f
        fontSize = keyH * 0.48f
        hintSize = maxOf(9f.dp, fontSize * 0.36f)
        textPaint.textSize = fontSize
        hintPaint.textSize = hintSize
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val kw = (w - (hPad * 2) - (gap * 2 * COLS)) / COLS
        val kh = kw * 0.95f
        // 5 char rows + 1 control row + padding top + bottom
        val totalH = (kh + gap * 2) * 6 + 8f.dp + 8f.dp
        setMeasuredDimension(w, totalH.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#CDD0D6"))
        keyRects.clear()

        val activeKeys = when (layer) { Layer.L1 -> LAYER1_KEYS; Layer.L2 -> LAYER2_KEYS; Layer.L3 -> LAYER3_KEYS }
        val radius = 5f.dp
        var top = 8f.dp

        // — Character rows —
        val rows = activeKeys.chunked(COLS)
        rows.forEach { row ->
            var left = hPad
            row.forEach { key ->
                val r = RectF(left + gap, top + gap, left + keyW + gap, top + keyH + gap)
                val isPressed = pressedIndex == keyRects.size && !pressedIsAction
                val paint = if (isPressed) pressedPaint else keyPaint
                if (!isPressed) canvas.drawRoundRect(r.shifted(0f, 1.5f.dp), radius, radius, shadowPaint)
                canvas.drawRoundRect(r, radius, radius, paint)
                drawCenteredText(canvas, key.primary, r, textPaint)
                if (key.longPress != null) drawHintText(canvas, key.longPress, r, hintPaint)
                keyRects.add(KeyRect(r, key, null))
                left += keyW + gap * 2
            }
            top += keyH + gap * 2
        }

        // — Control row —
        val controlDefs = buildControlRow()
        var left = hPad
        controlDefs.forEach { (label, tag, widthMul) ->
            val kw = keyW * widthMul + gap * 2 * (widthMul - 1)
            val r = RectF(left + gap, top + gap, left + kw + gap, top + keyH + gap)
            val isPressed = pressedIndex == keyRects.size && pressedIsAction
            val paint = if (isPressed) pressedPaint else actionPaint
            if (!isPressed) canvas.drawRoundRect(r.shifted(0f, 1.5f.dp), radius, radius, shadowPaint)
            canvas.drawRoundRect(r, radius, radius, paint)
            drawCenteredText(canvas, label, r, textPaint)
            keyRects.add(KeyRect(r, null, tag))
            left += kw + gap * 2
        }
    }

    private fun buildControlRow(): List<Triple<String, String, Float>> {
        val shiftLabel = when {
            layer == Layer.L2 && shiftMode == ShiftMode.LOCKED -> "⇪"
            else -> "⇧"
        }
        val l3Label = if (layer == Layer.L3) "←" else "…"
        return listOf(
            Triple("🌐", "switch_ime", 1f),
            Triple(shiftLabel, "shift", 1f),
            Triple(l3Label, "l3", 1f),
            Triple(" ", "space", 2f),
            Triple("⌫", "backspace", 1f),
            Triple("↵", "enter", 1f),
        )
    }

    private fun drawCenteredText(canvas: Canvas, text: String, r: RectF, paint: Paint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val x = r.centerX() - bounds.exactCenterX()
        val y = r.centerY() - bounds.exactCenterY()
        canvas.drawText(text, x, y, paint)
    }

    private fun drawHintText(canvas: Canvas, text: String, r: RectF, paint: Paint) {
        canvas.drawText(text, r.right - hintSize * 1.2f, r.top + hintSize * 1.1f, paint)
    }

    // Shifted copy for drawing shadow slightly below
    private fun RectF.shifted(dx: Float, dy: Float) = RectF(left + dx, top + dy, right + dx, bottom + dy)

    // — Touch handling —
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleDown(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(event.action == MotionEvent.ACTION_UP)
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        longPressFired = false
        cancelLongPressTimer()
        val idx = keyRects.indexOfFirst { it.rect.contains(x, y) }
        if (idx < 0) return
        val kr = keyRects[idx]
        pressedIndex = idx
        pressedIsAction = kr.actionTag != null
        invalidate()

        if (kr.def != null) showPopup(kr)

        if (kr.def?.longPress != null) {
            val lp = kr.def.longPress
            longPressRunnable = Runnable {
                longPressFired = true
                dismissPopup()
                pressedIndex = -1
                invalidate()
                commitChar(lp)
            }.also { longPressHandler.postDelayed(it, 320) }
        }
    }

    private fun handleUp(commit: Boolean) {
        cancelLongPressTimer()
        dismissPopup()
        val idx = pressedIndex
        pressedIndex = -1
        invalidate()
        if (!commit || idx < 0 || longPressFired) return
        val kr = keyRects.getOrNull(idx) ?: return
        if (kr.def != null) {
            commitChar(kr.def.primary)
        } else {
            handleAction(kr.actionTag ?: return)
        }
    }

    private fun commitChar(char: String) {
        onChar?.invoke(char)
        if (shiftMode == ShiftMode.ONESHOT) {
            layer = Layer.L1
            shiftMode = ShiftMode.NORMAL
            invalidate()
        }
    }

    private fun handleAction(tag: String) {
        when (tag) {
            "shift" -> {
                when {
                    layer == Layer.L3 -> { layer = Layer.L1; shiftMode = ShiftMode.NORMAL }
                    layer == Layer.L2 && shiftMode == ShiftMode.LOCKED -> { layer = Layer.L1; shiftMode = ShiftMode.NORMAL }
                    layer == Layer.L2 && shiftMode == ShiftMode.ONESHOT -> { shiftMode = ShiftMode.LOCKED }
                    else -> { layer = Layer.L2; shiftMode = ShiftMode.ONESHOT }
                }
                invalidate()
            }
            "l3" -> {
                layer = if (layer == Layer.L3) Layer.L1 else Layer.L3
                shiftMode = ShiftMode.NORMAL
                invalidate()
            }
            "switch_ime" -> onSwitchIME?.invoke()
            "space" -> onChar?.invoke(" ")
            "backspace" -> onBackspace?.invoke()
            "enter" -> onEnter?.invoke()
        }
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    // — iOS-style popup preview —
    private fun showPopup(kr: KeyRect) {
        dismissPopup()
        val tv = TextView(context).apply {
            text = kr.def?.primary
            textSize = fontSize * 1.4f / resources.displayMetrics.scaledDensity
            setTextColor(Color.parseColor("#111827"))
            setPadding(12.dp.toInt(), 8.dp.toInt(), 12.dp.toInt(), 8.dp.toInt())
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        val pw = PopupWindow(tv, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        pw.isOutsideTouchable = false
        pw.isTouchable = false
        val loc = IntArray(2); getLocationInWindow(loc)
        val popX = loc[0] + kr.rect.centerX().toInt() - 40.dp.toInt()
        val popY = loc[1] + kr.rect.top.toInt() - 80.dp.toInt()
        pw.showAtLocation(this, android.view.Gravity.NO_GRAVITY, popX, popY)
        popupWindow = pw
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }
}
