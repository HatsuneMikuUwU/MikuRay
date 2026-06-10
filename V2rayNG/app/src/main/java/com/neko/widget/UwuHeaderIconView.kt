package com.neko.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.handler.MmkvManager

class UwuHeaderIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var sectionIconRes: Int = 0

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.UwuHeaderIconView)
        sectionIconRes = ta.getResourceId(R.styleable.UwuHeaderIconView_sectionIcon, 0)
        ta.recycle()
    }

    private val styleChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applyStyle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStyle()
        ContextCompat.registerReceiver(
            context,
            styleChangeReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_CATEGORY_STYLE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(styleChangeReceiver) } catch (_: Exception) {}
    }

    fun applyStyle() {
        val style = MmkvManager.decodeSettingsString(AppConfig.PREF_CATEGORY_STYLE, "gradient")
        if (style == "gradient") {
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics
            ).toInt()
            layoutParams = layoutParams?.also {
                it.width = sizePx
                it.height = sizePx
            }
            val pad = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics
            ).toInt()
            setPadding(pad, pad, pad, pad)
            background = buildGradientBackground()
            val iconRes = if (sectionIconRes != 0) sectionIconRes else R.drawable.ic_sparkles_24dp
            val iconSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics
            ).toInt()
            val iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)
                ?.mutate()
                ?.also { it.setBounds(0, 0, iconSizePx, iconSizePx) }
            scaleType = ScaleType.CENTER
            setImageDrawable(iconDrawable)
            imageTintList = android.content.res.ColorStateList.valueOf(
                context.getColorAttr("colorOnPrimary")
            )
        } else {
            setPadding(0, 0, 0, 0)
            background = null
            imageTintList = null
            setImageResource(drawableForStyle(style))
        }
    }

    private fun buildGradientBackground(): GradientDrawable {
        val tv = TypedValue()
        context.getColorAttr("colorPrimary", tv, true)
        val colorStart = tv.data
        context.getColorAttr("colorTertiary", tv, true)
        val colorEnd = tv.data
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply {
            shape = GradientDrawable.OVAL
        }
    }

    companion object {
        fun drawableForStyle(style: String?): Int = when (style) {
            "miku2"  -> R.drawable.uwu_icon_miku_2
            "teto"   -> R.drawable.uwu_icon_teto
            "teto2"  -> R.drawable.uwu_icon_teto_2
            "neru"   -> R.drawable.uwu_icon_neru
            else     -> R.drawable.uwu_icon_miku
        }
    }
}
