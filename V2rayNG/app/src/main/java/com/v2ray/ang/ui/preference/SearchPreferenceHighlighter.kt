package com.v2ray.ang.ui.preference

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

object SearchPreferenceHighlighter {

    fun applyFromIntent(fragment: PreferenceFragmentCompat) {
        val key = fragment.activity?.intent
            ?.getStringExtra(AppConfig.EXTRA_HIGHLIGHT_KEY)
            ?: return

        Handler(Looper.getMainLooper()).post {
            jumpAndHighlight(fragment, key)
        }
    }

    private fun jumpAndHighlight(fragment: PreferenceFragmentCompat, key: String) {
        val appBarLayout = fragment.activity?.findViewById<AppBarLayout>(R.id.app_bar)
        val recyclerView = fragment.listView
        
        appBarLayout?.setExpanded(false, false)

        val pref = fragment.findPreference<androidx.preference.Preference>(key) ?: return
        val adapter = recyclerView.adapter ?: return

        fragment.scrollToPreference(pref)

        Handler(Looper.getMainLooper()).postDelayed({
            if (adapter is PreferenceGroup.PreferencePositionCallback) {
                val position = adapter.getPreferenceAdapterPosition(pref)
                if (position != RecyclerView.NO_POSITION) {
                    
                    recyclerView.scrollToPosition(position)
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        val holder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (holder != null) flashCard(holder.itemView)
                    }, 50)
                }
            }
        }, 150)
    }

    private fun flashCard(itemView: View) {
        val card = itemView as? MaterialCardView ?: return
        
        val highlightColor = card.context.getColorAttr("colorPrimary")

        val overlay = MaterialShapeDrawable(card.shapeAppearanceModel).apply {
            setTint(highlightColor) 
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER
        }

        card.foreground = overlay
        overlay.alpha = 0

        val targetAlpha = 128 

        val fadeIn = ObjectAnimator.ofInt(overlay, "alpha", 0, targetAlpha).apply {
            duration = 200
        }
        val fadeOut = ObjectAnimator.ofInt(overlay, "alpha", targetAlpha, 0).apply {
            duration = 400
            startDelay = 800
        }
        
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                card.foreground = null
            }
        })
        
        AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            start()
        }
    }
}
