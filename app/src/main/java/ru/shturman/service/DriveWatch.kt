package ru.shturman.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import ru.shturman.routing.LatLon
import ru.shturman.routing.SpeedRecorder
import ru.shturman.routing.SpeedStore
import ru.shturman.routing.StopRecorder

/**
 * Фоновый сбор обезличенных данных о дорогах во время любой поездки, даже когда приложение закрыто:
 * скорость на участках (квадрат карты + час недели) и ожидание на светофорах. Имя, аккаунт и маршруты
 * не записываются. Данные копятся в той же базе, что и в навигации, и раз в 2 часа уходят в общую базу.
 *
 * Чтобы не сажать батарею, служба почти всё время «дремлет»:
 *  - ДРЕМЛЕТ: слушает чужие положения (пассивно), редкие экономные положения по сети и датчик движения;
 *  - ПРОВЕРЯЕТ: если похоже на поездку — включает GPS на 2 минуты и смотрит скорость;
 *  - ЗАПИСЫВАЕТ: едем быстрее ~22 км/ч — пишет скорости и остановки; 4 минуты стоим — снова дремлет.
 * Пока открыт экран карты, пишет само приложение, а служба только следит за режимом (без двойного счёта).
 */
class DriveWatchService : Service() {
    private enum class Mode { IDLE, PROBE, DRIVE }

    private lateinit var lm: LocationManager
    private lateinit var speeds: SpeedRecorder
    private lateinit var stops: StopRecorder
    private val handler = Handler(Looper.getMainLooper())
    private var mode = Mode.IDLE
    private var started = false
    private var probeUntil = 0L
    private var lastMovingAt = 0L
    private var lastLoose: Location? = null
    private var failedProbes = 0
    private var nextProbeAt = 0L
    private var nextLooseProbeAt = 0L
    private var lastGpsNanos = 0L
    private var gpsOn = false
    private var wake: PowerManager.WakeLock? = null
    private var sensors: SensorManager? = null
    private var motion: Sensor? = null
    private var motionArmed = false

