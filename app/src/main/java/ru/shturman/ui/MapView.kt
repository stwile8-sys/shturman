package ru.shturman.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val FOLLOW_ZOOM = 17.0
private const val FOLLOW_TILT = 60.0

/**
 * Карта на весь экран: тёмная 3D-карта. Показывает стрелку «ты здесь» и ведёт за ней камеру.
 * recenterKey: каждый раз, когда число меняется, камера снова начинает следить за стрелкой.
 */
@Composable
fun DarkMap(modifier: Modifier = Modifier, recenterKey: Int = 0) {
    val context = LocalContext.current

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
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
        MapView(context).apply {
            getMapAsync { m ->
                map = m
                m.setStyle(
                    Style.Builder()
                        .fromJson(MapStyle.JSON)
                        .withImage(MapStyle.ONEWAY_IMAGE, MapStyle.onewayBitmap())
                ) { loaded -> style = loaded }
                // Пока нет GPS, показываем Псков
                m.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(57.8136, 28.3496))
                    .zoom(15.5)
                    .tilt(FOLLOW_TILT)
                    .build()
            }
        }
    }

    // Включаем стрелку положения, когда есть и стиль, и разрешение
    LaunchedEffect(map, style, hasPermission) {
        val m = map
        val st = style
        if (m != null && st != null && hasPermission) {
            enableLocation(context, m, st)
        }
    }

    // Кнопка «вернуться к себе»
    LaunchedEffect(recenterKey) {
        val m = map ?: return@LaunchedEffect
        if (recenterKey > 0 && m.locationComponent.isLocationComponentActivated) {
            m.locationComponent.cameraMode = CameraMode.TRACKING_GPS
            m.locationComponent.zoomWhileTracking(FOLLOW_ZOOM)
            m.locationComponent.tiltWhileTracking(FOLLOW_TILT)
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

    AndroidView(factory = { mapView }, modifier = modifier)
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

    val options = LocationComponentOptions.builder(context)
        .pulseEnabled(false)
        .accuracyAlpha(0f)
        .foregroundTintColor(0xFF3DDCFF.toInt())
        .minZoomIconScale(0.3f) // размер стрелки при отдалении (было 0.6)
        .maxZoomIconScale(0.5f) // размер стрелки при приближении (было 1.0)
        .build()

    val request = LocationEngineRequest.Builder(1000)
        .setFastestInterval(500)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
        .build()

    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(request)
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.GPS          // стрелка, повёрнутая по ходу движения
    component.cameraMode = CameraMode.TRACKING_GPS // камера едет за стрелкой и поворачивается по курсу
    component.zoomWhileTracking(FOLLOW_ZOOM)
    component.tiltWhileTracking(FOLLOW_TILT)
}
