package ru.shturman.routing

import com.valhalla.valhalla.Valhalla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Участок маршрута с известным ограничением скорости, км/ч. */
class LimitSeg(val startM: Double, val endM: Double, val kmh: Int)

/** Результат построения: варианты маршрута и признак, что время выезда учтено движком. */
class RouteResult(val options: List<RouteOption>, val timeAware: Boolean)

/**
 * Строит маршруты через Valhalla прямо на телефоне, по скачанным дорожным данным.
 * Время выезда передаётся движку, чтобы маршрут считался на нужный момент.
 */
class Router(private val roadData: RoadData) {

    private var engine: Valhalla? = null

    @Synchronized
    private fun engine(): Valhalla {
        engine?.let { return it }
        val configFile = writeConfig()
        return Valhalla(configFile.absolutePath).also { engine = it }
    }

    /** Берём стандартные настройки из библиотеки и подставляем пути к нашим файлам. */
    private fun writeConfig(): File {
        val text = Valhalla::class.java
            .getResourceAsStream("/com/valhalla/valhalla/default.json")
            ?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("В библиотеке нет настроек Valhalla")
        val config = JSONObject(text)
        val mjolnir = config.getJSONObject("mjolnir")
        val tileDir = File(roadData.dir, "tiles").apply { mkdirs() }
        mjolnir.put("tile_extract", roadData.tiles.absolutePath)
        mjolnir.put("tile_dir", tileDir.absolutePath)
        mjolnir.put("timezone", roadData.timezones.absolutePath)
        if (roadData.admins.length() > 0) {
            mjolnir.put("admin", roadData.admins.absolutePath)
        } else {
            mjolnir.remove("admin")
        }
        // Этих данных у нас нет, лишние пути убираем
        for (key in listOf("traffic_extract", "landmarks", "transit_dir", "transit_feeds_dir")) {
            mjolnir.remove(key)
        }
        val file = File(roadData.dir, "valhalla.json")
        file.writeText(config.toString())
        return file
    }

    /** Строит до трёх вариантов маршрута от from до to с выездом в момент departAt. */
    suspend fun route(from: LatLon, to: LatLon, departAt: Date): RouteResult =
        withContext(Dispatchers.Default) {
            val valhalla = engine()
            val departure = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(departAt)

            fun request(withTime: Boolean): String {
                val json = JSONObject()
                json.put(
                    "locations",
                    JSONArray()
                        .put(JSONObject().put("lat", from.lat).put("lon", from.lon))
                        .put(JSONObject().put("lat", to.lat).put("lon", to.lon))
                )
                json.put("costing", "auto")
                json.put("alternates", 2)
                json.put(
                    "directions_options",
                    JSONObject().put("language", "ru-RU").put("units", "kilometers")
                )
                if (withTime) {
                    json.put("date_time", JSONObject().put("type", 1).put("value", departure))
                }
                return json.toString()
            }

            // Сначала пробуем с временем выезда; если движок его не принял, строим без него
            var timeAware = true
            val raw = try {
                valhalla.routeRaw(request(true))
            } catch (e: Exception) {
                timeAware = false
                valhalla.routeRaw(request(false))
            }
            RouteResult(parse(raw), timeAware)
        }

    private fun parse(raw: String): List<RouteOption> {
        val root = JSONObject(raw)
        val trips = ArrayList<JSONObject>()
        trips.add(root.getJSONObject("trip"))
        root.optJSONArray("alternates")?.let { alts ->
            for (i in 0 until alts.length()) trips.add(alts.getJSONObject(i).getJSONObject("trip"))
        }
        return trips.map { trip ->
            val summary = trip.getJSONObject("summary")
            val leg = trip.getJSONArray("legs").getJSONObject(0)
            val maneuvers = ArrayList<Maneuver>()
            val ms = leg.getJSONArray("maneuvers")
            for (i in 0 until ms.length()) {
                val m = ms.getJSONObject(i)
                val names = m.optJSONArray("street_names")
                maneuvers.add(
                    Maneuver(
                        type = m.optInt("type"),
                        instruction = m.optString("instruction"),
                        street = if (names != null && names.length() > 0) names.getString(0) else "",
                        startIndex = m.optInt("begin_shape_index")
                    )
                )
            }
            RouteOption(
                timeSec = summary.getDouble("time"),
                lengthM = summary.getDouble("length") * 1000.0,
                points = decodePolyline6(leg.getString("shape")),
                maneuvers = maneuvers
            )
        }.sortedBy { it.timeSec }
    }

    /**
     * Ограничения скорости вдоль линии маршрута (из тех же дорожных данных, что и маршрут).
     * Участки, где ограничение в данных не указано, в список не попадают, и знак не показывается.
     */
    suspend fun speedLimits(points: List<LatLon>): List<LimitSeg> = withContext(Dispatchers.Default) {
        if (points.size < 2) return@withContext emptyList()
        val shape = JSONArray()
        points.forEach { shape.put(JSONObject().put("lat", it.lat).put("lon", it.lon)) }
        val request = JSONObject()
            .put("shape", shape)
            .put("costing", "auto")
            .put("shape_match", "edge_walk")
            .put(
                "filters",
                JSONObject()
                    .put("attributes", JSONArray().put("edge.length").put("edge.speed_limit"))
                    .put("action", "include")
            )
        val raw = engine().traceAttributesRaw(request.toString())
        val edges = JSONObject(raw).optJSONArray("edges") ?: return@withContext emptyList()
        val result = ArrayList<LimitSeg>()
        var at = 0.0
        for (i in 0 until edges.length()) {
            val e = edges.getJSONObject(i)
            val len = e.optDouble("length", 0.0) * 1000.0
            val limit = e.optInt("speed_limit", 0)
            if (limit in 5..140) result += LimitSeg(at, at + len, limit)
            at += len
        }
        result
    }

    @Synchronized
    fun close() {
        engine?.close()
        engine = null
    }
}
