package com.bandhanhara.bangla

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import java.text.BreakIterator
import kotlin.math.abs

class BanglaIMEService : InputMethodService() {

    private val imm: InputMethodManager by lazy { getSystemService(InputMethodManager::class.java) }
    private val clipboard: ClipboardManager by lazy { getSystemService(ClipboardManager::class.java) }
    private lateinit var prefs: Prefs
    private lateinit var suggester: Suggester
    private val clips = ClipHistory()

    // Views
    private var container: FrameLayout? = null
    private var keyboardView: BanglaKeyboardView? = null
    private var panel: View? = null
    /** Appearance settings the current views were built with; rebuilt when they change. */
    private var builtAppearance = ""

    // Per-field state
    private var suggestionsAllowed = true
    private var learningEnabled = true
    private var autoCapsAllowed = false
    /** The last thing we did was insert "word " from the strip; punctuation may replace that space. */
    private var autoSpaced = false
    /** The suggestion just picked, and how many backspaces followed it; deleting into it is a rejection. */
    private var lastPick: Pair<TypingContext, String>? = null
    private var backspacesAfterPick = 0
    /** A word has been typed that hasn't been learned yet (learned on space, punctuation, enter or leaving the field). */
    private var wordPending = false
    private var lastSpaceAt = 0L
    /** A clip the user has already pasted or dismissed from the chip. */
    private var chipDismissed: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clip = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            // Apps mark passwords and codes as sensitive (Android 13+); never keep those.
            val sensitive = clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            val text = if (sensitive) null else clip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) {
                clips.add(text)
                chipDismissed = null
                refreshClipChip()
            }
        } catch (_: SecurityException) {
            // Not the current keyboard: the system won't let us read the clipboard. Fine.
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        suggester = Suggester.get(this).apply { whenReady(::updateSuggestions) }
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        suggester.flush()
        super.onDestroy()
    }

    // ── Views ──────────────────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val ctx = themedContext(this, prefs.theme)
        builtAppearance = prefs.appearanceKey()
        val view = BanglaKeyboardView(ctx, prefs).apply {
            onChar = ::typeText
            onBackspace = ::backspace
            onDeleteWord = ::deleteWord
            onEnter = ::enter
            onGlobe = ::globe
            onShowImePicker = { imm.showInputMethodPicker() }
            onSuggestion = ::pickSuggestion
            onCursorMove = ::moveCursor
            onEmoji = ::showEmoji
            onClipboard = ::showClipboard
            onVoice = ::startVoice
            onSettings = ::openSettings
            onPasteClip = { clips.fresh(CHIP_MS)?.let(::pasteClip) }
        }
        keyboardView = view
        panel = null
        return FrameLayout(ctx).apply {
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            container = this
        }
    }

    /** Swap the keys for a panel of the same size (emoji, clipboard). */
    private fun showPanel(make: (Context) -> View) {
        val c = container ?: return
        val kb = keyboardView ?: return
        closePanel()
        val p = make(c.context)
        p.setPadding(0, 0, 0, kb.bottomInset)
        c.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, kb.keyboardHeight))
        kb.visibility = View.INVISIBLE // keeps its size, so the keyboard doesn't jump
        panel = p
    }

    private fun closePanel() {
        panel?.let { container?.removeView(it) }
        panel = null
        keyboardView?.visibility = View.VISIBLE
    }

    private fun lettersLabel() = if (keyboardView?.language == Language.ENGLISH) "ABC" else "কখগ"

    private fun showEmoji() = showPanel { ctx ->
        EmojiPanel(
            ctx, prefs, lettersLabel(),
            onEmoji = { e -> typeText(e) },
            onBackspace = ::backspace,
            onSpace = { typeText(" ") },
            onBack = ::closePanel,
        )
    }

    private fun showClipboard() = showPanel { ctx ->
        ClipboardPanel(ctx, clips, lettersLabel(), onPaste = { t -> pasteClip(t); closePanel() }, onBack = ::closePanel)
    }

    // ── Lifecycle per field ────────────────────────────────────────────────────

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (prefs.appearanceKey() != builtAppearance) setInputView(onCreateInputView())
        if (!restarting) closePanel()
        val view = keyboardView ?: return
        view.setLanguage(prefs.language)
        view.configure(enterAction = enterActionFor(info), numeric = isNumeric(info), resetLayer = !restarting)

        val plainText = allowsSuggestions(info)
        suggestionsAllowed = plainText && prefs.suggestions
        learningEnabled = plainText && suggester.learningEnabled &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        autoCapsAllowed = plainText && prefs.autoCaps
        autoSpaced = false
        lastPick = null
        wordPending = false
        refreshClipChip()
        updateSuggestions()
        applyWindowAppearance()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // The last word of a message sent with the app's own button never sees a space; learn it now.
        if (wordPending) learnWordBeforeCursor()
        super.onFinishInputView(finishingInput)
        keyboardView?.cancelInteraction()
        keyboardView?.closeToolbar()
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

    // ── Typing ─────────────────────────────────────────────────────────────────

    private fun typeText(text: String) {
        val ic = currentInputConnection ?: return
        val endsWord = text.none(::isWordChar)
        if (endsWord) learnWordBeforeCursor() else wordPending = true
        lastPick = null // typing anything after a pick accepts it
        dismissChip()

        // Double-tap space → "। " (Bangla) or ". " (English), after a word.
        if (text == " " && prefs.doubleSpacePeriod) {
            val now = SystemClock.uptimeMillis()
            val quick = now - lastSpaceAt < DOUBLE_SPACE_MS
            lastSpaceAt = now
            val before = ic.getTextBeforeCursor(2, 0)
            if (quick && before != null && before.length == 2 && before[1] == ' ' && isWordChar(before[0])) {
                val stop = if (isBanglaWordChar(before[0])) "।" else "."
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$stop ", 1)
                ic.endBatchEdit()
                lastSpaceAt = 0
                autoSpaced = false
                return
            }
        } else {
            lastSpaceAt = 0
        }

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

    /** Replace the partial word before the cursor with [word] (and a space, if that setting is on). */
    private fun pickSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val ctx = parseContext(ic.getTextBeforeCursor(CONTEXT_WINDOW, 0) ?: "")
        val space = prefs.autoSpace
        ic.beginBatchEdit()
        if (ctx.rawPrefixLength > 0) ic.deleteSurroundingText(ctx.rawPrefixLength, 0)
        ic.commitText(if (space) "$word " else word, 1)
        ic.endBatchEdit()
        if (learningEnabled) suggester.learn(ctx, word)
        autoSpaced = space
        wordPending = !space
        lastPick = ctx to word
        backspacesAfterPick = if (space) 0 else 1
        lastSpaceAt = 0
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
        val ic = currentInputConnection
        val before = ic?.getTextBeforeCursor(CONTEXT_WINDOW, 0) ?: ""
        val ctx = parseContext(before)

        // English: a capital at the start of a sentence.
        val caps = autoCapsAllowed && view.language == Language.ENGLISH && ctx.prefix.isEmpty() &&
            (ic?.getCursorCapsMode(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) ?: 0) != 0
        view.setAutoCaps(caps)
        // Bangla: vowel signs right after a consonant, full vowels everywhere else.
        view.setAutoVowelSigns(wantsVowelSign(before))

        if (!suggestionsAllowed) {
            view.setSuggestions(emptyList())
            return
        }
        view.setSuggestions(suggester.suggest(ctx, view.language, capitalize = caps))
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

    // ── Deleting ───────────────────────────────────────────────────────────────

    /**
     * Delete what's before the cursor: the selection if there is one; a whole emoji (even a
     * multi-part one like 👨‍👩‍👧); otherwise one code point, so a Bangla vowel sign or nukta comes
     * off on its own. Falls back to a DEL key event for fields that give us no text access.
     */
    private fun backspace() {
        autoSpaced = false
        lastSpaceAt = 0
        noteBackspaceAfterPick()
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(16, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        ic.deleteSurroundingText(lastDeletionLength(before.toString()), 0)
    }

    private fun lastDeletionLength(s: String): Int {
        val last = s.last()
        val emojiLike = Character.isSurrogate(last) || last == '️' || last == '‍' || last == '⃣' ||
            last in '←'..'⯿'
        if (!emojiLike) return if (s.length >= 2 && Character.isSurrogatePair(s[s.length - 2], last)) 2 else 1
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        val start = it.preceding(s.length)
        return (s.length - start).coerceAtLeast(1)
    }

    /** Held ⌫: delete the word before the cursor (and the spaces after it). */
    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        lastPick = null
        val before = ic.getTextBeforeCursor(CONTEXT_WINDOW, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0 && isWordChar(before[i - 1])) {
            while (i > 0 && isWordChar(before[i - 1])) i--
        } else if (i > 0) {
            i-- // a single punctuation mark
        }
        ic.deleteSurroundingText((before.length - i).coerceAtLeast(1), 0)
    }

    private fun enter() {
        learnWordBeforeCursor()
        autoSpaced = false
        lastPick = null
        lastSpaceAt = 0
        val info = currentInputEditorInfo
        if (info != null && enterActionFor(info) != EnterAction.NEWLINE) {
            currentInputConnection?.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** Space-bar drag: move the cursor [steps] characters (negative = left). */
    private fun moveCursor(steps: Int) {
        lastPick = null
        autoSpaced = false
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(abs(steps)) { sendDownUpKeyEvents(key) }
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

    // ── Clipboard ──────────────────────────────────────────────────────────────

    private fun pasteClip(text: String) {
        currentInputConnection?.commitText(text, 1)
        chipDismissed = text
        refreshClipChip()
    }

    private fun dismissChip() {
        val fresh = clips.fresh(CHIP_MS) ?: return
        if (chipDismissed != fresh) {
            chipDismissed = fresh
            refreshClipChip()
        }
    }

    /** Offer the just-copied text as a one-tap paste in the strip. */
    private fun refreshClipChip() {
        val fresh = clips.fresh(CHIP_MS)?.takeIf { it != chipDismissed && suggestionsAllowed }
        keyboardView?.setClipChip(fresh)
    }

    // ── Language, keyboards, voice, settings ───────────────────────────────────

    /** 🌐 tap: switch between বাংলা and English, or hand over to another keyboard app (a setting). */
    private fun globe() {
        if (prefs.globeAction == GlobeAction.SWITCH_KEYBOARD) {
            switchToOtherKeyboard()
            return
        }
        val view = keyboardView ?: return
        val next = if (view.language == Language.BANGLA) Language.ENGLISH else Language.BANGLA
        prefs.language = next
        view.setLanguage(next)
        updateSuggestions()
    }

    /** Hand over to the user's main keyboard directly, no picker. */
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

    /** Switch to the phone's voice-typing keyboard (e.g. Google voice typing), if there is one. */
    private fun startVoice() {
        for (imi in imm.enabledInputMethodList) {
            val voice = imm.getEnabledInputMethodSubtypeList(imi, true).firstOrNull { it.mode == "voice" } ?: continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchInputMethod(imi.id, voice)
            } else {
                @Suppress("DEPRECATION")
                imm.setInputMethodAndSubtype(window?.window?.attributes?.token ?: return, imi.id, voice)
            }
            return
        }
        Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun openSettings() {
        requestHideSelf(0)
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ── Window chrome ──────────────────────────────────────────────────────────

    /** Make the navigation bar under the keyboard match the keyboard colour. */
    private fun applyWindowAppearance() {
        val w = window?.window ?: return
        val night = when (prefs.theme) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) w.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.insetsController?.setSystemBarsAppearance(
                if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
        @Suppress("DEPRECATION")
        w.navigationBarColor = themedContext(this, prefs.theme).getColor(R.color.kb_bg)
    }

    private companion object {
        val SPACE_SWALLOWING_PUNCTUATION = setOf("।", ",", "?", "!", ";", ":", ".")
        const val DOUBLE_SPACE_MS = 450L
        /** How long after copying the strip offers a one-tap paste. */
        const val CHIP_MS = 90_000L

        /** Keyboards to hand over to, most preferred first; then any other enabled one; then the picker. */
        val PREFERRED_KEYBOARDS = listOf(
            "com.samsung.android.honeyboard",       // Samsung Keyboard (One UI)
            "com.sec.android.inputmethod",          // Samsung Keyboard (older)
            "com.google.android.inputmethod.latin", // Gboard
        )
    }
}
