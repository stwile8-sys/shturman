package ru.shturman.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Настройки уведомлений и автозапуска (хранятся в настройках телефона). */
object AutoPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun tripNotice(ctx: Context) = p(ctx).getBoolean("notif_trip", true)
    fun quietHours(ctx: Context) = p(ctx).getBoolean("quiet_hours", true)
    fun btAutostart(ctx: Context) = p(ctx).getBoolean("bt_autostart", false)
    fun btAutostop(ctx: Context) = p(ctx).getBoolean("bt_autostop", false)
    fun btAddress(ctx: Context) = p(ctx).getString("bt_address", null)
    fun chargeAutostart(ctx: Context) = p(ctx).getBoolean("charge_autostart", false)
    fun set(ctx: Context, key: String, v: Boolean) = p(ctx).edit().putBoolean(key, v).apply()
    fun setAddress(ctx: Context, address: String?, name: String?) =
        p(ctx).edit().putString("bt_address", address).putString("bt_name", name).apply()
    fun btName(ctx: Context) = p(ctx).getString("bt_name", null)

    /** Тихие часы: с 23:00 до 7:00 необязательные уведомления не показываются. */
    fun isQuietNow(ctx: Context, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean =
        quietHours(ctx) && isQuietHour(hour)

    fun isQuietHour(hour: Int) = hour >= 23 || hour < 7
}

/** Реагирует на подключение Bluetooth авто и зарядки: предлагает открыть APEX; при отключении авто может остановить маршрут. */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Подключились к машине — сразу проверяем, поехали ли, чтобы записать поездку с начала
                    if (matches(context, intent) || isCar(intent)) {
                        DriveWatch.carConnected = true
                        DriveWatch.start(context, poke = true)
                    }
                    if (AutoPrefs.btAutostart(context) && matches(context, intent)) {
                        prompt(context, "Подключено к авто", "Открыть APEX и построить маршрут?")
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (matches(context, intent) || isCar(intent)) DriveWatch.carConnected = false
                    if (AutoPrefs.btAutostop(context) && matches(context, intent)) NavService.onCancel?.invoke()
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    if (AutoPrefs.chargeAutostart(context)) prompt(context, "Зарядка подключена", "Открыть APEX?")
                }
            }
        }
    }

    /** Устройство похоже на машину: автомагнитола или громкая связь. */
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun isCar(intent: Intent): Boolean = runCatching {
        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return false
        val cls = dev.bluetoothClass?.deviceClass ?: return false
        cls == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun matches(ctx: Context, intent: Intent): Boolean {
        val want = AutoPrefs.btAddress(ctx) ?: return false
        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return false
        return dev.address == want
    }

    private fun prompt(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("prompts") == null) {
            nm.createNotificationChannel(NotificationChannel("prompts", "Предложения", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, 2, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "prompts") else Notification.Builder(ctx)
        nm.notify(
            4003,
            b.setSmallIcon(ru.shturman.R.drawable.ic_stat_apex).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build()
        )
    }
}
