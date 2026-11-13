package com.EarthWatch.myapp

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.maplibre.android.MapLibre
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.EarthWatch.myapp.core.maps.MapLibreMapPanel
import com.EarthWatch.myapp.core.maps.MapLocation
import com.EarthWatch.myapp.domain.model.GroundPosition
import com.EarthWatch.myapp.domain.model.GroundTrack
import com.EarthWatch.myapp.domain.model.SatelliteOrbit
import com.EarthWatch.myapp.domain.model.SavedPlace
import com.EarthWatch.myapp.feature.satellites.SatelliteUiState
import com.EarthWatch.myapp.feature.satellites.SatelliteViewModel
import com.EarthWatch.myapp.feature.passes.PassUiState
import com.EarthWatch.myapp.feature.passes.PassViewModel
import com.EarthWatch.myapp.feature.saved.SavedLocationsViewModel
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ice = Color(0xFFF4F9FF)
private val Blue = Color(0xFF0866E8)
private val Navy = Color(0xFF10213D)
private val Orange = Color(0xFFFF8A00)
private val Muted = Color(0xFF64748B)
private val PaleBlue = Color(0xFFEAF3FF)
private val BorderBlue = Color(0xFFD7E5F5)
private val Success = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        window.statusBarColor = android.graphics.Color.rgb(244, 249, 255)
        window.navigationBarColor = android.graphics.Color.rgb(244, 249, 255)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { EarthWatchApp() }
    }
}

@Composable
private fun EarthWatchApp() {
    val nav = rememberNavController()
    val satelliteViewModel: SatelliteViewModel = viewModel()
    val passViewModel: PassViewModel = viewModel()
    val satelliteState by satelliteViewModel.state.collectAsStateWithLifecycle()
    val observingState by passViewModel.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Ice, surface = Color.White, onPrimary = Color.White, onBackground = Navy, onSurface = Navy)) {
        Surface(Modifier.fillMaxSize(), color = Ice) {
            NavHost(navController = nav, startDestination = "welcome") {
                composable("welcome") { WelcomeScreen { nav.navigate("explore") { popUpTo("welcome") { inclusive = true } } } }
                composable("explore") { MainShell(nav, "explore") { ExploreScreen(nav, satelliteState.orbits, satelliteState.positions, satelliteState.tracks, satelliteState.fetchedAtMillis) } }
                composable("satellites") { MainShell(nav, "satellites") { SatellitesScreen(nav, satelliteState, satelliteViewModel::refreshCatalog) } }
                composable("passes") {
                    val passState by passViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(satelliteState.orbits) { passViewModel.updateCatalog(satelliteState.orbits) }
                    MainShell(nav, "passes") { PassesScreen(passState, passViewModel, satelliteState.fetchedAtMillis) }
                }
                composable("imagery") { MainShell(nav, "imagery") { ImageryScreen(observingState.location) } }
                composable("saved") {
                    val savedViewModel: SavedLocationsViewModel = viewModel()
                    val savedState by savedViewModel.state.collectAsStateWithLifecycle()
                    MainShell(nav, "saved") {
                        SavedScreen(savedState, savedViewModel) { place ->
                            passViewModel.updateLocation(com.EarthWatch.myapp.domain.model.ObservingLocation(place.name, place.latitudeDeg, place.longitudeDeg))
                            nav.navigate("passes") { launchSingleTop = true }
                        }
                    }
                }
                composable("settings") { MainShell(nav, "settings", showBottom = false) { SettingsScreen(nav) } }
                composable("globe") { MainShell(nav, "explore", showBottom = false) { GlobeScreen(nav) } }
                composable("detail/{norad}") { entry ->
                    val norad = entry.arguments?.getString("norad")?.toLongOrNull()
                    val satellite = satelliteState.orbits.firstOrNull { it.noradId == norad }
                    MainShell(nav, "satellites", showBottom = false) { SatelliteDetailScreen(satellite, satelliteState.positions.firstOrNull { it.noradId == norad }, satelliteState.fetchedAtMillis, nav) }
                }
            }
        }
    }
}

