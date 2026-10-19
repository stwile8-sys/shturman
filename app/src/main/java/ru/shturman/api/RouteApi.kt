package ru.shturman.api

import android.os.Bundle
import ru.shturman.routing.LatLon
import kotlin.math.roundToLong

/** Договор обмена с приложением «Заказы». Ключи и коды описаны в docs/api/route-api.md. */
object RouteApi {
    const val VERSION = 1
    const val K_VERSION = "v"
    const val MSG_PING = 0
    const val MSG_GEOCODE = 1
    const val MSG_ROUTE = 2

    const val E_DATA_NOT_READY = "DATA_NOT_READY"
    const val E_NO_LOCATION = "NO_LOCATION"
    const val E_NO_ROUTE = "NO_ROUTE"
    const val E_BAD_REQUEST = "BAD_REQUEST"
    const val E_VERSION = "VERSION"
    const val E_FAILED = "FAILED"

    /** Запас на сборы перед выездом. */
    const val BUFFER_MS = 10 * 60_000L
    const val MAX_STOPS = 12

    class Stop(val pos: LatLon, val name: String, val arriveBy: Long, val leaveAt: Long)
    class RouteRequest(val departAt: Long, val from: LatLon?, val stops: List<Stop>)

    fun ok(): Bundle = Bundle().apply { putInt(K_VERSION, VERSION) }
    fun error(code: String): Bundle = ok().apply { putString("error", code) }

    @Suppress("DEPRECATION")
    fun parseRoute(b: Bundle): RouteRequest {
        val list = b.getParcelableArrayList<Bundle>("stops") ?: throw IllegalArgumentException("нет остановок")
        require(list.size in 1..MAX_STOPS)
        val stops = list.map { s ->
            val p = LatLon(s.getDouble("lat", Double.NaN), s.getDouble("lon", Double.NaN))
            require(validPoint(p))
            val arrive = s.getLong("arriveBy", 0L)
            Stop(p, s.getString("name").orEmpty().take(80), arrive, s.getLong("leaveAt", arrive))
        }
        val from = if (b.getBoolean("fromCurrent", true)) null
            else LatLon(b.getDouble("fromLat", Double.NaN), b.getDouble("fromLon", Double.NaN)).also { require(validPoint(it)) }
        val depart = b.getLong("departAt", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        return RouteRequest(depart, from, stops)
    }

    fun validPoint(p: LatLon): Boolean =
        !p.lat.isNaN() && !p.lon.isNaN() && p.lat in -90.0..90.0 && p.lon in -180.0..180.0 && !(p.lat == 0.0 && p.lon == 0.0)
}

/** Кодирование линии маршрута в строку (формат Google Encoded Polyline, точность 6 знаков). */
object Polyline {
    fun encode(points: List<LatLon>, precision: Int = 6): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.lat * factor).roundToLong()
            val lon = (p.lon * factor).roundToLong()
            put(sb, lat - lastLat)
            put(sb, lon - lastLon)
            lastLat = lat; lastLon = lon
        }
        return sb.toString()
    }

    fun decode(s: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var i = 0; var lat = 0L; var lon = 0L
        fun next(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val b = s[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (i < s.length) {
            lat += next(); lon += next()
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }

    private fun put(sb: StringBuilder, v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x.toInt() + 63).toChar())
    }
}

/**
 * Команда «Поехать» из «Заказов»: точка ждёт здесь, пока водитель вошёл, данные готовы и есть GPS,
 * затем маршрут строится и ведение запускается само.
 */
object ExternalTrip {
    class Request(val lat: Double, val lon: Double, val name: String, val orderId: Long, val hasNext: Boolean)

    val pending = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)

    /** Поездка, запущенная из «Заказов» (для кнопки «Следующий заказ» по приезде). */
    @Volatile var active: Request? = null

    const val ACTION_NAVIGATE = "ru.apex.action.NAVIGATE"
    const val ZAKAZY_PACKAGE = "ru.zakazy"
    const val ACTION_NEXT_ORDER = "ru.zakazy.action.NEXT_ORDER"

    fun parse(intent: android.content.Intent?): Request? {
        if (intent?.action != ACTION_NAVIGATE) return null
        if (intent.getIntExtra("v", 0) != RouteApi.VERSION) return null
        val lat = intent.getDoubleExtra("lat", Double.NaN)
        val lon = intent.getDoubleExtra("lon", Double.NaN)
        if (!RouteApi.validPoint(LatLon(lat, lon))) return null
        return Request(lat, lon, intent.getStringExtra("name").orEmpty().take(80).ifEmpty { "Заказ" },
            intent.getLongExtra("orderId", 0L), intent.getBooleanExtra("hasNext", false))
    }

    fun nextOrderIntent(orderId: Long) = android.content.Intent(ACTION_NEXT_ORDER)
        .setPackage(ZAKAZY_PACKAGE)
        .putExtra("orderId", orderId)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
