package com.miku.ray.preferencesearch

import com.miku.ray.R
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.miku.ray.preferencesearch.ui.RevealAnimationSetting
import java.util.ArrayList
import java.util.Arrays
import androidx.annotation.NonNull

class SearchConfiguration() {
    companion object {
        private const val ARGUMENT_INDEX_FILES = "items"
        private const val ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES = "individual_prefs"
        private const val ARGUMENT_HISTORY_ENABLED = "history_enabled"
        private const val ARGUMENT_HISTORY_ID = "history_id"
        private const val ARGUMENT_SEARCH_BAR_ENABLED = "search_bar_enabled"
        private const val ARGUMENT_BREADCRUMBS_ENABLED = "breadcrumbs_enabled"
        private const val ARGUMENT_REVEAL_ANIMATION_SETTING = "reveal_anim_setting"
        private const val ARGUMENT_TEXT_HINT = "text_hint"
        private const val ARGUMENT_TEXT_CLEAR_HISTORY = "text_clear_history"
        private const val ARGUMENT_TEXT_NO_RESULTS = "text_no_results"
        private const val ARGUMENT_TEXT_CLEAR_INPUT = "text_clear_input"
        private const val ARGUMENT_TEXT_MORE = "text_more"

        fun fromBundle(bundle: Bundle): SearchConfiguration {
            val config = SearchConfiguration()
            config.filesToIndex = bundle.getParcelableArrayList(ARGUMENT_INDEX_FILES) ?: ArrayList()
            config.preferencesToIndex = bundle.getParcelableArrayList(ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES) ?: ArrayList()
            config.historyEnabled = bundle.getBoolean(ARGUMENT_HISTORY_ENABLED)
            config.revealAnimationSetting = bundle.getParcelable(ARGUMENT_REVEAL_ANIMATION_SETTING)
            config.breadcrumbsEnabled = bundle.getBoolean(ARGUMENT_BREADCRUMBS_ENABLED)
            config.searchBarEnabled = bundle.getBoolean(ARGUMENT_SEARCH_BAR_ENABLED)
            config.textHint = bundle.getString(ARGUMENT_TEXT_HINT)
            config.textClearHistory = bundle.getString(ARGUMENT_TEXT_CLEAR_HISTORY)
            config.textNoResults = bundle.getString(ARGUMENT_TEXT_NO_RESULTS)
            config.textClearInput = bundle.getString(ARGUMENT_TEXT_CLEAR_INPUT)
            config.textMore = bundle.getString(ARGUMENT_TEXT_MORE)
            config.historyId = bundle.getString(ARGUMENT_HISTORY_ID)
            return config
        }
    }

    private var filesToIndex: ArrayList<SearchIndexItem> = ArrayList()
    private var preferencesToIndex: ArrayList<PreferenceItem> = ArrayList()
    private var bannedKeys: ArrayList<String> = ArrayList()
    private var historyEnabled: Boolean = true
    private var historyId: String? = null
    private var breadcrumbsEnabled: Boolean = false
    private var searchBarEnabled: Boolean = true
    private var activity: AppCompatActivity? = null
    private var containerResId: Int = android.R.id.content
    private var revealAnimationSetting: RevealAnimationSetting? = null
    private var textClearHistory: String? = null
    private var textNoResults: String? = null
    private var textHint: String? = null
    private var textClearInput: String? = null
    private var textMore: String? = null

    constructor(activity: AppCompatActivity) : this() {
        setActivity(activity)
    }

    fun showSearchFragment(): SearchPreferenceFragment {
        val act = activity ?: throw IllegalStateException("setActivity() not called")

        val arguments = this.toBundle()
        val fragment = SearchPreferenceFragment()
        fragment.arguments = arguments
        act.supportFragmentManager.beginTransaction()
            .add(containerResId, fragment, SearchPreferenceFragment.TAG)
            .addToBackStack(SearchPreferenceFragment.TAG)
            .commit()
        return fragment
    }

    private fun toBundle(): Bundle {
        val arguments = Bundle()
        arguments.putParcelableArrayList(ARGUMENT_INDEX_FILES, filesToIndex)
        arguments.putParcelableArrayList(ARGUMENT_INDEX_INDIVIDUAL_PREFERENCES, preferencesToIndex)
        arguments.putBoolean(ARGUMENT_HISTORY_ENABLED, historyEnabled)
        arguments.putParcelable(ARGUMENT_REVEAL_ANIMATION_SETTING, revealAnimationSetting)
        arguments.putBoolean(ARGUMENT_BREADCRUMBS_ENABLED, breadcrumbsEnabled)
        arguments.putBoolean(ARGUMENT_SEARCH_BAR_ENABLED, searchBarEnabled)
        arguments.putString(ARGUMENT_TEXT_HINT, textHint)
        arguments.putString(ARGUMENT_TEXT_CLEAR_HISTORY, textClearHistory)
        arguments.putString(ARGUMENT_TEXT_NO_RESULTS, textNoResults)
        arguments.putString(ARGUMENT_TEXT_CLEAR_INPUT, textClearInput)
        arguments.putString(ARGUMENT_TEXT_MORE, textMore)
        arguments.putString(ARGUMENT_HISTORY_ID, historyId)
        return arguments
    }

