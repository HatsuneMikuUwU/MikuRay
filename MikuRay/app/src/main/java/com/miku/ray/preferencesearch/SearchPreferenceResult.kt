package com.miku.ray.preferencesearch

import android.content.Context
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView

class SearchPreferenceResult(private val key: String, private val file: Int, private val screen: String) {

    fun getKey(): String = key
    fun getResourceFile(): Int = file
    fun getScreen(): String = screen

    fun highlight(prefsFragment: PreferenceFragmentCompat) {
        Handler().post { doHighlight(prefsFragment) }
    }

    private fun doHighlight(prefsFragment: PreferenceFragmentCompat) {
        val prefResult = prefsFragment.findPreference<Preference>(getKey())
        if (prefResult == null) {
            Log.e("doHighlight", "Preference not found on given screen")
            return
        }
        val recyclerView = prefsFragment.listView
        val adapter = recyclerView.adapter
        if (adapter is PreferenceGroup.PreferencePositionCallback) {
            val callback = adapter
            val position = callback.getPreferenceAdapterPosition(prefResult)
            if (position != RecyclerView.NO_POSITION) {
                recyclerView.scrollToPosition(position)
                recyclerView.postDelayed({
                    val holder = recyclerView.findViewHolderForAdapterPosition(position)
                    if (holder != null) {
                        val oldBackground = holder.itemView.background
                        val color = getColorFromAttr(prefsFragment.requireContext(), android.R.attr.textColorPrimary)
                        holder.itemView.setBackgroundColor((color and 0xFFFFFF) or 0x33000000)
                        Handler().postDelayed({ holder.itemView.background = oldBackground }, 1000L)
                        return@postDelayed
                    }
                    highlightFallback(prefsFragment, prefResult)
                }, 200L)
                return
            }
        }
        highlightFallback(prefsFragment, prefResult)
    }

    private fun highlightFallback(prefsFragment: PreferenceFragmentCompat, prefResult: Preference) {
        val oldIcon = prefResult.icon
        val oldSpaceReserved = prefResult.isIconSpaceReserved
        val arrow = AppCompatResources.getDrawable(prefsFragment.requireContext(), com.miku.ray.remixicon.R.drawable.rmx_arrows_arrow_right_s_line)
        val color = getColorFromAttr(prefsFragment.requireContext(), android.R.attr.textColorPrimary)
        arrow?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        prefResult.icon = arrow
        prefsFragment.scrollToPreference(prefResult)
        Handler().postDelayed({
            prefResult.icon = oldIcon
            prefResult.isIconSpaceReserved = oldSpaceReserved
        }, 1000L)
    }

    private fun getColorFromAttr(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attr, typedValue, true)
        val arr = context.obtainStyledAttributes(typedValue.data, intArrayOf(android.R.attr.textColorPrimary))
        val color = arr.getColor(0, 0xff3F51B5.toInt())
        arr.recycle()
        return color
    }

    fun closeSearchPage(activity: AppCompatActivity) {
        val fm = activity.supportFragmentManager
        fm.beginTransaction().remove(fm.findFragmentByTag(SearchPreferenceFragment.TAG)).commit()
    }
}