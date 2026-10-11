package ru.shturman.routing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Слушает одну команду голосом и отдаёт распознанный текст. Нужно разрешение на микрофон. */
class VoiceListener(
    context: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    fun start() {
        if (!available()) return
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onState(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onState(false) }
            override fun onResults(results: Bundle?) {
                onState(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    fun stop() {
        runCatching { recognizer?.cancel(); recognizer?.destroy() }
        recognizer = null
        onState(false)
    }
}

/** Команда, понятая из сказанного. */
sealed class VoiceCommand {
    object Home : VoiceCommand()
    object Work : VoiceCommand()
    object Cancel : VoiceCommand()
    object Louder : VoiceCommand()
    object Quieter : VoiceCommand()
    class To(val query: String) : VoiceCommand()
    object Unknown : VoiceCommand()
}

/** Разбирает фразу вроде «Штурман, домой» или «поехали до Великих Лук». Слово «Штурман» необязательно. */
fun parseVoiceCommand(raw: String): VoiceCommand {
    val text = raw.lowercase().replace("штурман", "").replace(",", " ").trim()
    return when {
        text.isEmpty() -> VoiceCommand.Unknown
        Regex("(^|\\s)(домой|дом)(\\s|$)").containsMatchIn(text) -> VoiceCommand.Home
        text.contains("на работу") || text == "работа" -> VoiceCommand.Work
        text.contains("отмена") || text.contains("отмени") || text.contains("стоп") -> VoiceCommand.Cancel
        text.contains("громче") -> VoiceCommand.Louder
        text.contains("тише") -> VoiceCommand.Quieter
        else -> {
            val m = Regex("(?:до|в|на|к)\\s+(.+)").find(text)
            if (m != null) VoiceCommand.To(m.groupValues[1].trim()) else VoiceCommand.To(text)
        }
    }
}
