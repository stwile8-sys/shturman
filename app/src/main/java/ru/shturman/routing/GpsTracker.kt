package ru.shturman.routing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/**
 * Свой приём GPS раз в секунду. Нужен, чтобы перед показом стрелки мы могли привязать
 * положение к линии маршрута и повернуть стрелку по курсу.
 * Сеть (вышки, Wi-Fi) используется только пока GPS молчит.
 */
class GpsTracker(
    context: Context,
    private val onFix: (Location) -> Unit
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastGpsAt = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = SystemClock.elapsedRealtime()
            if (location.provider == LocationManager.GPS_PROVIDER) {
                // Грубые спутниковые данные (город, тоннель, холодный старт) только сбивают стрелку
                if (location.hasAccuracy() && location.accuracy > MAX_GPS_ACCURACY_M) return
                lastGpsAt = now
            } else {
                // Сеть годится только пока GPS ещё ни разу не поймал, и только если она не слишком грубая
                if (lastGpsAt != 0L) return
                if (!location.hasAccuracy() || location.accuracy > MAX_NETWORK_ACCURACY_M) return
            }
            onFix(location)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Нужен только для старых версий Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (manager.allProviders.contains(provider)) {
                runCatching {
                    manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                }
            }
        }
        // Сразу показываем последнее известное положение, пока GPS ловит спутники
        val last = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // Старое положение (больше двух минут назад) не показываем: оно только обманет
        if (last != null && System.currentTimeMillis() - last.time < 120_000L) onFix(last)
    }

    private companion object {
        const val MAX_GPS_ACCURACY_M = 35f
        const val MAX_NETWORK_ACCURACY_M = 150f
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
    }
}
