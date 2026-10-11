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
                lastGpsAt = now
            } else if (lastGpsAt != 0L && now - lastGpsAt < 15_000L) {
                return // GPS жив, неточные данные из сети не нужны
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
        if (last != null) onFix(last)
    }

    fun stop() {
        runCatching { manager.removeUpdates(listener) }
    }
}
