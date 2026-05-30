package com.neko.marquee.text

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class AutoMarqueeTextView @JvmOverloads constructor(
    context: Context, 
    attrs: AttributeSet? = null, 
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        setHorizontallyScrolling(true)
        isHorizontalFadingEdgeEnabled = false 
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSelected = true 
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isSelected = false 
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            isSelected = true
        }
    }
}
