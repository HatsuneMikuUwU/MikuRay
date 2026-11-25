package com.miku.ray.util

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * Options an editable dropdown offers for the text the user has typed.
 *
 * A blank query, or one that already equals an option, keeps every option so the menu still works
 * as a plain picker. Any other query keeps the options containing it anywhere, ignoring case and
 * surrounding whitespace, so a partly remembered or mid-string fragment still finds its entry.
 */
internal fun filterDropdownOptions(options: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    return if (trimmed.isEmpty() || trimmed in options) {
        options
    } else {
        options.filter { it.contains(trimmed, ignoreCase = true) }
    }
}

/**
 * [ArrayAdapter] for an AutoCompleteTextView whose filtering follows [filterDropdownOptions]
 * (substring match anywhere) instead of the default prefix match.
 */
class ContainsFilterArrayAdapter(
    context: Context,
    resource: Int,
    options: List<String>
) : ArrayAdapter<String>(context, resource, options.toMutableList()) {

    private val allOptions: List<String> = options.toList()

    private val containsFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val matched = filterDropdownOptions(allOptions, constraint?.toString().orEmpty())
            return FilterResults().apply {
                values = matched
                count = matched.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val matched = results?.values as? List<String> ?: allOptions
            clear()
            addAll(matched)
            if (matched.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return resultValue as? String ?: ""
        }
    }

    override fun getFilter(): Filter = containsFilter
}
