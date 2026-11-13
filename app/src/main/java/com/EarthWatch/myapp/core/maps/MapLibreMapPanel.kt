package com.EarthWatch.myapp.core.maps

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import com.EarthWatch.myapp.BuildConfig
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource

data class MapLocation(val name: String, val latitude: Double, val longitude: Double)

@Composable
fun MapLibreMapPanel(
    location: MapLocation,
    retryKey: Int,
    recenterKey: Int,
    onMapTap: (Double, Double) -> Unit,
    onStatus: (loading: Boolean, error: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapTap = rememberUpdatedState(onMapTap)
    val currentOnStatus = rememberUpdatedState(onStatus)
    val mapView = remember(context, retryKey) { MapView(context).apply { onCreate(Bundle()) } }
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val markerSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    DisposableEffect(mapView, lifecycleOwner) {
        currentOnStatus.value(true, null)
        mapView.addOnDidFailLoadingMapListener { message -> currentOnStatus.value(false, message) }
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isAttributionEnabled = false
            map.addOnMapClickListener { tapped ->
                currentOnMapTap.value(tapped.latitude, tapped.longitude)
                true
            }
            map.setStyle(BuildConfig.EARTHWATCH_MAP_STYLE_URL) { style ->
                val source = GeoJsonSource(
                    "earthwatch-selected-location-source",
                    Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)),
                )
                style.addSource(source)
                style.addLayer(
                    CircleLayer("earthwatch-selected-location-dot", "earthwatch-selected-location-source").withProperties(
                        circleRadius(8f),
                        circleColor("#0866E8"),
                        circleStrokeColor("#FFFFFF"),
                        circleStrokeWidth(3f),
                    ),
                )
                mapState.value = map
                markerSource.value = source
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        2.4,
                    ),
                )
                currentOnStatus.value(false, null)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapState.value = null
            markerSource.value = null
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapState.value, location.latitude, location.longitude, recenterKey) {
        val map = mapState.value ?: return@LaunchedEffect
        markerSource.value?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)),
        )
        map.animateCamera(
            CameraUpdateFactory.newLatLng(LatLng(location.latitude, location.longitude)),
        )
    }

    LaunchedEffect(retryKey) {
        delay(30_000)
        if (mapState.value == null) {
            currentOnStatus.value(false, "Map style did not finish loading. Check your connection and retry.")
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize(), update = {})
}
