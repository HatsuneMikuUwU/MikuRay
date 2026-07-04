package com.v2ray.ang.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.v2ray.ang.R
import com.v2ray.ang.util.WeatherHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One rendered cell in the hourly strip. */
data class HourlyForecastItem(
    val timeLabel: String,
    val dayLabel: String,
    val tempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int,
    val isNow: Boolean
)

data class DailyForecastItem(
    val weekdayLabel: String,
    val maxTempCelsius: Int,
    val minTempCelsius: Int,
    val precipProbability: Int,
    val iconRes: Int
)

class WeatherForecastActivity : BaseActivity() {
    private var job: Job? = null

    private lateinit var ivIcon: ImageView
    private lateinit var tvCondition: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvFeelsLike: TextView
    private lateinit var tvMaxMin: TextView
    private lateinit var tvError: TextView
    private lateinit var tvSummary: TextView
    private lateinit var cardCurrent: android.view.View
    private lateinit var cardSummary: android.view.View
    private lateinit var cardHourly: android.view.View
    private lateinit var cardDaily: android.view.View
    private lateinit var recyclerHourly: RecyclerView
    private lateinit var recyclerDaily: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_forecast)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.weather_forecast_title))

        ivIcon = findViewById(R.id.ivForecastCurrentIcon)
        tvCondition = findViewById(R.id.tvForecastCurrentCondition)
        tvTemp = findViewById(R.id.tvForecastCurrentTemp)
        tvFeelsLike = findViewById(R.id.tvForecastFeelsLike)
        tvMaxMin = findViewById(R.id.tvForecastMaxMin)
        tvError = findViewById(R.id.tvForecastError)
        tvSummary = findViewById(R.id.tvForecastSummary)
        cardCurrent = findViewById(R.id.cardForecastCurrent)
        cardSummary = findViewById(R.id.cardForecastSummary)
        cardHourly = findViewById(R.id.cardForecastHourly)
        cardDaily = findViewById(R.id.cardForecastDaily)
        recyclerHourly = findViewById(R.id.recyclerForecastHourly)
        recyclerDaily = findViewById(R.id.recyclerForecastDaily)

        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerDaily.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.isNestedScrollingEnabled = false
        recyclerDaily.isNestedScrollingEnabled = false

        val cached = WeatherHelper.getCachedWeatherEntry()
        if (cached != null) {
            render(cached)
        }
        loadForecast(force = false, showLoadingIndicator = cached == null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_weather_forecast, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_refresh_weather -> {
            loadForecast(force = true, showLoadingIndicator = true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadForecast(force: Boolean, showLoadingIndicator: Boolean) {
        job?.cancel()
        if (showLoadingIndicator) {
            showLoading()
        }
        job = lifecycleScope.launch {
            val fresh = WeatherHelper.fetchForecast(this@WeatherForecastActivity, force = force)
            if (showLoadingIndicator) {
                hideLoading()
            }
            if (fresh != null) {
                render(fresh)
            } else if (!cardCurrent.isVisible) {
                tvError.isVisible = true
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun render(entry: WeatherHelper.WeatherCacheEntry) {
        tvError.isVisible = false
        cardCurrent.isVisible = true

        ivIcon.setImageResource(WeatherHelper.iconResForCode(entry.weatherCode, entry.isDay))
        tvCondition.text = getString(WeatherHelper.conditionLabelRes(entry.weatherCode))
        tvTemp.text = "${Math.round(entry.temperatureCelsius)}\u00b0"
        tvFeelsLike.text = getString(
            R.string.weather_feels_like,
            "${Math.round(entry.apparentTemperatureCelsius)}\u00b0"
        )

        val todayMax = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val todayMin = entry.dailyTemperatureMinCelsius.getOrNull(0)
        tvMaxMin.isVisible = todayMax != null && todayMin != null
        if (todayMax != null && todayMin != null) {
            tvMaxMin.text = getString(
                R.string.weather_max_min,
                "${Math.round(todayMax)}\u00b0",
                "${Math.round(todayMin)}\u00b0"
            )
        }

        cardSummary.isVisible = true
        tvSummary.text = buildDaySummary(entry)

        val hourlyItems = buildHourlyItems(entry)
        cardHourly.isVisible = hourlyItems.isNotEmpty()
        recyclerHourly.adapter = WeatherHourlyAdapter(this, hourlyItems)

        val dailyItems = buildDailyItems(entry)
        cardDaily.isVisible = dailyItems.isNotEmpty()
        recyclerDaily.adapter = WeatherDailyAdapter(this, dailyItems)
    }

    /** Rule-based one-paragraph human summary of today's forecast, built from cached data already on hand. */
    private fun buildDaySummary(entry: WeatherHelper.WeatherCacheEntry): String {
        val conditionLabel = getString(WeatherHelper.conditionLabelRes(entry.weatherCode)).lowercase(Locale.getDefault())
        val hi = entry.dailyTemperatureMaxCelsius.getOrNull(0)
        val lo = entry.dailyTemperatureMinCelsius.getOrNull(0)
        val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(0) ?: 0
        val wind = entry.windSpeedKmh

        val parts = mutableListOf<String>()
        parts.add(getString(R.string.weather_summary_condition, conditionLabel))
        if (hi != null && lo != null) {
            parts.add(
                getString(
                    R.string.weather_summary_high_low,
                    "${Math.round(hi)}\u00b0",
                    "${Math.round(lo)}\u00b0"
                )
            )
        }
        when {
            precip >= 70 -> parts.add(getString(R.string.weather_summary_rain_likely))
            precip >= 40 -> parts.add(getString(R.string.weather_summary_rain_possible))
            precip >= 20 -> parts.add(getString(R.string.weather_summary_rain_slight))
        }
        if (wind >= 30) {
            parts.add(getString(R.string.weather_summary_windy))
        }
        return parts.joinToString(" ")
    }

    private fun buildHourlyItems(entry: WeatherHelper.WeatherCacheEntry): List<HourlyForecastItem> {
        val times = entry.hourlyTimeIso
        if (times.isEmpty()) return emptyList()

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
        val startIndex = times.indexOfFirst { it >= nowIso }.let { if (it < 0) 0 else it }
        val endIndex = minOf(times.size, startIndex + 24)

        val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val timeFmt = SimpleDateFormat("HH.mm", Locale.getDefault())
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return (startIndex until endIndex).mapNotNull { i ->
            val iso = times.getOrNull(i) ?: return@mapNotNull null
            val temp = entry.hourlyTemperatureCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.hourlyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.hourlyPrecipitationProbability.getOrNull(i) ?: 0
            val isDay = (entry.hourlyIsDay.getOrNull(i) ?: 1) == 1
            val date = try {
                isoParser.parse(iso)
            } catch (e: Exception) {
                null
            }

            HourlyForecastItem(
                timeLabel = if (i == startIndex) {
                    getString(R.string.weather_now)
                } else {
                    date?.let { timeFmt.format(it) } ?: ""
                },
                dayLabel = date?.let { dayFmt.format(it) } ?: "",
                tempCelsius = Math.round(temp).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, isDay),
                isNow = i == startIndex
            )
        }
    }

    private fun buildDailyItems(entry: WeatherHelper.WeatherCacheEntry): List<DailyForecastItem> {
        val dates = entry.dailyDateIso
        if (dates.isEmpty()) return emptyList()

        val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        return dates.indices.mapNotNull { i ->
            val dateStr = dates.getOrNull(i) ?: return@mapNotNull null
            val max = entry.dailyTemperatureMaxCelsius.getOrNull(i) ?: return@mapNotNull null
            val min = entry.dailyTemperatureMinCelsius.getOrNull(i) ?: return@mapNotNull null
            val code = entry.dailyWeatherCode.getOrNull(i) ?: 0
            val precip = entry.dailyPrecipitationProbabilityMax.getOrNull(i) ?: 0

            val weekdayLabel = if (i == 0) {
                getString(R.string.weather_today)
            } else {
                val date = try {
                    dateParser.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                date?.let { dayFmt.format(it) } ?: ""
            }

            DailyForecastItem(
                weekdayLabel = weekdayLabel,
                maxTempCelsius = Math.round(max).toInt(),
                minTempCelsius = Math.round(min).toInt(),
                precipProbability = precip,
                iconRes = WeatherHelper.iconResForCode(code, true)
            )
        }
    }
}
