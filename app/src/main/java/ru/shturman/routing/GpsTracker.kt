package ru.shturman.routing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Приём положения для стрелки. Если на телефоне есть службы Google, основной источник — их служба
 * местоположения (смешивает GPS, ГЛОНАСС, Galileo и датчики). Параллельно всегда слушаем системный GPS,
 * а пока его нет — сеть и системный «fused» (Android 12+): так положение приходит и на телефонах без
 * служб Google (Huawei, Honor). Каждое положение проходит через фильтр Калмана, который гасит шум.
 *
 * Важно: приём GPS не перезапускаем, пока ждём первое положение. Спутникам нужно время (на холодную —
 * до минуты), а частый перезапуск на части телефонов сбрасывает поиск, и положение не приходит вовсе.
 */
class GpsTracker(
    context: Context,
    private val onFix: (Location) -> Unit
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val hasGoogle = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
    private val fused = if (hasGoogle) runCatching { LocationServices.getFusedLocationProviderClient(appContext) }.getOrNull() else null
    private val kalman = Kalman()
    private val meter = SpeedMeter()
    private var lastGpsAt = 0L
    private var lastFusedAt = 0L
    private var lastAcceptedAt = 0L
    private var startedAt = 0L
    private var lastKickAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false

    /**
     * Сторож. Пока первого положения нет — только проверяет, что подписки живы (раз в 30 с), не трогая поиск спутников.
     * Если положения шли и пропали больше чем на 12 с — мягко переподписывается (не чаще раза в 20 с).
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (stopped) return
            val now = SystemClock.elapsedRealtime()
            refreshState()
            val everFixed = Diag.firstFixMs >= 0
            val silentFor = now - lastAcceptedAt
            if (everFixed && silentFor > 12_000L && now - lastKickAt > 20_000L) {
                kick(withGps = true)
            } else if (!everFixed && now - lastKickAt > 30_000L) {
                // GPS ещё ищет спутники — его не трогаем, обновляем только запасные источники
                kick(withGps = false)
            }
            handler.postDelayed(this, 3000L)
        }
    }

    private fun kick(withGps: Boolean) {
        lastKickAt = SystemClock.elapsedRealtime()
        Diag.restarts++
        // подписки обновляем поверх старых, без снятия: так поиск спутников не начинается заново
        requestFused()
        requestSystem(withGps)
    }

    private fun accept(raw: Location) {
        Diag.rawCount++
        // Бывают положения с пустыми (NaN) координатами: такие просто пропускаем
        if (!raw.latitude.isFinite() || !raw.longitude.isFinite()) { Diag.rejectedCount++; return }
        if (raw.hasAccuracy() && raw.accuracy > MAX_ACCURACY_M) {
            Diag.rejectedCount++
            Diag.lastRejectedAccuracy = raw.accuracy
            return
        }
        val now = SystemClock.elapsedRealtime()
        lastAcceptedAt = now
        if (Diag.firstFixMs < 0) Diag.firstFixMs = now - startedAt
        Diag.fixCount++
        Diag.lastFixAt = System.currentTimeMillis()
        Diag.lastAccuracy = if (raw.hasAccuracy()) raw.accuracy else -1f
        Diag.lastProvider = raw.provider ?: ""
        val filtered = kalman.filter(raw)
        // Скорость для экрана: от приёмника GPS (у сети она ненадёжна), сглаженная и без дрожания на месте
        val fromReceiver = if (raw.hasSpeed() && raw.provider != LocationManager.NETWORK_PROVIDER) raw.speed else null
        val fixMs = if (raw.elapsedRealtimeNanos > 0L) raw.elapsedRealtimeNanos / 1_000_000L else now
        val acc = if (raw.hasAccuracy()) raw.accuracy else 999f
        val v = meter.update(fixMs, filtered.latitude, filtered.longitude, acc, fromReceiver)
        onFix(Location(filtered).apply { speed = v.toFloat() })
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            lastFusedAt = SystemClock.elapsedRealtime()
            result.locations.forEach { accept(it) }
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = SystemClock.elapsedRealtime()
            // Служба Google работает — она точнее, системные положения не нужны
            if (now - lastFusedAt < 4000L) return
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGpsAt = now
            } else if (now - lastGpsAt < 5000L) {
                // свежий GPS есть — сеть и прочие источники только мешают
                return
            }
            accept(location)
        }

        override fun onProviderEnabled(provider: String) {
            // геолокацию включили — сразу подписываемся заново
            if (!stopped) handler.post { requestSystem(withGps = true) }
        }

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Нужен только для старых версий Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val gnssCallback = object : GnssStatusCompat.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            Diag.satSeen = status.satelliteCount
            Diag.satUsed = used
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        stopped = false
        startedAt = SystemClock.elapsedRealtime()
        lastAcceptedAt = startedAt
        lastKickAt = startedAt
        Diag.firstFixMs = -1L
        Diag.googleServices = hasGoogle
        refreshState()
        requestFused()
        requestSystem(withGps = true)
        if (hasFine()) runCatching { LocationManagerCompat.registerGnssStatusCallback(manager, ContextCompat.getMainExecutor(appContext), gnssCallback) }
        handler.postDelayed(watchdog, 3000L)
        // Свежее последнее известное положение — сразу, чтобы стрелка не ждала спутников
        val last = runCatching {
            manager.getProviders(true)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) accept(last)
    }

    @SuppressLint("MissingPermission")
    private fun requestFused() {
        val client = fused ?: return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(250L)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching {
            client.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnFailureListener { Diag.lastError = "службы Google: ${it.javaClass.simpleName}" }
        }.onFailure { Diag.lastError = "службы Google: ${it.javaClass.simpleName}" }
    }

    /** Системные источники: GPS всегда, сеть и «fused» Android 12+ — запасные. Повторный вызов обновляет подписку. */
    @SuppressLint("MissingPermission")
    private fun requestSystem(withGps: Boolean) {
        val list = mutableListOf(LocationManager.NETWORK_PROVIDER)
        if (withGps) list += LocationManager.GPS_PROVIDER
        if (Build.VERSION.SDK_INT >= 31) list += LocationManager.FUSED_PROVIDER
        val all = runCatching { manager.allProviders }.getOrDefault(emptyList())
        for (provider in list) {
            if (!all.contains(provider)) continue
            runCatching {
                manager.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
            }.onFailure { Diag.lastError = "$provider: ${it.javaClass.simpleName}" }
        }
    }

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Для отчёта: какое разрешение дано, включена ли геолокация и какие источники работают. */
    private fun refreshState() {
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Diag.permission = when {
            hasFine() -> "точное"
            coarse -> "только примерное"
            else -> "нет"
        }
        Diag.locationOn = runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(true)
        Diag.providers = runCatching { manager.getProviders(true).joinToString(",") }.getOrDefault("")
    }

    fun stop() {
        stopped = true
        handler.removeCallbacks(watchdog)
        runCatching { fused?.removeLocationUpdates(fusedCallback) }
        runCatching { manager.removeUpdates(listener) }
        runCatching { LocationManagerCompat.unregisterGnssStatusCallback(manager, gnssCallback) }
    }

    private companion object {
        /** Совсем грубые положения (больше 400 м) отбрасываем; остальные сглаживает фильтр Калмана с учётом их точности. */
        const val MAX_ACCURACY_M = 400f
    }
}

