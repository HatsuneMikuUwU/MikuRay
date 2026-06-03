package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.BaseActivity
import com.v2ray.ang.ui.MainActivity

class WelcomeActivity : BaseActivity() {

    private lateinit var page1: LinearLayout
    private lateinit var page2: LinearLayout
    private lateinit var page3: LinearLayout
    private lateinit var page1Button: MaterialButton
    private lateinit var page2Button: MaterialButton
    private lateinit var page3Button: MaterialButton
    private lateinit var page1TextView: TextView
    private lateinit var page2TextView: TextView

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

        initViews()
        setupListeners()
    }

    private fun initViews() {
        page1 = findViewById(R.id.page1)
        page2 = findViewById(R.id.page2)
        page3 = findViewById(R.id.page3)
        page1Button = findViewById(R.id.page_1button)
        page2Button = findViewById(R.id.page_2button)
        page3Button = findViewById(R.id.page_3button)
        page1TextView = findViewById(R.id.page_1textview)
        page2TextView = findViewById(R.id.page_2textview)

        page2.visibility = View.GONE
        page3.visibility = View.GONE
    }

    private fun setupListeners() {
        page1Button.setOnClickListener {
            page1.visibility = View.GONE
            page2.visibility = View.VISIBLE
        }

        page2Button.setOnClickListener {
            page2.visibility = View.GONE
            page3.visibility = View.VISIBLE
        }

        page3Button.setOnClickListener {
            navigateToMain()
        }

        page1TextView.setOnClickListener {
            navigateToMain()
        }

        page2TextView.setOnClickListener {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        MmkvManager.encodeSettings(PREF_WELCOME_SHOW, true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val PREF_WELCOME_SHOW = "pref_welcome_show"
    }
}
