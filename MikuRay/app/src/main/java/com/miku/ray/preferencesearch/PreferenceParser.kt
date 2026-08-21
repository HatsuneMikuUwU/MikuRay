package com.miku.ray.preferencesearch

import android.content.Context
import android.text.TextUtils
import android.util.Log
import org.xmlpull.v1.XmlPullParser

class PreferenceParser(private val context: Context) {
    companion object {
        private const val MAX_RESULTS = 10
        private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
        private const val NS_SEARCH = "http://schemas.android.com/apk/com.miku.ray.preferencesearch"
        private val BLACKLIST = listOf(SearchPreference::class.java.name)
        private val CONTAINERS = listOf("PreferenceCategory", "PreferenceScreen")
    }

    private val allEntries = ArrayList<PreferenceItem>()

    fun addResourceFile(item: SearchConfiguration.SearchIndexItem) {
        allEntries.addAll(parseFile(item))
    }

    fun addPreferenceItems(preferenceItems: ArrayList<PreferenceItem>) {
        allEntries.addAll(preferenceItems)
    }

    private fun parseFile(item: SearchConfiguration.SearchIndexItem): ArrayList<PreferenceItem> {
        val results = ArrayList<PreferenceItem>()
        val xpp: XmlPullParser = context.resources.getXml(item.getResId())
        val bannedKeys = item.getSearchConfiguration().getBannedKeys()

        try {
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            xpp.setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            val breadcrumbs = ArrayList<String>()
            val keyBreadcrumbs = ArrayList<String?>()
            if (!TextUtils.isEmpty(item.getBreadcrumb())) {
                breadcrumbs.add(item.getBreadcrumb())
            }
            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG) {
                    val result = parseSearchResult(xpp)
                    result.resId = item.getResId()

                    if (!BLACKLIST.contains(xpp.name)
                        && result.hasData()
                        && "true" != getAttribute(xpp, NS_SEARCH, "ignore")
                        && !bannedKeys.contains(result.key)
                    ) {
                        result.breadcrumbs = joinBreadcrumbs(breadcrumbs)
                        result.keyBreadcrumbs = cleanupKeyBreadcrumbs(keyBreadcrumbs)
                        results.add(result)
                    }
                    if (CONTAINERS.contains(xpp.name)) {
                        breadcrumbs.add(result.title ?: "")
                    }
                    if (xpp.name == "PreferenceScreen") {
                        keyBreadcrumbs.add(getAttribute(xpp, "key"))
                    }
                } else if (xpp.eventType == XmlPullParser.END_TAG && CONTAINERS.contains(xpp.name)) {
                    if (breadcrumbs.isNotEmpty()) {
                        breadcrumbs.removeAt(breadcrumbs.size - 1)
                    }
                    if (xpp.name == "PreferenceScreen" && keyBreadcrumbs.isNotEmpty()) {
                        keyBreadcrumbs.removeAt(keyBreadcrumbs.size - 1)
                    }
                }

                xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun cleanupKeyBreadcrumbs(keyBreadcrumbs: ArrayList<String?>): ArrayList<String> {
        val result = ArrayList<String>()
        for (keyBreadcrumb in keyBreadcrumbs) {
            if (keyBreadcrumb != null) {
                result.add(keyBreadcrumb)
            }
        }
        return result
    }

    private fun joinBreadcrumbs(breadcrumbs: ArrayList<String>): String {
        var result = ""
        for (crumb in breadcrumbs) {
            if (!TextUtils.isEmpty(crumb)) {
                result = Breadcrumb.concat(result, crumb)
            }
        }
        return result
    }

    private fun getAttribute(xpp: XmlPullParser, namespace: String?, attribute: String): String? {
        for (i in 0 until xpp.attributeCount) {
            Log.d("ns", xpp.getAttributeNamespace(i))
            if (attribute == xpp.getAttributeName(i) &&
                (namespace == null || namespace == xpp.getAttributeNamespace(i))
            ) {
                return xpp.getAttributeValue(i)
            }
        }
        return null
    }

    private fun getAttribute(xpp: XmlPullParser, attribute: String): String? {
        return if (hasAttribute(xpp, NS_SEARCH, attribute)) {
            getAttribute(xpp, NS_SEARCH, attribute)
        } else {
            getAttribute(xpp, NS_ANDROID, attribute)
        }
    }

    private fun hasAttribute(xpp: XmlPullParser, namespace: String?, attribute: String): Boolean {
        return getAttribute(xpp, namespace, attribute) != null
    }

    private fun parseSearchResult(xpp: XmlPullParser): PreferenceItem {
        val result = PreferenceItem()
        result.title = readString(getAttribute(xpp, "title"))
        result.summary = readString(getAttribute(xpp, "summary"))
        result.key = readString(getAttribute(xpp, "key"))
        result.entries = readStringArray(getAttribute(xpp, "entries"))
        result.keywords = readString(getAttribute(xpp, NS_SEARCH, "keywords"))

        Log.d("PreferenceParser", "Found: ${xpp.name}/$result")
        return result
    }

    private fun readStringArray(s: String?): String? {
        if (s == null) return null
        if (s.startsWith("@")) {
            try {
                val id = Integer.parseInt(s.substring(1))
                val elements = context.resources.getStringArray(id)
                return TextUtils.join(",", elements)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return s
    }

    private fun readString(s: String?): String? {
        if (s == null) return null
        if (s.startsWith("@")) {
            try {
                val id = Integer.parseInt(s.substring(1))
                return context.getString(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return s
    }

    fun searchFor(keyword: String?): List<PreferenceItem> {
        if (TextUtils.isEmpty(keyword)) {
            return ArrayList()
        }
        val results = ArrayList<PreferenceItem>()

        for (item in allEntries) {
            if (item.matches(keyword!!)) {
                results.add(item)
            }
        }

        return if (results.size > MAX_RESULTS) {
            results.subList(0, MAX_RESULTS)
        } else {
            results
        }
    }
}