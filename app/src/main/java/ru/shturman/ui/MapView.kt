package ru.shturman.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Карта на весь экран: тёмная 3D-карта с наклоном камеры, объёмными зданиями и символическими деревьями. */
@Composable
fun DarkMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(
                    Style.Builder()
                        .fromJson(MapStyle.JSON)
                        .withImage(MapStyle.TREE_IMAGE, MapStyle.treeBitmap())
                )
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(55.7558, 37.6173)) // заглушка: центр Москвы, потом будет твоё положение
                    .zoom(16.0)
                    .tilt(60.0)
                    .bearing(20.0)
                    .build()
            }
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
