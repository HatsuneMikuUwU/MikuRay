package com.v2ray.ang.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.CustomDividerItemDecoration
import com.v2ray.ang.util.DPIController
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.ThemeManager
import com.v2ray.ang.util.WindowBlurUtils
import com.qmdeve.blurview.widget.BlurView 

abstract class BaseActivity : AppCompatActivity() {
    private var loadingOverlay: FrameLayout? = null

    // Theme states
    private var currentThemeKey: String = "8"
    private var currentDynamicColor: Boolean = false
    private var currentTrueBlack: Boolean = false
    private var currentUseCustomColor: Boolean = false
    private var currentCustomColor: Int = 0
    private var currentDpi: Int = 0
    private var currentShowBannerHome: Boolean = true
    private var currentBannerUri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        currentThemeKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
        currentDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        currentTrueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        currentUseCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        currentCustomColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        currentDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        currentShowBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        currentBannerUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: ""

        ThemeManager.applyTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    if (f is DialogFragment) {
                        WindowBlurUtils.applyWindowBlur(f.dialog?.window)
                    }
                }
            },
            true
        )
    }

    override fun onResume() {
        super.onResume()

        val newThemeKey = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_THEME) ?: "8"
        val newDynamicColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        val newTrueBlack = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        val newUseCustomColor = MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_COLOR, false)
        val newCustomColor = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_COLOR, 0)
        val newDpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val newShowBannerHome = MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_HOME_BANNER, true)
        val newBannerUri = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_HOME_BANNER_URI) ?: ""

        if (currentThemeKey != newThemeKey ||
            currentDynamicColor != newDynamicColor ||
            currentTrueBlack != newTrueBlack ||
            currentUseCustomColor != newUseCustomColor ||
            currentCustomColor != newCustomColor ||
            currentDpi != newDpi ||
            currentShowBannerHome != newShowBannerHome ||
            currentBannerUri != newBannerUri
        ) {
            currentThemeKey = newThemeKey
            currentDynamicColor = newDynamicColor
            currentTrueBlack = newTrueBlack
            currentUseCustomColor = newUseCustomColor
            currentCustomColor = newCustomColor
            currentDpi = newDpi
            currentShowBannerHome = newShowBannerHome
            currentBannerUri = newBannerUri

            recreate()
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        val root = findViewById<android.view.View>(R.id.main_content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top    = maxOf(systemBars.top,    displayCutout.top),
                bottom = maxOf(systemBars.bottom, displayCutout.bottom),
                left   = maxOf(systemBars.left,   displayCutout.left),
                right  = maxOf(systemBars.right,  displayCutout.right)
            )
            insets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return
        val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
        val localeWrapped = MyContextWrapper.wrap(base, SettingsManager.getLocale())
        val finalContext = if (dpi > 0) DPIController.wrapWithDpi(localeWrapped, dpi) else localeWrapped
        super.attachBaseContext(finalContext)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
            
            val dpi = MmkvManager.decodeSettingsInt(AppConfig.PREF_CUSTOM_DPI, 0)
            if (dpi > 0) {
                overrideConfiguration.densityDpi = dpi
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    protected fun addCustomDividerToRecyclerView(recyclerView: RecyclerView, context: Context?, drawableResId: Int, orientation: Int = DividerItemDecoration.VERTICAL) {
        val drawable = ContextCompat.getDrawable(context!!, drawableResId)
        requireNotNull(drawable) { "Drawable resource not found" }

        val dividerItemDecoration = CustomDividerItemDecoration(drawable, orientation)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    protected fun setupToolbar(toolbar: Toolbar?, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val tb = toolbar ?: findViewById<Toolbar?>(R.id.toolbar)
        tb?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
            title?.let { t -> this.title = t }
        }
    }

    protected fun setContentViewWithToolbar(layoutResId: Int, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        LayoutInflater.from(this).inflate(layoutResId, container, true)
        super.setContentView(base)
        setupToolbar(base, showHomeAsUp, title)
    }

    protected fun setContentViewWithToolbar(childView: View, showHomeAsUp: Boolean = true, title: CharSequence? = null) {
        val base = LayoutInflater.from(this).inflate(R.layout.activity_base, null)
        val container = base.findViewById<FrameLayout>(R.id.content_container)
        container.addView(childView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        super.setContentView(base)
        setupToolbar(base, showHomeAsUp, title)
    }

    private fun setupToolbar(baseRoot: View, showHomeAsUp: Boolean, title: CharSequence?) {
        val toolbar = baseRoot.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.let {
            setSupportActionBar(it)
            supportActionBar?.setDisplayHomeAsUpEnabled(showHomeAsUp)
            title?.let { t -> supportActionBar?.title = t }
        }
    }

    private fun getOrCreateLoadingOverlay(): FrameLayout {
        loadingOverlay?.let { return it }

        val overlay = FrameLayout(this@BaseActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            elevation = 0f

            val isBlurEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_BLUR, false)

            if (isBlurEnabled) {
                val blurView = BlurView(this@BaseActivity, null).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setBlurRadius(12f)
                    setOverlayColor(Color.argb(120, 0, 0, 0))
                }
                addView(blurView)
            } else {
                setBackgroundColor(Color.argb(120, 0, 0, 0))
            }

            val customLoadingView = LayoutInflater.from(this@BaseActivity)
                .inflate(R.layout.layout_custom_loading, this, false)
            
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            addView(customLoadingView, params)
            
            visibility = View.GONE
        }

        val decorView = window.decorView as ViewGroup
        decorView.addView(overlay)
        loadingOverlay = overlay

        return overlay
    }

    protected fun showLoading() {
        runOnUiThread {
            val overlay = getOrCreateLoadingOverlay()
            if (overlay.visibility != View.VISIBLE) {
                overlay.visibility = View.VISIBLE
            }
        }
    }

    protected fun hideLoading() {
        runOnUiThread {
            loadingOverlay?.let {
                if (it.visibility == View.VISIBLE) {
                    it.visibility = View.GONE
                }
            }
        }
    }

    protected fun isLoadingVisible(): Boolean {
        return loadingOverlay?.visibility == View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        loadingOverlay = null
    }
}