@Composable
private fun MainShell(nav: NavHostController, selected: String, showBottom: Boolean = true, content: @Composable () -> Unit) {
    Scaffold(containerColor = Ice, bottomBar = { if (showBottom) BottomNavigation(nav, selected) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

private data class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
@Composable
private fun BottomNavigation(nav: NavHostController, selected: String) {
    val items = listOf(Destination("explore", "Explore", Icons.Default.Explore), Destination("satellites", "Satellites", Icons.Default.Hub), Destination("passes", "Passes", Icons.Default.Event), Destination("imagery", "Imagery", Icons.Default.Image), Destination("saved", "Saved", Icons.Default.BookmarkBorder))
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, BorderBlue)) {
        items.forEach { item ->
            NavigationBarItem(selected = selected == item.route, onClick = { nav.navigate(item.route) { launchSingleTop = true; restoreState = true } }, icon = { Icon(item.icon, null) }, label = { Text(item.label, fontSize = 10.sp, fontWeight = if (selected == item.route) FontWeight.SemiBold else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = PaleBlue))
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE4F3FF), Color.White, Ice))).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(.45f))
        EarthMark(Modifier.size(180.dp))
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Earth", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Watch", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Orange) }
        Text("Satellite Tracking &\nEarth Observation", textAlign = TextAlign.Center, color = Navy, fontSize = 18.sp, lineHeight = 25.sp)
        Spacer(Modifier.weight(.55f))
        EarthHorizon(Modifier.fillMaxWidth().height(120.dp))
        PrimaryButton("Get Started", onStart, Modifier.fillMaxWidth().padding(bottom = 28.dp))
    }
}

@Composable
private fun EarthMark(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(.72f).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF0B8FEA), Color(0xFF073B8D)))).border(2.dp, Color.White.copy(alpha = .85f), CircleShape), contentAlignment = Alignment.Center) {
            Text("🌍", fontSize = 82.sp)
        }
        Canvas(Modifier.fillMaxSize()) {
            rotate(-28f, pivot = center) {
                drawOval(Orange, topLeft = Offset(size.width * .10f, size.height * .31f), size = androidx.compose.ui.geometry.Size(size.width * .80f, size.height * .38f), style = Stroke(7.dp.toPx()))
            }
        }
        Text("✦", color = Color.White, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).offset((-12).dp, 16.dp))
    }
}

@Composable
private fun EarthHorizon(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFB9E3FF), Color(0xFF1689DD))))
        val horizon = Path().apply {
            moveTo(0f, size.height * .56f)
            cubicTo(size.width * .22f, size.height * .12f, size.width * .78f, size.height * .12f, size.width, size.height * .56f)
        }
        val earth = Path().apply { addPath(horizon); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(earth, Brush.verticalGradient(listOf(Color(0xFF6FC5F6), Color(0xFF064895))))
        drawPath(horizon, Color.White.copy(alpha = .72f), style = Stroke(4.dp.toPx()))
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Spacer(Modifier.width(18.dp)); Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
    }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Navy) }
        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy)
        Row(content = trailing)
    }
}

