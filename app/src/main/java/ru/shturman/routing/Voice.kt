package ru.shturman.routing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Calendar
import java.util.Locale

/**
 * Голос навигатора — встроенный в телефон синтезатор речи (русский язык).
 * Подбирается женский голос, речь чуть быстрее обычной и с задором; в режиме принцессы — мягче и выше.
 * На время фразы музыка и другие звуки приглушаются средствами Android (аудиофокус), потом возвращаются.
 * С 22:00 до 6:00 (если включено) голос тише, а фразы короче.
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private var tts: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null

    var princess = false
    /** Сообщение о состоянии голоса: ошибка или «всё хорошо». Показывается в настройках. */
    var onStatus: ((String) -> Unit)? = null
    private var lastText: String? = null
    private var retried = false
    var nightMode = true

    /** Сейчас ночь по часам телефона. */
    fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return nightMode && (h >= 22 || h < 6)
    }

    init {
        tts = TextToSpeech(app) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale("ru", "RU"))
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) onStatus?.invoke("Нет русского голоса: установите его в настройках телефона (Язык и ввод → Синтез речи)")
                engine.setAudioAttributes(attrs())
                pickFemaleVoice(engine)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { releaseFocus() }
                    @Deprecated("Нужен для старых версий Android")
                    override fun onError(utteranceId: String?) { releaseFocus() }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        releaseFocus()
                        // Выбранный голос не сработал (например, ему нужен интернет): берём голос по умолчанию и повторяем
                        val text = lastText
                        if (!retried && text != null) {
                            retried = true
                            runCatching { engine.voice = engine.defaultVoice }
                            onStatus?.invoke("Голос не сработал (код $errorCode), пробую другой")
                            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shturman-retry")
                        } else {
                            onStatus?.invoke("Ошибка синтезатора речи (код $errorCode)")
                        }
                    }
                })
            }
        }
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** У синтезатора нет понятия «пол голоса», поэтому ищем по названию: женские голоса обычно помечены. */
    private fun pickFemaleVoice(engine: TextToSpeech) {
        runCatching {
            val ru = engine.voices.filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }
                .ifEmpty { engine.voices.filter { it.locale.language == "ru" } }
            val best = ru.firstOrNull { it.name.contains("female", true) }
                ?: ru.firstOrNull { it.name.contains("-ruc-") || it.name.contains("-ruf-") }
                ?: ru.firstOrNull()
            if (best != null) engine.voice = best
        }
    }

    /**
     * Говорит фразу. flush = true прерывает то, что говорится сейчас (поворот важнее), false ставит в очередь.
     */
    fun say(text: String, flush: Boolean = true) {
        val engine = tts ?: return
        if (!ready) {
            onStatus?.invoke("Нет русского голоса: установите его в настройках телефона (Язык и ввод → Синтез речи)")
            return
        }
        lastText = text
        retried = false
        engine.setPitch(if (princess) 1.3f else 1.12f)
        engine.setSpeechRate(if (princess) 0.95f else 1.1f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (isNight()) 0.45f else 1.0f)
        }
        requestFocus()
        engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "shturman")
    }

    private fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs())
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        if (audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = req
    }

    private fun releaseFocus() {
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }

    fun shutdown() {
        releaseFocus()
        runCatching { tts?.stop(); tts?.shutdown() }
    }
}

/** Фраза для подсказки о повороте. level: 0 — заранее, 1 — близко, 2 — прямо сейчас. */
fun turnPhrase(level: Int, distanceM: Double, isLeft: Boolean, severity: Int, street: String, short: Boolean = false): String {
    val side = if (isLeft) "налево" else "направо"
    val how = when {
        severity <= 2 -> "резко $side"
        severity >= 5 -> "плавно $side"
        else -> side
    }
    if (short) {
        // Ночью: без лишних слов
        return when (level) {
            2 -> how.replaceFirstChar { it.uppercase() }
            else -> "$how, ${roundMeters(distanceM, if (level == 1) 50 else 100)}"
        }
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
