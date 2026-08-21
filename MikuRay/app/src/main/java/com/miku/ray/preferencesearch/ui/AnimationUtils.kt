package com.miku.ray.preferencesearch.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

object AnimationUtils {
    fun registerCircularRevealAnimation(context: Context, view: View, revealSettings: RevealAnimationSetting) {
        val startColor = revealSettings.getColorAccent()
        val endColor = getBackgroundColor(view)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    v.removeOnLayoutChangeListener(this)
                    view.visibility = View.VISIBLE
                    val cx = revealSettings.getCenterX()
                    val cy = revealSettings.getCenterY()
                    val width = revealSettings.getWidth()
                    val height = revealSettings.getHeight()
                    val duration = context.resources.getInteger(android.R.integer.config_longAnimTime)

                    val finalRadius = Math.sqrt((width * width + height * height).toDouble()).toFloat()
                    val anim = ViewAnimationUtils.createCircularReveal(v, cx, cy, 0f, finalRadius)
                    anim.duration = duration.toLong()
                    anim.interpolator = FastOutSlowInInterpolator()
                    anim.start()
                    startColorAnimation(view, startColor, endColor, duration)
                }
            })
        }
    }

    private fun startColorAnimation(view: View, startColor: Int, endColor: Int, duration: Int) {
        val anim = ValueAnimator.ofInt(startColor, endColor)
        anim.setEvaluator(ArgbEvaluator())
        anim.addUpdateListener { valueAnimator ->
            view.setBackgroundColor(valueAnimator.animatedValue as Int)
        }
        anim.duration = duration.toLong()
        anim.start()
    }

    fun startCircularExitAnimation(
        context: Context,
        view: View,
        revealSettings: RevealAnimationSetting,
        listener: OnDismissedListener
    ) {
        val startColor = getBackgroundColor(view)
        val endColor = revealSettings.getColorAccent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cx = revealSettings.getCenterX()
            val cy = revealSettings.getCenterY()
            val width = revealSettings.getWidth()
            val height = revealSettings.getHeight()
            val duration = context.resources.getInteger(android.R.integer.config_longAnimTime)

            val initRadius = Math.sqrt((width * width + height * height).toDouble()).toFloat()
            val anim = ViewAnimationUtils.createCircularReveal(view, cx, cy, initRadius, 0f)
            anim.duration = duration.toLong()
            anim.interpolator = FastOutSlowInInterpolator()
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.INVISIBLE
                    listener.onDismissed()
                }
            })
            anim.start()
            startColorAnimation(view, startColor, endColor, duration)
        } else {
            listener.onDismissed()
        }
    }

    private fun getBackgroundColor(view: View): Int {
        var color = Color.TRANSPARENT
        val background: Drawable? = view.background
        if (background is ColorDrawable) {
            color = background.color
        }
        return color
    }

    interface OnDismissedListener {
        fun onDismissed()
    }
}