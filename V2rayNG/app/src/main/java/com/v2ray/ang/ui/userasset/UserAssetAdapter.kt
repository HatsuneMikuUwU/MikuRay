package com.v2ray.ang.ui.userasset

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerUserAssetBinding
import com.v2ray.ang.extension.toTrafficString

import java.io.File
import java.text.DateFormat
import java.util.Date

class UserAssetAdapter(
    private val viewModel: UserAssetViewModel,
    private val extDir: File,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<UserAssetAdapter.UserAssetViewHolder>() {

    override fun getItemCount() = viewModel.itemCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
        return UserAssetViewHolder(
            ItemRecyclerUserAssetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
        val item = viewModel.getAsset(position) ?: return
        val file = extDir.listFiles()?.find { it.name == item.assetUrl.remarks }

        with(holder.binding) {
            assetName.text = item.assetUrl.remarks

            if (file != null) {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                assetProperties.text = "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
            } else {
                assetProperties.text = root.context.getString(R.string.msg_file_not_found)
            }

            layoutEdit.isVisible = item.assetUrl.locked != true && item.assetUrl.url != "file"

            layoutEdit.setOnClickListener {
                adapterListener?.onEdit(item.guid, position)
            }

            layoutRemove.setOnClickListener {
                adapterListener?.onRemove(item.guid, position)
            }

            layoutCard.setOnClickListener {
            }
        }
    }

    class UserAssetViewHolder(val binding: ItemRecyclerUserAssetBinding) :
        RecyclerView.ViewHolder(binding.root)
}
