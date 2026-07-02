package com.v2ray.ang.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.v2ray.ang.R

object AppFontResolver {

    private fun fontResId(value: String?): Int = when (value) {
        "google"        -> R.font.googlesansregular
        "roboto"        -> R.font.robotoregular
        "poppins"       -> R.font.poppinsregular
        "chococooky"    -> R.font.chococookyregular
        "simpleday"     -> R.font.simpleday
        "fucek"         -> R.font.fucek
        "sfprodisplay"  -> R.font.sfprodisplay
        "dancingscript" -> R.font.dancingscript
        "cream"         -> R.font.cream
        "oneui"         -> R.font.oneui
        "inconsolata"   -> R.font.incosolata
        "emilyscandy"   -> R.font.emilyscandy
        "summerdream"   -> R.font.summerdream
        "rine"          -> R.font.rine
        "evolve"        -> R.font.evolvesans
        else            -> 0
    }

    fun getTypeface(context: Context, value: String?): Typeface? {
        val resId = fontResId(value)
        if (resId == 0) return null
        
        return try {
            ResourcesCompat.getFont(context, resId)
        } catch (e: Exception) {
            null
        }
    }
}
