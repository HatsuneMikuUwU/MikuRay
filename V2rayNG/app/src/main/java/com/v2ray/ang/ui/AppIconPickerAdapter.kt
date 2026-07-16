package com.v2ray.ang.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

class AppIconPickerAdapter(
    private val context: Context,
    private val selectedValue: String,
    private val onSelect: (String) -> Unit,
) : RecyclerView.Adapter<AppIconPickerAdapter.VH>() {

    companion object {
        private val MIPMAP_NAMES: Map<String, String> = mapOf(
            AppConfig.APP_ICON_DEFAULT to "ic_launcher",
            AppConfig.APP_ICON_ALT1 to "ic_launcher_alt1",
            AppConfig.APP_ICON_ALT2 to "ic_launcher_alt2",
            AppConfig.APP_ICON_ALT3 to "ic_launcher_alt3",
            AppConfig.APP_ICON_ALT4 to "ic_launcher_alt4",
            AppConfig.APP_ICON_ALT5 to "ic_launcher_alt5",
        )

        fun icons(context: Context): List<Triple<String, String, String>> {
            val values = context.resources.getStringArray(R.array.app_icon_values)
            val entries = context.resources.getStringArray(R.array.app_icon_entries)
            return values.mapIndexed { i, value ->
                val label = entries.getOrElse(i) { value }
                val mipmapName = MIPMAP_NAMES[value] ?: "ic_launcher"
                Triple(value, mipmapName, label)
            }
        }
    }

    private var selected: String = selectedValue
    private val items: List<Triple<String, String, String>> = icons(context)

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_container)
        val icon: ImageView = view.findViewById(R.id.icon_image)
        val label: TextView = view.findViewById(R.id.icon_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context)
            .inflate(R.layout.item_app_icon_picker, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (value, mipmapName, label) = items[position]
        val resId = context.resources.getIdentifier(mipmapName, "mipmap", context.packageName)
        val isSelected = value == selected

        if (resId != 0) {
            holder.icon.setImageResource(resId)
        } else {
            holder.icon.setImageDrawable(null)
        }

        holder.label.text = label
        holder.card.strokeColor = if (isSelected) {
            context.getColorAttr(R.attr.colorPrimary)
        } else {
            android.graphics.Color.TRANSPARENT
        }

        holder.itemView.setOnClickListener {
            val prevIdx = items.indexOfFirst { it.first == selected }
            selected = value
            onSelect(value)
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            notifyItemChanged(position)
        }
    }
}
