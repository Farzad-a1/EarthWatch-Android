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
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.EarthWatch.myapp.domain.model.GroundPosition
import com.EarthWatch.myapp.domain.model.GroundTrack
import com.EarthWatch.myapp.BuildConfig
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

data class MapLocation(val name: String, val latitude: Double, val longitude: Double)

@Composable
fun MapLibreMapPanel(
    location: MapLocation,
    retryKey: Int,
    recenterKey: Int,
    onMapTap: (Double, Double) -> Unit,
    onStatus: (loading: Boolean, error: String?) -> Unit,
    satellitePositions: List<GroundPosition> = emptyList(),
    groundTracks: List<GroundTrack> = emptyList(),
    rasterTileTemplate: String? = null,
    initialZoom: Double = 2.4,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapTap = rememberUpdatedState(onMapTap)
    val currentOnStatus = rememberUpdatedState(onStatus)
    val mapView = remember(context, retryKey, rasterTileTemplate) { MapView(context).apply { onCreate(Bundle()) } }
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    val markerSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val satelliteSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val trackSource = remember { mutableStateOf<GeoJsonSource?>(null) }

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
                rasterTileTemplate?.let { tileTemplate ->
                    val rasterSource = RasterSource(
                        "earthwatch-nasa-imagery-source",
                        TileSet("2.2.0", tileTemplate).apply { attribution = "NASA Global Imagery Browse Services (GIBS)" },
                        256,
                    )
                    style.addSource(rasterSource)
                    style.addLayer(RasterLayer("earthwatch-nasa-imagery", "earthwatch-nasa-imagery-source").withProperties(rasterOpacity(0.96f)))
                }
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
                val satSource = GeoJsonSource("earthwatch-satellite-source", satelliteFeatureCollection(satellitePositions))
                style.addSource(satSource)
                style.addLayer(
                    CircleLayer("earthwatch-satellite-dots", "earthwatch-satellite-source").withProperties(
                        circleRadius(5f), circleColor("#FF8A00"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(1.5f),
                    ),
                )
                val orbitSource = GeoJsonSource("earthwatch-ground-track-source", groundTrackFeatureCollection(groundTracks))
                style.addSource(orbitSource)
                style.addLayer(LineLayer("earthwatch-ground-tracks", "earthwatch-ground-track-source").withProperties(
                    org.maplibre.android.style.layers.PropertyFactory.lineColor("#FF8A00"),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(1.5f),
                    org.maplibre.android.style.layers.PropertyFactory.lineOpacity(0.7f),
                ))
                mapState.value = map
                markerSource.value = source
                satelliteSource.value = satSource
                trackSource.value = orbitSource
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        initialZoom,
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
            satelliteSource.value = null
            trackSource.value = null
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

    LaunchedEffect(mapState.value, satellitePositions) {
        satelliteSource.value?.setGeoJson(satelliteFeatureCollection(satellitePositions))
    }

    LaunchedEffect(mapState.value, groundTracks) {
        trackSource.value?.setGeoJson(groundTrackFeatureCollection(groundTracks))
    }

    LaunchedEffect(retryKey) {
        delay(30_000)
        if (mapState.value == null) {
            currentOnStatus.value(false, "Map style did not finish loading. Check your connection and retry.")
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.fillMaxSize(), update = {})
}

private fun satelliteFeatureCollection(positions: List<GroundPosition>): FeatureCollection =
    FeatureCollection.fromFeatures(positions.map { position ->
        Feature.fromGeometry(Point.fromLngLat(position.longitudeDeg, position.latitudeDeg)).apply {
            addStringProperty("name", position.name)
            addNumberProperty("norad_id", position.noradId)
        }
    }.toTypedArray())

private fun groundTrackFeatureCollection(tracks: List<GroundTrack>): FeatureCollection =
    FeatureCollection.fromFeatures(tracks.flatMap { track ->
        splitTrackAtDateLine(track.points).mapNotNull { segment ->
            val coordinates = segment.map { (latitude, longitude) -> Point.fromLngLat(longitude, latitude) }
            if (coordinates.size < 2) null else Feature.fromGeometry(LineString.fromLngLats(coordinates)).apply {
                addNumberProperty("norad_id", track.noradId)
            }
        }
    }.toTypedArray())

private fun splitTrackAtDateLine(points: List<Pair<Double, Double>>): List<List<Pair<Double, Double>>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<Pair<Double, Double>>>()
    var segment = mutableListOf(points.first())
    points.zipWithNext().forEach { (previous, next) ->
        val longitudeDelta = next.second - previous.second
        if (kotlin.math.abs(longitudeDelta) > 180.0) {
            segments += segment
            segment = mutableListOf(next)
        } else {
            segment += next
        }
    }
    segments += segment
    return segments
}
