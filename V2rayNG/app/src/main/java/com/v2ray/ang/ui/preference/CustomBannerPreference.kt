package com.v2ray.ang.ui.preference

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

class CustomBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var onImageClick: (() -> Unit)? = null
    var onImageLongClick: (() -> Unit)? = null

    init {
        layoutResource = R.layout.uwu_banner_theme
    }

    fun refresh() {
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.setIsRecyclable(false)

        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        (holder.findViewById(R.id.uwu_name_title_summary) as? TextView)?.text = com.v2ray.ang.util.AppNameHelper.getDisplayName(context)
        (holder.findViewById(R.id.uwu_version_name_summary) as? TextView)?.text = context.getString(R.string.uwu_version_name)
        (holder.findViewById(R.id.uwu_version_code_summary) as? TextView)?.text = context.getString(R.string.uwu_version_code)
        (holder.findViewById(R.id.uwu_package_name_summary) as? TextView)?.text = context.getString(R.string.uwu_package_name)
        (holder.findViewById(R.id.uwu_build_date_summary) as? TextView)?.text = context.getString(R.string.uwu_build_date)

        val imageView = holder.findViewById(R.id.img_banner_preference) as? ImageView
        if (imageView != null) {
            val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_THEME_BANNER_URI)
            if (!uriString.isNullOrBlank()) {
                Glide.with(context)
                    .load(Uri.parse(uriString))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_theme)
                    .into(imageView)
            } else {
                Glide.with(context).clear(imageView)
                imageView.setImageResource(R.drawable.uwu_banner_theme)
            }
        }

        val imageClickTarget = holder.findViewById(R.id.theme_banner_image_card)
        imageClickTarget?.setOnClickListener {
            onImageClick?.invoke()
        }
        imageClickTarget?.setOnLongClickListener {
            onImageLongClick?.invoke()
            true
        }

        val clickTarget = holder.findViewById(R.id.onClick)
        clickTarget?.setOnClickListener {
            this.performClick()
        }
    }
}