    fun setActivity(@NonNull activity: AppCompatActivity) {
        this.activity = activity
        if (activity !is SearchPreferenceResultListener) {
            throw IllegalArgumentException("Activity must implement SearchPreferenceResultListener")
        }
    }

    fun setHistoryEnabled(historyEnabled: Boolean) {
        this.historyEnabled = historyEnabled
    }

    fun setHistoryId(historyId: String?) {
        this.historyId = historyId
    }

    fun setBreadcrumbsEnabled(breadcrumbsEnabled: Boolean) {
        this.breadcrumbsEnabled = breadcrumbsEnabled
    }

    fun setSearchBarEnabled(searchBarEnabled: Boolean) {
        this.searchBarEnabled = searchBarEnabled
    }

    fun setFragmentContainerViewId(@IdRes containerResId: Int) {
        this.containerResId = containerResId
    }

    fun useAnimation(centerX: Int, centerY: Int, width: Int, height: Int, @ColorInt colorAccent: Int) {
        revealAnimationSetting = RevealAnimationSetting(centerX, centerY, width, height, colorAccent)
    }

    fun index(@XmlRes resId: Int): SearchIndexItem {
        val item = SearchIndexItem(resId, this)
        filesToIndex.add(item)
        return item
    }

    fun indexItem(): PreferenceItem {
        val preferenceItem = PreferenceItem()
        preferencesToIndex.add(preferenceItem)
        return preferenceItem
    }

    fun indexItem(@NonNull preference: Preference): PreferenceItem {
        val preferenceItem = PreferenceItem()

        preference.key?.let { preferenceItem.key = it }
        preference.summary?.let { preferenceItem.summary = it.toString() }
        preference.title?.let { preferenceItem.title = it.toString() }
        if (preference is ListPreference) {
            preference.entries?.let { preferenceItem.entries = Arrays.toString(it) }
        }
        preferencesToIndex.add(preferenceItem)
        return preferenceItem
    }

    fun getBannedKeys(): ArrayList<String> = bannedKeys

    fun ignorePreference(@NonNull key: String) {
        bannedKeys.add(key)
    }

    fun getFiles(): ArrayList<SearchIndexItem> = filesToIndex

    fun getPreferencesToIndex(): ArrayList<PreferenceItem> = preferencesToIndex

    fun isHistoryEnabled(): Boolean = historyEnabled

    fun getHistoryId(): String? = historyId

    fun isBreadcrumbsEnabled(): Boolean = breadcrumbsEnabled

    fun isSearchBarEnabled(): Boolean = searchBarEnabled

    fun getRevealAnimationSetting(): RevealAnimationSetting? = revealAnimationSetting

    fun getTextClearHistory(): String? = textClearHistory

    fun setTextClearHistory(textClearHistory: String?) {
        this.textClearHistory = textClearHistory
    }

    fun getTextNoResults(): String? = textNoResults

    fun setTextNoResults(textNoResults: String?) {
        this.textNoResults = textNoResults
    }

    fun getTextHint(): String? = textHint

    fun setTextHint(textHint: String?) {
        this.textHint = textHint
    }

    fun getTextClearInput(): String? = textClearInput

    fun setTextClearInput(textClearInput: String?) {
        this.textClearInput = textClearInput
    }

    fun getTextMore(): String? = textMore

    fun setTextMore(textMore: String?) {
        this.textMore = textMore
    }

    class SearchIndexItem private constructor(
        @XmlRes val resId: Int,
        private val searchConfiguration: SearchConfiguration?
    ) : Parcelable {
        var breadcrumb: String = ""

        fun addBreadcrumb(@StringRes breadcrumbRes: Int): SearchIndexItem {
            assertNotParcel()
            return addBreadcrumb(searchConfiguration!!.activity!!.getString(breadcrumbRes))
        }

        fun addBreadcrumb(breadcrumb: String): SearchIndexItem {
            assertNotParcel()
            this.breadcrumb = Breadcrumb.concat(this.breadcrumb, breadcrumb)
            return this
        }

        private fun assertNotParcel() {
            if (searchConfiguration == null) {
                throw IllegalStateException("SearchIndexItems that are restored from parcel can not be modified.")
            }
        }

        fun getResId(): Int = resId

        fun getBreadcrumb(): String = breadcrumb

        fun getSearchConfiguration(): SearchConfiguration? = searchConfiguration

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(this.breadcrumb)
            dest.writeInt(this.resId)
        }

        override fun describeContents(): Int = 0

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SearchIndexItem> = object : Parcelable.Creator<SearchIndexItem> {
                override fun createFromParcel(parcel: Parcel): SearchIndexItem {
                    val breadcrumb = parcel.readString() ?: ""
                    val resId = parcel.readInt()
                    val item = SearchIndexItem(resId, null)
                    item.breadcrumb = breadcrumb
                    return item
                }

                override fun newArray(size: Int): Array<SearchIndexItem?> = arrayOfNulls(size)
            }
        }
    }
}