package ru.shturman.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import ru.shturman.R
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import ru.shturman.routing.CaravanMember
import ru.shturman.routing.GpsTracker
import ru.shturman.routing.pointsGeoJson
import ru.shturman.routing.LatLon
import ru.shturman.routing.Snap
import ru.shturman.routing.lineGeoJson
import ru.shturman.routing.pointGeoJson

private const val FOLLOW_ZOOM = 16.8 // чуть дальше, чем было: впереди видно больше дороги
private const val FOLLOW_TILT = 60.0

// Камера в поездке: ближе и круче, чем при обычном слежении
private const val START_ZOOM = 16.8 // после «Поехали» камера останавливается там же, где ведёт в поездке
private const val START_TILT = 64.0

/**
 * Масштаб камеры в поездке: до 50 км/ч — 16,8, дальше чем быстрее, тем дальше видно (130 км/ч — 15,6).
 * Если поворот ближе 8 секунд пути (не меньше 120 м) и едем не быстрее 80 км/ч — на 0,4 ближе, чтобы разглядеть его.
 */
internal fun autoZoom(kmh: Int, turnAheadM: Double): Double {
    val v = kmh.coerceIn(0, 150).toDouble()
    val base = if (v <= 50.0) FOLLOW_ZOOM else (FOLLOW_ZOOM - (v - 50.0) / 80.0 * 1.2).coerceAtLeast(15.6)
    val nearTurn = v < 80.0 && turnAheadM < maxOf(120.0, v / 3.6 * 8.0)
    return if (nearTurn) base + 0.4 else base
}

/** Наклон камеры: с 3D-картой — сильный (как в варианте «глубина»), без неё — умеренный. */
private object MapCam {
    var follow = FOLLOW_TILT
    var start = START_TILT
}

/**
 * Карта на весь экран: тёмная 3D-карта.
 * Стрелка «ты здесь» повёрнута по курсу, а во время поездки едет прямо по линии маршрута.
 *
 * recenterKey — каждый раз, когда число растёт, камера возвращается к стрелке.
 * startKey — число растёт при нажатии «Поехали»: камера плавно приближается к стрелке.
 * navigating — идёт поездка.
 * onFix вызывается на каждое новое положение GPS (примерно раз в секунду); если вернёт Snap,
 * стрелка рисуется в этой точке маршрута и смотрит по направлению дороги.
 */
