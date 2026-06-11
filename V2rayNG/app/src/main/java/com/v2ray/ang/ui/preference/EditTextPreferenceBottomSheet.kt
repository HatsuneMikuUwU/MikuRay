package com.v2ray.ang.ui.preference

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.WindowBlurUtils
import com.v2ray.ang.util.getColorAttr

/**
 * A Preference that replaces EditTextPreference's built-in dialog
 * with a Material3 BottomSheetDialog.
 *
 * Reads/writes directly via MmkvManager so it works alongside
 * MmkvPreferenceDataStore used in each settings Fragment.
 *
 * XML attributes (same namespace as EditTextPreference):
 *   android:inputType   — forwarded to the EditText
 *   android:singleLine  — forces singleLine / IME done
 */
class EditTextPreferenceBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var inputType: Int = InputType.TYPE_CLASS_TEXT
    private var singleLine: Boolean = true

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, intArrayOf(android.R.attr.inputType, android.R.attr.singleLine))
            inputType = ta.getInt(0, InputType.TYPE_CLASS_TEXT)
            singleLine = ta.getBoolean(1, true)
            ta.recycle()
        }
    }

    /** Current stored value (raw string from MMKV). */
    var text: String?
        get() = MmkvManager.decodeSettingsString(key)
        set(value) {
            MmkvManager.encodeSettings(key, value)
            notifyChanged()
        }

    override fun onClick() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.uwu_bottom_sheet_edittext, null)

        val textInputLayout = view.findViewById<TextInputLayout>(R.id.text_input_layout)
        val editText = view.findViewById<TextInputEditText>(R.id.edit_text)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save)

        // Set hint to preference title
        textInputLayout.hint = title?.toString() ?: ""

        // Configure EditText
        editText.inputType = inputType
        if (singleLine || (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)) {
            editText.setSingleLine(true)
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
        }

        // Pre-fill current value
        val current = MmkvManager.decodeSettingsString(key).orEmpty()
        editText.setText(current)
        editText.setSelection(current.length)

        // IME done → commit
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitAndDismiss(editText.text?.toString().orEmpty(), dialog)
                true
            } else false
        }

        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close)

        btnSave.setOnClickListener {
            commitAndDismiss(editText.text?.toString().orEmpty(), dialog)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)

        // Apply blur + color to sheet background
        val bgColor = context.getColorAttr("colorBg")
        dialog.window?.let { win ->
            WindowBlurUtils.applyWindowBlur(win)
            win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            win.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            win.navigationBarColor = bgColor
            win.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
        dialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let { sheet ->
            sheet.backgroundTintList = ColorStateList.valueOf(bgColor)
            sheet.clipToOutline = true
        }

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Height cap: status bar + small margin
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val screenHeight = v.resources.displayMetrics.heightPixels
            val margin = (8 * v.resources.displayMetrics.density).toInt()
            dialog.behavior.maxHeight = screenHeight - statusBarHeight - margin
            insets
        }

        dialog.setOnShowListener {
            editText.post {
                editText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.show()
    }

    private fun commitAndDismiss(newValue: String, dialog: BottomSheetDialog) {
        val abortDismiss = !callChangeListener(newValue)
        if (!abortDismiss) {
            MmkvManager.encodeSettings(key, newValue)
            // Update summary — mask password fields
            val masked = inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
                         inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD != 0
            summary = if (masked && newValue.isNotEmpty()) "••••••" else newValue
            notifyChanged()
            dialog.dismiss()
        }
    }
}
