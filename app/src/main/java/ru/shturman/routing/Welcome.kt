package ru.shturman.routing

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import ru.shturman.R

/**
 * Звук заставки. Запись лежит в приложении (res/raw/welcome.ogg): чтобы заменить звук, достаточно
 * положить на её место другой файл с тем же именем. Плеер готовится заранее, чтобы заставка не лагала.
 */
class Welcome(context: Context) {
    private var mp: MediaPlayer? = null

    init {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mp = MediaPlayer.create(context.applicationContext, R.raw.welcome, attrs, 0)
        }
    }

    fun play() {
        runCatching { mp?.start() }
    }

    fun release() {
        runCatching { mp?.release() }
        mp = null
    }
}