@Composable
fun DarkMap(
    modifier: Modifier = Modifier,
    recenterKey: Int = 0,
    startKey: Int = 0,
    navigating: Boolean = false,
    route: List<LatLon>? = null,
    destination: LatLon? = null,
    bumps: List<LatLon> = emptyList(),
    works: List<LatLon> = emptyList(),
    danger: List<LatLon> = emptyList(),
    members: List<CaravanMember> = emptyList(),
    /** Участники клана (без меня): точка, имя со скоростью и их маршрут. */
    clan: List<ru.shturman.routing.ClanMember> = emptyList(),
    satellite: Boolean = false,
    themeId: Int = 0,
    map3d: Boolean = true,
    /** Где держать стрелку в поездке: расстояние от низа экрана до её центра (0 — по центру, как обычно). */
    markerFromBottom: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
    /** Скорость, км/ч: в поездке от неё зависит масштаб (быстрее — видно дальше). Читается по запросу, чтобы карта не перерисовывалась на каждое положение. */
    speedKmh: () -> Int = { 0 },
    /** До ближайшего поворота, м: перед поворотом камера чуть ближе. */
    turnAheadM: () -> Double = { Double.MAX_VALUE },
    /** Отступ стрелки слева (горизонтальный экран: стрелка по центру свободной части справа от колонки). */
    markerFromLeft: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
    /** Поля при показе маршрута целиком: сверху кнопки, снизу шторка вариантов, слева колонка (горизонтально). */
    previewTop: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(130f),
    previewBottom: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(330f),
    previewLeft: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(32f),
    /** Каждый раз, когда число растёт, в поездке показывается весь оставшийся путь; через 12 секунд — обратно к стрелке. */
    overviewKey: Int = 0,
    /** Камера едет за стрелкой (true) или её сдвинули пальцем / показан весь путь (false). */
    onFollowChanged: (Boolean) -> Unit = {},
    onFix: (LatLon, Float, Float, Float, Float) -> Snap? = { _, _, _, _, _ -> null }
) {
    val context = LocalContext.current

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var routeShown by remember { mutableStateOf(false) }
    // Заглушка, пока карта не прорисовалась; через 15 секунд без карты — сообщение об ошибке
    var mapReady by remember { mutableStateOf(false) }
    var mapFailed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val currentOnFix by rememberUpdatedState(onFix)
    val currentNavigating by rememberUpdatedState(navigating)
    val currentRoute by rememberUpdatedState(route)
    val currentSpeed by rememberUpdatedState(speedKmh)
    val currentTurnAhead by rememberUpdatedState(turnAheadM)
    val currentMarkerBottom by rememberUpdatedState(markerFromBottom)
    val currentMarkerLeft by rememberUpdatedState(markerFromLeft)
    val currentPreviewTop by rememberUpdatedState(previewTop)
    val currentPreviewBottom by rememberUpdatedState(previewBottom)
    val currentPreviewLeft by rememberUpdatedState(previewLeft)
    val currentMap3d by rememberUpdatedState(map3d)
    // Когда маршрут последний раз вписывался в экран целиком (чтобы понять, двигали ли карту после этого)
    val previewFitAt = remember { longArrayOf(0L) }
    // С какими полями (сверху, снизу, слева, dp) маршрут вписывался последний раз
    val previewFitInsets = remember { floatArrayOf(0f, 0f, 0f) }
    // Что сейчас нарисовано линией маршрута (чтобы не пересылать карте одно и то же)
    val routeLine = remember { RouteLineCache() }
    // Когда пользователь последний раз сам двигал или масштабировал карту пальцами
    val lastGestureAt = remember { longArrayOf(0L) }
    // Где стрелка на линии маршрута (номер точки): обзор показывает путь отсюда до конца
    val lastSnapIndex = remember { intArrayOf(0) }
    val currentOnFollow by rememberUpdatedState(onFollowChanged)
    val densityNow = androidx.compose.ui.platform.LocalDensity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasPermission) ru.shturman.service.DriveWatch.start(context)
    }

    // Уведомление о поездке (Android 13+): просим один раз, отказ не мешает работе
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(hasPermission) {
        if (hasPermission && android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notif_asked", false)) {
                prefs.edit().putBoolean("notif_asked", true).apply()
                runCatching { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        // Больше места под кэш карты: уже виденные места показываются и при плохой связи
        runCatching {
            org.maplibre.android.offline.OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
                300L * 1024 * 1024,
                object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                    override fun onSuccess() {}
                    override fun onError(message: String) {}
                }
            )
        }
        MapView(context).apply {
            addOnDidFinishLoadingMapListener { mapReady = true; mapFailed = false }
            addOnDidBecomeIdleListener { mapReady = true; mapFailed = false }
            addOnDidFailLoadingMapListener { if (!mapReady) mapFailed = true }
            getMapAsync { m ->
                map = m
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) lastGestureAt[0] = System.currentTimeMillis()
                }
                // Долгое нажатие на карту копирует координаты точки
                m.addOnMapLongClickListener { p ->
                    runCatching {
                        val text = String.format(java.util.Locale.US, "%.6f, %.6f", p.latitude, p.longitude)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Координаты", text))
                        android.widget.Toast.makeText(context, "Координаты скопированы: $text", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                m.setStyle(
                    Style.Builder()
                        .fromJson(MapStyle.JSON)
                        .withImage(MapStyle.ONEWAY_IMAGE, MapStyle.onewayBitmap())
                        .withImage(MapStyle.HEART_IMAGE, MapStyle.heartBitmap())
                ) { loaded -> style = loaded }
                // Пока нет GPS, показываем Псков
                val saved = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                val sLat = saved.getFloat("map_lat", 0f).toDouble()
                val sLon = saved.getFloat("map_lon", 0f).toDouble()
                val hasSaved = sLat in -85.0..85.0 && sLat != 0.0 && sLon != 0.0
                m.cameraPosition = CameraPosition.Builder()
                    .target(if (hasSaved) LatLng(sLat, sLon) else LatLng(57.8136, 28.3496))
                    .zoom(if (hasSaved) saved.getFloat("map_zoom", 15.5f).toDouble().coerceIn(3.0, 19.0) else 15.5)
                    .tilt(MapCam.follow)
                    .build()
            }
        }
    }

    /**
     * Поля камеры в поездке, чтобы стрелка стояла на высоте кнопки «Где я» (markerFromBottom — от низа экрана):
     * центр кадра = (высота + отступ сверху) / 2. Слева — колонка горизонтального экрана. Без поездки — без полей.
     */
    fun markerPadding(): DoubleArray {
        val h = mapView.height.toFloat()
        val w = mapView.width.toFloat()
        if (!currentNavigating || h <= 0f) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val fromBottom = with(densityNow) { currentMarkerBottom.toPx() }
        val fromLeft = with(densityNow) { currentMarkerLeft.toPx() }
        val top = if (fromBottom > 0f) {
            val y = (h - fromBottom).coerceAtLeast(h * 0.5f)
            (2f * y - h).coerceIn(0f, h * 0.7f)
        } else 0f
        val left = if (fromLeft > 0f) fromLeft.coerceIn(0f, w * 0.6f) else 0f
        return doubleArrayOf(left.toDouble(), top.toDouble(), 0.0, 0.0)
    }

    /** Масштаб, с которым камера возвращается к стрелке: в поездке — от скорости и поворота, иначе обычный. */
    fun followZoom(): Double = if (currentNavigating) autoZoom(currentSpeed(), currentTurnAhead()) else FOLLOW_ZOOM

    /**
     * Камера возвращается к стрелке и едет за ней. Сдвиг стрелки (поля) ставится в том же движении:
     * раньше камера сначала подлетала, а потом второй раз съезжала вниз.
     */
    fun follow(m: MapLibreMap, durationMs: Long, zoom: Double, tilt: Double) {
        val lc = m.locationComponent
        if (!lc.isLocationComponentActivated) return
        val pad = markerPadding()
        val loc = lc.lastKnownLocation
        if (lc.cameraMode != CameraMode.NONE) {
            // камера уже следит: подлёта нет, только плавно ставим поля
            lc.setCameraMode(CameraMode.TRACKING_GPS, durationMs, zoom, null, tilt, null)
            runCatching { lc.paddingWhileTracking(pad, 400L) }
            return
        }
        if (loc == null || !loc.latitude.isFinite() || !loc.longitude.isFinite()) {
            // стрелки ещё нет: поля ставим сразу, подлёт — как раньше
            runCatching { m.moveCamera(CameraUpdateFactory.paddingTo(pad[0], pad[1], pad[2], pad[3])) }
            lc.setCameraMode(CameraMode.TRACKING_GPS, durationMs, zoom, null, tilt, null)
            return
        }
        val target = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(zoom)
            .tilt(tilt)
            .bearing(loc.bearing.toDouble())
            .padding(pad)
            .build()
        // кнопки сразу как при слежении («Весь маршрут», а не «Где я»)
        currentOnFollow(true)
        runCatching {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(target), durationMs.toInt().coerceAtLeast(1), object : MapLibreMap.CancelableCallback {
                override fun onCancel() {
                    if (lc.isLocationComponentActivated && lc.cameraMode == CameraMode.NONE) currentOnFollow(false)
                    // подлёт прервали пальцем в поездке — как и при слежении, через 8 секунд камера вернётся сама
                    if (currentNavigating && System.currentTimeMillis() - lastGestureAt[0] < 1500L) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (currentNavigating && lc.isLocationComponentActivated && lc.cameraMode == CameraMode.NONE &&
                                System.currentTimeMillis() - lastGestureAt[0] >= 7500L
                            ) follow(m, 600L, followZoom(), MapCam.follow)
                        }, 8000L)
                    }
                }
                override fun onFinish() {
                    // уже на месте: дальше камера просто едет за стрелкой (короткий доезд, если стрелка сдвинулась)
                    if (lc.isLocationComponentActivated && lc.cameraMode == CameraMode.NONE) {
                        lc.setCameraMode(CameraMode.TRACKING_GPS, 300L, zoom, null, tilt, null)
                    }
                }
            })
        }.onFailure { lc.setCameraMode(CameraMode.TRACKING_GPS, durationMs, zoom, null, tilt, null) }
    }

    // Свёрнутое приложение без поездки GPS не держит (батарея); в поездке приём идёт и в свёрнутом виде
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifeState by lifecycleOwner.lifecycle.currentStateAsState()
    val trackGps = navigating || lifeState.isAtLeast(Lifecycle.State.STARTED)
    // Экран виден: анимации на карте нужны только тогда
    val visibleNow = lifeState.isAtLeast(Lifecycle.State.STARTED)

    // Лёгкий дымный след за стрелкой: бледные расплывчатые пятна, которые растут и гаснут за долю секунды.
    // В новом оформлении следа нет; в свёрнутом приложении след не считается.
    val trail = remember { SmokeTrail() }
    LaunchedEffect(style, themeId, visibleNow) {
        val src = style?.getSourceAs<GeoJsonSource>("trail") ?: return@LaunchedEffect
        if (ru.shturman.ui.theme.ShColors.premium || !visibleNow) {
            src.setGeoJson(EMPTY_FEATURES)
            return@LaunchedEffect
        }
        var sentEmpty = false
        while (true) {
            kotlinx.coroutines.delay(90)
            val json = trail.tick()
            // пустой след отправляем один раз, а не каждые 90 мс
            if (trail.isEmpty) {
                if (!sentEmpty) { src.setGeoJson(json); sentEmpty = true }
            } else {
                src.setGeoJson(json)
                sentEmpty = false
            }
        }
    }

    // Включаем стрелку и свой приём GPS, когда есть и стиль, и разрешение
    DisposableEffect(map, style, hasPermission, trackGps) {
        val m = map
        val st = style
        if (m == null || st == null || !hasPermission || !trackGps) {
            return@DisposableEffect onDispose { }
        }
        // если маршрут уже показан целиком (и поездки нет), камеру к стрелке не уводим
        enableLocation(context, m, st, track = currentNavigating || (currentRoute?.size ?: 0) < 2)

        val bearing = BearingFilter()
        var lastTrimAt = 0L
        trail.setFrom(trail.currentPos(), LatLon(0.0, 0.0), false)
        val tracker = GpsTracker(context) { raw ->
            val here = LatLon(raw.latitude, raw.longitude)
            ru.shturman.routing.Diag.lastPos = here
            val snap = currentOnFix(here, raw.speed, if (raw.hasAccuracy()) raw.accuracy else 0f, if (raw.hasBearing()) raw.bearing else -1f, if (raw.hasAltitude()) raw.altitude.toFloat() else Float.NaN)

            // Пройденная часть маршрута исчезает за стрелкой: линия рисуется только от стрелки вперёд.
            // Линия пересылается карте не чаще раза в 1,5 с и только когда стрелка заметно продвинулась;
            // в свёрнутом приложении не пересылается вовсе (как вернёмся — обрежется сразу).
            val nowMs = System.currentTimeMillis()
            val r = currentRoute
            if (snap != null) lastSnapIndex[0] = snap.index
            if (snap != null && currentNavigating && r != null && r.size >= 2 &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                nowMs - lastTrimAt >= 1500L && routeLine.needsTrim(r, snap.index, snap.point)
            ) {
                lastTrimAt = nowMs
                st.getSourceAs<GeoJsonSource>("route")?.setGeoJson(routeLine.trimmed(r, snap.index, snap.point))
            }

            val shown = Location(raw)
            if (snap != null) {
                // В поездке: стрелка на линии маршрута и смотрит туда же, куда дорога
                shown.latitude = snap.point.lat
                shown.longitude = snap.point.lon
                bearing.update(snap.bearing)
            } else if (raw.hasBearing() && raw.speed > 1.5f) {
                // Вне маршрута: курс по движению; стоя на месте остаётся прошлый курс
                bearing.update(raw.bearing.toDouble())
            }
            bearing.value?.let { shown.bearing = it }

            if (shown.latitude.isFinite() && shown.longitude.isFinite()) {
                trail.setFrom(trail.currentPos(), LatLon(shown.latitude, shown.longitude), raw.speed > 1.5f)
            }
            if (shown.latitude.isFinite() && shown.longitude.isFinite() && m.locationComponent.isLocationComponentActivated) {
                m.locationComponent.forceLocationUpdate(shown)
            }
        }
        tracker.start()
        // Пока экран карты сам получает положения, фоновая служба не пишет скорости второй раз
        ru.shturman.service.DriveWatch.uiTracking = true
        ru.shturman.service.DriveWatch.start(context)

        // Если во время поездки камеру сдвинули пальцем, через 8 секунд она сама возвращается к стрелке
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val back = Runnable {
            val lc = m.locationComponent
            if (currentNavigating && lc.isLocationComponentActivated && lc.cameraMode == CameraMode.NONE) {
                follow(m, 600L, followZoom(), MapCam.follow)
            }
        }
        val trackListener = object : org.maplibre.android.location.OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                if (currentNavigating) {
                    handler.removeCallbacks(back)
                    handler.postDelayed(back, 8000L)
                }
            }
            override fun onCameraTrackingChanged(currentMode: Int) {
                currentOnFollow(currentMode != CameraMode.NONE)
            }
        }
        runCatching { m.locationComponent.addOnCameraTrackingChangedListener(trackListener) }
        onDispose {
            runCatching {
                val c = m.cameraPosition.target
                if (c != null) context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit()
                    .putFloat("map_lat", c.latitude.toFloat()).putFloat("map_lon", c.longitude.toFloat())
                    .putFloat("map_zoom", m.cameraPosition.zoom.toFloat()).apply()
            }
            tracker.stop()
            ru.shturman.service.DriveWatch.uiTracking = false
            handler.removeCallbacks(back)
            runCatching { m.locationComponent.removeOnCameraTrackingChangedListener(trackListener) }
        }
    }

    // Линия маршрута и точка назначения на карте
    LaunchedEffect(style, route) {
        val src = style?.getSourceAs<GeoJsonSource>("route")
        val bloom = style?.getSourceAs<GeoJsonSource>("bloom")
        val th = ru.shturman.ui.theme.ShColors.T
        if (src != null && route != null && route.size > 2 && !currentNavigating && th.routeMs > 0) {
            // Маршрут прорисовывается от начала к концу; у некоторых тем рывками
            val steps = (th.routeMs / 40).coerceIn(8, 30)
            val frames = if (th.routeJerky) 8 else steps
            for (k in 1 until frames) {
                // нажали «Поехали» или маршрут перестроился — прорисовку не продолжаем
                if (currentNavigating) break
                val upto = (route.size * k / frames).coerceAtLeast(2)
                src.setGeoJson(lineGeoJson(route.subList(0, upto)))
                // Принцесса: по пути расцветают цветочки
                if (th.id == 1) {
                    val every = (route.size / 50).coerceAtLeast(1)
                    bloom?.setGeoJson(pointsGeoJson((0 until upto step every).map { route[it] }))
                }
                kotlinx.coroutines.delay((th.routeMs / frames).toLong())
            }
        }
        src?.setGeoJson(lineGeoJson(route))
        // линия снова целая: в поездке её надо обрезать заново
        routeLine.reset()
        if (th.id == 1 && bloom != null && route != null) {
            kotlinx.coroutines.delay(2500)
            bloom.setGeoJson(pointsGeoJson(emptyList()))
        }
    }
    LaunchedEffect(style, bumps) {
        style?.getSourceAs<GeoJsonSource>("bumps")?.setGeoJson(pointsGeoJson(bumps))
    }
    LaunchedEffect(style, works) {
        style?.getSourceAs<GeoJsonSource>("works")?.setGeoJson(pointsGeoJson(works))
    }
    LaunchedEffect(style, danger) {
        style?.getSourceAs<GeoJsonSource>("danger")?.setGeoJson(pointsGeoJson(danger))
    }
    LaunchedEffect(style, members) {
        val features = members.joinToString(",") { m ->
            val name = m.name.filter { it.isLetterOrDigit() || it == ' ' }
            String.format(
                java.util.Locale.US,
                "{\"type\":\"Feature\",\"properties\":{\"name\":\"%s\",\"leader\":%b},\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]}}",
                name, m.leader, m.position.lon, m.position.lat
            )
        }
        style?.getSourceAs<GeoJsonSource>("caravan")?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[$features]}")
    }
    // Клан: точки участников с именем и скоростью и их маршруты пунктиром своего цвета.
    // Раз в 30 с подписи пересчитываются, чтобы «был N мин назад» не застывало.
    LaunchedEffect(style, clan) {
        val st = style ?: return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val shown = clan.filter { it.sharing && it.position != null && now - it.updated < ru.shturman.routing.CLAN_SHOW_MS }
            val points = shown.joinToString(",") { m ->
                val p = m.position!!
                val fresh = now - m.updated <= ru.shturman.routing.CLAN_FRESH_MS
                val name = m.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
                val label = if (fresh) (if (m.speedKmh >= 5) "$name\\n${m.speedKmh} км/ч" else "$name\\nстоит")
                    else "$name\\n${ru.shturman.routing.clanStatus(m, now)}"
                String.format(
                    java.util.Locale.US,
                    "{\"type\":\"Feature\",\"properties\":{\"label\":\"%s\",\"color\":\"%s\",\"fresh\":%b},\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]}}",
                    label, m.color, fresh, p.lon, p.lat
                )
            }
            st.getSourceAs<GeoJsonSource>("clan")?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[$points]}")
            val lines = shown.filter { it.route != null && now - it.updated <= 10 * 60_000L }.joinToString(",") { m ->
                val coords = m.route!!.joinToString(",") { String.format(java.util.Locale.US, "[%.5f,%.5f]", it.lon, it.lat) }
                "{\"type\":\"Feature\",\"properties\":{\"color\":\"${m.color}\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coords]}}"
            }
            st.getSourceAs<GeoJsonSource>("clan-routes")?.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[$lines]}")
            if (shown.isEmpty()) break
            kotlinx.coroutines.delay(30_000L)
        }
    }
    // Спутник: показываем снимки поверх карты и прячем 3D-дома
    LaunchedEffect(style, satellite) {
        val st = style ?: return@LaunchedEffect
        st.getLayer("sat-layer")?.setProperties(PropertyFactory.visibility(if (satellite) Property.VISIBLE else Property.NONE))
        st.getLayer("building-3d")?.setProperties(PropertyFactory.visibility(if (satellite) Property.NONE else Property.VISIBLE))
    }
    // Тема оформления: цвета карты и маршрута
    LaunchedEffect(style, themeId) {
        style?.let { MapStyle.applyTheme(it, ru.shturman.ui.theme.ShColors.T) }
        val m = map ?: return@LaunchedEffect
        runCatching { if (m.locationComponent.isLocationComponentActivated) m.locationComponent.applyStyle(puckOptions(context)) }
    }
    LaunchedEffect(style, destination) {
        style?.getSourceAs<GeoJsonSource>("dest")?.setGeoJson(pointGeoJson(destination))
    }

    // 3D-карта: высокие дома и сильный наклон; без неё дома плоские, наклон меньше
    MapCam.follow = if (map3d) FOLLOW_TILT else 40.0
    MapCam.start = if (map3d) START_TILT else 45.0
    LaunchedEffect(style, map3d) {
        val st = style ?: return@LaunchedEffect
        st.getLayer("building-3d")?.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.visibility(
                if (map3d) org.maplibre.android.style.layers.Property.VISIBLE else org.maplibre.android.style.layers.Property.NONE
            )
        )
        val m = map ?: return@LaunchedEffect
        runCatching { if (m.locationComponent.isLocationComponentActivated) m.locationComponent.tiltWhileTracking(MapCam.follow) }
    }

    // Просмотр маршрута: по линии бежит светящаяся точка; точка назначения пульсирует кольцом
    LaunchedEffect(style, route, navigating, destination, themeId, visibleNow) {
        val st = style ?: return@LaunchedEffect
        val comet = st.getSourceAs<GeoJsonSource>("comet")
        val pulse = st.getLayer("dest-pulse")
        comet?.setGeoJson(pointsGeoJson(emptyList()))
        if (destination == null) return@LaunchedEffect
        // новое оформление: без бегущей точки и пульса, точка назначения спокойная
        if (ru.shturman.ui.theme.ShColors.premium) return@LaunchedEffect
        // в поездке кольцо у точки назначения стоит спокойно, а в свёрнутом приложении ничего не анимируем
        if (navigating || !visibleNow) {
            pulse?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.circleRadius(16f),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity(0.7f)
            )
            return@LaunchedEffect
        }
        val pts = route
        val cum = if (pts != null && pts.size >= 2) DoubleArray(pts.size).also { c ->
            for (i in 1 until pts.size) c[i] = c[i - 1] + ru.shturman.routing.distanceM(pts[i - 1], pts[i])
        } else null
        if (cum != null && !navigating) delay(ru.shturman.ui.theme.ShColors.T.routeMs.toLong() + 300)
        val t0 = System.currentTimeMillis()
        while (true) {
            val t = System.currentTimeMillis() - t0
            if (cum != null && pts != null && !navigating) {
                val cyc = 3000L
                val ph = (t % cyc) / cyc.toDouble()
                val e = if (ph < 0.85) { val x = ph / 0.85; x * x * (3 - 2 * x) } else 1.0
                val target = cum.last() * e
                var i = java.util.Arrays.binarySearch(cum, target)
                if (i < 0) i = (-i - 2)
                i = i.coerceIn(0, pts.size - 2)
                val seg = (cum[i + 1] - cum[i]).coerceAtLeast(0.001)
                val f = ((target - cum[i]) / seg).coerceIn(0.0, 1.0)
                val a = pts[i]; val b = pts[i + 1]
                comet?.setGeoJson(pointGeoJson(LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)))
            }
            val q = (t % 1800L) / 1800f
            pulse?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.circleRadius(10f + 24f * q),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity(1f - q)
            )
            delay(40)
        }
    }

    /** Весь маршрут на экране с полями (поля берутся последние измеренные). */
    fun fitPreview(m: MapLibreMap, r: List<LatLon>, durationMs: Int) {
        val bounds = LatLngBounds.Builder()
            .includes(r.map { LatLng(it.lat, it.lon) })
            .build()
        val d = context.resources.displayMetrics.density
        runCatching { m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0)) }
        m.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds, 0.0, if (currentMap3d) 32.0 else 0.0,
                (currentPreviewLeft.value * d).toInt(), (currentPreviewTop.value * d).toInt(), (32 * d).toInt(), (currentPreviewBottom.value * d).toInt()
            ),
            durationMs
        )
        previewFitAt[0] = System.currentTimeMillis()
        previewFitInsets[0] = currentPreviewTop.value
        previewFitInsets[1] = currentPreviewBottom.value
        previewFitInsets[2] = currentPreviewLeft.value
    }

    // Когда маршрут построен, показываем его целиком; когда отменён или закончился, возвращаемся к себе
    LaunchedEffect(map, route, navigating) {
        val m = map ?: return@LaunchedEffect
        if (navigating) return@LaunchedEffect
        val active = m.locationComponent.isLocationComponentActivated
        if (route != null && route.size >= 2) {
            routeShown = true
            if (active) m.locationComponent.cameraMode = CameraMode.NONE
            fitPreview(m, route, 900)
        } else if (routeShown) {
            routeShown = false
            if (active) {
                // поля от показа маршрута (снизу — под шторку) снимаем в том же движении, что и возврат к стрелке
                m.locationComponent.cameraMode = CameraMode.NONE
                follow(m, 800L, FOLLOW_ZOOM, MapCam.follow)
            } else {
                runCatching { m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0)) }
            }
        }
    }

    // Шторка вариантов померилась уже после показа маршрута — вписываем его ещё раз, если карту не трогали пальцами
    LaunchedEffect(previewTop, previewBottom, previewLeft) {
        val m = map ?: return@LaunchedEffect
        val r = currentRoute
        if (currentNavigating || !routeShown || r == null || r.size < 2) return@LaunchedEffect
        if (previewFitAt[0] == 0L || lastGestureAt[0] > previewFitAt[0]) return@LaunchedEffect
        delay(150) // высота шторки меняется в несколько шагов — ждём, пока успокоится
        if (currentNavigating || currentRoute !== r || lastGestureAt[0] > previewFitAt[0]) return@LaunchedEffect
        // поля те же, с которыми уже вписали, — ничего не делаем
        if (previewFitInsets[0] == currentPreviewTop.value && previewFitInsets[1] == currentPreviewBottom.value &&
            previewFitInsets[2] == currentPreviewLeft.value) return@LaunchedEffect
        runCatching { fitPreview(m, r, 500) }
    }

    // В поездке стрелка стоит в нижней части экрана — на высоте кнопки «Где я» (markerFromBottom — расстояние
    // от низа экрана): впереди видно больше дороги и поворотов. Камера сдвигается отступом сверху:
    // центр кадра = (высота + отступ) / 2. Подлёт камеры при старте и возврат к стрелке могут сбить отступ,
    // поэтому в поездке он проверяется каждые полторы секунды и при необходимости ставится заново.
    LaunchedEffect(map, style, hasPermission, navigating, markerFromBottom, markerFromLeft) {
        val m = map ?: return@LaunchedEffect
        var tries = 0
        while (!m.locationComponent.isLocationComponentActivated && tries < 40) { delay(250); tries++ }
        if (!m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        while (true) {
            if (mapView.height > 0 && m.locationComponent.cameraMode != CameraMode.NONE) {
                val want = if (navigating) markerPadding() else doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                val pad = runCatching { m.cameraPosition.padding }.getOrNull()
                val curTop = pad?.getOrNull(1) ?: 0.0
                val curLeft = pad?.getOrNull(0) ?: 0.0
                val curRight = pad?.getOrNull(2) ?: 0.0
                val curBottom = pad?.getOrNull(3) ?: 0.0
                if (kotlin.math.abs(curTop - want[1]) > 2.0 || kotlin.math.abs(curLeft - want[0]) > 2.0 ||
                    kotlin.math.abs(curRight) > 2.0 || kotlin.math.abs(curBottom) > 2.0
                ) {
                    runCatching { m.locationComponent.paddingWhileTracking(want, 400L, null) }
                }
            }
            if (!navigating) {
                // поездка закончилась: снимаем сдвиг стрелки, даже если камера сейчас не следит
                // (но не трогаем показ маршрута целиком — там свои поля под шторку)
                val pad = runCatching { m.cameraPosition.padding }.getOrNull()
                if (!routeShown && pad != null && (kotlin.math.abs(pad.getOrNull(1) ?: 0.0) > 2.0 || kotlin.math.abs(pad.getOrNull(0) ?: 0.0) > 2.0)) {
                    runCatching { m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0)) }
                }
                break
            }
            delay(1500)
        }
    }

    // Масштаб в поездке от скорости: в городе 16,8, на трассе дальше (до 15,6 на 130 км/ч);
    // перед поворотом (ближе 8 секунд пути) — чуть ближе. Если масштаб меняли пальцами, 10 секунд не трогаем.
    LaunchedEffect(map, navigating) {
        val m = map ?: return@LaunchedEffect
        if (!navigating) return@LaunchedEffect
        delay(3000) // сначала подлёт камеры после «Поехали»
        // Последний заданный масштаб: плавное приближение (1,5 с) не перезапускается каждую секунду,
        // пока цель та же. Если масштаб сбили (пальцами, обзором) — поправляем не чаще раза в 3 секунды.
        var lastTarget = Double.NaN
        var lastIssuedAt = 0L
        while (true) {
            delay(1000)
            val lc = m.locationComponent
            if (!lc.isLocationComponentActivated || lc.cameraMode != CameraMode.TRACKING_GPS) continue
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastGestureAt[0] < 10_000L) continue
            val target = autoZoom(currentSpeed(), currentTurnAhead())
            val now = m.cameraPosition.zoom
            val targetChanged = lastTarget.isNaN() || kotlin.math.abs(target - lastTarget) > 0.12
            val drifted = kotlin.math.abs(now - target) > 0.12 && nowMs - lastIssuedAt > 3000L
            if (kotlin.math.abs(now - target) > 0.12 && (targetChanged || drifted)) {
                lastTarget = target
                lastIssuedAt = nowMs
                runCatching { lc.zoomWhileTracking(target, 1500L) }
            }
        }
    }

    // «Поехали»: пока идёт заставка, камера плавно подлетает к стрелке
    LaunchedEffect(startKey) {
        val m = map ?: return@LaunchedEffect
        if (startKey > 0 && m.locationComponent.isLocationComponentActivated) {
            // Спутники ещё не поймались — ставим стрелку в начало маршрута (оттуда он и построен),
            // чтобы камере было куда подлететь. Как придёт настоящее положение, стрелка переедет туда.
            val r = currentRoute
            if (!ru.shturman.routing.Diag.hasLiveFix() && r != null && r.size >= 2) {
                runCatching {
                    m.locationComponent.forceLocationUpdate(Location("route").apply {
                        latitude = r[0].lat
                        longitude = r[0].lon
                        bearing = ru.shturman.routing.bearingDeg(r[0], r[1]).toFloat()
                        time = System.currentTimeMillis()
                    })
                }
            }
            delay(250)
            follow(m, 2100L, START_ZOOM, MapCam.start)
        }
    }

    // «Обзор»: весь оставшийся путь на экране, через 12 секунд камера сама возвращается к стрелке
    LaunchedEffect(overviewKey) {
        val m = map ?: return@LaunchedEffect
        val r = currentRoute
        if (overviewKey == 0 || r == null || r.size < 2) return@LaunchedEffect
        val lc = m.locationComponent
        if (lc.isLocationComponentActivated) lc.cameraMode = CameraMode.NONE
        val from = lastSnapIndex[0].coerceIn(0, r.size - 2)
        val d = context.resources.displayMetrics.density
        runCatching {
            val bounds = LatLngBounds.Builder().includes(r.subList(from, r.size).map { LatLng(it.lat, it.lon) }).build()
            m.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
            m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds, 0.0, 0.0,
                    (previewLeft.value * d).toInt(), (previewTop.value * d).toInt(), (32 * d).toInt(), (previewBottom.value * d).toInt()
                ),
                900
            )
        }
        delay(12_000)
        if (currentNavigating && lc.isLocationComponentActivated && lc.cameraMode == CameraMode.NONE) {
            follow(m, 800L, followZoom(), MapCam.follow)
        }
    }

    // Кнопка «вернуться к себе»
    LaunchedEffect(recenterKey) {
        val m = map ?: return@LaunchedEffect
        if (recenterKey > 0 && m.locationComponent.isLocationComponentActivated) {
            follow(m, 600L, followZoom(), MapCam.follow)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(reloadKey) {
        kotlinx.coroutines.delay(15_000)
        if (!mapReady) mapFailed = true
    }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        androidx.compose.animation.AnimatedVisibility(
            visible = !mapReady,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500))
        ) {
            MapPlaceholder(
                failed = mapFailed,
                onRetry = {
                    mapFailed = false
                    reloadKey++
                    runCatching { map?.triggerRepaint() }
                }
            )
        }
    }
}

