package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogUrlTestProgressBinding
import com.v2ray.ang.databinding.ItemUrlTestResultBinding
import com.v2ray.ang.dto.TestProgressInfo
import com.v2ray.ang.extension.vibrateOnError
import com.v2ray.ang.handler.MmkvManager

/**
 * Progress dialog controller shared by the URL/TCP ping test and the country
 * code lookup test. Both tests use the same dialog + result list layout and
 * only differ in how a single result row is rendered, so [Mode] picks the
 * per-row formatting instead of keeping two near-identical classes/layouts.
 */
class TestProgressDialogController(
    private val context: Context,
    private val mode: Mode,
    private val onCancel: () -> Unit
) {
    enum class Mode { URL_TEST, COUNTRY_CODE }

    private var dialog: AlertDialog? = null
    private var binding: DialogUrlTestProgressBinding? = null
    private val adapter = ResultAdapter()

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show(total: Int, @StringRes titleResId: Int = defaultTitleResId()) {
        if (isShowing) {
            val b = binding ?: return
            adapter.clear()
            b.tvTitle.setText(titleResId)
            b.progressIndicator.visibility = View.VISIBLE
            b.progressIndicator.isIndeterminate = true
            b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
            b.negativeButton.visibility = View.VISIBLE
            b.positiveButton.text = context.getString(R.string.action_minimize)
            b.positiveButton.setOnClickListener { dismiss() }
            return
        }

        val b = DialogUrlTestProgressBinding.inflate(LayoutInflater.from(context))
        binding = b
        b.tvTitle.setText(titleResId)
        b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
        b.progressIndicator.isIndeterminate = true
        b.listView.layoutManager = LinearLayoutManager(context)
        b.listView.adapter = adapter
        adapter.clear()

        val d = MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setCancelable(false)
            .create()
        WindowBlurUtils.applyWindowBlur(d.window)

        b.negativeButton.setOnClickListener {
            dismiss()
            onCancel()
        }
        b.positiveButton.setOnClickListener { dismiss() }
        d.setOnDismissListener {
            dialog = null
            binding = null
        }
        d.show()
        dialog = d
    }

    fun update(info: TestProgressInfo) {
        val b = binding ?: return

        if (info.guid.isNotEmpty()) {
            val profile = MmkvManager.decodeServerConfig(info.guid)
            val content = rowContentFor(info)
            adapter.append(
                ResultRow(
                    remarks = profile?.remarks.orEmpty(),
                    protocol = profile?.configType?.name.orEmpty(),
                    resultText = content.text,
                    resultColorRes = content.colorRes
                )
            )
            b.listView.post {
                if (adapter.itemCount > 0) b.listView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }

        if (b.progressIndicator.isIndeterminate) b.progressIndicator.isIndeterminate = false
        if (info.total > 0) {
            b.progressIndicator.setProgressCompat(
                ((info.current.toFloat() / info.total.toFloat()) * 100).toInt(), true
            )
        }
        b.tvCounter.text = context.getString(R.string.test_progress_counter, info.current, info.total)
    }

    fun finish() {
        val b = binding ?: return
        b.progressIndicator.isIndeterminate = false
        b.progressIndicator.setProgressCompat(100, true)
        b.progressIndicator.visibility = View.GONE
        b.negativeButton.visibility = View.GONE
        b.positiveButton.text = context.getString(android.R.string.ok)
        b.positiveButton.setOnClickListener { dismiss() }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
        adapter.clear()
    }

    private fun defaultTitleResId() = when (mode) {
        Mode.URL_TEST -> R.string.title_real_ping_all_server
        Mode.COUNTRY_CODE -> R.string.title_country_code_all_server
    }

    /** Text + color for a single result row, resolved once per [mode]. */
    private data class RowContent(val text: String, @androidx.annotation.ColorRes val colorRes: Int)

    private fun rowContentFor(info: TestProgressInfo): RowContent = when (mode) {
        Mode.URL_TEST -> {
            if (info.delayMillis > 0L) {
                RowContent(context.getString(R.string.test_progress_ping_ms, info.delayMillis), R.color.colorPing)
            } else {
                context.vibrateOnError()
                RowContent(context.getString(R.string.connection_test_fail), R.color.colorPingRed)
            }
        }
        Mode.COUNTRY_CODE -> {
            val code = MmkvManager.decodeServerAffiliationInfo(info.guid)?.countryCode
                ?.trim()?.uppercase()?.takeIf { it.length == 2 }
            if (code == null) {
                context.vibrateOnError()
                RowContent(context.getString(R.string.toast_failure), R.color.colorPingRed)
            } else {
                val text = listOf(Utils.countryCodeToFlag(code), code)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { context.getString(R.string.toast_failure) }
                RowContent(text, R.color.colorPing)
            }
        }
    }

    private data class ResultRow(
        val remarks: String,
        val protocol: String,
        val resultText: String,
        @androidx.annotation.ColorRes val resultColorRes: Int
    )

    private inner class ResultAdapter : RecyclerView.Adapter<ResultAdapter.RowHolder>() {
        private val rows = mutableListOf<ResultRow>()

        fun append(row: ResultRow) {
            rows.add(row)
            notifyItemInserted(rows.size - 1)
        }

        fun clear() {
            val size = rows.size
            if (size > 0) {
                rows.clear()
                notifyItemRangeRemoved(0, size)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            return RowHolder(ItemUrlTestResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(rows[position])
        override fun getItemCount() = rows.size

        inner class RowHolder(private val b: ItemUrlTestResultBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(row: ResultRow) {
                b.tvServerName.text = row.remarks
                b.tvProtocol.text = row.protocol
                b.tvPingResult.text = row.resultText
                b.tvPingResult.setTextColor(ContextCompat.getColor(context, row.resultColorRes))
            }
        }
    }
}
