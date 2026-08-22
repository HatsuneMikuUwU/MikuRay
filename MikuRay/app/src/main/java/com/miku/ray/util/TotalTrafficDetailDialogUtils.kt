package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.databinding.DialogTotalTrafficDetailBinding
import com.miku.ray.handler.MmkvManager
import com.miku.ray.widget.TrafficBarChartView

private const val HISTORY_DAYS = 7

fun showTotalTrafficDetailDialog(context: Context) {
    val (uploadBytes, downloadBytes) = MmkvManager.getTotalTrafficDetail() ?: (0L to 0L)

    val binding = DialogTotalTrafficDetailBinding.inflate(LayoutInflater.from(context))
    binding.tvUploadValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes)
    binding.tvDownloadValue.text = MmkvManager.formatTrafficBytesPublic(downloadBytes)
    binding.tvCombinedValue.text = MmkvManager.formatTrafficBytesPublic(uploadBytes + downloadBytes)

    val (todayUp, todayDown) = MmkvManager.getTodayTrafficDetail()
    binding.tvTodayValue.text = MmkvManager.formatTrafficBytesPublic(todayUp + todayDown)

    val (monthUp, monthDown) = MmkvManager.getCurrentMonthTrafficDetail()
    binding.tvMonthValue.text = MmkvManager.formatTrafficBytesPublic(monthUp + monthDown)

    val history = MmkvManager.getDailyTrafficHistory(HISTORY_DAYS)
    val hasHistory = history.any { (_, up, down) -> up + down > 0L }
    binding.chartDailyTraffic.isVisible = hasHistory
    binding.tvHistoryEmpty.isVisible = !hasHistory
    binding.chartDailyTraffic.setEntries(
        history.map { (dateKey, up, down) -> TrafficBarChartView.DayEntry(dateKey, up, down) }
    )

    val dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok, null)
        .create()
    WindowBlurUtils.applyWindowBlur(dialog.window)

    dialog.show()
}