    private val motionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            motionArmed = false
            handler.post { if (mode == Mode.IDLE) tryProbe(cooldown = true) }
        }
    }

    private val listener = LocationListenerCompat { loc -> onLocation(loc) }

    /** Раз в 5 секунд: не истекла ли проверка, не пора ли перестать писать поездку. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            when (mode) {
                Mode.PROBE -> if (now > probeUntil) {
                    failedProbes++
                    // Чем чаще ложные тревоги (ходим пешком), тем реже проверяем по датчику: 5, 10, 20, 30 минут
                    nextProbeAt = now + minOf(30L, 5L shl (failedProbes - 1).coerceIn(0, 3)) * 60_000L
                    nextLooseProbeAt = now + 2 * 60_000L
                    enterIdle()
                }
                Mode.DRIVE -> if (now - lastMovingAt > STILL_MS) enterIdle()
                Mode.IDLE -> Unit
            }
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val store = SpeedStore.shared(this)
        speeds = SpeedRecorder(store)
        stops = StopRecorder(store)
        sensors = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        motion = runCatching { sensors?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DriveWatch.hasLocation(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val n = notification(this, mode == Mode.DRIVE)
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(ID, n)
            }
        } catch (e: Exception) {
            ru.shturman.Errors.log("driveWatch.start", e)
            stopSelf()
            return START_NOT_STICKY
        }
        DriveWatch.running = true
        if (!started) {
            started = true
            enterIdle()
            handler.postDelayed(tick, 5000L)
        }
        // Подключились к Bluetooth машины — сразу проверяем, едем ли
        if (intent?.action == ACTION_POKE && mode == Mode.IDLE) tryProbe(cooldown = false)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(armTask)
        runCatching { LocationManagerCompat.removeUpdates(lm, listener) }
        disarmMotion()
        flush()
        releaseWake()
        DriveWatch.running = false
        DriveWatch.mode = "выключена"
        super.onDestroy()
    }

    // ---------- режимы ----------

    private fun enterIdle() {
        if (mode == Mode.DRIVE) flush()
        mode = Mode.IDLE
        DriveWatch.mode = "ждёт поездку"
        releaseWake()
        runCatching { LocationManagerCompat.removeUpdates(lm, listener) }
        gpsOn = false
        val all = runCatching { lm.allProviders }.getOrDefault(emptyList())
        // чужие положения — бесплатно; сеть и системный fused — редко и экономно, без GPS
        request(LocationManager.PASSIVE_PROVIDER, 0L, LocationRequestCompat.QUALITY_LOW_POWER)
        if (all.contains(LocationManager.NETWORK_PROVIDER)) request(LocationManager.NETWORK_PROVIDER, 60_000L, LocationRequestCompat.QUALITY_LOW_POWER)
        if (Build.VERSION.SDK_INT >= 31 && all.contains(LocationManager.FUSED_PROVIDER)) {
            request(LocationManager.FUSED_PROVIDER, 60_000L, LocationRequestCompat.QUALITY_LOW_POWER)
        }
        armMotionLater()
        updateNotification()
    }

    /** Датчик движения включаем сразу или после паузы, если недавно были ложные тревоги. */
    private fun armMotionLater() {
        val wait = nextProbeAt - SystemClock.elapsedRealtime()
        handler.removeCallbacks(armTask)
        if (wait <= 0L) armMotion() else handler.postDelayed(armTask, wait)
    }

    private val armTask = Runnable { if (mode == Mode.IDLE) armMotion() }

    private fun tryProbe(cooldown: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (mode != Mode.IDLE) return
        if (cooldown && now < nextProbeAt) { armMotionLater(); return }
        mode = Mode.PROBE
        DriveWatch.mode = "проверяет, едем ли"
        probeUntil = now + PROBE_MS
        gpsOn()
    }

    private fun enterDrive() {
        mode = Mode.DRIVE
        DriveWatch.mode = "записывает поездку"
        failedProbes = 0
        nextProbeAt = 0L
        lastMovingAt = SystemClock.elapsedRealtime()
        DriveWatch.drives++
        DriveWatch.lastDriveAt = System.currentTimeMillis()
        stops.reset()
        acquireWake()
        gpsOn()
        updateNotification()
    }

    private fun gpsOn() {
        if (gpsOn) return
        gpsOn = true
        request(LocationManager.GPS_PROVIDER, 1000L, LocationRequestCompat.QUALITY_HIGH_ACCURACY)
    }

    // ---------- положения ----------

    private fun onLocation(loc: Location) {
        if (!loc.latitude.isFinite() || !loc.longitude.isFinite()) return
        val now = SystemClock.elapsedRealtime()
        val gps = loc.provider == LocationManager.GPS_PROVIDER
        val acc = if (loc.hasAccuracy()) loc.accuracy else 999f
        when (mode) {
            Mode.IDLE -> {
                if (gps && loc.hasSpeed() && loc.speed >= DRIVE_MPS && acc <= 50f) {
                    // кто-то другой (например, Яндекс) уже включил GPS, и мы едем
                    enterDrive()
                } else if (!gps && looseSaysDriving(loc) && now >= nextLooseProbeAt) {
                    // сеть показывает, что мы явно куда-то едем — проверяем GPS без долгой паузы
                    tryProbe(cooldown = false)
                }
            }
            Mode.PROBE -> if (gps && loc.hasSpeed() && loc.speed >= DRIVE_MPS && acc <= 50f) enterDrive()
            Mode.DRIVE -> if (gps && loc.elapsedRealtimeNanos != lastGpsNanos) {
                // одно и то же положение может прийти дважды (своё и через «чужие»)
                lastGpsNanos = loc.elapsedRealtimeNanos
                val speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0
                if (speed >= MOVING_MPS) lastMovingAt = now
                // Пока открыт экран карты, скорости пишет само приложение — второй раз не считаем
                if (!DriveWatch.uiTracking && acc <= 30f) {
                    val wall = System.currentTimeMillis()
                    speeds.add(loc.latitude, loc.longitude, speed, wall)
                    stops.add(LatLon(loc.latitude, loc.longitude), speed, wall)
                    DriveWatch.points++
                }
            }
        }
    }

    /** Два грубых положения (сеть) подряд разнесены так, что мы явно едем быстрее ~30 км/ч. */
    private fun looseSaysDriving(loc: Location): Boolean {
        val prev = lastLoose
        lastLoose = loc
        if (prev == null) return false
        val dt = (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
        if (dt < 20.0 || dt > 600.0) return false
        val a1 = if (prev.hasAccuracy()) prev.accuracy else 999f
        val a2 = if (loc.hasAccuracy()) loc.accuracy else 999f
        if (a1 > 500f || a2 > 500f) return false
        val d = prev.distanceTo(loc)
        return d > (a1 + a2) && d / dt >= 8.0
    }

    // ---------- служебное ----------

    private fun request(provider: String, intervalMs: Long, quality: Int) {
        runCatching {
            val req = LocationRequestCompat.Builder(intervalMs).setQuality(quality).build()
            LocationManagerCompat.requestLocationUpdates(lm, provider, req, ContextCompat.getMainExecutor(this), listener)
        }.onFailure { if (it is SecurityException) stopSelf() }
    }

    private fun flush() {
        runCatching { speeds.flush() }
        runCatching { stops.reset() }
    }

    private fun armMotion() {
        val s = motion ?: return
        if (motionArmed) return
        motionArmed = runCatching { sensors?.requestTriggerSensor(motionListener, s) == true }.getOrDefault(false)
    }

    private fun disarmMotion() {
        val s = motion ?: return
        runCatching { sensors?.cancelTriggerSensor(motionListener, s) }
        motionArmed = false
    }

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apex:drive").also { it.acquire(4 * 60 * 60 * 1000L) }
        }
    }

    private fun releaseWake() {
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        wake = null
    }

    private fun updateNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ID, notification(this, mode == Mode.DRIVE))
        }
    }

    companion object {
        private const val CHANNEL = "road_data"
        private const val ID = 4005
        const val ACTION_POKE = "ru.shturman.action.DRIVE_POKE"
        /** Быстрее этого (≈22 км/ч) — это поездка на машине, а не пешком. */
        private const val DRIVE_MPS = 6f
        /** Быстрее этого (≈7 км/ч) считаем, что ещё едем (пробка, светофор не в счёт стоянки). */
        private const val MOVING_MPS = 2.0
        private const val PROBE_MS = 120_000L
        private const val STILL_MS = 4 * 60_000L

        private fun notification(ctx: Context, driving: Boolean): Notification {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Сбор данных о дорогах", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "Пока идёт сбор обезличенных скоростей для прогнозов"
                        setShowBadge(false)
                    }
                )
            }
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                PendingIntent.getActivity(ctx, 5, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            return Notification.Builder(ctx, CHANNEL).setSmallIcon(ru.shturman.R.drawable.ic_stat_apex)
                .setContentTitle(if (driving) "APEX: запись поездки" else "APEX")
                .setContentText(if (driving) "Записываю скорости на дороге (обезличенно)" else "Сбор данных о дорогах для прогнозов")
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
        }
    }
}

