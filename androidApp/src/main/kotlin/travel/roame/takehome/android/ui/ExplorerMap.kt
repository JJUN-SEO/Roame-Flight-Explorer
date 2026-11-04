package travel.roame.takehome.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.LibraryLoader
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.storage.FileSource
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.GreatCircle
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.presentation.DestinationMarker
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.presentation.FlightItem

/** OpenFreeMap's free public "Positron" style, light grey so the pills and route stand out, with no key (D8, D27). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/** D24: nothing draws until the style loads, so stop waiting after this and say so. MapLibre's own failure event isn't reliable. */
private const val STYLE_TIMEOUT_MS = 10_000L

private const val MARKERS = "markers"
private const val PILLS = "marker-pills"
private const val ROUTE = "route"
private const val ORIGIN = "origin"

/** Room around fitted markers and routes, so no pill is clipped at the edge. 80 px clipped one in Step 0.3. */
private val FitPadding = 56.dp

/** Pills are wide but short, so a route needs less room above and below, which lets long routes fit above the card. */
private val FitPaddingVertical = 40.dp

/**
 * The results as destination pills, with the selected flight's great-circle route (UI_SPEC §2.2, M1–M8).
 * [camera] outlives this map, so switching to the list and back, or rotating, keeps the view (M8).
 */
@Composable
fun ExplorerMap(
    state: ExplorerState,
    camera: MutableState<DoubleArray?>,
    bottomInset: Dp,
    onMarkerTap: (code: String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // PLAN 6.1: starting MapLibre reads the disk to load its native library, to find its cache folder and to find
    // the app's files folder for its default options. MapLibre keeps each result, so doing them here on a
    // background thread first means the main thread never reads the disk.
    val appContext = LocalContext.current.applicationContext
    val options by produceState<MapLibreMapOptions?>(null) {
        value = withContext(Dispatchers.IO) {
            LibraryLoader.load()
            FileSource.getResourcesCachePath(appContext)
            MapLibreMapOptions.createFromAttributes(appContext)
        }
    }
    options?.let { MapContent(it, state, camera, bottomInset, onMarkerTap, onReset, modifier) } ?: Box(modifier)
}

@Composable
private fun MapContent(
    options: MapLibreMapOptions,
    state: ExplorerState,
    camera: MutableState<DoubleArray?>,
    bottomInset: Dp,
    onMarkerTap: (code: String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var timedOut by remember { mutableStateOf(false) }
    val latestTap by rememberUpdatedState(onMarkerTap)
    val latestMarkers by rememberUpdatedState(state.markers)
    val colors = PillColors(
        fill = MaterialTheme.colorScheme.surface.toArgb(),
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        accent = MaterialTheme.colorScheme.primary.toArgb(),
        onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
    )

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The map usually leaves while the screen stays resumed (List was chosen). MapLibre expects onPause
            // and onStop before onDestroy, so that it stops its renderer and connectivity listener.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // D24: load the style, and show "Can't load the map" if it hasn't arrived in time. Retry starts again.
    LaunchedEffect(map, attempt) {
        val loadedMap = map ?: return@LaunchedEffect
        timedOut = false
        loadedMap.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
            loaded.addOverlays()
            style = loaded
            timedOut = false
        }
        delay(STYLE_TIMEOUT_MS)
        if (style == null) timedOut = true
    }

    LaunchedEffect(style, state.markers, state.selected) {
        val loaded = style ?: return@LaunchedEffect
        loaded.showMarkers(state.markers, state.selected?.destinationCode, density.density, colors)
        loaded.showRoute(state.selected)
    }

    // M8: the camera moves on an explicit selection, never because filters changed.
    val inset by rememberUpdatedState(bottomInset)
    var shownSelection by remember { mutableStateOf(state.selected?.id) }
    LaunchedEffect(map, state.selected?.id) {
        val loadedMap = map ?: return@LaunchedEffect
        val selected = state.selected
        val isNew = selected != null && selected.id != shownSelection
        shownSelection = selected?.id
        if (selected != null && isNew) {
            // The card appears with the selection. Wait for its height, or the route is fitted underneath it.
            val cardInset = withTimeoutOrNull(1_000) { snapshotFlow { inset }.first { it > 0.dp } } ?: inset
            val sides = with(density) { FitPadding.roundToPx() }
            val top = with(density) { FitPaddingVertical.roundToPx() }
            val bottom = with(density) { (FitPaddingVertical + cardInset).roundToPx() }
            loadedMap.move(routeCamera(selected, sides, top, bottom), animate = !reduceMotion)
        }
    }

    // OpenStreetMap and OpenFreeMap require their credit to stay visible, so the ⓘ button sits above the card.
    LaunchedEffect(map, bottomInset) {
        val settings = map?.uiSettings ?: return@LaunchedEffect
        // In the corner: its default left margin makes room for the logo, which D27 hides.
        val left = with(density) { 8.dp.roundToPx() }
        val bottom = with(density) { (bottomInset + 4.dp).roundToPx() }
        settings.setAttributionMargins(left, settings.attributionMarginTop, settings.attributionMarginRight, bottom)
    }

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { loadedMap ->
                        // D27: MapLibre's BSD licence doesn't require its logo. The ⓘ credit stays.
                        loadedMap.uiSettings.isLogoEnabled = false
                        val saved = camera.value
                        if (saved != null) {
                            loadedMap.cameraPosition = saved.toCameraPosition()
                        } else {
                            // M8: on first display, fit every marker. Wait for layout, which fitting needs.
                            view.post { markersCamera(latestMarkers, with(density) { FitPadding.roundToPx() })?.let(loadedMap::moveCamera) }
                        }
                        loadedMap.addOnCameraIdleListener { camera.value = loadedMap.cameraPosition.toArray() }
                        loadedMap.addOnMapClickListener { point ->
                            val hit = loadedMap.queryRenderedFeatures(loadedMap.projection.toScreenLocation(point), PILLS)
                                .maxByOrNull { it.getNumberProperty("sort")?.toInt() ?: 0 }
                            hit?.getStringProperty("code")?.let { latestTap(it) }
                            hit != null
                        }
                        map = loadedMap
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            style == null && timedOut -> MapUnavailable(onRetry = { attempt++ })
            // M6: no markers, but the map stays usable around the message.
            style != null && state.results.isEmpty() -> MapEmpty(onReset, Modifier.align(Alignment.TopCenter).padding(16.dp))
        }
    }
}

