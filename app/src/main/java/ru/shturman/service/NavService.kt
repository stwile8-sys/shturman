package ru.shturman.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Служба на время навигации: показывает постоянное уведомление (скорость, поворот, дистанция, кнопка отмены)
 * и не даёт системе выгрузить приложение, когда экран выключен или открыто другое приложение.
 */
class NavService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        setWake(this, false)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "APEX"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Навигация"
        try {
            val n = build(this, title, text)
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(ID, n)
            }
            setWake(this, true)
        } catch (e: Exception) {
            ru.shturman.Errors.log("navService", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "nav"
        private const val ID = 4001
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        const val ACTION_CANCEL = "ru.shturman.action.CANCEL_ROUTE"

        /** Что делать по кнопке «Отмена маршрута»; ставит экран. */
        @Volatile var onCancel: (() -> Unit)? = null
        private var lastText = ""
        private var wake: android.os.PowerManager.WakeLock? = null

        /** Не даёт процессору уснуть во время поездки, чтобы GPS не отключался. На паузе отпускается. */
        @Synchronized
        fun setWake(ctx: Context, on: Boolean) {
            runCatching {
                if (on) {
                    if (wake?.isHeld == true) return
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    wake = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "shturman:nav").also { it.acquire(6 * 60 * 60 * 1000L) }
                } else {
                    wake?.let { if (it.isHeld) it.release() }
                    wake = null
                }
            }
        }

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Навигация", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Постоянное уведомление во время поездки"
                            setShowBadge(false)
                        }
                    )
                }
            }
        }

        private fun build(ctx: Context, title: String, text: String): Notification {
            ensureChannel(ctx)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                PendingIntent.getActivity(ctx, 0, it, flags)
            }
            val cancel = PendingIntent.getBroadcast(
                ctx, 1, Intent(ACTION_CANCEL).setPackage(ctx.packageName), flags
            )
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
            return b.setSmallIcon(ru.shturman.R.drawable.ic_stat_apex)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(0, "Отмена маршрута", cancel)
                .build()
        }

        fun start(ctx: Context, title: String, text: String) {
            ru.shturman.AppState.navigating = true
            runCatching {
                lastText = ""
                ctx.startForegroundService(
                    Intent(ctx, NavService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
                )
            }.onFailure { ru.shturman.Errors.log("navService.start", it) }
        }

        /** Обновляет текст уведомления; одинаковый текст повторно не посылается. */
        fun update(ctx: Context, title: String, text: String) {
            val key = "$title|$text"
            if (key == lastText) return
            lastText = key
            runCatching {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(ID, build(ctx, title, text))
            }
        }

        /** Итог поездки отдельным уведомлением (без звука). Если уведомления запрещены, ничего не происходит. */
        fun notifyTripEnd(ctx: Context, text: String) {
            if (!AutoPrefs.tripNotice(ctx) || AutoPrefs.isQuietNow(ctx)) return
            runCatching {
                ensureChannel(ctx)
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
                nm.notify(
                    4002,
                    b.setSmallIcon(ru.shturman.R.drawable.ic_stat_apex).setContentTitle("Поездка завершена")
                        .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build()
                )
            }
        }

        /** Короткое информационное уведомление (резервная копия и т.п.). В тихие часы не показывается. */
        fun notifyInfo(ctx: Context, title: String, text: String) {
            if (AutoPrefs.isQuietNow(ctx)) return
            runCatching {
                ensureChannel(ctx)
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
                nm.notify(4004, b.setSmallIcon(ru.shturman.R.drawable.ic_stat_apex).setContentTitle(title).setContentText(text).setAutoCancel(true).build())
            }
        }

        fun stop(ctx: Context) {
            ru.shturman.AppState.navigating = false
            runCatching { ctx.stopService(Intent(ctx, NavService::class.java)) }
        }
    }
}

/** Принимает нажатие «Отмена маршрута» в уведомлении. */
class NavActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NavService.ACTION_CANCEL) NavService.onCancel?.invoke()
    }
}
