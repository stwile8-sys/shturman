package ru.shturman.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.shturman.AppState
import ru.shturman.BuildConfig
import ru.shturman.Errors
import ru.shturman.R
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Автообновление в фоне. Раз в 4 часа (по умолчанию только по Wi-Fi: сборка весит около 100 МБ) смотрит,
 * нет ли новой сборки, тихо скачивает её и ставит, когда приложение закрыто и вы не в поездке
 * (и не идёт фоновая запись поездки). На Android 12 и новее приложение обновляет само себя без вопросов;
 * на старых Android система просит подтвердить — приходит уведомление «Обновление готово», одно нажатие.
 * Если свернули приложение, а сборка уже скачана, — ставим через 2 минуты.
 */
object AutoUpdate {
    private const val KEY_ON = "auto_update"
    private const val KEY_MOBILE = "auto_update_mobile"
    /** Какую сборку начали ставить: после обновления новая версия скажет «APEX обновлён». */
    private const val KEY_INSTALLING = "auto_update_build"
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 4010
    private const val PERIODIC = "autoupdate4h"
    private const val NOW = "autoupdate-now"
    const val EXTRA_BUILD = "build"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, true)
    fun mobileAllowed(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MOBILE, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(ctx, replace = true) else runCatching {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(ctx).cancelUniqueWork(NOW)
        }
    }

    fun setMobileAllowed(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MOBILE, on).apply()
        if (enabled(ctx)) schedule(ctx, replace = true)
    }

    private fun constraints(ctx: Context) = Constraints.Builder()
        .setRequiredNetworkType(if (mobileAllowed(ctx)) NetworkType.CONNECTED else NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Ставит проверку раз в 4 часа. Вызывается при запуске приложения. */
    fun schedule(ctx: Context, replace: Boolean = false) {
        cleanup(ctx)
        if (!enabled(ctx)) return
        runCatching {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoUpdateWorker>(4, TimeUnit.HOURS).setConstraints(constraints(ctx)).build()
            )
        }.onFailure { Errors.log("autoupdate.schedule", it) }
    }

    /** Приложение свернули: если новая сборка уже скачана, через 2 минуты ставим её (если не вернулись). */
    fun onAppHidden(ctx: Context) {
        if (!enabled(ctx) || Updater.readyFile(ctx) == null) return
        runCatching {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoUpdateWorker>().setInitialDelay(2, TimeUnit.MINUTES).build()
            )
        }
    }

    /** Сейчас нельзя прерывать: приложение на экране, идёт поездка или фоновая запись поездки. */
    fun busy(): Boolean = AppState.foreground || AppState.navigating || ru.shturman.service.DriveWatch.recording

    /** Одна проверка: есть ли новая сборка → скачать → поставить, если сейчас можно. */
    suspend fun run(ctx: Context) {
        if (!enabled(ctx)) return
        val info = Updater.check() ?: return
        val file = Updater.download(ctx, info) {}
        if (!valid(ctx, file, info.build)) {
            runCatching { file.delete() }
            return
        }
        if (busy()) return
        if (!Updater.canInstall(ctx)) {
            // нет разрешения ставить обновления — откроется приложение, там «Установить» попросит разрешение
            notifyReady(ctx, info.build, null)
            return
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { install(ctx, file, info.build) }
    }

    /** Скачанный файл — наше приложение и новее установленного. */
    private fun valid(ctx: Context, file: File, build: Int): Boolean = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(file.path, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        info.packageName == ctx.packageName && code > BuildConfig.BUILD_NUMBER && code >= build
    }.getOrDefault(false)

    /** Установка через PackageInstaller: на Android 12+ приложение обновляет само себя без вопросов. */
    fun install(ctx: Context, file: File, build: Int) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("apex.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out, 256 * 1024) }
                session.fsync(out)
            }
            val intent = Intent(ctx, UpdateResultReceiver::class.java).putExtra(EXTRA_BUILD, build)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            prefs(ctx).edit().putInt(KEY_INSTALLING, build).apply()
            session.commit(pending.intentSender)
        }
    }

    private fun channel(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Обновления", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return nm
    }

    /** Уведомление «Обновление готово»: нажатие — подтверждение установки (или открыть приложение). */
    fun notifyReady(ctx: Context, build: Int, confirm: Intent?) {
        runCatching {
            val nm = channel(ctx)
            val target = confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: return
            val tap = PendingIntent.getActivity(ctx, 1, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("Обновление APEX готово")
                .setContentText("Сборка $build скачана. Нажмите, чтобы установить.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, n)
        }
    }

    /** Приложение обновилось (вызывается из новой версии): короткое уведомление, старый файл удаляем. */
    fun onUpdated(ctx: Context) {
        val p = prefs(ctx)
        val build = p.getInt(KEY_INSTALLING, 0)
        cleanup(ctx)
        if (build == 0 || build != BuildConfig.BUILD_NUMBER) return
        p.edit().remove(KEY_INSTALLING).apply()
        runCatching {
            val nm = channel(ctx)
            nm.cancel(NOTIF_ID)
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("APEX обновлён")
                .setContentText("Установлена сборка $build.")
                .setContentIntent(PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, n)
        }
    }

    /** Удаляет скачанные сборки, которые уже не новее установленной. */
    private fun cleanup(ctx: Context) {
        runCatching {
            Updater.dir(ctx).listFiles()?.forEach { f ->
                val b = f.name.substringAfter("-", "").substringBefore(".").toIntOrNull()
                if (b == null || b <= BuildConfig.BUILD_NUMBER) f.delete()
            }
        }
    }
}

/** Фоновая проверка и установка обновления. */
class AutoUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        AutoUpdate.run(applicationContext)
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Errors.log("autoupdate", e)
        Result.retry()
    }
}

/** Ответ системы об установке: нужна ли подтверждение пользователя, получилось ли. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val build = intent.getIntExtra(AutoUpdate.EXTRA_BUILD, 0)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                if (AppState.foreground) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { AutoUpdate.notifyReady(context, build, confirm) }
                } else {
                    AutoUpdate.notifyReady(context, build, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // приложение уже заменено; новая версия сама скажет «обновлён»
            else -> Errors.log(
                "autoupdate.install",
                IllegalStateException("status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            )
        }
    }
}
