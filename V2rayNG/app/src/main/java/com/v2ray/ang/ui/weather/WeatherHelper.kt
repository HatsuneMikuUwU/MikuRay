package com.v2ray.ang.ui.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object WeatherHelper {

    @Volatile
    private var isFirstSessionLaunch = true

    data class WeatherResult(
        val emoji: String,
        val tempCelsius: Int
    ) {
        fun getTemperatureString(celsius: Boolean = isDefaultCelsius()): String =
            if (celsius) "${tempCelsius}°C"
            else "${Math.round(tempCelsius * 9.0 / 5.0 + 32)}°F"
    }

    data class WeatherCacheEntry(
        val latitude: Double,
        val longitude: Double,
        val fetchedAtEpochMs: Long,
        val temperatureCelsius: Double,
        val apparentTemperatureCelsius: Double,
        val relativeHumidity: Int,
        val dewPointCelsius: Double,
        val weatherCode: Int,
        val windSpeedKmh: Double,
        val windDirectionDeg: Int,
        val pressureMsl: Double,
        val visibilityMeters: Double,
        val cloudCoverPercent: Int,
        val windGustsKmh: Double,
        val isDay: Boolean,
        val hourlyTimeIso: List<String> = emptyList(),
        val hourlyTemperatureCelsius: List<Double> = emptyList(),
        val hourlyWeatherCode: List<Int> = emptyList(),
        val hourlyPrecipitationProbability: List<Int> = emptyList(),
        val hourlyIsDay: List<Int> = emptyList(),
        val dailyDateIso: List<String> = emptyList(),
        val dailyWeatherCode: List<Int> = emptyList(),
        val dailyTemperatureMaxCelsius: List<Double> = emptyList(),
        val dailyTemperatureMinCelsius: List<Double> = emptyList(),
        val dailyPrecipitationProbabilityMax: List<Int> = emptyList()
    ) {
        fun toWeatherResult(): WeatherResult = WeatherResult(
            emoji = emojiForCode(weatherCode, isDay),
            tempCelsius = Math.round(temperatureCelsius).toInt()
        )
    }

    private fun emojiForCode(code: Int, isDay: Boolean): String = when (code) {
        0, 1    -> if (isDay) "\u2600" else moonPhaseEmoji()
        2       -> if (isDay) "\u26c5" else "\ud83c\udf19"
        3       -> "\u2601"
        45, 48  -> "\ud83d\ude36\u200d\ud83c\udf2b"
        51, 53, 55,
        61, 63,
        80, 81  -> "\ud83c\udf26"
        56, 57,
        65, 66, 67,
        82      -> "\ud83c\udf27"
        71, 73, 75, 77,
        85, 86  -> "\ud83c\udf28"
        95      -> "\u26a1"
        96, 99  -> "\u26c8"
        else    -> if (isDay) "\u2600" else moonPhaseEmoji()
    }

    private fun moonPhaseEmoji(): String {
        val newMoonRef = 2451550.1
        val synodicMonth = 29.53058867
        val julianNow = System.currentTimeMillis() / 86400000.0 + 2440588.0 - 0.5
        val phase = ((julianNow - newMoonRef) % synodicMonth + synodicMonth) % synodicMonth
        return when {
            phase < 1.85  -> "\ud83c\udf1a"
            phase < 5.54  -> "\ud83c\udf1b"
            phase < 9.23  -> "\ud83c\udf13"
            phase < 12.92 -> "\ud83c\udf14"
            phase < 16.61 -> "\ud83c\udf1d"
            phase < 20.30 -> "\ud83c\udf16"
            phase < 23.99 -> "\ud83c\udf17"
            phase < 27.68 -> "\ud83c\udf1c"
            else          -> "\ud83c\udf1a"
        }
    }

    fun iconResForEmoji(emoji: String?): Int {
        if (emoji.isNullOrEmpty()) return R.drawable.ic_weather_sunny
        return when (emoji) {
            "\u2600"                                            -> R.drawable.ic_weather_sunny
            "\u2601"                                            -> R.drawable.ic_cloud
            "\u26c5"                                            -> R.drawable.ic_weather_partly_cloudy_day
            "\ud83c\udf19"                                     -> R.drawable.ic_weather_partly_cloudy_night
            "\ud83c\udf26"                                     -> R.drawable.ic_weather_drizzle
            "\ud83c\udf27"                                     -> R.drawable.ic_weather_rain
            "\u26a1", "\u26c8"                                 -> R.drawable.ic_weather_storm
            "\u2744", "\ud83c\udf28"                          -> R.drawable.ic_weather_snow
            "\ud83d\ude36\u200d\ud83c\udf2b"                  -> R.drawable.ic_weather_fog
            "\ud83c\udf13", "\ud83c\udf14",
            "\ud83c\udf16", "\ud83c\udf17",
            "\ud83c\udf1a", "\ud83c\udf1b",
            "\ud83c\udf1c", "\ud83c\udf1d"                    -> R.drawable.ic_weather_night
            else                                               -> R.drawable.ic_weather_sunny
        }
    }

    fun iconResForCode(code: Int, isDay: Boolean): Int = iconResForEmoji(emojiForCode(code, isDay))

    fun conditionLabelRes(code: Int): Int = when (code) {
        0, 1 -> R.string.weather_condition_clear
        2 -> R.string.weather_condition_partly_cloudy
        3 -> R.string.weather_condition_cloudy
        45, 48 -> R.string.weather_condition_fog
        51, 53, 55,
        61, 63,
        80, 81 -> R.string.weather_condition_rain_light
        56, 57,
        65, 66, 67,
        82 -> R.string.weather_condition_rain_heavy
        71, 73, 75, 77,
        85, 86 -> R.string.weather_condition_snow
        95 -> R.string.weather_condition_thunder
        96, 99 -> R.string.weather_condition_thunder_hail
        else -> R.string.weather_condition_unknown
    }

    fun getCustomLocationRaw(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CUSTOM_LOCATION, "") ?: ""

    fun hasCustomLocation(): Boolean = getCustomLocationRaw().isNotBlank()

    fun getCustomLocationResolvedName(): String? =
        MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_NAME, "")
            ?.takeIf { it.isNotBlank() }

    fun clearCustomLocationCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_RAW_CACHED, "")
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LAT, 0f)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LON, 0f)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_NAME, "")
        clearCache()
    }

    private fun parseLatLon(raw: String): android.location.Location? {
        val parts = raw.split(",").map { it.trim() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
        return android.location.Location("custom").apply {
            latitude = lat
            longitude = lon
        }
    }

    private fun geocodeCustomLocation(raw: String): Pair<android.location.Location, String>? {
        return try {
            val encoded = java.net.URLEncoder.encode(raw, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=id&format=json"
            val body = getBody(url) ?: return null
            val response = JsonUtil.fromJsonSafe(body, OpenMeteoGeocodingResponse::class.java) ?: return null
            val first = response.results?.firstOrNull() ?: return null
            val lat = first.latitude ?: return null
            val lon = first.longitude ?: return null
            val nameParts = listOfNotNull(first.name, first.admin1, first.country)
            val name = nameParts.joinToString(", ")
            val location = android.location.Location("custom").apply {
                latitude = lat
                longitude = lon
            }
            location to name
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveCustomLocation(): android.location.Location? {
        val raw = getCustomLocationRaw()
        if (raw.isBlank()) return null

        val cachedRaw = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_RAW_CACHED, "")
        if (cachedRaw == raw) {
            val lat = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LAT, 0f)
            val lon = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LON, 0f)
            if (lat != 0f || lon != 0f) {
                return android.location.Location("custom").apply {
                    latitude = lat.toDouble()
                    longitude = lon.toDouble()
                }
            }
        }

        val directLatLon = parseLatLon(raw)
        val (location, name) = if (directLatLon != null) {
            directLatLon to raw
        } else {
            geocodeCustomLocation(raw) ?: return null
        }

        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_RAW_CACHED, raw)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LAT, location.latitude.toFloat())
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_LON, location.longitude.toFloat())
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CUSTOM_LOCATION_NAME, name)
        return location
    }

    private suspend fun getEffectiveLocation(
        context: Context,
        force: Boolean = false
    ): android.location.Location? {
        val custom = withContext(Dispatchers.IO) { resolveCustomLocation() }
        if (custom != null) return custom
        return getCurrentLocation(context, force)
    }

    fun isDefaultCelsius(): Boolean {
        val tz = TimeZone.getDefault().id
        return !(tz.startsWith("US/") ||
            tz == "America/Nassau" ||
            tz == "America/Belize" ||
            tz == "America/Cayman" ||
            tz == "Pacific/Palau")
    }

    fun isCelsius(): Boolean {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_USE_CELSIUS, "")
        return if (stored.isNullOrEmpty()) isDefaultCelsius() else stored == "true"
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCachedWeather(): WeatherResult? {
        val entry = readCacheEntry() ?: return null
        if (System.currentTimeMillis() - entry.fetchedAtEpochMs > AppConfig.WEATHER_CACHE_TTL_MS) return null
        return entry.toWeatherResult()
    }

    fun getCachedWeatherStale(): WeatherResult? = readCacheEntry()?.toWeatherResult()

    fun getCachedWeatherEntry(): WeatherCacheEntry? = readCacheEntry()

    fun getCacheAgeMs(): Long {
        val entry = readCacheEntry() ?: return -1L
        return System.currentTimeMillis() - entry.fetchedAtEpochMs
    }

    private fun isCacheValidForLocation(location: android.location.Location): Boolean {
        val entry = readCacheEntry() ?: return false
        if (entry.latitude == 0.0 && entry.longitude == 0.0) return false
        val results = FloatArray(1)
        android.location.Location.distanceBetween(entry.latitude, entry.longitude,
            location.latitude, location.longitude, results)
        val moved = results[0]
        if (moved > AppConfig.WEATHER_LOCATION_STALE_METERS) {
            return false
        }
        return true
    }

    private fun readCacheEntry(): WeatherCacheEntry? {
        val json = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CACHE_ENTRY, "")
        if (json.isNullOrBlank()) return null
        return JsonUtil.fromJsonSafe(json, WeatherCacheEntry::class.java)
    }

    private fun saveCache(entry: WeatherCacheEntry) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_ENTRY, JsonUtil.toJson(entry))
    }

    fun clearCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_ENTRY, "")
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.CLEARTEXT,
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS
                )
            )
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    return listOf(
                        Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809)),
                        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808)),
                        Proxy.NO_PROXY
                    )
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                }
            })
            .build()
    }

    private suspend fun getCurrentLocation(
        context: Context,
        force: Boolean = false
    ): android.location.Location? {
        if (!hasLocationPermission(context)) return null

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        if (!force) {
            try {
                val lastKnown = fusedClient.lastLocation.await()
                if (lastKnown != null) {
                    return lastKnown
                }
            } catch (e: SecurityException) {
                return null
            } catch (e: Exception) {
            }
        }

        val priority = if (hasFineLocationPermission(context))
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val locationRequest = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setDurationMillis(AppConfig.WEATHER_LOCATION_TIMEOUT_MS)
            .setMaxUpdateAgeMillis(if (force) 0L else 60_000L)
            .build()

        return try {
            withTimeoutOrNull(AppConfig.WEATHER_LOCATION_TIMEOUT_MS + 1000L) {
                fusedClient.getCurrentLocation(locationRequest, null).await()
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? =
        fetchWeatherEntry(context, force)?.toWeatherResult()

    suspend fun fetchForecast(context: Context, force: Boolean = false): WeatherCacheEntry? =
        fetchWeatherEntry(context, force)

    private suspend fun fetchWeatherEntry(context: Context, force: Boolean): WeatherCacheEntry? =
        withContext(Dispatchers.IO) {
            val forceRefresh = force || isFirstSessionLaunch
            if (isFirstSessionLaunch) {
                isFirstSessionLaunch = false
            }

            val location = getEffectiveLocation(context, forceRefresh) ?: return@withContext null

            if (!forceRefresh) {
                val cachedEntry = readCacheEntry()
                val cachedFresh = cachedEntry != null &&
                    System.currentTimeMillis() - cachedEntry.fetchedAtEpochMs <= AppConfig.WEATHER_CACHE_TTL_MS
                if (cachedFresh && isCacheValidForLocation(location)) {
                    return@withContext cachedEntry
                }
            }

            try {
                fetchOpenMeteo(location)?.also { saveCache(it) }
            } catch (e: Exception) {
                null
            }
        }

    private fun fetchOpenMeteo(location: android.location.Location): WeatherCacheEntry? {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(location.latitude)
            append("&longitude=").append(location.longitude)
            append("&timezone=auto")
            append("&current=").append(
                listOf(
                    "temperature_2m",
                    "apparent_temperature",
                    "relative_humidity_2m",
                    "dew_point_2m",
                    "weather_code",
                    "wind_speed_10m",
                    "wind_direction_10m",
                    "pressure_msl",
                    "visibility",
                    "cloud_cover",
                    "wind_gusts_10m",
                    "is_day"
                ).joinToString(",")
            )
            append("&hourly=").append(
                listOf(
                    "temperature_2m",
                    "weather_code",
                    "precipitation_probability",
                    "is_day"
                ).joinToString(",")
            )
            append("&daily=").append(
                listOf(
                    "weather_code",
                    "temperature_2m_max",
                    "temperature_2m_min",
                    "precipitation_probability_max"
                ).joinToString(",")
            )
            append("&forecast_days=7")
        }
        val body = getBody(url) ?: return null
        val response = JsonUtil.fromJsonSafe(body, OpenMeteoForecastResponse::class.java) ?: return null
        val current = response.current ?: return null
        val temp = current.temperature ?: return null
        val hourly = response.hourly
        val daily = response.daily
        return WeatherCacheEntry(
            latitude = location.latitude,
            longitude = location.longitude,
            fetchedAtEpochMs = System.currentTimeMillis(),
            temperatureCelsius = temp,
            apparentTemperatureCelsius = current.apparentTemperature,
            relativeHumidity = current.relativeHumidity,
            dewPointCelsius = current.dewPoint,
            weatherCode = current.weatherCode,
            windSpeedKmh = current.windSpeed,
            windDirectionDeg = current.windDirection,
            pressureMsl = current.pressureMsl,
            visibilityMeters = current.visibility,
            cloudCoverPercent = current.cloudCover,
            windGustsKmh = current.windGusts,
            isDay = current.isDay == 1,
            hourlyTimeIso = hourly?.time ?: emptyList(),
            hourlyTemperatureCelsius = hourly?.temperature ?: emptyList(),
            hourlyWeatherCode = hourly?.weatherCode ?: emptyList(),
            hourlyPrecipitationProbability = hourly?.precipitationProbability ?: emptyList(),
            hourlyIsDay = hourly?.isDay ?: emptyList(),
            dailyDateIso = daily?.time ?: emptyList(),
            dailyWeatherCode = daily?.weatherCode ?: emptyList(),
            dailyTemperatureMaxCelsius = daily?.temperatureMax ?: emptyList(),
            dailyTemperatureMinCelsius = daily?.temperatureMin ?: emptyList(),
            dailyPrecipitationProbabilityMax = daily?.precipitationProbabilityMax ?: emptyList()
        )
    }

    private fun getBody(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MikuRay/${BuildConfig.VERSION_NAME} (Android)")
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }

    fun scheduleBackgroundUpdates(context: Context, forceReschedule: Boolean = false) {
        if (!hasCustomLocation() && !hasLocationPermission(context)) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            AppConfig.WEATHER_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(AppConfig.WEATHER_UPDATE_TASK_NAME)
            .build()
        val policy = if (forceReschedule) ExistingPeriodicWorkPolicy.REPLACE
        else ExistingPeriodicWorkPolicy.KEEP
        RemoteWorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(AppConfig.WEATHER_UPDATE_TASK_NAME, policy, request)
    }

    fun cancelBackgroundUpdates(context: Context) {
        RemoteWorkManager.getInstance(context).cancelUniqueWork(AppConfig.WEATHER_UPDATE_TASK_NAME)
    }

    class UpdateWorker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false))
                return Result.success()
                
            if (!hasCustomLocation() && !hasBackgroundLocationPermission(applicationContext))
                return Result.success()
                
            val result = fetchCurrentWeather(applicationContext)
            return if (result != null) Result.success() else Result.retry()
        }
    }
}
