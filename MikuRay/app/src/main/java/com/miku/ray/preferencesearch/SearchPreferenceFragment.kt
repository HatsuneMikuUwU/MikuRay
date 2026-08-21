package com.miku.ray.preferencesearch

import com.miku.ray.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.preferencesearch.ui.AnimationUtils
import com.miku.ray.preferencesearch.ui.RevealAnimationSetting

class SearchPreferenceFragment : Fragment(), SearchPreferenceAdapter.SearchClickListener {

    companion object {
        const val TAG = "SearchPreferenceFragment"
        private const val SHARED_PREFS_FILE = "preferenceSearch"
        private const val MAX_HISTORY = 5
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var searcher: PreferenceParser
    private var results: List<PreferenceItem> = ArrayList()
    private var history: MutableList<HistoryItem> = ArrayList()
    private lateinit var viewHolder: SearchViewHolder
    private lateinit var searchConfiguration: SearchConfiguration
    private lateinit var adapter: SearchPreferenceAdapter
    private var historyClickListener: HistoryClickListener? = null
    private var searchTermPreset: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle??) {
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        searcher = PreferenceParser(requireContext())

        searchConfiguration = SearchConfiguration.fromBundle(arguments)
        val files = searchConfiguration.getFiles()
        for (file in files) {
            searcher.addResourceFile(file)
        }
        searcher.addPreferenceItems(searchConfiguration.getPreferencesToIndex())
        loadHistory()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle??): View {
        val rootView = inflater.inflate(R.layout.searchpreference_fragment, container, false)
        viewHolder = SearchViewHolder(rootView)

        viewHolder.clearButton.setOnClickListener { viewHolder.searchView.setText("") }
        if (searchConfiguration.isHistoryEnabled()) {
            viewHolder.moreButton.visibility = View.VISIBLE
        }
        searchConfiguration.getTextHint()?.let { viewHolder.searchView.hint = it }
        searchConfiguration.getTextNoResults()?.let { viewHolder.noResults.text = it }
        searchConfiguration.getTextClearInput()?.let { viewHolder.clearButton.contentDescription = it }
        searchConfiguration.getTextMore()?.let { viewHolder.moreButton.contentDescription = it }

        viewHolder.moreButton.setOnClickListener { _ ->
            val popup = PopupMenu(requireContext(), viewHolder.moreButton)
            popup.menuInflater.inflate(R.menu.searchpreference_more, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.clear_history) {
                    clearHistory()
                }
                true
            }
            searchConfiguration.getTextClearHistory()?.let {
                popup.menu.findItem(R.id.clear_history).title = it
            }
            popup.show()
        }

        viewHolder.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchPreferenceAdapter()
        adapter.setSearchConfiguration(searchConfiguration)
        adapter.setOnItemClickListener(this)
        viewHolder.recyclerView.adapter = adapter

        viewHolder.searchView.addTextChangedListener(textWatcher)
        viewHolder.searchView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        if (!searchConfiguration.isSearchBarEnabled()) {
            viewHolder.cardView.visibility = View.GONE
        }

        searchTermPreset?.let { viewHolder.searchView.setText(it) }

        val anim: RevealAnimationSetting? = searchConfiguration.getRevealAnimationSetting()
        anim?.let { AnimationUtils.registerCircularRevealAnimation(requireContext(), rootView, it) }

