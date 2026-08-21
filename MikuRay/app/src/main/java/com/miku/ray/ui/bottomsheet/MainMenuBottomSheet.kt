package com.miku.ray.ui.bottomsheet

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.ParticlesController
import com.miku.ray.particlesdrawable.ParticlesView

class MainMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnOptionClickListener {
        fun onOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null
    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnOptionClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_layout_bottom_sheet_main_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val particlesView = view.findViewById<ParticlesView>(R.id.ParticlesView)
        if (particlesView != null) {
            val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_PARTICLES_SHEET, false)
            particlesView.visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) {
                ParticlesController.applyTo(particlesView)
            }
        }
        loadBanner(view)

        val clickListener = View.OnClickListener {
            mListener?.onOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.menu_sub_setting,
            R.id.menu_routing_setting,
            R.id.menu_settings,
            R.id.menu_logcat,
            R.id.menu_backup_restore,
            R.id.menu_about
        )

        actionIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    private fun loadBanner(view: View) {
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet) ?: return
        bannerImageView.setLayerType(View.LAYER_TYPE_NONE, null)
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
        val targetTag = if (uriString.isNullOrBlank()) TAG_SHEET_DEFAULT else uriString
        if (bannerImageView.tag != targetTag) {
            if (!uriString.isNullOrBlank()) {
                val isGif = uriString.lowercase().endsWith(".gif")
                if (isGif) {
                    Glide.with(this)
                        .asGif()
                        .load(Uri.parse(uriString))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .error(R.drawable.uwu_banner_sheet)
                        .into(bannerImageView)
                } else {
                    Glide.with(this)
                        .load(Uri.parse(uriString))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .error(R.drawable.uwu_banner_sheet)
                        .into(bannerImageView)
                }
            } else {
                Glide.with(this).clear(bannerImageView)
                bannerImageView.setImageResource(R.drawable.uwu_banner_sheet)
            }
            bannerImageView.tag = targetTag
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "MainMenuBottomSheet"
    }
}
