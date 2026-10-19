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
                val result = engine.setLanguage(Locale.forLanguageTag("ru-RU"))
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
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
        // Сначала готовые записи: один и тот же голос для всех подсказок, синтезатор телефона не нужен
        val clip = VoiceClips.map[text.trimEnd('.', '!', ' ')]
        if (clip != null) {
            playClip(clip, flush)
            return
        }
        val engine = tts ?: return
        if (!ready) {
            onStatus?.invoke("Для этой фразы нет записи и нет голоса в телефоне")
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

    private val queue = ArrayDeque<Int>()
    private var player: android.media.MediaPlayer? = null

    private fun playClip(resId: Int, flush: Boolean) {
        if (flush) {
            queue.clear()
            runCatching { player?.release() }
            player = null
        }
        queue.addLast(resId)
        if (player == null) playNext()
    }

    private fun playNext() {
        val id = queue.removeFirstOrNull()
        if (id == null) {
            releaseFocus()
            return
        }
        requestFocus()
        val mp = runCatching { android.media.MediaPlayer.create(app, id, attrs(), audio.generateAudioSessionId()) }.getOrNull()
        if (mp == null) {
            onStatus?.invoke("Не удалось проиграть запись голоса")
            player = null
            playNext()
            return
        }
        player = mp
        val v = if (isNight()) 0.45f else 1.0f
        mp.setVolume(v, v)
        mp.setOnCompletionListener { done ->
            runCatching { done.release() }
            if (player === done) player = null
            playNext()
        }
        mp.setOnErrorListener { bad, _, _ ->
            runCatching { bad.release() }
            if (player === bad) player = null
            playNext()
            true
        }
        mp.start()
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
        queue.clear()
        runCatching { player?.release() }
        runCatching { tts?.stop(); tts?.shutdown() }
    }
}

/**
 * Фраза для подсказки о повороте. level: 0 — «через километр» (на скорости), 1 — заранее (до 600 м),
 * 2 — близко, 3 — прямо сейчас. roundaboutExit > 0 — кольцо с номером съезда.
 * Текст точно совпадает с готовыми записями (VoiceClips): названия улиц в записи не входят.
 */
fun turnPhrase(level: Int, distanceM: Double, isLeft: Boolean, severity: Int, roundaboutExit: Int = 0): String {
    val near = when (level) {
        2 -> "Через ${roundMeters(distanceM, 50).coerceIn(50, 600)} метров"
        else -> "Через ${roundMeters(distanceM, 100).coerceIn(50, 600)} метров"
    }
    if (roundaboutExit > 0) {
        val exit = EXIT_WORDS.getOrNull(roundaboutExit - 1)?.let { "$it съезд" }
        return when (level) {
            3 -> if (exit != null) "На кольце $exit" else "Круговое движение"
            0 -> "Через километр, круговое движение" + (exit?.let { ", $it" } ?: "")
            else -> "$near, круговое движение" + (exit?.let { ", $it" } ?: "")
        }
    }
    val side = if (isLeft) "налево" else "направо"
    val how = when {
        severity <= 2 -> "резко $side"
        severity >= 5 -> "плавно $side"
        else -> side
    }
    return when (level) {
        3 -> how.replaceFirstChar { it.uppercase() }
        0 -> "Через километр, $how"
        else -> "$near, $how"
    }
}

/** Номера съездов с кольца, для которых есть записи. */
private val EXIT_WORDS = listOf("первый", "второй", "третий", "четвёртый", "пятый")

/** Совет сбавить скорость перед крутым поворотом: запись есть для 20…70 км/ч. */
fun curvePhrase(adviceKmh: Int): String = "Впереди крутой поворот, сбавьте до ${adviceKmh.coerceIn(20, 70)}"

private fun roundMeters(d: Double, step: Int): Int = (Math.round(d / step) * step).toInt().coerceAtLeast(step)

private fun meterWord(n: Int): String = when {
    n % 100 in 11..14 -> "метров"
    n % 10 == 1 -> "метр"
    n % 10 in 2..4 -> "метра"
    else -> "метров"
}
