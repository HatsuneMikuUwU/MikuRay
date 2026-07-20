package com.v2ray.ang.extension

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.getColorAttr
import com.neko.toasty.Toasty
import java.io.Serializable
import java.lang.ref.WeakReference
import java.net.URI
import java.util.Locale

val Context.v2RayApplication: AngApplication?
    get() = applicationContext as? AngApplication

object ForegroundActivityTracker : Application.ActivityLifecycleCallbacks {

    private var resumedActivity: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = resumedActivity?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) {
            resumedActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

private fun Context.vibrateOnError() {
    try {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200L)
            }
        }
    } catch (e: Exception) {
    }
}

fun Context.toast(message: Int) {
    Toasty.normal(this, message).show()
}

fun Context.toast(message: CharSequence) {
    Toasty.normal(this, message).show()
}

fun Context.toastSuccess(message: Int) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastSuccess(message: CharSequence) {
    Toasty.success(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: Int) {
    vibrateOnError()
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastError(message: CharSequence) {
    vibrateOnError()
    Toasty.error(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastInfo(message: Int) {
    Toasty.info(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastInfo(message: CharSequence) {
    Toasty.info(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastWarning(message: Int) {
    Toasty.warning(this, message, Toast.LENGTH_SHORT, true).show()
}

fun Context.toastWarning(message: CharSequence) {
    Toasty.warning(this, message, Toast.LENGTH_SHORT, true).show()
}

private fun showSnackbar(
    context: Context,
    title: CharSequence,
    message: CharSequence,
    @DrawableRes iconRes: Int,
    @AttrRes backgroundColorAttr: Int?,
    @AttrRes textColorAttr: Int?,
    duration: Int
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post {
            showSnackbar(context, title, message, iconRes, backgroundColorAttr, textColorAttr, duration)
        }
        return
    }

    val activity = context as? Activity ?: ForegroundActivityTracker.currentActivity
    val parent = activity?.window?.decorView

    if (activity == null || parent == null) {
        val fallbackMessage = if (title.isNotNullEmpty()) "$title: $message" else message
        Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
        return
    }
    
    val snackbar = Snackbar.make(parent, "", Snackbar.LENGTH_INDEFINITE)
    val snackbarLayout = snackbar.view as ViewGroup
    snackbarLayout.contentDescription = if (title.isNotNullEmpty()) "$title: $message" else message

    snackbarLayout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        ?.visibility = View.INVISIBLE

    val contentView = LayoutInflater.from(activity)
        .inflate(R.layout.layout_snackbar_custom, snackbarLayout, false)

    val resolvedTextColor = if (textColorAttr != null) {
        activity.getColorAttr(textColorAttr)
    } else {
        activity.getColorAttr(R.attr.colorOnSurfaceInverse)
    }

    contentView.findViewById<ImageView>(R.id.iv_snackbar_icon)?.apply {
        setImageResource(iconRes)
        DrawableCompat.setTint(drawable.mutate(), resolvedTextColor)
    }
    contentView.findViewById<TextView>(R.id.tv_snackbar_title)?.apply {
        if (title.isNotNullEmpty()) {
            text = title
            visibility = View.VISIBLE
            setTextColor(resolvedTextColor)
        } else {
            visibility = View.GONE
        }
    }
    contentView.findViewById<TextView>(R.id.tv_snackbar_message)?.apply {
        text = message
        setTextColor(resolvedTextColor)
    }

    snackbarLayout.addView(contentView, 0)

    (snackbarLayout.parent as? ViewGroup)?.bringChildToFront(snackbarLayout)

    val layoutParams = snackbarLayout.layoutParams
    when (layoutParams) {
        is CoordinatorLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
        is FrameLayout.LayoutParams -> layoutParams.gravity = Gravity.TOP
    }
    snackbarLayout.layoutParams = layoutParams

    ViewCompat.setOnApplyWindowInsetsListener(snackbarLayout) { view, insets ->
        val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        
        val margin5dp = (5f * view.context.resources.displayMetrics.density).toInt()
        val margin16dp = (16f * view.context.resources.displayMetrics.density).toInt()

        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarTop + margin5dp
            bottomMargin = margin5dp
            leftMargin = margin16dp
            rightMargin = margin16dp
        }
        insets
    }

    snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE

    snackbarLayout.doOnPreDraw { view ->
        view.translationY = -view.height.toFloat()
        view.animate()
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun slideRightThenDismiss() {
        if (!snackbarLayout.isAttachedToWindow) {
            snackbar.dismiss()
            return
        }
        snackbarLayout.animate()
            .translationX(snackbarLayout.width.toFloat())
            .setDuration(300L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { snackbar.dismiss() }
            .start()
    }

    val autoDismissDelayMs = when (duration) {
        Snackbar.LENGTH_INDEFINITE -> null
        Snackbar.LENGTH_SHORT -> 1500L
        else -> 2750L
    }
    autoDismissDelayMs?.let {
        Handler(Looper.getMainLooper()).postDelayed(::slideRightThenDismiss, it)
    }

    val cornerRadiusPx = 28f * activity.resources.displayMetrics.density
    
    val backgroundColor = if (backgroundColorAttr != null) {
        activity.getColorAttr(backgroundColorAttr)
    } else {
        activity.getColorAttr(R.attr.colorSurfaceInverse)
    }

    snackbarLayout.backgroundTintList = null
    snackbarLayout.backgroundTintMode = null

    snackbarLayout.background = MaterialShapeDrawable(
        ShapeAppearanceModel.builder().setAllCornerSizes(cornerRadiusPx).build()
    ).apply {
        fillColor = ColorStateList.valueOf(backgroundColor)
        elevation = snackbarLayout.elevation
    }

    snackbar.show()
}

fun Context.snackbarDefault(message: Int, title: CharSequence = "") {
    showSnackbar(this, title, getString(message), R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

fun Context.snackbarDefault(message: CharSequence, title: CharSequence = "") {
    showSnackbar(this, title, message, R.drawable.ic_about_24dp, null, null, Snackbar.LENGTH_LONG)
}

fun Context.snackbarSuccess(message: Int, title: CharSequence = "") {
    showSnackbar(
        this, title, getString(message), R.drawable.ic_check_circle,
        R.attr.colorPrimary,
        R.attr.colorOnPrimary,
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarSuccess(message: CharSequence, title: CharSequence = "") {
    showSnackbar(
        this, title, message, R.drawable.ic_check_circle,
        R.attr.colorPrimary,
        R.attr.colorOnPrimary,
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarError(message: Int, title: CharSequence = "") {
    vibrateOnError()
    showSnackbar(
        this, title, getString(message), R.drawable.ic_warning,
        R.attr.colorError,
        R.attr.colorOnError,
        Snackbar.LENGTH_LONG
    )
}

fun Context.snackbarError(message: CharSequence, title: CharSequence = "") {
    vibrateOnError()
    showSnackbar(
        this, title, message, R.drawable.ic_warning,
        R.attr.colorError,
        R.attr.colorOnError,
        Snackbar.LENGTH_LONG
    )
}

const val THRESHOLD = 1000L
const val DIVISOR = 1024.0

fun Long.toSpeedString(): String = this.toTrafficString() + "/s"

fun Long.toTrafficString(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= THRESHOLD && unitIndex < units.size - 1) {
        size /= DIVISOR
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
}

val URI.idnHost: String
    get() = host?.replace("[", "")?.replace("]", "").orEmpty()

fun String?.removeWhiteSpace(): String? = this?.replace(" ", "")

fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

fun String.toLongEx(): Long = toLongOrNull() ?: 0

fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            })
        }
    }

inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}

fun CharSequence?.isNotNullEmpty(): Boolean = !this.isNullOrBlank()

fun String.concatUrl(vararg paths: String): String {
    val builder = StringBuilder(this.trimEnd('/'))

    paths.forEach { path ->
        val trimmedPath = path.trim('/')
        if (trimmedPath.isNotEmpty()) {
            builder.append('/').append(trimmedPath)
        }
    }

    return builder.toString()
}

fun String.matchesPattern(regex: Regex?, keyword: String?, ignoreCase: Boolean = true): Boolean {
    if (keyword.isNullOrEmpty()) {
        return true
    }
    return regex?.containsMatchIn(this)
        ?: this.contains(keyword, ignoreCase = ignoreCase)
}

fun EConfigType.isGroupType(): Boolean {
    return this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}

fun EConfigType.isComplexType(): Boolean {
    return this == EConfigType.CUSTOM || this == EConfigType.POLICYGROUP || this == EConfigType.PROXYCHAIN
}
