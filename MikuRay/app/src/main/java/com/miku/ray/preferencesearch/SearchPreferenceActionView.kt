package com.miku.ray.preferencesearch

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager

class SearchPreferenceActionView : SearchView {
    protected var searchFragment: SearchPreferenceFragment? = null
    protected var searchConfiguration: SearchConfiguration = SearchConfiguration()
    protected var activity: AppCompatActivity? = null

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView()
    }

    private fun initView() {
        searchConfiguration.setSearchBarEnabled(false)
        setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchFragment?.setSearchTerm(newText ?: "")
                return true
            }
        })
        setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && (searchFragment == null || searchFragment?.isVisible != true)) {
                searchFragment = searchConfiguration.showSearchFragment()
                searchFragment?.setHistoryClickListener { entry -> setQuery(entry, false) }
            }
        }
    }

    fun getSearchConfiguration(): SearchConfiguration = searchConfiguration

    fun cancelSearch(): Boolean {
        setQuery("", false)

        var didSomething = false
        if (!isIconified) {
            isIconified = true
            didSomething = true
        }
        if (searchFragment != null && searchFragment?.isVisible == true) {
            removeFragment()
            didSomething = true
        }
        return didSomething
    }

    protected fun removeFragment() {
        if (searchFragment?.isVisible == true && activity != null) {
            val fm = activity!!.supportFragmentManager
            fm.beginTransaction().remove(searchFragment!!).commit()
            fm.popBackStack(SearchPreferenceFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    fun setActivity(activity: AppCompatActivity) {
        searchConfiguration.setActivity(activity)
        this.activity = activity
    }
}