package ru.shturman.routing

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import ru.shturman.R

/**
 * Приветствие при запуске: «Welcome to Gerty Airline» тем же голосом, что и подсказки.
 * Запись лежит в самом приложении (res/raw/welcome.ogg), синтезатор телефона не нужен.
 */
class Welcome(context: Context) {
    init {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val mp = MediaPlayer.create(context.applicationContext, R.raw.welcome, attrs, 0)
            mp?.setOnCompletionListener { it.release() }
            mp?.setOnErrorListener { player, _, _ -> player.release(); true }
            mp?.start()
        }
    }
}
