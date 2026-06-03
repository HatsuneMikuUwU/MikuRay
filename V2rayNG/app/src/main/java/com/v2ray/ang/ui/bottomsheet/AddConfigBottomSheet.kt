package com.v2ray.ang.ui.bottomsheet

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.request.target.Target
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

class AddConfigBottomSheet : BaseBottomSheetFragment() {

    interface OnAddConfigClickListener {
        fun onAddConfigOptionClicked(viewId: Int)
    }

    private var mListener: OnAddConfigClickListener? = null
    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAddConfigClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnAddConfigClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_layout_bottom_sheet_add_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadBanner(view)

        val clickListener = View.OnClickListener {
            mListener?.onAddConfigOptionClicked(it.id)
            dismiss()
        }

        val actionIds = listOf(
            R.id.import_qrcode,
            R.id.import_clipboard,
            R.id.import_local,
            R.id.import_manually_policy_group,
            R.id.import_manually_proxy_chain,
            R.id.import_manually_vmess,
            R.id.import_manually_vless,
            R.id.import_manually_ss,
            R.id.import_manually_socks,
            R.id.import_manually_http,
            R.id.import_manually_trojan,
            R.id.import_manually_wireguard,
            R.id.import_manually_hysteria2
        )

        actionIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    private fun loadBanner(view: View) {
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet) ?: return
        bannerImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
        val targetTag = if (uriString.isNullOrBlank()) TAG_SHEET_DEFAULT else uriString
        if (bannerImageView.tag != targetTag) {
            if (!uriString.isNullOrBlank()) {
                Glide.with(this)
                    .load(Uri.parse(uriString))
                    .downsample(DownsampleStrategy.NONE)
                    .set(GifOptions.DECODE_FORMAT, DecodeFormat.PREFER_ARGB_8888)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .override(Target.SIZE_ORIGINAL)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .skipMemoryCache(false)
                    .error(R.drawable.uwu_banner_image_about)
                    .into(bannerImageView)
            } else {
                Glide.with(this).clear(bannerImageView)
                bannerImageView.setImageResource(R.drawable.uwu_banner_image_about)
            }
            bannerImageView.tag = targetTag
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "AddConfigBottomSheet"
    }
}
