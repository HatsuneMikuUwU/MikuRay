package com.miku.ray.preferencesearch

import com.miku.ray.R
import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class SearchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr), View.OnClickListener {

    private var searchConfiguration: SearchConfiguration = SearchConfiguration()
    private var hint: String? = null

    init {
        setLayoutResource(R.layout.searchpreference_preference)
        attrs?.let { parseAttrs(it) }
    }

    private fun parseAttrs(attrs: AttributeSet) {
        var a = context.obtainStyledAttributes(attrs, intArrayOf(R.attr.textHint))
        try {
            val text = a.getText(0)
            if (text != null) {
                hint = text.toString()
                searchConfiguration.setTextHint(text.toString())
            }
        } finally {
            a.recycle()
        }

        a = context.obtainStyledAttributes(attrs, intArrayOf(R.attr.textClearHistory))
        try {
            val text = a.getText(0)
            if (text != null) {
                searchConfiguration.setTextClearHistory(text.toString())
            }
        } finally {
            a.recycle()
        }

        a = context.obtainStyledAttributes(attrs, intArrayOf(R.attr.textNoResults))
        try {
            val text = a.getText(0)
            if (text != null) {
                searchConfiguration.setTextNoResults(text.toString())
            }
        } finally {
            a.recycle()
        }

        a = context.obtainStyledAttributes(attrs, intArrayOf(R.attr.textClearInput))
        try {
            val text = a.getText(0)
            if (text != null) {
                searchConfiguration.setTextClearInput(text.toString())
            }
        } finally {
            a.recycle()
        }

        a = context.obtainStyledAttributes(attrs, intArrayOf(R.attr.textMore))
        try {
            val text = a.getText(0)
            if (text != null) {
                searchConfiguration.setTextMore(text.toString())
            }
        } finally {
            a.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val searchText = holder.findViewById(R.id.search) as? EditText
        searchText?.isFocusable = false
        searchText?.inputType = InputType.TYPE_NULL
        searchText?.setOnClickListener(this)

        hint?.let { searchText?.hint = it }

        holder.findViewById(R.id.search_card)?.setOnClickListener(this)
        holder.itemView.setOnClickListener(this)
        holder.itemView.setBackgroundColor(0x0)
    }

    override fun onClick(view: View?) {
        getSearchConfiguration().showSearchFragment()
    }

    fun getSearchConfiguration(): SearchConfiguration = searchConfiguration
}