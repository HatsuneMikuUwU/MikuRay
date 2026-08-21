package com.miku.ray.preferencesearch

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.XmlRes
import java.util.ArrayList
import java.util.Locale

class PreferenceItem() : ListItem(), Parcelable {
    companion object {
        const val TYPE = 2

        @JvmField
        val CREATOR: Parcelable.Creator<PreferenceItem> = object : Parcelable.Creator<PreferenceItem> {
            override fun createFromParcel(parcel: Parcel): PreferenceItem {
                return PreferenceItem(parcel)
            }

            override fun newArray(size: Int): Array<PreferenceItem?> {
                return arrayOfNulls(size)
            }
        }
    }

    var title: String? = null
    var summary: String? = null
    var key: String? = null
    var entries: String? = null
    var breadcrumbs: String? = null
    var keywords: String? = null
    var keyBreadcrumbs: ArrayList<String> = ArrayList()
    var resId: Int = 0

    private constructor(parcel: Parcel) : this() {
        title = parcel.readString()
        summary = parcel.readString()
        key = parcel.readString()
        breadcrumbs = parcel.readString()
        keywords = parcel.readString()
        resId = parcel.readInt()
    }

    fun hasData(): Boolean {
        return title != null || summary != null
    }

    fun matches(keyword: String): Boolean {
        val locale = Locale.getDefault()
        return getInfo().lowercase(locale).contains(keyword.lowercase(locale))
    }

    private fun getInfo(): String {
        val infoBuilder = StringBuilder()
        if (!title.isNullOrEmpty()) {
            infoBuilder.append("ø").append(title)
        }
        if (!summary.isNullOrEmpty()) {
            infoBuilder.append("ø").append(summary)
        }
        if (!entries.isNullOrEmpty()) {
            infoBuilder.append("ø").append(entries)
        }
        if (!breadcrumbs.isNullOrEmpty()) {
            infoBuilder.append("ø").append(breadcrumbs)
        }
        if (!keywords.isNullOrEmpty()) {
            infoBuilder.append("ø").append(keywords)
        }
        return infoBuilder.toString()
    }

    fun withKey(key: String?): PreferenceItem {
        this.key = key
        return this
    }

    fun withSummary(summary: String?): PreferenceItem {
        this.summary = summary ?: ""
        return this
    }

    fun withTitle(title: String?): PreferenceItem {
        this.title = title
        return this
    }

    fun withEntries(entries: String?): PreferenceItem {
        this.entries = entries
        return this
    }

    fun withKeywords(keywords: String?): PreferenceItem {
        this.keywords = keywords
        return this
    }

    fun withResId(@XmlRes resId: Int?): PreferenceItem {
        this.resId = resId ?: 0
        return this
    }

    fun addBreadcrumb(breadcrumb: String?): PreferenceItem {
        this.breadcrumbs = Breadcrumb.concat(this.breadcrumbs, breadcrumb)
        return this
    }

    override fun toString(): String {
        return "PreferenceItem: $title $summary $key"
    }

    override fun getType(): Int {
        return TYPE
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(summary)
        parcel.writeString(key)
        parcel.writeString(breadcrumbs)
        parcel.writeString(keywords)
        parcel.writeInt(resId)
    }
}