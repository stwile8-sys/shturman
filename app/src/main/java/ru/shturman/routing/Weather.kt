package ru.shturman.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Погода на час выезда: короткая подпись и на сколько она замедляет езду (1.0 — без замедления). */
class Weather(val label: String, val tempC: Int, val speedFactor: Double)

/** Берёт прогноз Open-Meteo (бесплатно, без ключа). При любой ошибке возвращает null. */
suspend fun fetchWeather(at: LatLon, whenMs: Long): Weather? = withContext(Dispatchers.IO) {
    try {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m,precipitation,snowfall,weather_code&forecast_days=3&timezone=UTC",
            at.lat, at.lon
        )
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
        }
        val text = try {
            if (conn.responseCode != 200) return@withContext null
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
        val hourly = JSONObject(text).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        var idx = 0
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until times.length()) {
            val diff = Math.abs((fmt.parse(times.getString(i)) ?: Date(0)).time - whenMs)
            if (diff < bestDiff) { bestDiff = diff; idx = i }
        }
        val temp = hourly.getJSONArray("temperature_2m").getDouble(idx)
        val rain = hourly.getJSONArray("precipitation").optDouble(idx, 0.0)
        val snow = hourly.getJSONArray("snowfall").optDouble(idx, 0.0)
        val code = hourly.getJSONArray("weather_code").optInt(idx, 0)
        describe(temp, rain, snow, code)
    } catch (e: Exception) {
        null
    }
}

private fun describe(temp: Double, rain: Double, snow: Double, code: Int): Weather {
    val t = temp.roundToInt()
    val (label, factor) = when {
        code in intArrayOf(56, 57, 66, 67) -> "Гололёд" to 0.75
        snow > 0.2 || code in intArrayOf(71, 73, 75, 77, 85, 86) -> "Снег" to 0.82
        rain > 0.2 && temp <= 0.5 -> "Мокрый снег, скользко" to 0.8
        rain >= 2.5 || code in intArrayOf(65, 82, 95, 96, 99) -> "Сильный дождь" to 0.88
        rain > 0.2 || code in intArrayOf(51, 53, 55, 61, 63, 80, 81) -> "Дождь" to 0.94
        code in intArrayOf(45, 48) -> "Туман" to 0.92
        else -> "Без осадков" to 1.0
    }
    return Weather(label, t, factor)
}
