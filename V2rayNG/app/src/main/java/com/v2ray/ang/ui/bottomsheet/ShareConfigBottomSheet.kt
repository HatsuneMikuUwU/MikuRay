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
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.handler.MmkvManager

class ShareConfigBottomSheet : BaseBottomSheetFragment() {

    interface OnShareOptionClickListener {
        fun onShareOptionClicked(optionId: Int, guid: String)
    }

    private var mListener: OnShareOptionClickListener? = null
    private var configGuid: String = ""
    private var configType: Int = 0
    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnShareOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnShareOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configGuid = arguments?.getString(ARG_GUID) ?: ""
        configType = arguments?.getInt(ARG_CONFIG_TYPE) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_share_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadBanner(view)

        val clickListener = View.OnClickListener {
            mListener?.onShareOptionClicked(it.id, configGuid)
            dismiss()
        }

        view.findViewById<View>(R.id.share_qrcode)?.setOnClickListener(clickListener)

        val shareClipboardView = view.findViewById<View>(R.id.share_clipboard)
        shareClipboardView?.setOnClickListener(clickListener)

        view.findViewById<View>(R.id.share_full_clipboard)?.setOnClickListener(clickListener)

        val typeEnum = EConfigType.fromInt(configType)
        val isCustomConfig = typeEnum?.isComplexType() == true

        if (isCustomConfig) {
            shareClipboardView?.visibility = View.GONE
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
        const val TAG = "ShareConfigBottomSheet"
        private const val ARG_GUID = "arg_guid"
        private const val ARG_CONFIG_TYPE = "arg_config_type"

        fun newInstance(guid: String, configType: Int): ShareConfigBottomSheet {
            return ShareConfigBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_GUID, guid)
                    putInt(ARG_CONFIG_TYPE, configType)
                }
            }
        }
    }
}
