package ru.shturman.routing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Мягкое «вжжж» при превышении скорости. Звук синтезируется сам, зацикливается и становится громче
 * по мере того, как скорость растёт над ограничением.
 */
class OverspeedBuzz {
    private var track: AudioTrack? = null
    private var playing = false

    private fun create(): AudioTrack? = runCatching {
        val rate = 22050
        val frames = (rate * 0.4).toInt()
        val samples = ShortArray(frames) { i ->
            val t = i.toDouble() / rate
            // два низких тона и быстрая дрожь громкости: получается ровное мягкое жужжание
            val tone = 0.6 * sin(2 * PI * 110 * t) + 0.3 * sin(2 * PI * 165 * t)
            val wobble = 0.65 + 0.35 * sin(2 * PI * 25 * t)
            (tone * wobble * 0.55 * Short.MAX_VALUE).toInt().toShort()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 2)
            .build()
        t.write(samples, 0, samples.size)
        t.setLoopPoints(0, frames, -1)
        t
    }.getOrNull()

    /** excessKmh — на сколько км/ч быстрее ограничения (меньше или равно нулю — тишина). */
    fun update(enabled: Boolean, excessKmh: Int) {
        if (!enabled || excessKmh <= 2) {
            stop()
            return
        }
        val t = track ?: create().also { track = it } ?: return
        // от тихого (0.12) до заметного (0.7) при превышении на 40 км/ч и больше
        val volume = (0.12f + (excessKmh - 2).coerceAtMost(40) / 40f * 0.58f)
        runCatching {
            t.setVolume(volume)
            if (!playing) {
                t.play()
                playing = true
            }
        }
    }

    fun stop() {
        if (!playing) return
        runCatching { track?.pause(); track?.flush() }
        playing = false
    }

    fun release() {
        stop()
        runCatching { track?.release() }
        track = null
    }
}
