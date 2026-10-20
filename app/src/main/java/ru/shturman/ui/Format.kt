package ru.shturman.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Расстояние для людей: «320 м» или «12,4 км». */
fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} м"
    } else {
        String.format(Locale.US, "%.1f", meters / 1000).replace('.', ',') + " км"
    }

/** Только число километров, без подписи: «12,4». */
fun formatKm(meters: Double): String =
    String.format(Locale.US, "%.1f", meters / 1000).replace('.', ',')

/** Длительность: «42 мин» или «1 ч 05 мин». */
fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${String.format(Locale.US, "%02d", minutes % 60)} мин"
}

// Один формат времени на поток: не создаём новый на каждый кадр (SimpleDateFormat не потокобезопасен)
private val clockFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("HH:mm", Locale.US)
}

/** Время суток «18:42» по часам телефона. */
fun formatClock(millis: Long): String =
    clockFormat.get()!!.format(Date(millis))