        rootView.setOnTouchListener { _, _ -> true }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle??) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = if (searchConfiguration.isSearchBarEnabled())
                kotlin.math.max(systemBars.top, cutout.top) else 0
            val bottomInset = kotlin.math.max(systemBars.bottom, cutout.bottom)
            v.setPadding(
                v.paddingLeft,
                topInset,
                v.paddingRight,
                v.paddingBottom
            )

            val baseBottomPadding = (16 * v.resources.displayMetrics.density).toInt()
            viewHolder.recyclerView.setPadding(
                viewHolder.recyclerView.paddingLeft,
                viewHolder.recyclerView.paddingTop,
                viewHolder.recyclerView.paddingRight,
                baseBottomPadding + bottomInset
            )
            insets
        }
    }

    private fun loadHistory() {
        history = ArrayList()
        if (!searchConfiguration.isHistoryEnabled()) {
            return
        }

        val size = prefs.getInt(historySizeKey(), 0)
        for (i in 0 until size) {
            val title = prefs.getString(historyEntryKey(i), null)
            history.add(HistoryItem(title))
        }
    }

    private fun saveHistory() {
        val editor = prefs.edit()
        editor.putInt(historySizeKey(), history.size)
        for (i in history.indices) {
            editor.putString(historyEntryKey(i), history[i].getTerm())
        }
        editor.apply()
    }

    private fun historySizeKey(): String {
        return searchConfiguration.getHistoryId()?.let { "${it}_history_size" } ?: "history_size"
    }

    private fun historyEntryKey(i: Int): String {
        return searchConfiguration.getHistoryId()?.let { "${it}_history_$i" } ?: "history_$i"
    }

    fun hasHistory(): Boolean = history.isNotEmpty()

    fun clearHistory() {
        viewHolder.searchView.setText("")
        history.clear()
        saveHistory()
        updateSearchResults("")
    }

    private fun addHistoryEntry(entry: String) {
        val newItem = HistoryItem(entry)
        if (!history.contains(newItem)) {
            if (history.size >= MAX_HISTORY) {
                history.removeAt(history.size - 1)
            }
            history.add(0, newItem)
            saveHistory()
            updateSearchResults(viewHolder.searchView.text.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        updateSearchResults(viewHolder.searchView.text.toString())

        if (searchConfiguration.isSearchBarEnabled()) {
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        viewHolder.searchView.post {
            viewHolder.searchView.requestFocus()
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(viewHolder.searchView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (view != null && imm != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    fun setSearchTerm(term: CharSequence) {
        if (::viewHolder.isInitialized) {
            viewHolder.searchView.setText(term)
        } else {
            searchTermPreset = term
        }
    }

    private fun updateSearchResults(keyword: String) {
        adapter.setKeyword(keyword)

        if (keyword.isEmpty()) {
            showHistory()
            return
        }

        results = searcher.searchFor(keyword)
        adapter.setContent(ArrayList(results))

        setEmptyViewShown(results.isEmpty())
    }

    private fun setEmptyViewShown(shown: Boolean) {
        if (shown) {
            viewHolder.noResults.visibility = View.VISIBLE
            viewHolder.recyclerView.visibility = View.GONE
        } else {
            viewHolder.noResults.visibility = View.GONE
            viewHolder.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showHistory() {
        viewHolder.noResults.visibility = View.GONE
        viewHolder.recyclerView.visibility = View.VISIBLE

        val validHistory: MutableList<HistoryItem> = ArrayList()
        for (item in history) {
            val itemResults = searcher.searchFor(item.getTerm())
            if (itemResults.isNotEmpty()) {
                validHistory.add(item)
            }
        }

        adapter.setContent(ArrayList(validHistory))
        setEmptyViewShown(validHistory.isEmpty())
    }

    override fun onItemClicked(item: ListItem, position: Int) {
        if (item.getType() == HistoryItem.TYPE) {
            val text = (item as HistoryItem).getTerm()
            viewHolder.searchView.setText(text)
            viewHolder.searchView.setSelection(text.length)
            historyClickListener?.onHistoryEntryClicked(text.toString())
        } else {
            hideKeyboard()

            try {
                val callback = activity as SearchPreferenceResultListener
                val r = results[position]
                r.title?.let { addHistoryEntry(it) }
                var screen: String? = null
                if (!r.keyBreadcrumbs.isEmpty()) {
                    screen = r.keyBreadcrumbs[r.keyBreadcrumbs.size - 1]
                }
                val result = SearchPreferenceResult(r.key, r.resId, screen)
                callback.onSearchResultClicked(result)
            } catch (e: ClassCastException) {
                throw ClassCastException("${activity.toString()} must implement SearchPreferenceResultListener")
            }
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(editable: Editable?) {
            val text = editable?.toString() ?: ""
            updateSearchResults(text)
            viewHolder.clearButton.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    fun setHistoryClickListener(historyClickListener: HistoryClickListener) {
        this.historyClickListener = historyClickListener
    }

    private class SearchViewHolder(root: View) {
        val clearButton: ImageView = root.findViewById(R.id.clear)
        val moreButton: ImageView = root.findViewById(R.id.more)
        val searchView: EditText = root.findViewById(R.id.search)
        val recyclerView: RecyclerView = root.findViewById(R.id.list)
        val noResults: TextView = root.findViewById(R.id.no_results)
        val cardView: CardView = root.findViewById(R.id.search_card)
    }

    interface HistoryClickListener {
        fun onHistoryEntryClicked(entry: String)
    }
}