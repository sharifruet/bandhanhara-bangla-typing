package com.bandhanhara.bangla

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class BanglaIMEService : InputMethodService() {

    private var keyboardView: BanglaKeyboardView? = null
    private val imm: InputMethodManager by lazy { getSystemService(InputMethodManager::class.java) }
    private lateinit var suggester: Suggester

    /** Suggestions are off in password, email, URL and number fields. */
    private var suggestionsEnabled = true
    /** Learning is also off when the app asks for no personalised learning (incognito). */
    private var learningEnabled = true
    /** The last thing we did was insert "word " from the strip; punctuation may replace that space. */
    private var autoSpaced = false

    /** The suggestion just picked, and how many backspaces followed it; deleting into it is a rejection. */
    private var lastPick: Pair<TypingContext, String>? = null
    private var backspacesAfterPick = 0
    /** A word has been typed that hasn't been learned yet (it's learned on space, punctuation, enter or leaving the field). */
    private var wordPending = false

    override fun onCreate() {
        super.onCreate()
        suggester = Suggester.get(this).apply { whenReady(::updateSuggestions) }
    }

    override fun onDestroy() {
        suggester.flush()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = BanglaKeyboardView(this)
        view.onChar = ::typeText
        view.onBackspace = ::backspace
        view.onEnter = ::enter
        view.onSwitchIme = ::switchToOtherKeyboard
        view.onShowImePicker = { imm.showInputMethodPicker() }
        view.onSuggestion = ::pickSuggestion
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.configure(
            enterAction = enterActionFor(info),
            numeric = isNumeric(info),
            resetLayer = !restarting,
        )
        suggestionsEnabled = allowsSuggestions(info)
        learningEnabled = suggestionsEnabled && suggester.learningEnabled &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        autoSpaced = false
        lastPick = null
        wordPending = false
        updateSuggestions()
        applyWindowAppearance()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // The last word of a message sent with the app's own button never sees a space; learn it now.
        if (wordPending) learnWordBeforeCursor()
        super.onFinishInputView(finishingInput)
        keyboardView?.cancelInteraction()
        suggester.flush()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (newSelStart != newSelEnd) keyboardView?.setSuggestions(emptyList()) else updateSuggestions()
    }

    override fun onEvaluateFullscreenMode() = false

    /** Show even with a hardware keyboard attached: physical keyboards can't type Bangla. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    // ── Editing ────────────────────────────────────────────────────────────────

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        val endsWord = text.none(::isWordChar)
        if (endsWord) learnWordBeforeCursor() else wordPending = true
        lastPick = null // typing anything after a pick accepts it

        // "আমি " + । → "আমি। " — punctuation pulls back the space a suggestion added.
        if (autoSpaced && text in SPACE_SWALLOWING_PUNCTUATION && ic.getTextBeforeCursor(1, 0) == " ") {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$text ", 1)
            ic.endBatchEdit()
            return // stays autoSpaced, so "?!" keeps working
        }
        autoSpaced = false
        ic.commitText(text, 1)
    }

    /** Replace the partial word before the cursor with [word] and a space. */
    private fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val ctx = parseContext(ic.getTextBeforeCursor(CONTEXT_WINDOW, 0) ?: "")
        ic.beginBatchEdit()
        if (ctx.rawPrefixLength > 0) ic.deleteSurroundingText(ctx.rawPrefixLength, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        if (learningEnabled) suggester.learn(ctx, word)
        autoSpaced = true
        wordPending = false
        lastPick = ctx to word
        backspacesAfterPick = 0
    }

    private fun learnWordBeforeCursor() {
        wordPending = false
        if (!learningEnabled) return
        val ctx = parseContext(currentInputConnection?.getTextBeforeCursor(CONTEXT_WINDOW, 0) ?: return)
        if (ctx.prefix.isNotEmpty()) suggester.learn(ctx, ctx.prefix)
    }

    /**
     * After a pick, the first backspace just removes the added space. A second one deletes into the
     * word itself — the suggestion was wrong, so undo what the pick taught and demote it here.
     */
    private fun noteBackspaceAfterPick() {
        val (ctx, word) = lastPick ?: return
        backspacesAfterPick++
        if (backspacesAfterPick >= 2) {
            if (learningEnabled) suggester.reject(ctx, word)
            lastPick = null
        }
    }

    private fun updateSuggestions() {
        val view = keyboardView ?: return
        if (!suggestionsEnabled) {
            view.setSuggestions(emptyList())
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(CONTEXT_WINDOW, 0) ?: ""
        view.setSuggestions(suggester.suggest(parseContext(before)))
    }

    private fun allowsSuggestions(info: EditorInfo): Boolean {
        if (isNumeric(info)) return false
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        return when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> false
            else -> true
        }
    }

    /**
     * Delete one code point before the cursor (so a matra or nukta comes off on its own),
     * or the selection if there is one. Falls back to a DEL key event for fields that give
     * us no text access.
     */
    private fun backspace() {
        autoSpaced = false
        noteBackspaceAfterPick()
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(2, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val n = before.length
        val units = if (n >= 2 && Character.isSurrogatePair(before[n - 2], before[n - 1])) 2 else 1
        ic.deleteSurroundingText(units, 0)
    }

    private fun enter() {
        learnWordBeforeCursor()
        autoSpaced = false
        lastPick = null
        val info = currentInputEditorInfo
        if (info != null && enterActionFor(info) != EnterAction.NEWLINE) {
            currentInputConnection?.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun enterActionFor(info: EditorInfo): EnterAction {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEWLINE
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            else -> EnterAction.NEWLINE
        }
    }

    private fun isNumeric(info: EditorInfo) = when (info.inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
    }

    // ── Switching keyboards ────────────────────────────────────────────────────

    /** 🌐 tap: hand over to the user's main keyboard directly, no picker. */
    private fun switchToOtherKeyboard() {
        val others = imm.enabledInputMethodList.filter { it.packageName != packageName }
        val target = PREFERRED_KEYBOARDS.firstNotNullOfOrNull { pkg -> others.firstOrNull { it.packageName == pkg } }
            ?: others.firstOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when {
                target != null -> switchInputMethod(target.id)
                !switchToNextInputMethod(false) -> imm.showInputMethodPicker()
            }
        } else if (target == null) {
            imm.showInputMethodPicker()
        } else {
            @Suppress("DEPRECATION")
            imm.setInputMethod(window?.window?.attributes?.token, target.id)
        }
    }

    // ── Window chrome ──────────────────────────────────────────────────────────

    /** Make the navigation bar under the keyboard match the keyboard colour. */
    private fun applyWindowAppearance() {
        val w = window?.window ?: return
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.insetsController?.setSystemBarsAppearance(
                if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
        @Suppress("DEPRECATION")
        w.navigationBarColor = getColor(R.color.kb_bg)
    }

    private companion object {
        val SPACE_SWALLOWING_PUNCTUATION = setOf("।", ",", "?", "!", ";", ":", ".")

        /** Keyboards to hand over to, most preferred first; then any other enabled one; then the picker. */
        val PREFERRED_KEYBOARDS = listOf(
            "com.samsung.android.honeyboard",       // Samsung Keyboard (One UI)
            "com.sec.android.inputmethod",          // Samsung Keyboard (older)
            "com.google.android.inputmethod.latin", // Gboard
        )
    }
}