@Composable
private fun ExploreScreen(nav: NavHostController, orbits: List<SatelliteOrbit>, positions: List<GroundPosition>, tracks: List<GroundTrack>, fetchedAtMillis: Long?) {
    var globe by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(MapLocation("Tehran, Iran", 35.6892, 51.3890)) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var mapLoading by remember { mutableStateOf(true) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var recenterKey by remember { mutableIntStateOf(0) }
    var showLayersInfo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("EarthWatch", trailing = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Menu, "Settings", tint = Navy) }; IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, "Notifications", tint = Navy) } })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(CircleShape).background(PaleBlue).padding(4.dp)) {
            listOf("Map", "Globe").forEachIndexed { i, label ->
                val selected = globe == (i == 1)
                Box(Modifier.weight(1f).clip(CircleShape).background(if (selected) Blue else Color.Transparent).clickable { globe = i == 1; if (globe) nav.navigate("globe") }.padding(vertical = 10.dp), Alignment.Center) { Text(label, color = if (selected) Color.White else Navy, fontWeight = FontWeight.SemiBold) }
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp).clip(RoundedCornerShape(22.dp))) {
            MapLibreMapPanel(
                location = location,
                retryKey = retryKey,
                recenterKey = recenterKey,
                onMapTap = { latitude, longitude -> location = MapLocation("Selected location", latitude, longitude) },
                onStatus = { loading, error -> mapLoading = loading; mapError = error },
                satellitePositions = positions,
                groundTracks = tracks,
                modifier = Modifier.fillMaxSize(),
            )
            Row(Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(Color.White.copy(alpha = .95f)).clickable { showLocationDialog = true }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Blue, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(location.name, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Spacer(Modifier.width(8.dp)); Text(String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude), color = Muted, fontSize = 10.sp, maxLines = 1) }
            Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp) { IconButton(onClick = { showLayersInfo = true }) { Icon(Icons.Default.Layers, "Map layers", tint = Navy) } }
                Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp) { IconButton(onClick = { recenterKey++ }) { Icon(Icons.Default.MyLocation, "Center map", tint = Blue) } }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 56.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .94f)) { Text("OpenFreeMap · © OpenMapTiles · Data from OpenStreetMap", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 10.sp, color = Muted) }
            if (mapLoading) Card(Modifier.align(Alignment.Center), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .95f))) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Loading map…", fontSize = 13.sp, color = Navy) } }
            mapError?.let { message ->
                Card(Modifier.align(Alignment.Center).padding(20.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CloudOff, null, tint = Blue); Text("Map unavailable", color = Navy, fontWeight = FontWeight.Bold); Text(message.take(120), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center); TextButton(onClick = { retryKey++; mapError = null; mapLoading = true }) { Text("Retry") } }
                }
            }
        }
    }
    if (showLocationDialog) CoordinateEntryDialog(location, onDismiss = { showLocationDialog = false }) { updated -> location = updated; showLocationDialog = false }
    if (showLayersInfo) AlertDialog(onDismissRequest = { showLayersInfo = false }, title = { Text("Map layers") }, text = { Text("The map shows propagated positions for ${positions.size} satellites and ${tracks.size} orbital ground tracks when catalog data is available. Catalog data: ${fetchedAtMillis?.let(::formatFetchedAt) ?: "not yet loaded"}.") }, confirmButton = { TextButton(onClick = { showLayersInfo = false }) { Text("OK") } })
}

@Composable
private fun CoordinateEntryDialog(current: MapLocation, onDismiss: () -> Unit, onSave: (MapLocation) -> Unit) {
    var name by remember(current) { mutableStateOf(current.name) }
    var coordinates by remember(current) { mutableStateOf("${current.latitude}, ${current.longitude}") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a map location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Location name") }, singleLine = true)
                OutlinedTextField(coordinates, { coordinates = it; error = null }, label = { Text("Latitude, longitude") }, singleLine = true, placeholder = { Text("35.6892, 51.3890") })
                Text("Coordinate entry works without GPS or a geocoder.", color = Muted, fontSize = 11.sp)
                error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parts = coordinates.split(',', ' ', '\n').map(String::trim).filter(String::isNotEmpty)
                val latitude = parts.getOrNull(0)?.toDoubleOrNull()
                val longitude = parts.getOrNull(1)?.toDoubleOrNull()
                if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                    error = "Enter latitude (−90 to 90) and longitude (−180 to 180)."
                } else {
                    onSave(MapLocation(name.ifBlank { "Selected location" }, latitude, longitude))
                }
            }) { Text("Use location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WorldMapArt(modifier: Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF187DC3), Color(0xFF074C8B))))) {
        val land = Color(0xFF79A96B)
        val continents = listOf(
            listOf(.08f to .2f, .25f to .12f, .34f to .24f, .31f to .43f, .24f to .52f, .17f to .4f, .1f to .36f),
            listOf(.28f to .52f, .36f to .5f, .4f to .67f, .35f to .9f, .3f to .77f),
            listOf(.45f to .17f, .6f to .14f, .68f to .24f, .61f to .36f, .55f to .3f, .48f to .4f, .43f to .3f),
            listOf(.52f to .4f, .64f to .39f, .68f to .56f, .63f to .72f, .56f to .67f, .5f to .53f),
            listOf(.68f to .19f, .82f to .21f, .91f to .32f, .87f to .44f, .75f to .39f, .7f to .3f)
        )
        continents.forEach { shape ->
            val path = Path().apply { shape.forEachIndexed { index, p -> val point = Offset(size.width * p.first, size.height * p.second); if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }; close() }
            drawPath(path, Brush.linearGradient(listOf(Color(0xFFB0C77C), land, Color(0xFF437B58))))
            drawPath(path, Color.White.copy(alpha = .16f), style = Stroke(1.dp.toPx()))
        }
        val orbit = Path().apply { moveTo(-size.width * .1f, size.height * .85f); cubicTo(size.width * .24f, size.height * .5f, size.width * .57f, size.height * .67f, size.width * 1.1f, size.height * .15f) }
        drawPath(orbit, Orange, style = Stroke(3.dp.toPx()))
        drawCircle(Orange, 10.dp.toPx(), Offset(size.width * .52f, size.height * .49f)); drawCircle(Color.White, 5.dp.toPx(), Offset(size.width * .52f, size.height * .49f))
        listOf(.22f to .29f, .72f to .34f, .43f to .68f, .82f to .7f).forEach { p -> drawCircle(Color.White, 4.dp.toPx(), Offset(size.width*p.first, size.height*p.second)); drawCircle(Blue, 2.5.dp.toPx(), Offset(size.width*p.first, size.height*p.second)) }
    }
}

