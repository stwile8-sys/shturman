package ru.shturman.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

const val YANDEX_MUSIC = "ru.yandex.music"

/** Что сейчас играет. */
class MusicState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val posMs: Long,
    val durMs: Long,
    val pkg: String
)

/**
 * Управление музыкой через медиасессии Android: работает с Яндекс Музыкой и любым другим плеером.
 * Нужно один раз выдать приложению «Доступ к уведомлениям».
 */
object Music {
    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isYandexInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(YANDEX_MUSIC, 0); true }.getOrDefault(false)

    /** Открывает Яндекс Музыку. Возвращает false, если её нет на телефоне. */
    fun openYandex(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(YANDEX_MUSIC) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun controller(context: Context): MediaController? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = runCatching {
            manager.getActiveSessions(ComponentName(context, MusicListener::class.java))
        }.getOrNull().orEmpty()
        return list.firstOrNull { it.packageName == YANDEX_MUSIC }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
    }

    fun read(context: Context): MusicState? {
        val c = controller(context) ?: return null
        val meta = c.metadata ?: return null
        val state = c.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        var pos = state?.position ?: 0L
        if (playing && state != null) {
            pos += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
        return MusicState(title, artist, art, playing, pos.coerceAtLeast(0L), dur.coerceAtLeast(0L), c.packageName)
    }

    fun toggle(context: Context) {
        val c = controller(context) ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next(context: Context) { controller(context)?.transportControls?.skipToNext() }
    fun previous(context: Context) { controller(context)?.transportControls?.skipToPrevious() }
    fun seek(context: Context, ms: Long) { controller(context)?.transportControls?.seekTo(ms) }
}