/** Запуск и состояние фонового сбора. */
object DriveWatch {
    @Volatile var running = false
    /** Экран карты сам получает положения и пишет скорости — службе не нужно считать их второй раз. */
    @Volatile var uiTracking = false
    @Volatile var mode = "выключена"
    @Volatile var drives = 0
    @Volatile var points = 0
    @Volatile var lastDriveAt = 0L

    fun hasLocation(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Разрешено ли положение «всегда» (нужно, чтобы сбор сам включался после перезагрузки телефона). */
    fun hasBackground(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else hasLocation(ctx)

    /** Снято ли для приложения ограничение батареи (иначе система может остановить сбор в фоне). */
    fun batteryFree(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }.getOrDefault(false)

    /** Включает сбор. poke — сразу проверить, едем ли (подключились к машине). Ошибки фонового запуска не страшны. */
    fun start(ctx: Context, poke: Boolean = false) {
        if (!hasLocation(ctx)) return
        val i = Intent(ctx, DriveWatchService::class.java)
        if (poke) i.action = DriveWatchService.ACTION_POKE
        runCatching {
            if (running) ctx.startService(i) else ContextCompat.startForegroundService(ctx, i)
        }.onFailure { ru.shturman.Errors.log("driveWatch.launch", it) }
    }

    fun text(ctx: Context): String {
        val ago = if (lastDriveAt == 0L) "не было" else "${(System.currentTimeMillis() - lastDriveAt) / 60_000} мин назад"
        return "Фоновый сбор: $mode, поездок $drives (последняя $ago), точек $points; " +
            "положение «всегда»: ${if (hasBackground(ctx)) "да" else "нет"}, без ограничения батареи: ${if (batteryFree(ctx)) "да" else "нет"}"
    }
}

/** После перезагрузки телефона и после обновления приложения снова включает сбор данных о дорогах. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" ->
                if (DriveWatch.hasBackground(context)) DriveWatch.start(context)
        }
    }
}