@Composable
private fun SatellitesScreen(nav: NavHostController, state: SatelliteUiState, onRefresh: () -> Unit) {
    var category by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    val categories = listOf("All", "Earth Obs.", "Weather", "Comm.")
    val filtered = state.orbits.filter { orbit ->
        val categoryMatches = when (category) {
            "Earth Obs." -> orbit.category == "Earth Observation"
            "Weather" -> orbit.category == "Weather"
            "Comm." -> orbit.category == "Communication"
            else -> true
        }
        categoryMatches && (orbit.name.contains(query, true) || orbit.noradId.toString().contains(query))
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Satellites", trailing = { IconButton(onClick = { query = if (query.isEmpty()) "Sentinel" else "" }) { Icon(Icons.Default.Search, "Search", tint = Navy) }; IconButton(onClick = { category = if (category == "All") "Weather" else "All" }) { Icon(Icons.Default.FilterList, "Filter", tint = Navy) } })
        if (query.isNotEmpty()) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Search satellites") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { label -> FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label) }, shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = PaleBlue, labelColor = Navy), border = null) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.orbits.size} CelesTrak objects · ${state.fetchedAtMillis?.let(::formatFetchedAt) ?: "waiting for catalog"}", Modifier.weight(1f), fontSize = 11.sp, color = Muted)
            IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh catalog", tint = Blue) }
        }
        state.errorMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Color(0xFF9A5B00), fontSize = 11.sp) }
        if (state.loading && state.orbits.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Loading CelesTrak catalog…", Modifier.padding(top = 12.dp), color = Muted) } }
        } else if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.orbits.isEmpty()) "Satellite data is not available yet." else "No satellites match this search.", color = Muted)
                if (state.orbits.isEmpty()) TextButton(onClick = onRefresh) { Text("Try again") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(filtered, key = { it.noradId }) { item -> SatelliteCard(item, state.positions.firstOrNull { it.noradId == item.noradId }) { nav.navigate("detail/${item.noradId}") } }
        }
    }
}

@Composable
private fun SatelliteCard(sat: SatelliteOrbit, position: GroundPosition?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderBlue), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(PaleBlue), Alignment.Center) { Text("🛰️", fontSize = 33.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(sat.name, fontWeight = FontWeight.Bold, color = Navy, maxLines = 1); Text("NORAD: ${sat.noradId}", fontSize = 12.sp, color = Navy); Text(position?.let { "Altitude: ${"%.0f".format(Locale.US, it.altitudeKm)} km" } ?: "Altitude: calculating…", fontSize = 12.sp, color = Navy) }
            Icon(Icons.Default.ChevronRight, null, tint = Navy)
        }
    }
}

