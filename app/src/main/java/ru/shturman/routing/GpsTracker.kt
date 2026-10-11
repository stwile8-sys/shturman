package ru.shturman.routing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Приём положения для стрелки. Основной источник — служба местоположения Google (она смешивает
 * GPS, ГЛОНАСС, Galileo и датчики телефона и точнее обычного GPS). Если её нет, берём чистый GPS.
 * Каждое положение проходит через фильтр Калмана, который гасит шум: стоя на месте стрелка
 * не дрожит, а в движении не скачет.
 */
class GpsTracker(
    context: Context,
    private val onFix: (Location) -> Unit
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val kalman = Kalman()
    private var lastGpsAt = 0L
    private var fusedWorks = false
    private var fallbackOn = false

    private fun accept(raw: Location) {
        // Бывают положения с пустыми (NaN) координатами: такие просто пропускаем
        if (!raw.latitude.isFinite() || !raw.longitude.isFinite()) return
        if (raw.hasAccuracy() && raw.accuracy > MAX_ACCURACY_M) return
        onFix(kalman.filter(raw))
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            fusedWorks = true
            result.locations.forEach { accept(it) }
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (fusedWorks) return
            val now = SystemClock.elapsedRealtime()
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGpsAt = now
            } else if (lastGpsAt != 0L) {
                return
            }
            accept(location)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Нужен только для старых версий Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(250L)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching {
            fused.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnFailureListener { startFallback() }
        }.onFailure { startFallback() }
        // Если служба Google молчит, через 4 секунды включаем обычный GPS
        Handler(Looper.getMainLooper()).postDelayed({ if (!fusedWorks) startFallback() }, 4000L)

        val last = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) accept(last)
    }

    @SuppressLint("MissingPermission")
    private fun startFallback() {
        if (fallbackOn) return
        fallbackOn = true
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (manager.allProviders.contains(provider)) {
                runCatching {
                    manager.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
    }

    fun stop() {
        runCatching { fused.removeLocationUpdates(fusedCallback) }
        runCatching { manager.removeUpdates(listener) }
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
