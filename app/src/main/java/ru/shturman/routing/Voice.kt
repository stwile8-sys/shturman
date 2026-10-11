package ru.shturman.routing

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Голосовые подсказки через встроенный в телефон синтезатор речи (русский язык). */
class Voice(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale("ru", "RU"))
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    fun say(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shturman")
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
    }
}

/** Фраза для подсказки о повороте. level: 0 — заранее, 1 — близко, 2 — прямо сейчас. */
fun turnPhrase(level: Int, distanceM: Double, isLeft: Boolean, severity: Int, street: String): String {
    val side = if (isLeft) "налево" else "направо"
    val how = when {
        severity <= 2 -> "резко $side"
        severity >= 5 -> "плавно $side"
        else -> side
    }
    return when (level) {
        2 -> how.replaceFirstChar { it.uppercase() }
        1 -> "Через ${roundMeters(distanceM, 50)} ${meterWord(roundMeters(distanceM, 50))}, $how"
        else -> {
            val m = roundMeters(distanceM, 100)
            "Через $m ${meterWord(m)}, $how" + if (street.isNotBlank()) ", на $street" else ""
        }
    }
}

private fun roundMeters(d: Double, step: Int): Int = (Math.round(d / step) * step).toInt().coerceAtLeast(step)

private fun meterWord(n: Int): String = when {
    n % 100 in 11..14 -> "метров"
    n % 10 == 1 -> "метр"
    n % 10 in 2..4 -> "метра"
    else -> "метров"
}
