package com.v2ray.ang.ui.bottomsheet

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

private const val TRANSITION_DURATION = 300L

private fun View.toggleWithTransition(parentView: ViewGroup, isExpanding: Boolean) {
    TransitionManager.beginDelayedTransition(parentView, AutoTransition().setDuration(TRANSITION_DURATION))
    this.visibility = if (isExpanding) View.VISIBLE else View.GONE
}

private fun ImageView.animateRotation(endDegree: Float) {
    this.animate()
        .rotation(endDegree)
        .setDuration(TRANSITION_DURATION)
        .start()
}

class MoreMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnMoreOptionClickListener {
        fun onMoreOptionClicked(viewId: Int)
    }

    private var mListener: OnMoreOptionClickListener? = null

    private var currentOrder: Int = ORDER_ORIGIN
    private var subscriptionId: String = ""

    private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

    private fun orderKey(): String {
        val subId = subscriptionId.ifEmpty { AppConfig.DEFAULT_SUBSCRIPTION_ID }
        return "${AppConfig.PREF_SERVER_ORDER}_$subId"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnMoreOptionClickListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnMoreOptionClickListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        subscriptionId = arguments?.getString(ARG_SUBSCRIPTION_ID).orEmpty()
        currentOrder = MmkvManager.decodeSettingsInt(orderKey(), ORDER_ORIGIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.uwu_bottom_sheet_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootContainer = view.findViewById<ViewGroup>(R.id.menu_container_parent) ?: view as ViewGroup

        val particlesView = view.findViewById<View>(R.id.ParticlesView)
        if (particlesView != null) {
            val disabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_DISABLE_PARTICLES_SHEET, false)
            particlesView.visibility = if (disabled) View.GONE else View.VISIBLE
        }
        loadBanner(view)

        val qaHeader  = view.findViewById<View>(R.id.quick_actions_expand_header)
        val qaContent = view.findViewById<View>(R.id.quick_actions_expand_content)
        val qaArrow   = view.findViewById<ImageView>(R.id.quick_actions_expand_arrow)
        setupExpandable(rootContainer, qaHeader, qaContent, qaArrow)

        val managementHeader  = view.findViewById<View>(R.id.management_expand_header)
        val managementContent = view.findViewById<View>(R.id.management_expand_content)
        val managementArrow   = view.findViewById<ImageView>(R.id.management_expand_arrow)
        setupExpandable(rootContainer, managementHeader, managementContent, managementArrow)

        val checkOrigin = view.findViewById<CheckedTextView>(R.id.action_order_origin)
        val checkName   = view.findViewById<CheckedTextView>(R.id.action_order_by_name)
        val checkDelay  = view.findViewById<CheckedTextView>(R.id.action_order_by_delay)

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked = order == ORDER_ORIGIN
            checkName?.isChecked   = order == ORDER_BY_NAME
            checkDelay?.isChecked  = order == ORDER_BY_DELAY
        }
        updateChecks(currentOrder)

        val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
        view.findViewById<View>(R.id.reset_traffic)?.visibility = if (isTrafficEnabled) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.card_order_origin)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_origin)?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_name)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_by_name)?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_delay)?.setOnClickListener {
            view.findViewById<View>(R.id.action_order_by_delay)?.performClick()
        }

        val clickListener = View.OnClickListener { v ->
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_order_origin   -> ORDER_ORIGIN
                R.id.action_order_by_name  -> ORDER_BY_NAME
                R.id.action_order_by_delay -> ORDER_BY_DELAY
                else -> currentOrder
            }
            MmkvManager.encodeSettings(orderKey(), newOrder)
            currentOrder = newOrder
            updateChecks(newOrder)
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        listOf(
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(orderClickListener)
        }

        listOf(
            R.id.service_restart,
            R.id.del_all_config,
            R.id.del_duplicate_config,
            R.id.del_invalid_config,
            R.id.export_all,
            R.id.real_ping_all,
            R.id.locate_selected_config,
            R.id.sub_update,
            R.id.reset_traffic
        ).forEach { id ->
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
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_image_about)
                    .into(bannerImageView)
            } else {
                Glide.with(this).clear(bannerImageView)
                bannerImageView.setImageResource(R.drawable.uwu_banner_image_about)
            }
            bannerImageView.tag = targetTag
        }
    }

    private fun setupExpandable(
        parent: ViewGroup,
        toggleHeader: View?,
        expandableContent: View?,
        arrowIcon: ImageView?
    ) {
        if (toggleHeader == null || expandableContent == null) return

        arrowIcon?.rotation = if (expandableContent.visibility == View.VISIBLE) 90f else 0f

        toggleHeader.setOnClickListener {
            val isExpanding = expandableContent.visibility == View.GONE
            expandableContent.toggleWithTransition(parent, isExpanding)
            arrowIcon?.animateRotation(if (isExpanding) 90f else 0f)
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "MoreMenuBottomSheet"

        const val ORDER_ORIGIN   = 0
        const val ORDER_BY_NAME  = 1
        const val ORDER_BY_DELAY = 2

        private const val ARG_SUBSCRIPTION_ID = "subscriptionId"

        fun newInstance(subscriptionId: String): MoreMenuBottomSheet {
            return MoreMenuBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SUBSCRIPTION_ID, subscriptionId)
                }
            }
        }
    }
}
