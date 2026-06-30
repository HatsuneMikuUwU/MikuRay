package com.v2ray.ang.util

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

    private fun emojiForCode(code: Int, isDay: Boolean): String = when (code) {
        0, 1    -> if (isDay) "\u2600" else moonPhaseEmoji()   // ☀ / moon
        2       -> "\u26c5"                                      // ⛅
        3       -> "\u2601"                                      // ☁
        45, 48  -> "\ud83d\ude36\u200d\ud83c\udf2b"            // 😶‍🌫
        51, 53, 55,
        61, 63,
        80, 81  -> "\ud83c\udf26"                               // 🌦
        56, 57,
        65, 66, 67,
        82      -> "\ud83c\udf27"                               // 🌧
        71, 73, 75, 77,
        85, 86  -> "\ud83c\udf28"                               // 🌨
        95      -> "\u26a1"                                      // ⚡
        96, 99  -> "\u26c8"                                     // ⛈
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
            "\u26c5", "\ud83c\udf24"                          -> R.drawable.ic_cloud
            "\ud83c\udf26", "\ud83c\udf27"                    -> R.drawable.ic_weather_rain
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
            val json = JsonUtil.parseString(body) ?: return null
            val results = json.getAsJsonArray("results") ?: return null
            if (results.size() == 0) return null
            val first = results[0].asJsonObject
            val lat = first.get("latitude")?.asDouble ?: return null
            val lon = first.get("longitude")?.asDouble ?: return null
            val nameParts = listOfNotNull(
                first.get("name")?.asString,
                first.get("admin1")?.asString,
                first.get("country")?.asString
            )
            val name = nameParts.joinToString(", ")
            val location = android.location.Location("custom").apply {
                latitude = lat
                longitude = lon
            }
            location to name
        } catch (e: Exception) {
            LogUtil.w("WeatherHelper", "geocodeCustomLocation failed: ${e.message}")
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
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return null
        if (System.currentTimeMillis() - ts > AppConfig.WEATHER_CACHE_TTL_MS) return null
        return readCacheEntry()
    }

    fun getCachedWeatherStale(): WeatherResult? {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return null
        return readCacheEntry()
    }

    fun getCacheAgeMs(): Long {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return -1L
        return System.currentTimeMillis() - ts
    }

    private fun isCacheValidForLocation(location: android.location.Location): Boolean {
        val cachedLat = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        val cachedLon = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
        if (cachedLat == 0f && cachedLon == 0f) return false
        val results = FloatArray(1)
        android.location.Location.distanceBetween(cachedLat.toDouble(), cachedLon.toDouble(),
            location.latitude, location.longitude, results)
        val moved = results[0]
        if (moved > AppConfig.WEATHER_LOCATION_STALE_METERS) {
            LogUtil.d("WeatherHelper", "Location moved ${moved.toInt()}m > threshold, cache invalid")
            return false
        }
        return true
    }

    private fun readCacheEntry(): WeatherResult? {
        val temp = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return null
        val emoji = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CACHE_EMOJI, "") ?: ""
        return WeatherResult(emoji = emoji, tempCelsius = temp)
    }

    private fun saveCache(result: WeatherResult, location: android.location.Location? = null) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, result.tempCelsius)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, result.emoji)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, System.currentTimeMillis())
        if (location != null) {
            MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, location.latitude.toFloat())
            MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, location.longitude.toFloat())
        }
    }

    fun clearCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, "")
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
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
                    LogUtil.w("WeatherHelper", "Local proxy connection failed: ${ioe?.message}")
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
                    LogUtil.d("WeatherHelper", "Using last known location (Fused)")
                    return lastKnown
                }
            } catch (e: SecurityException) {
                LogUtil.w("WeatherHelper", "SecurityException getting last location: ${e.message}")
                return null
            } catch (e: Exception) {
                LogUtil.w("WeatherHelper", "Failed to get last location: ${e.message}")
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
            }.also { loc ->
                if (loc == null) LogUtil.w("WeatherHelper", "Fused location request returned null")
            }
        } catch (e: SecurityException) {
            LogUtil.w("WeatherHelper", "SecurityException on getCurrentLocation: ${e.message}")
            null
        } catch (e: Exception) {
            LogUtil.w("WeatherHelper", "getCurrentLocation failed: ${e.message}")
            null
        }
    }

    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? =
        withContext(Dispatchers.IO) {
            val forceRefresh = force || isFirstSessionLaunch
            if (isFirstSessionLaunch) {
                isFirstSessionLaunch = false
                LogUtil.d("WeatherHelper", "Force refresh trigger: First session launch")
            }

            val location = getEffectiveLocation(context, forceRefresh) ?: return@withContext null
            
            if (!forceRefresh) {
                val cached = getCachedWeather()
                if (cached != null && isCacheValidForLocation(location)) {
                    LogUtil.d("WeatherHelper", "Cache still valid for current location, skipping fetch")
                    return@withContext cached
                }
            }
            
            try {
                fetchOpenMeteo(location)?.also { saveCache(it, location) }
            } catch (e: Exception) {
                LogUtil.e("WeatherHelper", "fetchCurrentWeather failed: ${e.message}")
                null
            }
        }

    private fun fetchOpenMeteo(location: android.location.Location): WeatherResult? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code,is_day"
        val body = getBody(url) ?: return null
        val json = JsonUtil.parseString(body) ?: return null
        val current = json.getAsJsonObject("current") ?: return null
        val temp = current.get("temperature_2m")?.asDouble ?: return null
        val code = current.get("weather_code")?.asInt ?: 0
        val isDay = (current.get("is_day")?.asInt ?: 1) == 1
        return WeatherResult(
            emoji = emojiForCode(code, isDay),
            tempCelsius = Math.round(temp).toInt()
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
