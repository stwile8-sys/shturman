package ru.shturman.api

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.shturman.routing.Geocoder
import ru.shturman.routing.LatLon
import ru.shturman.routing.RoadData
import ru.shturman.routing.Router
import ru.shturman.routing.SpeedStore
import ru.shturman.routing.fetchWeather
import ru.shturman.routing.forecastTimeSec
import ru.shturman.routing.lastKnownPosition
import ru.shturman.routing.seasonEffect
import java.util.Date

/**
 * Невидимый «движок маршрутов» для приложения «Заказы» (ru.zakazy).
 * Водитель его не видит: ни экранов, ни уведомлений, ни голоса. Привязаться может только приложение,
 * подписанное тем же ключом (разрешение ru.apex.permission.ROUTE_API уровня signature).
 * Адреса и координаты из запросов нигде не сохраняются и не уходят в общую базу.
 * Описание обмена — docs/api/route-api.md.
 */
class RouteApiService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var router: Router? = null
    private lateinit var roadData: RoadData

    private val messenger by lazy {
        Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val reply = msg.replyTo ?: return
                val what = msg.what
                val data = Bundle(msg.data ?: Bundle())
                scope.launch {
                    val answer = runCatching { handle(what, data) }
                        .getOrElse { RouteApi.error(if (it is IllegalArgumentException) RouteApi.E_BAD_REQUEST else RouteApi.E_FAILED) }
                    runCatching { reply.send(Message.obtain(null, what).apply { this.data = answer }) }
                }
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        roadData = RoadData(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        runCatching { router?.close() }
        router = null
        super.onDestroy()
    }

    private suspend fun handle(what: Int, req: Bundle): Bundle {
        if (req.getInt(RouteApi.K_VERSION, 0) != RouteApi.VERSION) return RouteApi.error(RouteApi.E_VERSION)
        return when (what) {
            RouteApi.MSG_PING -> RouteApi.ok().apply {
                putBoolean("dataReady", roadData.isReady())
                putInt("apexBuild", ru.shturman.BuildConfig.BUILD_NUMBER)
            }
            RouteApi.MSG_GEOCODE -> {
                val q = req.getString("query").orEmpty().trim()
                require(q.length >= 3)
                val found = Geocoder.search(q)
                RouteApi.ok().apply {
                    putParcelableArrayList("results", ArrayList(found.map { p ->
                        Bundle().apply {
                            putString("name", p.title); putString("address", p.subtitle)
                            putDouble("lat", p.position.lat); putDouble("lon", p.position.lon)
                        }
                    }))
                }
            }
            RouteApi.MSG_ROUTE -> route(RouteApi.parseRoute(req))
            else -> RouteApi.error(RouteApi.E_BAD_REQUEST)
        }
    }

    private suspend fun route(r: RouteApi.RouteRequest): Bundle {
        if (!roadData.isReady()) return RouteApi.error(RouteApi.E_DATA_NOT_READY)
        val start = r.from ?: lastKnownPosition(applicationContext) ?: return RouteApi.error(RouteApi.E_NO_LOCATION)
        val store = SpeedStore.shared(applicationContext)
        val legs = ArrayList<Bundle>()
        // Один движок на службу; запросы считаются по очереди, чтобы не съесть память телефона
        lock.withLock {
            val engine = router ?: Router(roadData).also { router = it }
            var from: LatLon = start
            var leaveAt = r.departAt
            for ((i, stop) in r.stops.withIndex()) {
                val departAt = Date(leaveAt)
                val res = engine.route(from, stop.pos, departAt)
                // То же время, что видит водитель: свои скорости по часу выезда, погода и сезон
                val best = res.options.map { o ->
                    val t = runCatching { forecastTimeSec(o, store, leaveAt) }.getOrDefault(o.timeSec)
                    o to t
                }.minByOrNull { it.second } ?: return RouteApi.error(RouteApi.E_NO_ROUTE)
                val weather = runCatching { fetchWeather(from, leaveAt) }.getOrNull()
                val wf = if (weather == null) 1.0 else 1.0 / weather.speedFactor
                val timeSec = best.second * wf * seasonEffect(leaveAt, best.first.lengthM).slowdown
                val arriveAt = leaveAt + (timeSec * 1000).toLong()
                legs += Bundle().apply {
                    putInt("fromIdx", i - 1)
                    putInt("toIdx", i)
                    putString("polyline", Polyline.encode(best.first.points))
                    putInt("timeSec", timeSec.toInt())
                    putInt("lengthM", best.first.lengthM.toInt())
                    putLong("departBy", stop.arriveBy - (timeSec * 1000).toLong() - RouteApi.BUFFER_MS)
                    putBoolean("late", stop.arriveBy > 0 && arriveAt > stop.arriveBy)
                }
                from = stop.pos
                leaveAt = maxOf(arriveAt, stop.leaveAt)
            }
        }
        return RouteApi.ok().apply { putParcelableArrayList("legs", legs) }
    }
}