/** Сглаживает курс, чтобы стрелка не дёргалась на изгибах дороги и шуме GPS. */
private class BearingFilter {
    var value: Float? = null
        private set

    fun update(target: Double) {
        val current = value
        value = if (current == null) {
            target.toFloat()
        } else {
            turnToward(current.toDouble(), target, 0.45).toFloat()
        }
    }
}

/** Поворот угла a к углу b по кратчайшему пути, на долю k. */
private fun turnToward(a: Double, b: Double, k: Double): Double {
    val diff = ((b - a + 540.0) % 360.0) - 180.0
    return (a + diff * k + 360.0) % 360.0
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Значок «я здесь»: в новом оформлении — белая стрелка с тёмной обводкой (res/drawable/puck_premium.xml),
 * у APEX — прежняя голубая (puck_arrow.xml). Бледный круг точности GPS — цветом акцента темы.
 */
private fun puckOptions(context: Context): LocationComponentOptions {
    val premium = ru.shturman.ui.theme.ShColors.premium
    val icon = if (premium) R.drawable.puck_premium else R.drawable.puck_arrow
    val accuracy = if (premium) android.graphics.Color.argb(255,
        (ru.shturman.ui.theme.ShColors.Accent.red * 255).toInt(),
        (ru.shturman.ui.theme.ShColors.Accent.green * 255).toInt(),
        (ru.shturman.ui.theme.ShColors.Accent.blue * 255).toInt()) else 0xFF3DDCFF.toInt()
    return LocationComponentOptions.builder(context)
        .pulseEnabled(false)
        .accuracyAlpha(if (premium) 0.12f else 0.16f) // бледный круг показывает, насколько GPS сам не уверен в месте
        .accuracyColor(accuracy)
        .elevation(0f)
        .enableStaleState(false)
        .gpsDrawable(icon)
        .foregroundDrawable(icon)
        .bearingDrawable(icon)
        .minZoomIconScale(0.35f) // размер стрелки при отдалении
        .maxZoomIconScale(0.55f) // размер стрелки при приближении
        .build()
}

@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style, track: Boolean) {
    val component = map.locationComponent
    if (component.isLocationComponentActivated) return

    val options = puckOptions(context)

    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(false) // положение подаём сами, из GpsTracker
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.GPS          // стрелка повёрнута по курсу
    // камера едет за стрелкой и поворачивается по курсу; если на экране показан маршрут целиком — не уводим её
    if (track) {
        component.cameraMode = CameraMode.TRACKING_GPS
        component.zoomWhileTracking(FOLLOW_ZOOM)
        component.tiltWhileTracking(MapCam.follow)
    }
}


