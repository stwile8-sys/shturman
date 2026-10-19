package ru.shturman.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import ru.shturman.Errors
import ru.shturman.routing.Cameras
import ru.shturman.routing.SharedSync
import ru.shturman.routing.SpeedStore
import java.util.concurrent.TimeUnit

/** Фоновая синхронизация общей базы: раз в 2 часа, только при интернете. Без входа в аккаунт ничего не делает. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            SharedSync(applicationContext, SpeedStore.shared(applicationContext)).sync()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Errors.log("syncWorker", e)
            Result.retry()
        }
    }
}

/** Сторож фонового сбора данных о дорогах: если система остановила службу, включает её снова. */
class DriveWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!ru.shturman.service.DriveWatch.running) ru.shturman.service.DriveWatch.start(applicationContext)
        return Result.success()
    }
}

/** Раз в неделю обновляет список камер. */
class CamerasWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Cameras(applicationContext).updateIfStale()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Errors.log("camerasWorker", e)
        Result.retry()
    }
}

object Background {
    fun schedule(ctx: Context) {
        runCatching {
            val wm = WorkManager.getInstance(ctx)
            val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            wm.enqueueUniquePeriodicWork(
                "sync2h", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS).setConstraints(net).build()
            )
            wm.enqueueUniquePeriodicWork(
                "drivewatch15m", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DriveWatchWorker>(15, TimeUnit.MINUTES).build()
            )
            wm.enqueueUniquePeriodicWork(
                "cameras7d", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CamerasWorker>(7, TimeUnit.DAYS).setConstraints(net).build()
            )
        }.onFailure { Errors.log("background.schedule", it) }
        ru.shturman.update.AutoUpdate.schedule(ctx)
    }
}