/**
 * Фильтр Калмана для широты и долготы. Чем точнее новое измерение и чем меньше мы успели
 * проехать, тем меньше оно двигает оценку. Курс и скорость берутся из измерения как есть.
 */
private class Kalman {
    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0 // квадрат ошибки, м²; отрицательное — ещё нет данных
    private var timeMs = 0L

    fun filter(raw: Location): Location {
        val accuracy = if (raw.hasAccuracy() && raw.accuracy.isFinite()) raw.accuracy.coerceAtLeast(3f) else 10f
        if (variance < 0 || raw.time - timeMs > 10_000L) {
            lat = raw.latitude
            lon = raw.longitude
            variance = (accuracy * accuracy).toDouble()
            timeMs = raw.time
            return raw
        }
        val dt = ((raw.time - timeMs).coerceAtLeast(0L)) / 1000.0
        timeMs = raw.time
        // Сколько мы могли проехать с прошлого измерения: растит неопределённость
        val speed = if (raw.hasSpeed() && raw.speed.isFinite()) raw.speed.toDouble() else 5.0
        variance += dt * maxOf(speed, 1.0) * maxOf(speed, 1.0) * 0.25
        val gain = variance / (variance + accuracy * accuracy)
        lat += gain * (raw.latitude - lat)
        lon += gain * (raw.longitude - lon)
        variance *= (1 - gain)
        // Если из-за чего-то в расчёт попал NaN, начинаем фильтр заново с этого измерения
        if (!lat.isFinite() || !lon.isFinite() || !variance.isFinite()) {
            lat = raw.latitude
            lon = raw.longitude
            variance = (accuracy * accuracy).toDouble()
            return raw
        }
        return Location(raw).apply {
            latitude = lat
            longitude = lon
            this.accuracy = Math.sqrt(variance).toFloat()
        }
    }
}
