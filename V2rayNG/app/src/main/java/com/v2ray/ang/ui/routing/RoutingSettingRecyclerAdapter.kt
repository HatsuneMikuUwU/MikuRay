package com.v2ray.ang.ui.routing

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerRoutingSettingBinding
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder

class RoutingSettingRecyclerAdapter(
    private val viewModel: RoutingSettingsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<RoutingSettingRecyclerAdapter.MainViewHolder>(),
    ItemTouchHelperAdapter {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val rulesets = viewModel.getAll()
        val ruleset = rulesets[position]

        holder.itemRoutingSettingBinding.remarks.text = ruleset.remarks
        holder.itemRoutingSettingBinding.domainIp.text = (ruleset.domain ?: ruleset.ip ?: ruleset.process ?: ruleset.port)?.toString()
        holder.itemRoutingSettingBinding.outboundTag.text = ruleset.outboundTag
        holder.itemRoutingSettingBinding.chkEnable.isChecked = ruleset.enabled

        holder.itemRoutingSettingBinding.imgLocked.isVisible = ruleset.locked == true

        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemRoutingSettingBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit("", position)
        }

        holder.itemRoutingSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            ruleset.enabled = isChecked
            viewModel.update(position, ruleset)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerRoutingSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemRoutingSettingBinding: ItemRecyclerRoutingSettingBinding) :
        BaseViewHolder(itemRoutingSettingBinding.root), ItemTouchHelperViewHolder {

        override fun onItemSelected() {
            val context = itemView.context
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true)
            itemRoutingSettingBinding.layoutCard.setCardBackgroundColor(typedValue.data)
        }

        override fun onItemClear() {
            val context = itemView.context
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorCard, typedValue, true)
            itemRoutingSettingBinding.layoutCard.setCardBackgroundColor(typedValue.data)
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemTouchHelperViewHolder {
        override fun onItemSelected() {}
        override fun onItemClear() {}
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        viewModel.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }
}
