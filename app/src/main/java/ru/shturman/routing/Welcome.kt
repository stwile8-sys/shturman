package ru.shturman.routing

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Приветствие при запуске: «Welcome to Gerty Airline» спокойным женским голосом.
 * Используется английский голос телефона; если его нет, приветствие просто пропускается.
 */
class Welcome(context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { engine.shutdown(); return@TextToSpeech }
            val result = engine.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.shutdown()
                return@TextToSpeech
            }
            runCatching {
                val voices = engine.voices.filter { it.locale.language == "en" }
                val best = voices.firstOrNull { it.name.contains("female", true) }
                    ?: voices.firstOrNull { v -> listOf("-sfg-", "-iob-", "-iog-", "-tpf-").any { v.name.contains(it) } }
                if (best != null) engine.voice = best
            }
            engine.setSpeechRate(0.8f)  // медленно и расслабленно
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { engine.shutdown() }
                @Deprecated("Нужен для старых версий Android")
                override fun onError(utteranceId: String?) { engine.shutdown() }
            })
            engine.speak("Welcome to Gerty Airline.", TextToSpeech.QUEUE_FLUSH, null, "welcome")
        }
    }
}
