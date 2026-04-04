package com.bandhanhara.bangla

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class BanglaIMEService : InputMethodService() {

    private lateinit var keyboardView: BanglaKeyboardView

    override fun onCreateInputView(): View {
        keyboardView = BanglaKeyboardView(this)

        keyboardView.onChar = { char ->
            currentInputConnection?.commitText(char, 1)
        }

        keyboardView.onBackspace = {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        keyboardView.onEnter = {
            val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection?.performEditorAction(action)
            } else {
                currentInputConnection?.commitText("\n", 1)
            }
        }

        keyboardView.onSwitchIME = {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            // Find Samsung keyboard directly by package name and switch to it without showing picker
            val samsungIME = imm.enabledInputMethodList.find {
                it.packageName == "com.sec.android.inputmethod"
            }
            if (samsungIME != null) {
                switchInputMethod(samsungIME.id)
            } else {
                // Samsung not found — fall back to picker so user can choose
                imm.showInputMethodPicker()
            }
        }

        return keyboardView
    }

    override fun onEvaluateFullscreenMode() = false
}
