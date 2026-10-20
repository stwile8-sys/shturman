package ru.shturman.routing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Одна точка из OpenStreetMap: положение и, для камер, место установки. */
class Camera(val position: LatLon, val place: String) // place: "" — обычная, "bridge" — на мосту, "tunnel" — в тоннеле

/** Запрос к Overpass (данные OpenStreetMap) с представлением приложения. Возвращает массив "elements" или null. */
internal fun overpass(query: String): JSONArray? = try {
    val conn = (URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8"))
        .openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 90_000
        setRequestProperty("User-Agent", "Shturman/1.0 (Android navigation app)")
    }
    try {
        if (conn.responseCode != 200) null
        else JSONObject(conn.inputStream.use { it.readBytes().decodeToString() }).getJSONArray("elements")
    } finally {
        conn.disconnect()
    }
} catch (e: Exception) {
    null
}

private const val BBOX = "(55.6,27.0,59.3,31.6)"
private const val WEEK_MS = 7L * 24 * 3600 * 1000

private fun elementPoint(e: JSONObject): LatLon? = when {
    e.has("lat") -> LatLon(e.getDouble("lat"), e.getDouble("lon"))
    e.has("center") -> e.getJSONObject("center").let { LatLon(it.getDouble("lat"), it.getDouble("lon")) }
    else -> null
}

/**
 * Камеры скорости из OpenStreetMap. Те, что стоят на мосту или в тоннеле, помечаются отдельно.
 * Обновляются раз в неделю, хранятся в памяти телефона.
 */
class Cameras(context: Context) {
    private val file = File(context.filesDir, "cameras_v2.json")

    fun load(): List<Camera> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val a = arr.getJSONArray(i)
            Camera(LatLon(a.getDouble(0), a.getDouble(1)), a.optString(2, ""))
        }
    }.getOrDefault(emptyList())

    suspend fun updateIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS) return@withContext false
        val all = overpass("[out:json][timeout:80];node[\"highway\"=\"speed_camera\"]$BBOX;out;") ?: return@withContext false
        // Камеры, являющиеся точками мостов или тоннелей
        val onBridge = overpass("[out:json][timeout:80];way[\"bridge\"]$BBOX;node(w)[\"highway\"=\"speed_camera\"];out;")
        val onTunnel = overpass("[out:json][timeout:80];way[\"tunnel\"]$BBOX;node(w)[\"highway\"=\"speed_camera\"];out;")
        fun ids(a: JSONArray?): Set<Long> = a?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getLong("id") }.toSet() } ?: emptySet()
        val bridge = ids(onBridge)
        val tunnel = ids(onTunnel)
        val out = JSONArray()
        for (i in 0 until all.length()) {
            val e = all.getJSONObject(i)
            val p = elementPoint(e) ?: continue
            val id = e.getLong("id")
            out.put(JSONArray().put(p.lat).put(p.lon).put(if (id in tunnel) "tunnel" else if (id in bridge) "bridge" else ""))
        }
        file.writeText(out.toString())
        true
    }
}

/**
 * Точки из OpenStreetMap по запросу: лежачие полицейские (traffic_calming) и ремонт дорог (construction).
 * Каждая группа хранится в своём файле и обновляется раз в неделю.
 */
class OsmPoints(context: Context, name: String, private val query: String) {
    private val file = File(context.filesDir, "osm_$name.json")

    fun load(): List<LatLon> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { i -> arr.getJSONArray(i).let { LatLon(it.getDouble(0), it.getDouble(1)) } }
    }.getOrDefault(emptyList())

    suspend fun updateIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS) return@withContext false
        val elements = overpass(query) ?: return@withContext false
        val out = JSONArray()
        for (i in 0 until elements.length()) {
            val p = elementPoint(elements.getJSONObject(i)) ?: continue
            out.put(JSONArray().put(p.lat).put(p.lon))
        }
        file.writeText(out.toString())
        true
    }

    companion object {
        fun bumps(context: Context) = OsmPoints(
            context, "bumps",
            "[out:json][timeout:80];node[\"traffic_calming\"~\"bump|hump|table|cushion|rumble_strip|chicane|island\"]$BBOX;out;"
        )

        fun roadworks(context: Context) = OsmPoints(
            context, "works",
            "[out:json][timeout:80];(way[\"highway\"=\"construction\"]$BBOX;way[\"construction\"]$BBOX;node[\"construction\"]$BBOX;);out center;"
        )
    }
}

private const val M_PER_DEG = 111_320.0

/**
 * Для каждой точки из items — номер ближайшей вершины линии не дальше maxM (или -1).
 * Быстро: сначала отбрасываем всё, что дальше прямоугольника вокруг линии (+50 м), а расстояние
 * внутри считаем «на плоскости» (на таких расстояниях это то же самое, что точная формула).
 */
private fun nearestVertex(points: List<LatLon>, items: List<LatLon>, maxM: Double): IntArray {
    val out = IntArray(items.size) { -1 }
    if (points.isEmpty() || items.isEmpty()) return out
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
    for (p in points) {
        if (p.lat < minLat) minLat = p.lat
        if (p.lat > maxLat) maxLat = p.lat
        if (p.lon < minLon) minLon = p.lon
        if (p.lon > maxLon) maxLon = p.lon
    }
    val k = Math.cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.1)
    val padLat = maxOf(maxM, 50.0) / M_PER_DEG
    val padLon = padLat / k
    val max2 = maxM * maxM
    for ((idx, c) in items.withIndex()) {
        if (c.lat < minLat - padLat || c.lat > maxLat + padLat || c.lon < minLon - padLon || c.lon > maxLon + padLon) continue
        var best = -1
        var bestD = max2
        for (i in points.indices) {
            val p = points[i]
            val dy = (p.lat - c.lat) * M_PER_DEG
            if (dy * dy >= bestD) continue
            val dx = (p.lon - c.lon) * M_PER_DEG * k
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        out[idx] = best
    }
    return out
}

/** Объекты, стоящие не дальше maxM от линии, в порядке появления вдоль неё: расстояние от начала линии и индекс объекта. */
fun alongRoute(route: RouteOption, items: List<LatLon>, maxM: Double): List<Pair<Double, Int>> {
    if (route.points.isEmpty()) return emptyList()
    val near = nearestVertex(route.points, items, maxM)
    val found = ArrayList<Pair<Double, Int>>()
    for (idx in items.indices) {
        val best = near[idx]
        if (best >= 0) found += route.cumM[best] to idx
    }
    return found.sortedBy { it.first }
}

/** Камеры, стоящие не дальше 35 м от линии, в порядке их появления вдоль неё (для объезда). */
fun camerasNear(points: List<LatLon>, cameras: List<LatLon>): List<LatLon> {
    if (points.isEmpty()) return emptyList()
    val near = nearestVertex(points, cameras, 35.0)
    val found = ArrayList<Pair<Int, LatLon>>()
    for ((idx, c) in cameras.withIndex()) {
        if (near[idx] >= 0) found += near[idx] to c
    }
    return found.sortedBy { it.first }.map { it.second }
}

fun pointsGeoJson(list: List<LatLon>): String {
    val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
    list.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.append(String.format(java.util.Locale.US,
            """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""", p.lon, p.lat))
    }
    return sb.append("]}").toString()
}
