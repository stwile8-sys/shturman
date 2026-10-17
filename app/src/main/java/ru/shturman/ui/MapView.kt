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

private const val FOLLOW_ZOOM = 17.0
private const val FOLLOW_TILT = 60.0

// Камера в поездке: ближе и круче, чем при обычном слежении
private const val START_ZOOM = 18.0
private const val START_TILT = 64.0

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
    satellite: Boolean = false,
    themeId: Int = 0,
    map3d: Boolean = true,
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

    // Лёгкий дымный след за стрелкой: бледные расплывчатые пятна, которые растут и гаснут за долю секунды
    val trail = remember { SmokeTrail() }
    LaunchedEffect(style) {
        val src = style?.getSourceAs<GeoJsonSource>("trail") ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(90)
            src.setGeoJson(trail.tick())
        }
    }

    // Включаем стрелку и свой приём GPS, когда есть и стиль, и разрешение
    DisposableEffect(map, style, hasPermission) {
        val m = map
        val st = style
        if (m == null || st == null || !hasPermission) {
            return@DisposableEffect onDispose { }
        }
        enableLocation(context, m, st)

        val bearing = BearingFilter()
        var lastTrimAt = 0L
        trail.setFrom(trail.currentPos(), LatLon(0.0, 0.0), false)
        val tracker = GpsTracker(context) { raw ->
            val here = LatLon(raw.latitude, raw.longitude)
            val snap = currentOnFix(here, raw.speed, if (raw.hasAccuracy()) raw.accuracy else 0f, if (raw.hasBearing()) raw.bearing else -1f, if (raw.hasAltitude()) raw.altitude.toFloat() else Float.NaN)

            // Пройденная часть маршрута исчезает за стрелкой: линия рисуется только от стрелки вперёд
            val nowMs = System.currentTimeMillis()
            val r = currentRoute
            if (snap != null && currentNavigating && r != null && nowMs - lastTrimAt > 400L) {
                lastTrimAt = nowMs
                val rest = ArrayList<LatLon>(maxOf(r.size - snap.index, 1))
                rest.add(snap.point)
                for (j in snap.index + 1 until r.size) rest.add(r[j])
                st.getSourceAs<GeoJsonSource>("route")?.setGeoJson(lineGeoJson(rest))
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
                lc.setCameraMode(CameraMode.TRACKING_GPS, 600L, FOLLOW_ZOOM, null, MapCam.follow, null)
            }
        }
        val trackListener = object : org.maplibre.android.location.OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                if (currentNavigating) {
                    handler.removeCallbacks(back)
                    handler.postDelayed(back, 8000L)
                }
            }
            override fun onCameraTrackingChanged(currentMode: Int) {}
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
    // Спутник: показываем снимки поверх карты и прячем 3D-дома
    LaunchedEffect(style, satellite) {
        val st = style ?: return@LaunchedEffect
        st.getLayer("sat-layer")?.setProperties(PropertyFactory.visibility(if (satellite) Property.VISIBLE else Property.NONE))
        st.getLayer("building-3d")?.setProperties(PropertyFactory.visibility(if (satellite) Property.NONE else Property.VISIBLE))
    }
    // Тема оформления: цвета карты и маршрута
    LaunchedEffect(style, themeId) {
        style?.let { MapStyle.applyTheme(it, ru.shturman.ui.theme.ShColors.T) }
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
    LaunchedEffect(style, route, navigating, destination) {
        val st = style ?: return@LaunchedEffect
        val comet = st.getSourceAs<GeoJsonSource>("comet")
        val pulse = st.getLayer("dest-pulse")
        comet?.setGeoJson(pointsGeoJson(emptyList()))
        if (destination == null) return@LaunchedEffect
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

    // Когда маршрут построен, показываем его целиком; когда отменён или закончился, возвращаемся к себе
    LaunchedEffect(map, route, navigating) {
        val m = map ?: return@LaunchedEffect
        if (navigating) return@LaunchedEffect
        val active = m.locationComponent.isLocationComponentActivated
        if (route != null && route.size >= 2) {
            routeShown = true
            if (active) m.locationComponent.cameraMode = CameraMode.NONE
            val bounds = LatLngBounds.Builder()
                .includes(route.map { LatLng(it.lat, it.lon) })
                .build()
            val d = context.resources.displayMetrics.density
            m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    bounds, 0.0, if (map3d) 32.0 else 0.0,
                    (32 * d).toInt(), (130 * d).toInt(), (32 * d).toInt(), (330 * d).toInt()
                ),
                900
            )
        } else if (routeShown) {
            routeShown = false
            if (active) {
                m.locationComponent.setCameraMode(
                    CameraMode.TRACKING_GPS, 800L, FOLLOW_ZOOM, null, MapCam.follow, null
                )
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
            m.locationComponent.setCameraMode(
                CameraMode.TRACKING_GPS, 2100L, START_ZOOM, null, MapCam.start, null
            )
        }
    }

    // Кнопка «вернуться к себе»
    LaunchedEffect(recenterKey) {
        val m = map ?: return@LaunchedEffect
        if (recenterKey > 0 && m.locationComponent.isLocationComponentActivated) {
            m.locationComponent.setCameraMode(
                CameraMode.TRACKING_GPS, 600L, FOLLOW_ZOOM, null, MapCam.follow, null
            )
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

@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
    val component = map.locationComponent
    if (component.isLocationComponentActivated) return

    // Все значки положения — одна и та же голубая стрелка (res/drawable/puck_arrow.xml)
    val options = LocationComponentOptions.builder(context)
        .pulseEnabled(false)
        .accuracyAlpha(0.16f) // бледный круг показывает, насколько GPS сам не уверен в месте
        .accuracyColor(0xFF3DDCFF.toInt())
        .elevation(0f)
        .enableStaleState(false)
        .gpsDrawable(R.drawable.puck_arrow)
        .foregroundDrawable(R.drawable.puck_arrow)
        .bearingDrawable(R.drawable.puck_arrow)
        .minZoomIconScale(0.35f) // размер стрелки при отдалении
        .maxZoomIconScale(0.55f) // размер стрелки при приближении
        .build()

    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(false) // положение подаём сами, из GpsTracker
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.GPS          // стрелка повёрнута по курсу
    component.cameraMode = CameraMode.TRACKING_GPS // камера едет за стрелкой и поворачивается по курсу
    component.zoomWhileTracking(FOLLOW_ZOOM)
    component.tiltWhileTracking(MapCam.follow)
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
