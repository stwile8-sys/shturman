package ru.shturman.ui

import android.content.Context
import android.media.MediaPlayer
import ru.shturman.R

/** Звук старта: рёв мотора спорткара и визг шин. Файл лежит в res/raw/start_engine.wav. */
object StartSound {
    fun play(context: Context) {
        runCatching {
            val player = MediaPlayer.create(context.applicationContext, R.raw.start_engine) ?: return
            player.setOnCompletionListener { it.release() }
            player.start()
        }
    }
}
