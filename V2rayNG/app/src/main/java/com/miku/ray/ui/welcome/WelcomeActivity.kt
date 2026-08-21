package com.miku.ray.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.splash.SplashActivity

class WelcomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MmkvManager.decodeSettingsBool(PREF_WELCOME_SHOW)) {
            navigateToMain()
            return
        }

        setContentView(R.layout.uwu_activity_welcome)

        val rootLayout = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        setupViewsAndListeners()
    }

    private fun setupViewsAndListeners() {
        val page1 = findViewById<View>(R.id.page1)
        val page2 = findViewById<View>(R.id.page2)
        val page3 = findViewById<View>(R.id.page3)

        page2.visibility = View.GONE
        page3.visibility = View.GONE

        findViewById<View>(R.id.page_1button).setOnClickListener {
            page1.visibility = View.GONE
            page2.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.page_2button).setOnClickListener {
            page2.visibility = View.GONE
            page3.visibility = View.VISIBLE
        }

        val navigateAction = View.OnClickListener { navigateToMain() }

        findViewById<View>(R.id.page_3button).setOnClickListener(navigateAction)
        findViewById<View>(R.id.page_1_skip).setOnClickListener(navigateAction)
        findViewById<View>(R.id.page_2_skip).setOnClickListener(navigateAction)
    }

    private fun navigateToMain() {
        MmkvManager.encodeSettings(PREF_WELCOME_SHOW, true)
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    companion object {
        const val PREF_WELCOME_SHOW = "pref_welcome_show"
    }
}
