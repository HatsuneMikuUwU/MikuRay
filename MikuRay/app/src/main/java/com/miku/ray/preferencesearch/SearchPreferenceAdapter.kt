package com.miku.ray.preferencesearch

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R
import com.miku.ray.util.ThemeManagerKt
import java.util.ArrayList
import java.util.Locale

open class SearchPreferenceAdapter : RecyclerView.Adapter<SearchPreferenceAdapter.ViewHolder>() {
    private var dataset: MutableList<ListItem> = ArrayList()
    private lateinit var searchConfiguration: SearchConfiguration
    private var onItemClickListener: SearchClickListener? = null
    private var keyword: String = ""

    fun setKeyword(keyword: String?) {
        this.keyword = keyword ?: ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == PreferenceItem.TYPE) {
            PreferenceViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.searchpreference_list_item_result, parent, false
                )
            )
        } else {
            HistoryViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.searchpreference_list_item_history, parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        val listItem = dataset[position]

        val highlightColor = getColorAttr(h.root.context, "colorPrimary")

        when (getItemViewType(position)) {
            HistoryItem.TYPE -> {
                val holder = h as HistoryViewHolder
                val item = listItem as HistoryItem
                holder.term.text = highlight(item.term, highlightColor)
            }
            PreferenceItem.TYPE -> {
                val holder = h as PreferenceViewHolder
                val item = listItem as PreferenceItem
                holder.title.text = highlight(item.title, highlightColor)

                if (TextUtils.isEmpty(item.summary)) {
                    holder.summary.visibility = View.GONE
                } else {
                    holder.summary.visibility = View.VISIBLE
                    holder.summary.text = highlight(item.summary, highlightColor)
                }

                if (searchConfiguration.isBreadcrumbsEnabled()) {
                    holder.breadcrumbs.text = item.breadcrumbs
                    holder.breadcrumbs.alpha = 0.6f
                    holder.summary.alpha = 1.0f
                } else {
                    holder.breadcrumbs.visibility = View.GONE
                    holder.summary.alpha = 0.6f
                }
            }
        }

        h.root.setOnClickListener {
            onItemClickListener?.onItemClicked(listItem, h.adapterPosition)
        }
    }

    private fun highlight(text: String?, highlightColor: Int): SpannableString {
        val spannable = SpannableString(text ?: "")
        if (keyword.isEmpty() || text == null) return spannable

        val textLower = text.lowercase(Locale.getDefault())
        val keywordLower = keyword.lowercase(Locale.getDefault())

        var start = 0
        while (true) {
            start = textLower.indexOf(keywordLower, start)
            if (start == -1) break
            val end = start + keywordLower.length
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = end
        }
        return spannable
    }

    fun setContent(items: List<ListItem>) {
        dataset = ArrayList(items)
        this.notifyDataSetChanged()
    }

    override fun getItemCount(): Int = dataset.size

    override fun getItemViewType(position: Int): Int = dataset[position].getType()

    fun setSearchConfiguration(searchConfiguration: SearchConfiguration) {
        this.searchConfiguration = searchConfiguration
    }

    fun setOnItemClickListener(onItemClickListener: SearchClickListener?) {
        this.onItemClickListener = onItemClickListener
    }

    interface SearchClickListener {
        fun onItemClicked(item: ListItem, position: Int)
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v
    }

    class HistoryViewHolder(v: View) : ViewHolder(v) {
        val term: TextView = v.findViewById(R.id.term)
    }

    class PreferenceViewHolder(v: View) : ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val summary: TextView = v.findViewById(R.id.summary)
        val breadcrumbs: TextView = v.findViewById(R.id.breadcrumbs)
    }
}