@Composable
private fun PassesScreen(state: PassUiState, viewModel: PassViewModel, fetchedAtMillis: Long?) {
    var showLocationDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        viewModel.setAlertsEnabled(allowed)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Pass Predictions")
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PaleBlue), border = BorderStroke(1.dp, BorderBlue)) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.location.name, fontWeight = FontWeight.Bold)
                    Text(formatLocation(state.location.latitudeDeg, state.location.longitudeDeg), fontSize = 12.sp, color = Muted)
                }
                IconButton(onClick = { showLocationDialog = true }) { Icon(Icons.Default.Settings, "Choose observer location", tint = Navy) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Next 24 hours · elevation above 10°", Modifier.weight(1f), color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                if (state.alertsEnabled) viewModel.setAlertsEnabled(false)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                else viewModel.setAlertsEnabled(true)
            }) {
                Icon(if (state.alertsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, null, tint = if (state.alertsEnabled) Blue else Muted)
                Spacer(Modifier.width(4.dp)); Text(if (state.alertsEnabled) "Alerts on" else "Alert me")
            }
        }
        InfoBanner("Passes use SGP4 elements ${fetchedAtMillis?.let { "retrieved ${formatFetchedAt(it)}" } ?: "when available"} and your device time zone. Reminders are scheduled about 15 minutes before a pass; Android may delay background notifications.")
        if (state.isCalculating) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Calculating visible passes…", color = Muted) }
        } else if (!state.catalogAvailable) {
            InfoBanner("Pass times will appear after the CelesTrak orbit catalog is available.")
        } else if (state.passes.isEmpty()) {
            InfoBanner("No satellites are predicted above 10° from this location in the next 24 hours.")
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.passes, key = { "${it.satelliteId}-${it.aos.epochSecond}" }) { pass ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderBlue)) {
                        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = Navy)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pass.satelliteName, fontWeight = FontWeight.Bold, color = Navy)
                                Text("${formatPassTime(pass.aos)} – ${formatPassTime(pass.los)}", fontSize = 12.sp, color = Navy)
                                Text("Peak ${"%.0f".format(Locale.US, pass.maximumElevationDeg)}° · Az ${"%.0f".format(Locale.US, pass.azimuthAtMaximumDeg)}°", fontSize = 11.sp, color = Muted)
                            }
                            Text("${Duration.between(Instant.now(), pass.aos).toMinutes().coerceAtLeast(0)} min", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        state.errorMessage?.let { InfoBanner("Could not calculate passes: $it") }
    }
    if (showLocationDialog) {
        val mapLocation = MapLocation(state.location.name, state.location.latitudeDeg, state.location.longitudeDeg)
        CoordinateEntryDialog(mapLocation, onDismiss = { showLocationDialog = false }) { updated ->
            viewModel.updateLocation(com.EarthWatch.myapp.domain.model.ObservingLocation(updated.name, updated.latitude, updated.longitude))
            showLocationDialog = false
        }
    }
}

private fun formatPassTime(instant: Instant): String = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm z", Locale.getDefault())
    .withZone(ZoneId.systemDefault()).format(instant)

private fun formatLocation(latitude: Double, longitude: Double): String =
    "${"%.4f".format(Locale.US, kotlin.math.abs(latitude))}° ${if (latitude >= 0) "N" else "S"}, ${"%.4f".format(Locale.US, kotlin.math.abs(longitude))}° ${if (longitude >= 0) "E" else "W"}"