/**
 * Дымный след: положение стрелки между двумя GPS-точками считается так же плавно, как её двигает карта,
 * и через каждые ~90 мс в этом месте остаётся пятно, которое за 0,7 с растёт и растворяется.
 */
private class SmokeTrail {
    private class Puff(val lat: Double, val lon: Double, val born: Long)

    private var from: LatLon? = null
    private var to: LatLon? = null
    private var t0 = 0L
    private var moving = false
    private val puffs = ArrayList<Puff>()

    fun setFrom(start: LatLon?, end: LatLon, isMoving: Boolean) {
        if (end.lat == 0.0 && end.lon == 0.0) { from = null; to = null; moving = false; return }
        from = start ?: end
        to = end
        t0 = System.currentTimeMillis()
        moving = isMoving
    }

    fun currentPos(): LatLon? {
        val a = from ?: return to
        val b = to ?: return a
        val f = ((System.currentTimeMillis() - t0) / 1000.0).coerceIn(0.0, 1.0)
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }

    /** Пятен нет — след пустой. */
    val isEmpty: Boolean get() = puffs.isEmpty()

    fun tick(): String {
        val now = System.currentTimeMillis()
        val pos = currentPos()
        if (moving && pos != null && pos.lat.isFinite() && pos.lon.isFinite()) puffs.add(Puff(pos.lat, pos.lon, now))
        puffs.removeAll { now - it.born > LIFE_MS }
        val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
        puffs.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            val a = ((now - p.born).toDouble() / LIFE_MS).coerceIn(0.0, 1.0)
            sb.append("{\"type\":\"Feature\",\"properties\":{\"a\":").append(a)
                .append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[").append(p.lon).append(',').append(p.lat).append("]}}")
        }
        return sb.append("]}").toString()
    }

    private companion object { const val LIFE_MS = 700L }
}

