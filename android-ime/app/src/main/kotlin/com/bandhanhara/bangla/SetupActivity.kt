package com.bandhanhara.bangla

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max

/**
 * Launcher / onboarding screen: enable the keyboard, select it, try it.
 * Also opened by the gear icon next to the keyboard in system settings.
 */
class SetupActivity : Activity() {

    private lateinit var step1Status: TextView
    private lateinit var step2Status: TextView
    private lateinit var btnEnable: Button
    private lateinit var btnChoose: Button


    private val imm: InputMethodManager by lazy { getSystemService(InputMethodManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        step1Status = findViewById(R.id.step1_status)
        step2Status = findViewById(R.id.step2_status)
        btnEnable = findViewById(R.id.btn_enable)
        btnChoose = findViewById(R.id.btn_choose)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        btnChoose.setOnClickListener { imm.showInputMethodPicker() }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }


        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        findViewById<TextView>(R.id.footer).text = getString(R.string.footer, version)

        // Edge-to-edge (enforced from Android 15): pad for system bars and the keyboard ourselves,
        // then keep the focused "try it" field in view above the keyboard.
        val root = findViewById<ScrollView>(R.id.root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom)
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            if (v.paddingTop != top || v.paddingBottom != bottom) {
                v.setPadding(0, top, 0, bottom)
                v.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        v.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val f = currentFocus ?: return
                        // ScrollView's own "is it visible" check ignores padding, so do it by hand.
                        val r = Rect(0, 0, f.width, f.height)
                        root.offsetDescendantRectToMyCoords(f, r)
                        val visibleBottom = root.scrollY + root.height - root.paddingBottom
                        val margin = (16 * resources.displayMetrics.density).toInt()
                        if (r.bottom + margin > visibleBottom) {
                            root.smoothScrollBy(0, r.bottom + margin - visibleBottom)
                        }
                    }
                })
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // the keyboard picker is a system dialog, so onResume does not fire
    }

    private fun refresh() {
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            .orEmpty()
            .startsWith("$packageName/")

        setStatus(step1Status, enabled)
        setStatus(step2Status, current)
        btnEnable.visibility = if (enabled) View.GONE else View.VISIBLE
        btnChoose.visibility = if (current || !enabled) View.GONE else View.VISIBLE
    }


    private fun setStatus(view: TextView, done: Boolean) {
        view.setText(if (done) R.string.status_done else R.string.status_pending)
        view.setTextColor(getColor(if (done) R.color.setup_success else R.color.setup_muted))
    }
}