@Composable
private fun ImageryScreen(location: com.EarthWatch.myapp.domain.model.ObservingLocation) {
    var sensor by remember { mutableStateOf("Terra") }
    val latestAvailableDate = remember { LocalDate.now(ZoneOffset.UTC).minusDays(2) }
    var selectedDate by remember { mutableStateOf(latestAvailableDate) }
    val layer = if (sensor == "Terra") "MODIS_Terra_CorrectedReflectance_TrueColor" else "MODIS_Aqua_CorrectedReflectance_TrueColor"
    val tileTemplate = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/$layer/default/$selectedDate/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg"
    Column(Modifier.fillMaxSize()) {
        TopBar("Satellite Imagery")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Terra", "Aqua").forEach { name ->
                FilterChip(selected = sensor == name, onClick = { sensor = name }, label = { Text("MODIS $name") }, shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = PaleBlue, labelColor = Navy), border = null)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))) {
            MapLibreMapPanel(
                location = MapLocation(location.name, location.latitudeDeg, location.longitudeDeg),
                retryKey = 0,
                recenterKey = 0,
                onMapTap = { _, _ -> },
                onStatus = { _, _ -> },
                rasterTileTemplate = tileTemplate,
                initialZoom = 4.0,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(Modifier.align(Alignment.BottomStart).padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .94f)) {
                Text("NASA GIBS · MODIS Corrected Reflectance · Daily composite", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 10.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous imagery date", tint = Navy) }
            Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())), Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold, color = Navy)
            IconButton(onClick = { if (selectedDate < latestAvailableDate) selectedDate = selectedDate.plusDays(1) }, enabled = selectedDate < latestAvailableDate) { Icon(Icons.Default.ChevronRight, "Next imagery date", tint = if (selectedDate < latestAvailableDate) Blue else Muted) }
        }
        Text("Browse imagery near ${location.name}. NASA imagery is a visual composite and may be a day or more behind current conditions.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SavedScreen(state: com.EarthWatch.myapp.feature.saved.SavedLocationsState, viewModel: SavedLocationsViewModel, onUseLocation: (SavedPlace) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Saved Locations", trailing = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add location", tint = Navy) } })
        state.errorMessage?.let { InfoBanner(it) }
        if (state.loading && state.locations.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.locations.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, null, tint = Blue, modifier = Modifier.size(38.dp))
                Text("No saved locations yet", color = Navy, fontWeight = FontWeight.SemiBold)
                Text("Add coordinates to use them for pass predictions.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { showAdd = true }) { Text("Add a location") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.locations, key = SavedPlace::id) { place ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderBlue)) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(PaleBlue), Alignment.Center) { Icon(Icons.Default.LocationOn, null, tint = Blue) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(place.name, fontWeight = FontWeight.Bold); Text(formatLocation(place.latitudeDeg, place.longitudeDeg), fontSize = 11.sp, color = Muted) }
                            TextButton(onClick = { onUseLocation(place) }) { Text("Use") }
                            IconButton(onClick = { viewModel.delete(place.id) }) { Icon(Icons.Default.DeleteOutline, "Remove ${place.name}", tint = Muted) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false; validationError = null },
        title = { Text("Add a saved location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; validationError = null }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(latitude, { latitude = it; validationError = null }, label = { Text("Latitude (−90 to 90)") }, singleLine = true)
                OutlinedTextField(longitude, { longitude = it; validationError = null }, label = { Text("Longitude (−180 to 180)") }, singleLine = true)
                validationError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat = latitude.trim().toDoubleOrNull()
                val lon = longitude.trim().toDoubleOrNull()
                if (name.isBlank() || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    validationError = "Enter a name and valid latitude and longitude."
                } else {
                    viewModel.save(name, lat, lon)
                    name = ""; latitude = ""; longitude = ""; validationError = null; showAdd = false
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false; validationError = null }) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsScreen(nav: NavHostController) {
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = null)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, BorderBlue, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp)) {
            SettingRow(Icons.Default.DarkMode, "Appearance", "Light mode · default", action = {
                dialogTitle = "Appearance"; dialogText = "EarthWatch currently uses the light blue and white theme. Dark mode is not available in this release."
            })
            SettingRow(Icons.Default.Notifications, "Notifications", "Manage pass alerts on the Passes tab", action = { nav.navigate("passes") { launchSingleTop = true } })
            SettingRow(Icons.Default.Straighten, "Units", "Kilometers · device local time", action = {
                dialogTitle = "Units"; dialogText = "Orbital distances are shown in kilometers. Pass dates and times use your device time zone."
            })
            SettingRow(Icons.Default.Map, "Map", "Map style, layers", action = {
                dialogTitle = "Map"; dialogText = "The interactive map uses the OpenFreeMap Liberty style. Satellite positions and orbit tracks appear when current catalog data is available."
            })
            SettingRow(Icons.Default.Sync, "Data Update", "CelesTrak groups cached for two hours", action = {
                dialogTitle = "Data Update"; dialogText = "Public CelesTrak orbital elements are refreshed no more often than every two hours and may be unavailable. Cached records are kept on this device."
            })
            SettingRow(Icons.Default.Info, "About", "EarthWatch · version 0.1.0", action = {
                dialogTitle = "About EarthWatch"; dialogText = "EarthWatch is a local-first satellite tracking and Earth observation app. Predictions are estimates based on public orbital elements."
            })
            SettingRow(Icons.AutoMirrored.Filled.MenuBook, "Privacy & Licenses", "Data use and third-party services", action = {
                dialogTitle = "Privacy & Licenses"; dialogText = "No account, analytics, advertising, or device location is used. Saved locations and orbit caches stay on this device. Network services: OpenFreeMap, OpenMapTiles, OpenStreetMap contributors, CelesTrak, and NASA GIBS. See the project README and privacy policy template for details."
            })
        }
    }
    dialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { dialogTitle = null },
            title = { Text(title) },
            text = { Text(dialogText) },
            confirmButton = { TextButton(onClick = { dialogTitle = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, action: () -> Unit = {}, trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = Navy) }) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ice), Alignment.Center) { Icon(icon, null, tint = Blue) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(subtitle, fontSize = 11.sp, color = Muted) }; trailing() }
    HorizontalDivider(color = BorderBlue)
}

