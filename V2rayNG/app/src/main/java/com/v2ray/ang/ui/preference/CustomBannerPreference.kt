package com.v2ray.ang.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.v2ray.ang.R

class CustomBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.uwu_banner_theme
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

        val clickTarget = holder.findViewById(R.id.onClick)
        clickTarget?.setOnClickListener {
            this.performClick()
        }
    }
}
