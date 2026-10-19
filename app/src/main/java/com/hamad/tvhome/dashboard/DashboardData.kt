package com.hamad.tvhome.dashboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DayForecast(val weekday: Int, val code: Int, val max: Int, val min: Int)

data class CityWeather(
    val name: String,
    val currentTemp: Int,
    val currentCode: Int,
    val isDay: Boolean,
    val days: List<DayForecast>,
)

data class BtcQuote(val usd: Long, val change24h: Double)

private data class City(val name: String, val lat: Double, val lon: Double)

/**
 * Live data for the dashboard: Open-Meteo forecasts and the CoinGecko BTC price. Both are keyless.
 * The last good response is cached so a short outage keeps showing real (recent) numbers; anything
 * older than [MAX_AGE_MS] is dropped rather than shown as current.
 */
class DashboardData(context: Context) {
    private val prefs = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun weather(): List<CityWeather> = withContext(Dispatchers.IO) {
        CITIES.mapNotNull { city ->
            val key = "wx_${city.name}"
            val body = fetch(
                "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=Asia%2FBaghdad&forecast_days=7",
            )?.also { save(key, it) } ?: cached(key)
            body?.let { runCatching { parseWeather(city.name, JSONObject(it)) }.getOrNull() }
        }
    }

    suspend fun bitcoin(): BtcQuote? = withContext(Dispatchers.IO) {
        val body = fetch("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")
            ?.also { save(KEY_BTC, it) } ?: cached(KEY_BTC)
        body?.let {
            runCatching {
                val b = JSONObject(it).getJSONObject("bitcoin")
                BtcQuote(Math.round(b.getDouble("usd")), b.optDouble("usd_24h_change", 0.0))
            }.getOrNull()
        }
    }

    private fun parseWeather(name: String, o: JSONObject): CityWeather {
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val days = (0 until dates.length()).map { i ->
            val (y, m, d) = dates.getString(i).split("-").map(String::toInt)
            val cal = java.util.GregorianCalendar(y, m - 1, d)
            DayForecast(
                weekday = cal[java.util.Calendar.DAY_OF_WEEK] - 1,
                code = codes.getInt(i),
                max = Math.round(max.getDouble(i)).toInt(),
                min = Math.round(min.getDouble(i)).toInt(),
            )
        }
        return CityWeather(
            name = name,
            currentTemp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            currentCode = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            days = days,
        )
    }

    private fun fetch(url: String): String? = try {
        http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null.also { Log.w(TAG, "HTTP ${r.code} for $url") }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Fetch failed: $url", e)
        null
    }

    private fun save(key: String, body: String) {
        prefs.edit().putString(key, body).putLong("${key}_at", System.currentTimeMillis()).apply()
    }

    private fun cached(key: String): String? {
        val at = prefs.getLong("${key}_at", 0L)
        return if (System.currentTimeMillis() - at < MAX_AGE_MS) prefs.getString(key, null) else null
    }

    private companion object {
        const val TAG = "Dashboard"
        const val KEY_BTC = "btc"
        const val MAX_AGE_MS = 12L * 60 * 60 * 1000
        val CITIES = listOf(
            City("بغداد", 33.3152, 44.3661),
            City("البصرة", 30.5085, 47.7804),
        )
    }
}