/** Every flight to one destination, in O2 order (M5). Choosing one selects it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerSheet(marker: DestinationMarker, state: ExplorerState, store: ExplorerStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            pluralStringResource(
                R.plurals.marker_sheet_title,
                marker.flightCount,
                marker.city,
                marker.code,
                groupedCount(marker.flightCount),
                PointsFormat.full(marker.minPoints),
            ),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(marker.flights, key = { it.id }) { flight ->
                FlightRow(
                    flight,
                    isSelected = flight.id == state.selected?.id,
                    isPending = flight.id in state.pendingWriteIds,
                    onClick = {
                        store.select(flight.id)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    onToggleSave = { store.toggleSave(flight.id) },
                )
            }
        }
    }
}

/** UI_SPEC §4, D24. */
@Composable
private fun MapUnavailable(onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_unavailable_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.map_unavailable_body),
                Modifier.padding(top = 8.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** UI_SPEC §4 map empty: a floating card, so the map stays visible. */
@Composable
private fun MapEmpty(onReset: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.map_empty), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReset) { Text(stringResource(R.string.reset_filters)) }
        }
    }
}

private fun Style.addOverlays() {
    addSource(GeoJsonSource(ROUTE))
    addLayer(
        LineLayer("route-line", ROUTE).withProperties(
            PropertyFactory.lineColor(ROUTE_COLOR),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    addSource(GeoJsonSource(ORIGIN))
    addLayer(
        CircleLayer("origin-dot", ORIGIN).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor(ROUTE_COLOR),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    addSource(GeoJsonSource(MARKERS))
    addLayer(
        SymbolLayer(PILLS, MARKERS).withProperties(
            PropertyFactory.iconImage(get("image")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            // M4: the selected destination is drawn on top of a nearby one, such as HND and NRT.
            PropertyFactory.symbolSortKey(get("sort")),
        ),
    )
}

/** The route's colour; the map style is always light, so one value works in both themes. */
private const val ROUTE_COLOR = 0xFF1E4FA3.toInt()

private class PillColors(val fill: Int, val text: Int, val accent: Int, val onAccent: Int)

/** M2, M3: one pill per destination, reading `CODE count·minPts` (UI_SPEC §3). */
private fun Style.showMarkers(markers: List<DestinationMarker>, selectedCode: String?, density: Float, colors: PillColors) {
    val features = markers.map { marker ->
        val label = "${marker.code} ${marker.flightCount}·${PointsFormat.compact(marker.minPoints)}"
        val selected = marker.code == selectedCode
        val image = "pill/$label/$selected"
        if (getImage(image) == null) addImage(image, pillBitmap(label, selected, density, colors))
        Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).apply {
            addStringProperty("code", marker.code)
            addStringProperty("image", image)
            addNumberProperty("sort", if (selected) 1 else 0)
        }
    }
    getSourceAs<GeoJsonSource>(MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** M7: the great-circle line from the shared `GreatCircle`, with a dot at the origin. */
private fun Style.showRoute(selected: FlightItem?) {
    val route = selected?.let(::routePoints)
    val line = route?.let { Feature.fromGeometry(LineString.fromLngLats(it)) }
    val origin = route?.first()?.let { Feature.fromGeometry(it) }
    getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(line)))
    getSourceAs<GeoJsonSource>(ORIGIN)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(origin)))
}

private fun routePoints(flight: FlightItem): List<Point> =
    GreatCircle.interpolate(
        GeoPoint(flight.originLatitude, flight.originLongitude),
        GeoPoint(flight.destinationLatitude, flight.destinationLongitude),
    ).map { Point.fromLngLat(it.longitude, it.latitude) }

/** Fits the route. Its longitudes are unwrapped, so east is never less than west, even across the antimeridian. */
private fun routeCamera(flight: FlightItem, sides: Int, top: Int, bottom: Int): CameraUpdate {
    val points = routePoints(flight)
    val bounds = LatLngBounds.from(points.maxOf { it.latitude() }, points.maxOf { it.longitude() }, points.minOf { it.latitude() }, points.minOf { it.longitude() })
    return CameraUpdateFactory.newLatLngBounds(bounds, sides, top, sides, bottom)
}

private fun markersCamera(markers: List<DestinationMarker>, padding: Int): CameraUpdate? = when (markers.size) {
    0 -> null
    1 -> CameraUpdateFactory.newLatLngZoom(LatLng(markers[0].latitude, markers[0].longitude), 4.0)
    else -> CameraUpdateFactory.newLatLngBounds(
        LatLngBounds.from(markers.maxOf { it.latitude }, markers.maxOf { it.longitude }, markers.minOf { it.latitude }, markers.minOf { it.longitude }),
        padding,
    )
}

private fun MapLibreMap.move(update: CameraUpdate, animate: Boolean) = if (animate) animateCamera(update, 800) else moveCamera(update)

private fun CameraPosition.toArray() = doubleArrayOf(target?.latitude ?: 0.0, target?.longitude ?: 0.0, zoom, bearing, tilt)

private fun DoubleArray.toCameraPosition() =
    CameraPosition.Builder().target(LatLng(this[0], this[1])).zoom(this[2]).bearing(this[3]).tilt(this[4]).build()

/** Draws the whole pill, text included, so it needs no fonts from the network (Step 0.3). */
private fun pillBitmap(label: String, selected: Boolean, density: Float, colors: PillColors): Bitmap {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        typeface = Typeface.DEFAULT_BOLD
        color = if (selected) colors.onAccent else colors.text
    }
    val horizontal = 10f * density
    val height = 26f * density
    val width = text.measureText(label) + 2 * horizontal
    val bitmap = Bitmap.createBitmap(width.toInt() + 1, height.toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val shape = RectF(density, density, width - density, height - density)
    val radius = height / 2
    canvas.drawRoundRect(shape, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (selected) colors.accent else colors.fill })
    canvas.drawRoundRect(
        shape, radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) colors.accent else colors.text
            style = Paint.Style.STROKE
            strokeWidth = density
        },
    )
    canvas.drawText(label, horizontal, height / 2 - (text.ascent() + text.descent()) / 2, text)
    return bitmap
}

/** UI_SPEC §8: with animations off in the system settings, the camera jumps and views don't fade. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { animationsOff(context) }
}

private fun animationsOff(context: Context) =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