private const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""

/**
 * Линия маршрута в поездке: координаты точек переводятся в текст один раз на маршрут,
 * а обрезанная линия (от стрелки до конца) собирается из готовых кусков.
 */
private class RouteLineCache {
    private var coordsFor: List<LatLon>? = null
    private var coords: Array<String> = emptyArray()
    private var trimmedFor: List<LatLon>? = null
    private var trimmedIndex = -1
    private var trimmedHead: LatLon? = null

    /** Линию нарисовали целиком заново — следующую обрезку надо сделать обязательно. */
    fun reset() {
        trimmedFor = null
        trimmedIndex = -1
        trimmedHead = null
    }

    /** Нужно ли обрезать заново: стрелка перешла на другой отрезок или ушла вперёд больше чем на 15 м. */
    fun needsTrim(r: List<LatLon>, index: Int, head: LatLon): Boolean {
        if (trimmedFor !== r || index != trimmedIndex) return true
        val prev = trimmedHead ?: return true
        return ru.shturman.routing.distanceM(prev, head) > 15.0
    }

    fun trimmed(r: List<LatLon>, index: Int, head: LatLon): String {
        if (coordsFor !== r) {
            coords = Array(r.size) { String.format(java.util.Locale.US, "[%.6f,%.6f]", r[it].lon, r[it].lat) }
            coordsFor = r
        }
        trimmedFor = r
        trimmedIndex = index
        trimmedHead = head
        val from = (index + 1).coerceAtLeast(0)
        if (from >= r.size) return EMPTY_FEATURES
        val sb = StringBuilder(64 + (r.size - from + 1) * 24)
        sb.append("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
        sb.append(String.format(java.util.Locale.US, "[%.6f,%.6f]", head.lon, head.lat))
        for (j in from until r.size) sb.append(',').append(coords[j])
        sb.append("]}}")
        return sb.toString()
    }
}
