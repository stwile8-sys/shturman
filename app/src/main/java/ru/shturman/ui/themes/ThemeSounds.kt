package ru.shturman.ui.themes

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** События, для которых у каждой темы есть свой короткий звук. Голос навигатора сюда не входит. */
enum class ThemeSound(val key: String) { TAP("tap"), NAV("nav"), SPEED("speed"), SYNC("sync"), ERROR("err") }

/**
 * Звуки темы лежат в res/raw под именами t1_tap, t1_nav, t1_speed, t1_sync, t1_err (t1 … t8 — номер темы).
 * Чтобы заменить звук, достаточно положить на его место файл с тем же именем.
 */
object ThemeSounds {
    @Volatile var enabled = true
    private var pool: SoundPool? = null
    private var ids = HashMap<ThemeSound, Int>()
    private var loadedFor = -1
    private var lastSpeedAt = 0L

    /** Загружает звуки выбранной темы. Вызывается при запуске и при смене темы. */
    @Synchronized
    fun setTheme(context: Context, themeId: Int) {
        if (loadedFor == themeId && pool != null) return
        runCatching { pool?.release() }
        ids = HashMap()
        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        val app = context.applicationContext
        for (s in ThemeSound.values()) {
            val res = app.resources.getIdentifier("t${Themes.soundSet(themeId) + 1}_${s.key}", "raw", app.packageName)
            if (res != 0) runCatching { ids[s] = sp.load(app, res, 1) }
        }
        pool = sp
        loadedFor = themeId
    }

    fun play(sound: ThemeSound, volume: Float = 1f) {
        if (!enabled) return
        playAlways(sound, volume)
    }

    /** Без оглядки на «Звуки темы»: для сигналов со своим выключателем. */
    private fun playAlways(sound: ThemeSound, volume: Float) {
        val id = ids[sound] ?: return
        runCatching { pool?.play(id, volume, volume, 1, 0, 1f) }
    }

    /**
     * Один сигнал о превышении (когда звучать, решает routing/Overspeed.kt). Громче при большем превышении.
     * Защита от двойного сигнала: не чаще раза в 2 секунды. У сигнала своя настройка («Сигнал превышения»),
     * поэтому он звучит и при выключенных звуках темы.
     */
    fun speeding(excessKmh: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSpeedAt < 2000L) return
        lastSpeedAt = now
        playAlways(ThemeSound.SPEED, (0.6f + (excessKmh - 15).coerceIn(0, 30) / 30f * 0.4f))
    }
}