@Composable
private fun SatelliteDetailScreen(sat: SatelliteOrbit?, position: GroundPosition?, fetchedAtMillis: Long?, nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Satellite Details", onBack = { nav.popBackStack() })
        if (sat == null) {
            InfoBanner("This satellite is not in the currently loaded catalog. Return to the list and select an available object.")
            return@Column
        }
        Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderBlue)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(92.dp).clip(CircleShape).background(PaleBlue), Alignment.Center) { Text("🛰️", fontSize = 54.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(sat.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Success)); Spacer(Modifier.width(6.dp)); Text("Orbit elements loaded", color = Success, fontSize = 12.sp) }
                    Spacer(Modifier.height(12.dp))
                    val period = 1440.0 / sat.meanMotionRevPerDay
                    listOf(
                        "NORAD ID" to sat.noradId.toString(),
                        "Object ID" to (sat.internationalDesignator ?: "Not supplied"),
                        "Type" to sat.category,
                        "Altitude" to (position?.let { "${"%.1f".format(Locale.US, it.altitudeKm)} km (propagated)" } ?: "Calculating current position"),
                        "Inclination" to "${"%.2f".format(Locale.US, sat.inclinationDeg)}°",
                        "Orbital period" to "${"%.2f".format(Locale.US, period)} min",
                        "Epoch" to sat.epoch,
                        "Elements retrieved" to (fetchedAtMillis?.let(::formatFetchedAt) ?: "Unknown"),
                    ).forEach { (label, value) -> Text(label, fontSize = 11.sp, color = Muted); Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); HorizontalDivider(Modifier.padding(vertical = 5.dp), color = BorderBlue) }
                }
            }
        }
        position?.let { InfoBanner("Current propagated sub-satellite point: ${"%.3f".format(Locale.US, it.latitudeDeg)}°, ${"%.3f".format(Locale.US, it.longitudeDeg)}°. SGP4 with CelesTrak OMM elements.") }
    }
}

private fun formatFetchedAt(epochMillis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.US).withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("retrieved")

@Composable
private fun GlobeScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Globe View", onBack = { nav.popBackStack() })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE5F4FF), Color.White))), Alignment.Center) {
            EarthMark(Modifier.fillMaxWidth(.85f).aspectRatio(1f))
            Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("3D Globe · Planned", fontWeight = FontWeight.Bold, color = Navy); Text("Orbital globe view is planned for a later milestone.", textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(PaleBlue).padding(12.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Info, null, tint = Blue, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(message, color = Muted, fontSize = 12.sp, lineHeight = 17.sp) }
}
