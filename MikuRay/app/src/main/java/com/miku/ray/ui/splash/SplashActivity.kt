package com.miku.ray.ui.splash

import com.miku.ray.ui.main.MainActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.miku.ray.AppConfig.PREF_SHOW_SPLASH
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.util.AppNameHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!isTaskRoot) {
            val intentAction = intent.action
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction == Intent.ACTION_MAIN) {
                finish()
                return
            }
        }

        super.onCreate(savedInstanceState)

        if (!MmkvManager.decodeSettingsBool(PREF_SHOW_SPLASH, false)) {
            navigateToMain()
            return
        }

        setContentView(R.layout.uwu_activity_splash)

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

        findViewById<TextView>(R.id.splash_name).text = AppNameHelper.getDisplayName(this)

        val versionText = findViewById<TextView>(R.id.splash_version)
        versionText.text = getString(
            R.string.uwu_splash_summary,
            getString(R.string.uwu_version_name),
            getString(R.string.uwu_version_code).toInt()
        )

        lifecycleScope.launch {
            delay(2000)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)

        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.fade_in,
            R.anim.fade_out
        )

        startActivity(intent, options.toBundle())

        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }
}
