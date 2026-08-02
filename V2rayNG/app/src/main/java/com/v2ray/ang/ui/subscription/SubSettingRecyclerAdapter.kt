package com.v2ray.ang.ui.subscription

import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerSubSettingBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.Utils

class SubSettingRecyclerAdapter(
    private val viewModel: SubscriptionsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>(), ItemTouchHelperAdapter {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subscriptions = viewModel.getAll()
        val subId = subscriptions[position].guid
        val subItem = subscriptions[position].subscription
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        holder.itemSubSettingBinding.tvUrl.text = subItem.url
        holder.itemSubSettingBinding.chkEnable.isChecked = subItem.enabled
        val serverCount = MmkvManager.decodeServerList(subId).size
        holder.itemSubSettingBinding.tvServerCount.text = holder.itemView.context.resources.getQuantityString(
            R.plurals.sub_setting_server_count, serverCount, serverCount
        )
        holder.itemSubSettingBinding.tvLastUpdated.text = Utils.formatTimestamp(subItem.lastUpdated)
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(subId, position)
        }

        holder.itemSubSettingBinding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(subId, position)
        }

        holder.itemSubSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            subItem.enabled = isChecked
            viewModel.update(subId, subItem)
        }

        if (TextUtils.isEmpty(subItem.url)) {
            holder.itemSubSettingBinding.tvUrl.visibility = View.GONE
            holder.itemSubSettingBinding.layoutShare.visibility = View.GONE
            holder.itemSubSettingBinding.chkEnable.visibility = View.GONE
            holder.itemSubSettingBinding.tvLastUpdated.visibility = View.GONE
            holder.itemSubSettingBinding.tvServerCount.visibility = View.GONE
        } else {
            holder.itemSubSettingBinding.tvUrl.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.visibility = View.VISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.VISIBLE
            holder.itemSubSettingBinding.tvLastUpdated.visibility = View.VISIBLE
            holder.itemSubSettingBinding.tvServerCount.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(subItem.url)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerSubSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) :
        BaseViewHolder(itemSubSettingBinding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        viewModel.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        viewModel.commitOrder()
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
}
