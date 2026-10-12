package ru.shturman.media

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Состояние стороннего приложения-антирадара. */
class AntiRadarInfo(
    val pkg: String,
    val label: String,
    val running: Boolean,
    val text: String,
    val canStop: Boolean
)

/**
 * Связь с установленным приложением-антирадаром. Его база камер закрыта внутри самого приложения, поэтому мы
 * не копируем камеры, а находим приложение, показываем его статус по уведомлению, запускаем и останавливаем.
 */
object AntiRadar {
    /** Ищет среди установленных приложение, в названии которого есть «антирадар». */
    fun find(context: Context): Pair<String, String>? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .firstOrNull { (pkg, label) ->
                pkg != context.packageName &&
                    (label.contains("антирадар", true) || label.contains("antiradar", true) || label.contains("anti-radar", true))
            }
    }

    fun open(context: Context): Boolean {
        val found = find(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(found.first) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun read(context: Context): AntiRadarInfo? {
        val (pkg, label) = find(context) ?: return null
        val listener = MusicListener.instance
        val notes = runCatching { listener?.activeNotifications?.filter { it.packageName == pkg } }.getOrNull().orEmpty()
        val n = notes.firstOrNull()?.notification
        val title = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val stop = n?.actions?.firstOrNull { it.title.toString().contains("стоп", true) || it.title.toString().contains("stop", true) }
        return AntiRadarInfo(
            pkg = pkg, label = label, running = n != null,
            text = listOf(title, text).filter { it.isNotBlank() }.joinToString(" · "),
            canStop = stop != null
        )
    }

    /** Нажимает кнопку «СТОП» в уведомлении антирадара. */
    fun stop(context: Context): Boolean {
        val (pkg, _) = find(context) ?: return false
        val notes = runCatching { MusicListener.instance?.activeNotifications?.filter { it.packageName == pkg } }.getOrNull().orEmpty()
        val action = notes.firstNotNullOfOrNull { sbn ->
            sbn.notification.actions?.firstOrNull { it.title.toString().contains("стоп", true) || it.title.toString().contains("stop", true) }
        } ?: return false
        return runCatching { action.actionIntent.send(); true }.getOrDefault(false)
    }

    fun isInstalled(context: Context): Boolean = find(context) != null
}
