@file:Suppress("DEPRECATION")

package com.v2ray.ang.ui

import android.os.Bundle
import com.mikepenz.aboutlibraries.LibsBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLibraryBinding

class LibraryActivity : BaseActivity() {

    private val binding by lazy { ActivityLibraryBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar(binding.toolbar, showHomeAsUp = true, title = getString(R.string.title_oss_license))
        binding.collapsingToolbar.title = getString(R.string.title_oss_license)

        if (savedInstanceState == null) {
            val fragment = LibsBuilder().withEdgeToEdge(true).supportFragment()
            supportFragmentManager.beginTransaction()
                .replace(binding.libraryFragmentContainer.id, fragment).commit()
        }
    }
}
