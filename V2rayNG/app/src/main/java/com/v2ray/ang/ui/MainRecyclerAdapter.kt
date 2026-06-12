package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel
import java.util.Collections
import com.v2ray.ang.util.IndicatorStyle
import com.v2ray.ang.AppConfig

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private var data: MutableList<ServersCache> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.itemMainBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            //Name address
            holder.itemMainBinding.tvName.text = profile.remarks
            holder.itemMainBinding.tvStatistics.text = getAddress(profile)
            holder.itemMainBinding.tvType.text = getProtocolName(profile)

            // Network & security icon+text di bawah alamat
            val isNetSecEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_NETWORK_SECURITY_ENABLED) == true
            bindNetworkSecurity(holder, profile, isNetSecEnabled)

            //TestResult
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            if ((aff?.testDelayMillis ?: 0L) < 0L) {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
            }

            val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
            val trafficStr = MmkvManager.getProfileTrafficString(guid)
            
            if (isTrafficEnabled && !trafficStr.isNullOrEmpty()) {
                holder.itemMainBinding.tvTraffic.text = trafficStr
                holder.itemMainBinding.tvTraffic.visibility = View.VISIBLE
            } else {
                holder.itemMainBinding.tvTraffic.visibility = View.GONE
            }

            //layoutIndicator & Card Background (Transparent when selected)
            if (guid == MmkvManager.getSelectServer()) {
                val styleName = MmkvManager.decodeSettingsString(
                    AppConfig.PREF_INDICATOR_STYLE,
                    IndicatorStyle.STYLE_0.name
                ) ?: IndicatorStyle.STYLE_0.name
                val indicatorStyle = runCatching {
                    IndicatorStyle.valueOf(styleName)
                }.getOrDefault(IndicatorStyle.STYLE_0)
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(indicatorStyle.drawableRes)
                holder.itemMainBinding.layoutCard.setCardBackgroundColor(Color.TRANSPARENT)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(0)
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.colorCard, typedValue, true)
                holder.itemMainBinding.layoutCard.setCardBackgroundColor(typedValue.data)
            }

            //subscription remarks
            val subRemarks = getSubscriptionRemarks(profile)
            holder.itemMainBinding.tvSubscription.text = subRemarks
            
            val isSubVisible = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE
            holder.itemMainBinding.tvSubscription.visibility = isSubVisible
            holder.itemMainBinding.layoutSubscription.visibility = isSubVisible

            //layout
            holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
            holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
            holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE

            holder.itemMainBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(guid, profile, position, false)
            }

            holder.itemMainBinding.layoutEdit.setOnClickListener {
                adapterListener?.onEdit(guid, position, profile)
            }
            
            holder.itemMainBinding.layoutRemove.setOnClickListener {
                adapterListener?.onRemove(guid, position)
            }

            holder.itemMainBinding.infoContainer.setOnClickListener {
                adapterListener?.onSelectServer(guid)
            }
        }
    }

    private fun getAddress(profile: ProfileItem): String {
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
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

    private fun bindNetworkSecurity(
        holder: MainViewHolder,
        profile: ProfileItem,
        enabled: Boolean
    ) {
        val context = holder.itemView.context
        
        val iconSize = (14 * context.resources.displayMetrics.density).toInt()

        fun makeIcon(drawableRes: Int): android.graphics.drawable.Drawable? {
            val d = androidx.core.content.ContextCompat.getDrawable(context, drawableRes) ?: return null
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate())
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                wrapped,
                com.google.android.material.color.MaterialColors.getColor(
                    holder.itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            wrapped.setBounds(0, 0, iconSize, iconSize)
            return wrapped
        }

        val isComplex = profile.configType.isComplexType()
        val network = profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }
        val security = profile.security?.takeIf { it.isNotBlank() }

        val showAny = enabled && !isComplex && (network != null || security != null)
        holder.itemMainBinding.layoutNetworkSecurity.visibility =
            if (showAny) View.VISIBLE else View.GONE

        // tv_network
        if (enabled && !isComplex && network != null) {
            holder.itemMainBinding.tvNetwork.text = network
            holder.itemMainBinding.tvNetwork.setCompoundDrawables(makeIcon(R.drawable.ic_web_24dp), null, null, null)
            holder.itemMainBinding.tvNetwork.visibility = View.VISIBLE
        } else {
            holder.itemMainBinding.tvNetwork.visibility = View.GONE
        }

        // tv_security
        if (enabled && !isComplex && security != null) {
            holder.itemMainBinding.tvSecurity.text = security
            holder.itemMainBinding.tvSecurity.setCompoundDrawables(makeIcon(R.drawable.ic_lock_24dp), null, null, null)
            holder.itemMainBinding.tvSecurity.visibility = View.VISIBLE
        } else {
            holder.itemMainBinding.tvSecurity.visibility = View.GONE
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
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemTouchHelperViewHolder {
        override fun onItemSelected() {}
        override fun onItemClear() {}
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root) {
        
        override fun onItemSelected() {
            val context = itemView.context
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            itemMainBinding.layoutCard.setCardBackgroundColor(typedValue.data)
        }

        override fun onItemClear() {
            val context = itemView.context
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorCard, typedValue, true)
            itemMainBinding.layoutCard.setCardBackgroundColor(typedValue.data)
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

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }
}
