package com.v2ray.ang.ui.main


import com.miku.ray.remixicon.R as RemixR
import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.databinding.ItemRecyclerMainGridBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import java.util.Collections
import com.v2ray.ang.util.IndicatorStyle
import com.v2ray.ang.util.SelectedProfileBannerController
import com.v2ray.ang.util.SensorTextController
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.util.Utils
import com.v2ray.ang.AppConfig

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter,
    FastScrollRecyclerView.SectionedAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM_LIST = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_ITEM_GRID = 3
    }

    override fun getSectionName(position: Int): String {
        val remarks = data.getOrNull(position)?.profile?.remarks.orEmpty()
        return remarks.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: ""
    }

    private var data: MutableList<ServersCache> = mutableListOf()
    private var isGridMode: Boolean = false

    val isServerListEmpty: Boolean
        get() = data.isEmpty()

    @SuppressLint("NotifyDataSetChanged")
    fun setGridMode(gridMode: Boolean) {
        if (isGridMode != gridMode) {
            isGridMode = gridMode
            notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshDisplayPrefs() {
        notifyDataSetChanged()
    }

    private var isRunningObserver: androidx.lifecycle.Observer<Boolean>? = null
    private var selectedBannerController: SelectedProfileBannerController? = null

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val lifecycleOwner = recyclerView.context as? androidx.lifecycle.LifecycleOwner
        if (lifecycleOwner != null) {
            isRunningObserver = androidx.lifecycle.Observer { _ ->
                val selectedGuid = MmkvManager.getSelectServer()
                val position = data.indexOfFirst { it.guid == selectedGuid }
                if (position >= 0) {
                    notifyItemChanged(position)
                }
            }
            mainViewModel.isRunning.observe(lifecycleOwner, isRunningObserver!!)
        }

        val controller = SelectedProfileBannerController(recyclerView.context)
        selectedBannerController = controller
        controller.registerChangeListener {
            val selectedGuid = MmkvManager.getSelectServer()
            val position = data.indexOfFirst { it.guid == selectedGuid }
            if (position >= 0) {
                notifyItemChanged(position)
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        isRunningObserver?.let {
            mainViewModel.isRunning.removeObserver(it)
        }
        selectedBannerController?.unregisterChangeListener()
        selectedBannerController = null
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.views.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            holder.views.tvName.text = profile.remarks
            holder.views.tvStatistics.text = if (profile.configType == EConfigType.POLICYGROUP) {
                getPolicyGroupSubText(context, profile)
            } else {
                SensorTextController.getAddress(profile)
            }
            holder.views.tvType.text = getProtocolName(profile)

            val isNetSecEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_NETWORK_SECURITY_ENABLED) == true
            bindNetworkSecurity(holder, profile, isNetSecEnabled)

            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.views.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            val countryCode = aff?.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
            val countryFlag = Utils.countryCodeToFlag(countryCode)
            holder.views.tvCountryCode.text = listOf(countryFlag, countryCode)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" ")
            holder.views.tvCountryCode.visibility =
                if (countryCode != null) View.VISIBLE else View.GONE
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.views.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            } else {
                holder.views.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
            }

            val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
            val trafficStr = MmkvManager.getProfileTrafficString(guid)

            if (isTrafficEnabled && !trafficStr.isNullOrEmpty()) {
                holder.views.tvTraffic.text = trafficStr
                holder.views.tvTraffic.visibility = View.VISIBLE
            } else {
                holder.views.tvTraffic.visibility = View.GONE
            }

            val isPinned = MmkvManager.isServerPinned(guid)
            holder.views.ivPinIndicator.visibility = if (isPinned) View.VISIBLE else View.GONE

            val isSelectedServer = (guid == MmkvManager.getSelectServer())
            val isVpnConnected = mainViewModel.isRunning.value == true

            if (isSelectedServer && isVpnConnected) {
                holder.views.vStatusDot.setBackgroundResource(R.drawable.blink_color)
                val blinkAnimDrawable = holder.views.vStatusDot.background

                if (blinkAnimDrawable is android.graphics.drawable.AnimationDrawable) {
                    holder.views.vStatusDot.visibility = View.VISIBLE
                    holder.views.vStatusDot.post {
                        if (!blinkAnimDrawable.isRunning) {
                            blinkAnimDrawable.start()
                        }
                    }
                }
            } else {
                val blinkAnimDrawable = holder.views.vStatusDot.background
                if (blinkAnimDrawable is android.graphics.drawable.AnimationDrawable) {
                    blinkAnimDrawable.stop()
                }
                holder.views.vStatusDot.visibility = View.GONE
                holder.views.vStatusDot.background = null
            }

            if (isGridMode) {
                selectedBannerController?.clear(holder.views.layoutIndicator)
                holder.views.layoutIndicator.setBackgroundResource(0)
                holder.views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
                
                if (isSelectedServer) {
                    val strokeWidthPx = (3 * context.resources.displayMetrics.density).toInt()
                    holder.views.layoutCard.strokeWidth = strokeWidthPx
                    holder.views.layoutCard.setStrokeColor(context.getColorAttr("colorPrimary"))
                } else {
                    holder.views.layoutCard.strokeWidth = 0
                }
            } else if (isSelectedServer) {
                val styleName = MmkvManager.decodeSettingsString(
                    AppConfig.PREF_INDICATOR_STYLE,
                    IndicatorStyle.STYLE_0.name
                ) ?: IndicatorStyle.STYLE_0.name
                val indicatorStyle = runCatching {
                    IndicatorStyle.valueOf(styleName)
                }.getOrDefault(IndicatorStyle.STYLE_0)

                val bannerController = selectedBannerController
                if (bannerController != null && bannerController.isEnabled() && bannerController.hasBanner()) {
                    bannerController.applyTo(holder.views.layoutIndicator)
                } else {
                    bannerController?.clear(holder.views.layoutIndicator)
                    holder.views.layoutIndicator.setBackgroundResource(indicatorStyle.drawableRes)
                }
                holder.views.layoutCard.strokeWidth = 0
                holder.views.layoutCard.setCardBackgroundColor(Color.TRANSPARENT)
            } else {
                selectedBannerController?.clear(holder.views.layoutIndicator)
                holder.views.layoutIndicator.setBackgroundResource(0)
                holder.views.layoutCard.strokeWidth = 0
                holder.views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
            }

            val subRemarks = getSubscriptionRemarks(profile)
            holder.views.tvSubscription.text = subRemarks

            val isSubVisible = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE
            holder.views.tvSubscription.visibility = isSubVisible
            holder.views.layoutSubscription.visibility = isSubVisible

            holder.views.layoutShare.visibility = View.VISIBLE
            holder.views.layoutEdit.visibility = View.VISIBLE
            holder.views.layoutRemove.visibility = View.VISIBLE

            holder.views.layoutShare.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, false)
            }

            holder.views.layoutEdit.setOnClickListener {
                adapterListener?.onEdit(guid, position, profile)
            }

            holder.views.layoutRemove.setOnClickListener {
                adapterListener?.onRemove(guid, position)
            }

            val gestureDetector = android.view.GestureDetector(
                context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    // onSingleTapUp fires immediately on finger-up instead of
                    // waiting out the double-tap timeout, so selecting a server
                    // stays instant. Re-tapping the already-selected server here
                    // is a no-op (setSelectServer only acts when guid changes),
                    // so it's safe to let this fire before onDoubleTap resolves.
                    override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                        adapterListener?.onSelectServer(guid)
                        return true
                    }

                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                        if (isSelectedServer) {
                            // Double-tapping the currently selected server opens
                            // the pin/unpin dialog instead of re-selecting it.
                            adapterListener?.onPinToggle(guid, position, isPinned)
                        } else {
                            adapterListener?.onSelectServer(guid)
                        }
                        return true
                    }
                }
            )
            holder.views.infoContainer.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks
            else
                null

        return subRemarks?.take(5) ?: ""
    }

    private fun getProtocolName(profile: ProfileItem): String {
        return profile.configType.name
    }

    private fun getPolicyGroupSubText(context: android.content.Context, profile: ProfileItem): String {
        val subId = profile.policyGroupSubscriptionId
        if (subId.isNullOrEmpty()) {
            return context.getString(R.string.filter_config_all)
        }
        val sub = MmkvManager.decodeSubscriptions().firstOrNull { it.guid == subId }
        val name = sub?.subscription?.remarks?.takeIf { it.isNotBlank() }
        return name ?: subId
    }

    private fun bindNetworkSecurity(
        holder: MainViewHolder,
        profile: ProfileItem,
        enabled: Boolean
    ) {
        val context = holder.itemView.context
        val iconSize = (14 * context.resources.displayMetrics.density).toInt()

        fun makeIcon(drawableRes: Int): android.graphics.drawable.Drawable? {
            val d = ContextCompat.getDrawable(context, drawableRes) ?: return null
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate())
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                wrapped,
                context.getColorAttr("colorOnSurfaceVariant")
            )
            wrapped.setBounds(0, 0, iconSize, iconSize)
            return wrapped
        }

        val isComplex = profile.configType.isComplexType() && profile.configType != EConfigType.CUSTOM
        val isPolicyGroup = profile.configType == EConfigType.POLICYGROUP
        val network = profile.network?.takeIf { it.isNotBlank() }

        val policyGroupTypeLabel = if (isPolicyGroup) {
            val typePos = profile.policyGroupType?.toIntOrNull() ?: 0
            context.resources.getStringArray(R.array.policy_group_type)
                .getOrNull(typePos)
                ?.lowercase()
        } else {
            null
        }

        val security = profile.security?.takeIf { it.isNotBlank() }?.let { sec ->
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                "$sec(insecure)"
            } else {
                sec
            }
        } ?: "none"

        val showAny = enabled && (isPolicyGroup || !isComplex)
        holder.views.layoutNetworkSecurity.visibility =
            if (showAny) View.VISIBLE else View.GONE

        if (enabled && isPolicyGroup && policyGroupTypeLabel != null) {
            holder.views.tvNetwork.text = policyGroupTypeLabel
            holder.views.tvNetwork.setCompoundDrawables(makeIcon(RemixR.drawable.rmx_system_thumb_up_line), null, null, null)
            holder.views.tvNetwork.visibility = View.VISIBLE
        } else if (enabled && !isComplex && network != null) {
            holder.views.tvNetwork.text = network
            holder.views.tvNetwork.setCompoundDrawables(makeIcon(RemixR.drawable.rmx_system_thumb_up_line), null, null, null)
            holder.views.tvNetwork.visibility = View.VISIBLE
        } else {
            holder.views.tvNetwork.visibility = View.GONE
        }

        if (enabled && !isComplex) {
            holder.views.tvSecurity.text = security
            val iconRes = if (security == "none") RemixR.drawable.rmx_lock_unlock_line else RemixR.drawable.rmx_lock_line
            holder.views.tvSecurity.setCompoundDrawables(makeIcon(iconRes), null, null, null)
            holder.views.tvSecurity.visibility = View.VISIBLE
        } else {
            holder.views.tvSecurity.visibility = View.GONE
        }
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM_LIST ->
                MainViewHolder(
                    ListItemViews(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
                )
            VIEW_TYPE_ITEM_GRID ->
                MainViewHolder(
                    GridItemViews(ItemRecyclerMainGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
                )
            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == data.size -> VIEW_TYPE_FOOTER
            isGridMode -> VIEW_TYPE_ITEM_GRID
            else -> VIEW_TYPE_ITEM_LIST
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemTouchHelperViewHolder {
        override fun onItemSelected() {}
        override fun onItemClear() {}
    }

    interface MainItemViews {
        val root: View
        val layoutCard: com.google.android.material.card.MaterialCardView
        val layoutIndicator: View
        val infoContainer: View
        val tvName: android.widget.TextView
        val vStatusDot: View
        val ivPinIndicator: android.widget.ImageView
        val tvType: com.google.android.material.chip.Chip
        val layoutSubscription: View
        val tvSubscription: com.google.android.material.chip.Chip
        val layoutShare: View
        val layoutEdit: View
        val layoutRemove: View
        val tvStatistics: android.widget.TextView
        val tvTestResult: android.widget.TextView
        val tvCountryCode: android.widget.TextView
        val layoutNetworkSecurity: View
        val tvNetwork: android.widget.TextView
        val tvSecurity: android.widget.TextView
        val tvTraffic: android.widget.TextView
    }

    private class ListItemViews(private val b: ItemRecyclerMainBinding) : MainItemViews {
        override val root get() = b.root
        override val layoutCard get() = b.layoutCard
        override val layoutIndicator get() = b.layoutIndicator
        override val infoContainer get() = b.infoContainer
        override val tvName get() = b.tvName
        override val vStatusDot get() = b.vStatusDot
        override val ivPinIndicator get() = b.ivPinIndicator
        override val tvType get() = b.tvType
        override val layoutSubscription get() = b.layoutSubscription
        override val tvSubscription get() = b.tvSubscription
        override val layoutShare get() = b.layoutShare
        override val layoutEdit get() = b.layoutEdit
        override val layoutRemove get() = b.layoutRemove
        override val tvStatistics get() = b.tvStatistics
        override val tvTestResult get() = b.tvTestResult
        override val tvCountryCode get() = b.tvCountryCode
        override val layoutNetworkSecurity get() = b.layoutNetworkSecurity
        override val tvNetwork get() = b.tvNetwork
        override val tvSecurity get() = b.tvSecurity
        override val tvTraffic get() = b.tvTraffic
    }

    private class GridItemViews(private val b: ItemRecyclerMainGridBinding) : MainItemViews {
        override val root get() = b.root
        override val layoutCard get() = b.layoutCard
        override val layoutIndicator get() = b.layoutIndicator
        override val infoContainer get() = b.infoContainer
        override val tvName get() = b.tvName
        override val vStatusDot get() = b.vStatusDot
        override val ivPinIndicator get() = b.ivPinIndicator
        override val tvType get() = b.tvType
        override val layoutSubscription get() = b.layoutSubscription
        override val tvSubscription get() = b.tvSubscription
        override val layoutShare get() = b.layoutShare
        override val layoutEdit get() = b.layoutEdit
        override val layoutRemove get() = b.layoutRemove
        override val tvStatistics get() = b.tvStatistics
        override val tvTestResult get() = b.tvTestResult
        override val tvCountryCode get() = b.tvCountryCode
        override val layoutNetworkSecurity get() = b.layoutNetworkSecurity
        override val tvNetwork get() = b.tvNetwork
        override val tvSecurity get() = b.tvSecurity
        override val tvTraffic get() = b.tvTraffic
    }

    class MainViewHolder(val views: MainItemViews) :
        BaseViewHolder(views.root) {
        override fun onItemSelected() {
            val context = itemView.context
            views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorSurfaceVariant"))
        }
        override fun onItemClear() {
            val context = itemView.context
            views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
        }
    }

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < data.size && toPosition < data.size) {
            Collections.swap(data, fromPosition, toPosition)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {}
    override fun onItemDismiss(position: Int) {}
}
