package travel.roame.takehome.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
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
import travel.roame.takehome.domain.PlaneFlight
import travel.roame.takehome.domain.PlaneFrame
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.domain.RouteArc
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.presentation.DestinationMarker
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.presentation.FlightItem

/** OpenFreeMap's free public "Liberty" style, in colour like iOS's map, with no key (D8, D48). [matchIos] mutes it. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** D48: iOS's open sea, sampled from MapKit's map at the results' first view, in place of Liberty's deeper blue. */
private const val IOS_WATER = "#96DBFD"

/** D24: nothing draws until the style loads, so stop waiting after this and say so. MapLibre's own failure event isn't reliable. */
private const val STYLE_TIMEOUT_MS = 10_000L

private const val MARKERS = "markers"
private const val PILLS = "marker-pills"
private const val SELECTED_PILL = "selected-pill"
private const val ROUTE = "route"
private const val ROUTE_LINE = "route-line"
private const val ORIGIN = "origin"
private const val PLANE = "plane"
private const val PLANE_IMAGE = "plane-image"

/**
 * M9: the route as dots the size and spacing of iOS's, 5.5 dp across and 16 dp apart: a zero-length dash with round
 * caps is a dot, and the gap is in line widths.
 */
private const val DOT_WIDTH = 5.5f
private val DOT_PATTERN = arrayOf(0f, 2.9f)

/** Room around fitted markers and routes, so no pill is clipped at the edge. 80 px clipped one in Step 0.3. */
private val FitPadding = 56.dp

/** Pills are wide but short, so a route needs less room above and below, which lets long routes fit above the card. */
private val FitPaddingVertical = 40.dp

/**
 * D31: the screen's one MapLibre map. It's made the first time Map is shown and kept while the list or the Saved tab
 * is shown, paused and stopped, because every map MapLibre destroys leaves about 7 MB of native memory behind
 * (Step 6.1). It keeps its style and listeners too, so showing it again is instant. [rememberMapHolder] destroys it
 * with the screen.
 */
class MapHolder internal constructor() {
    internal var view: MapView? = null
        private set
    internal var map: MapLibreMap? by mutableStateOf(null)
        private set
    internal var style: Style? by mutableStateOf(null)
    internal var attempt by mutableIntStateOf(0)
    internal var timedOut by mutableStateOf(false)

    // The map's listeners are added once, so they read the latest screen from here.
    internal var onMarkerTap: (code: String) -> Unit = {}
    /** The pill images the last two marker updates used. Older ones are removed, so filtering doesn't pile them up. */
    internal var pillImages: List<Set<String>> = emptyList()
    internal var markers: List<DestinationMarker> = emptyList()
    /** How much of the map's bottom the results sheet covers, so the first fit leaves the markers above it. */
    internal var coveredBottom: Int = 0
    internal var selectedId: String? = null

    /**
     * M8: the map was made with no camera to restore, so its first view is fitted: the markers, or the route of a flight
     * already chosen, such as a best offer's (MapContent fits that one).
     */
    internal var fitsFirstView = false

    private var started = false
    private var resumed = false

    internal fun create(context: Context, options: MapLibreMapOptions, camera: MutableState<DoubleArray?>, fitPadding: Int): MapView {
        MapLibre.getInstance(context.applicationContext)
        val created = MapView(context, options).apply { onCreate(null) }
        fitsFirstView = camera.value == null
        created.getMapAsync { loadedMap ->
            // D27: MapLibre's BSD licence doesn't require its logo. The ⓘ credit stays.
            loadedMap.uiSettings.isLogoEnabled = false
            // North stays up and the map stays flat, as on iOS, so the plane's heading stays true (M9).
            loadedMap.uiSettings.isRotateGesturesEnabled = false
            loadedMap.uiSettings.isTiltGesturesEnabled = false
            val saved = camera.value
            if (saved != null) {
                loadedMap.cameraPosition = saved.toCameraPosition()
            } else if (selectedId == null) {
                // M8: on first display, fit every marker. Wait for layout, which fitting needs.
                created.post { markersCamera(markers, fitPadding, fitPadding + coveredBottom)?.let(loadedMap::moveCamera) }
            }
            loadedMap.addOnCameraIdleListener { camera.value = loadedMap.cameraPosition.toArray() }
            loadedMap.addOnMapClickListener { point ->
                val hit = loadedMap.queryRenderedFeatures(loadedMap.projection.toScreenLocation(point), PILLS, SELECTED_PILL)
                    .maxByOrNull { it.getNumberProperty("sort")?.toInt() ?: 0 }
                hit?.getStringProperty("code")?.let { onMarkerTap(it) }
                hit != null
            }
            map = loadedMap
        }
        view = created
        return created
    }

    // MapLibre expects onStart before onResume, onPause before onStop, and both before onDestroy.
    internal fun start() {
        if (!started) view?.onStart()
        started = true
    }

    internal fun resume() {
        start()
        if (!resumed) view?.onResume()
        resumed = true
    }

    internal fun pause() {
        if (resumed) view?.onPause()
        resumed = false
    }

    internal fun stop() {
        pause()
        if (started) view?.onStop()
        started = false
    }

    internal fun destroy() {
        stop()
        view?.onDestroy()
        view = null
        map = null
        style = null
    }
}

/** D31: one map per screen. It outlives List and Map switches and tab switches, and goes with the activity. */
@Composable
fun rememberMapHolder(): MapHolder {
    val holder = remember { MapHolder() }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    return holder
}

/**
 * The results as destination pills, with the selected flight's route as an arc of dots and a plane flying along it
 * (UI_SPEC §2.1, M1–M9). [camera] outlives the map, so it's kept even when the activity is recreated (M8). While not
 * [visible], the map stays in place but hidden, paused, and out of reach of taps and TalkBack (D31). [bottomInset] is
 * how much of the map the results sheet and the selection card cover; a selected route is fitted above it once
 * [insetReady], which waits for the card to be measured.
 */
@Composable
fun ExplorerMap(
    state: ExplorerState,
    holder: MapHolder,
    visible: Boolean,
    camera: MutableState<DoubleArray?>,
    bottomInset: Dp,
    insetReady: Boolean,
    onMarkerTap: (code: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    SideEffect {
        holder.onMarkerTap = onMarkerTap
        holder.markers = state.markers
        holder.selectedId = state.selected?.id
        holder.coveredBottom = with(density) { bottomInset.roundToPx() }
    }
    val mapView = holder.view ?: rememberNewMapView(holder, camera)
    if (mapView == null) {
        Box(modifier)
    } else {
        MapContent(mapView, holder, visible, state, bottomInset, insetReady, modifier)
    }
}

/**
 * PLAN 6.1: starting MapLibre reads the disk to load its native library, to find its cache folder and to find the
 * app's files folder for its default options. MapLibre keeps each result, so doing them on a background thread first
 * means the main thread never reads the disk. Null until that's done.
 */
@Composable
private fun rememberNewMapView(holder: MapHolder, camera: MutableState<DoubleArray?>): MapView? {
    val context = LocalContext.current
    val fitPadding = with(LocalDensity.current) { FitPadding.roundToPx() }
    val options by produceState<MapLibreMapOptions?>(null) {
        val appContext = context.applicationContext
        value = withContext(Dispatchers.IO) {
            LibraryLoader.load()
            FileSource.getResourcesCachePath(appContext)
            MapLibreMapOptions.createFromAttributes(appContext)
        }
    }
    return options?.let { remember(it) { holder.create(context, it, camera, fitPadding) } }
}

@Composable
private fun MapContent(
    mapView: MapView,
    holder: MapHolder,
    visible: Boolean,
    state: ExplorerState,
    bottomInset: Dp,
    insetReady: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val map = holder.map
    val style = holder.style
    val colors = LightPillColors

    // D31: while shown, the map follows the screen's lifecycle. While hidden, or when it leaves with the Explore tab,
    // it's paused and stopped, not destroyed.
    DisposableEffect(lifecycle, holder, visible) {
        if (!visible) {
            holder.stop()
            return@DisposableEffect onDispose {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_STOP -> holder.stop()
                else -> Unit
            }
        }
        // MapLibre draws on a SurfaceView. When the map was first made inside the results, Android never gave that view
        // a surface until the window was hidden and shown again, so the map stayed blank (found in Step 6.3d's review).
        // So once the map has a size it starts following the lifecycle (adding the observer delivers the start and
        // resume it missed), and hiding and showing it once makes Android create the surface.
        val sized = mapView.doOnceSized {
            lifecycle.addObserver(observer)
            mapView.post {
                // Unless it was hidden in the meantime, which recreates the surface anyway.
                if (mapView.visibility == View.VISIBLE) {
                    mapView.visibility = View.INVISIBLE
                    mapView.visibility = View.VISIBLE
                }
            }
        }
        onDispose {
            mapView.removeOnLayoutChangeListener(sized)
            lifecycle.removeObserver(observer)
            holder.stop()
        }
    }

    // D24: load the style once, and show "Can't load the map" if it hasn't arrived in time. Retry starts again.
    LaunchedEffect(map, holder.attempt) {
        val loadedMap = map ?: return@LaunchedEffect
        if (holder.style != null) return@LaunchedEffect
        holder.timedOut = false
        loadedMap.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
            loaded.matchIos()
            loaded.addOverlays(planeBitmap(context, density.density))
            holder.style = loaded
            holder.timedOut = false
        }
        delay(STYLE_TIMEOUT_MS)
        if (holder.style == null) holder.timedOut = true
    }

    // The font scale is a key too, so the pills follow the text size.
    LaunchedEffect(style, state.markers, state.selected, colors, density) {
        val loaded = style ?: return@LaunchedEffect
        holder.pillImages = loaded.showMarkers(context, state.markers, state.selected?.destinationCode, density, colors, holder.pillImages)
        loaded.showRoute(state.selected)
    }

    // M9: the plane flies the route while it shows on screen, from the origin for each new selection, as on iOS. Under
    // Reduce Motion it rests halfway.
    LaunchedEffect(style, state.selected?.id, visible, reduceMotion) {
        val loaded = style ?: return@LaunchedEffect
        val selected = state.selected
        if (selected == null || !visible || reduceMotion) {
            loaded.showPlane(selected, PlaneFlight.resting)
            return@LaunchedEffect
        }
        val takeOff = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            loaded.showPlane(selected, PlaneFlight.at(now - takeOff))
        }
    }

    // M8: the camera moves on an explicit selection, never because filters changed.
    val inset by rememberUpdatedState(bottomInset)
    val ready by rememberUpdatedState(insetReady)
    // A flight already chosen when the map is made counts as new, so its route is the first view.
    var shownSelection by remember { mutableStateOf(if (holder.fitsFirstView) null else state.selected?.id) }
    LaunchedEffect(map, state.selected?.id, visible) {
        val loadedMap = map ?: return@LaunchedEffect
        val selected = state.selected
        val isNew = selected != null && selected.id != shownSelection
        shownSelection = selected?.id
        // A flight chosen in the list while the map is hidden doesn't move it, as before D31 and as on iOS.
        if (!visible) return@LaunchedEffect
        if (selected != null && isNew) {
            // The card appears with the selection and the sheet drops. Wait for both, or the route is fitted underneath.
            withTimeoutOrNull(1_000) { snapshotFlow { ready }.first { it } }
            val cardInset = inset
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

    Box(if (visible) modifier else modifier.clearAndSetSemantics {}) {
        AndroidView(
            // D31: the same view comes back when the Explore tab does, so take it from where it was shown last.
            factory = {
                (mapView.parent as? ViewGroup)?.removeView(mapView)
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            // An invisible view gets no taps and isn't read by TalkBack. Its surface goes too, as when the app is in
            // the background, but the view stays attached: detaching it is what leaks (Step 6.1).
            update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
        )
        // M6: with no results the map shows no markers, and the results sheet says why.
        if (visible && style == null && holder.timedOut) MapUnavailable(onRetry = { holder.attempt++ })
    }
}

/** Every flight to one destination, in O2 order (M5). Choosing one selects it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerSheet(marker: DestinationMarker, state: ExplorerState, store: ExplorerStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // UI_SPEC §8: spoken with "points" in full.
    val description = stringResource(
        R.string.marker_sheet_description,
        marker.city,
        marker.code,
        pluralStringResource(R.plurals.result_count, marker.flightCount, groupedCount(marker.flightCount)),
        spokenPoints(marker.minPoints),
    )
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
            // Centred, like the filter sheets and iOS's (D38).
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = description },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
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

/**
 * Bottom to top, as on iOS: the other pills, the route and its origin, the selected destination's pill (M4), and the
 * plane. The owner asked for the route's dots to cross the other pills (review of Step 6.3d).
 */
private fun Style.addOverlays(plane: Bitmap) {
    addSource(GeoJsonSource(MARKERS))
    addLayer(SymbolLayer(PILLS, MARKERS).withFilter(eq(get("sort"), literal(0))).withProperties(*pillProperties()))
    addSource(GeoJsonSource(ROUTE))
    addLayer(
        LineLayer(ROUTE_LINE, ROUTE).withProperties(
            PropertyFactory.lineColor(ROUTE_COLOR),
            PropertyFactory.lineWidth(DOT_WIDTH),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineDasharray(DOT_PATTERN),
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
    addLayer(SymbolLayer(SELECTED_PILL, MARKERS).withFilter(eq(get("sort"), literal(1))).withProperties(*pillProperties()))
    // M9: the plane, turned to the route's heading; it turns with the map, which doesn't rotate anyway.
    addImage(PLANE_IMAGE, plane)
    addSource(GeoJsonSource(PLANE))
    addLayer(
        SymbolLayer("plane-icon", PLANE).withProperties(
            PropertyFactory.iconImage(PLANE_IMAGE),
            PropertyFactory.iconRotate(get("heading")),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconOpacity(get("opacity")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

private fun pillProperties() = arrayOf(
    PropertyFactory.iconImage(get("image")),
    PropertyFactory.iconAllowOverlap(true),
    PropertyFactory.iconIgnorePlacement(true),
)

/** The route's colour; the map style is always light, so one value works in both themes. */
/** Roame's pink (Step 6.3). */
private const val ROUTE_COLOR = 0xFFEA4167.toInt()

private data class PillColors(val card: Int, val text: Int, val border: Int, val accent: Int, val onAccent: Int, val tint: Int)

/**
 * D45: the map stays light in dark mode, so its pills keep the light theme's card, text, card border, accent,
 * on-accent and light pink tint (Theme.kt), as iOS's do.
 */
private val LightPillColors = PillColors(
    card = 0xFFFFFFFF.toInt(),
    text = 0xFF302D2A.toInt(),
    border = 0xFFE8E5E0.toInt(),
    accent = 0xFFD63A60.toInt(),
    onAccent = 0xFFFFFFFF.toInt(),
    tint = 0xFFFDE3E9.toInt(),
)

/**
 * M2, M3: one pill per destination (UI_SPEC §3). Each pill is an image named by what it shows; returns the names this
 * update and the one before it used, given [before], and removes any older ones.
 */
private fun Style.showMarkers(
    context: Context,
    markers: List<DestinationMarker>,
    selectedCode: String?,
    density: Density,
    colors: PillColors,
    before: List<Set<String>>,
): List<Set<String>> {
    val used = mutableSetOf<String>()
    val features = markers.map { marker ->
        val selected = marker.code == selectedCode
        val image = "pill/${marker.code}/${marker.flightCount}/$selected/${density.fontScale}/${colors.hashCode()}"
        if (getImage(image) == null) addImage(image, pillBitmap(context, marker, selected, density, colors))
        used += image
        Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).apply {
            addStringProperty("code", marker.code)
            addStringProperty("image", image)
            addNumberProperty("sort", if (selected) 1 else 0)
        }
    }
    getSourceAs<GeoJsonSource>(MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    // The previous update's images stay a little longer, as MapLibre may still be drawing them.
    val kept = listOfNotNull(before.lastOrNull(), used)
    before.flatten().filterNot { name -> kept.any { name in it } }.forEach(::removeImage)
    return kept
}

/** M7: the gentle arc from the shared `RouteArc` (D50), with a dot at the origin. */
private fun Style.showRoute(selected: FlightItem?) {
    val route = selected?.let(::routePoints)
    val line = route?.let { Feature.fromGeometry(LineString.fromLngLats(it)) }
    val origin = route?.first()?.let { Feature.fromGeometry(it) }
    getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(line)))
    getSourceAs<GeoJsonSource>(ORIGIN)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(origin)))
}

/** M9: the plane [frame] of the way along the selected route, or no plane. */
private fun Style.showPlane(selected: FlightItem?, frame: PlaneFrame) {
    val plane = selected?.let {
        val at = RouteArc.plane(
            GeoPoint(it.originLatitude, it.originLongitude),
            GeoPoint(it.destinationLatitude, it.destinationLongitude),
            frame.fraction,
        )
        Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude)).apply {
            addNumberProperty("heading", at.headingDegrees)
            addNumberProperty("opacity", frame.opacity)
        }
    }
    getSourceAs<GeoJsonSource>(PLANE)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(plane)))
}

/** M9: the plane in Roame's pink on a white disc, pointing north, so `iconRotate` turns it to the heading. */
private fun planeBitmap(context: Context, density: Float): Bitmap {
    val size = (32 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f - density
    canvas.drawCircle(size / 2f, size / 2f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    canvas.drawCircle(
        size / 2f, size / 2f, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
            style = Paint.Style.STROKE
            strokeWidth = density
        },
    )
    val inset = (6 * density).toInt()
    ContextCompat.getDrawable(context, R.drawable.ms_flight)?.mutate()?.apply {
        setTint(ROUTE_COLOR)
        setBounds(inset, inset, size - inset, size - inset)
        draw(canvas)
    }
    return bitmap
}

private fun routePoints(flight: FlightItem): List<Point> =
    RouteArc.interpolate(
        GeoPoint(flight.originLatitude, flight.originLongitude),
        GeoPoint(flight.destinationLatitude, flight.destinationLongitude),
    ).map { Point.fromLngLat(it.longitude, it.latitude) }

/** Fits the route. Its longitudes are unwrapped, so east is never less than west, even across the antimeridian. */
private fun routeCamera(flight: FlightItem, sides: Int, top: Int, bottom: Int): CameraUpdate {
    val points = routePoints(flight)
    val bounds = LatLngBounds.from(points.maxOf { it.latitude() }, points.maxOf { it.longitude() }, points.minOf { it.latitude() }, points.minOf { it.longitude() })
    return CameraUpdateFactory.newLatLngBounds(bounds, sides, top, sides, bottom)
}

private fun markersCamera(markers: List<DestinationMarker>, padding: Int, bottom: Int): CameraUpdate? = when (markers.size) {
    0 -> null
    1 -> CameraUpdateFactory.newLatLngZoom(LatLng(markers[0].latitude, markers[0].longitude), 4.0)
    else -> CameraUpdateFactory.newLatLngBounds(
        LatLngBounds.from(markers.maxOf { it.latitude }, markers.maxOf { it.longitude }, markers.minOf { it.latitude }, markers.minOf { it.longitude }),
        padding, padding, padding, bottom,
    )
}

private fun MapLibreMap.move(update: CameraUpdate, animate: Boolean) = if (animate) animateCamera(update, 800) else moveCamera(update)

private fun CameraPosition.toArray() = doubleArrayOf(target?.latitude ?: 0.0, target?.longitude ?: 0.0, zoom, bearing, tilt)

private fun DoubleArray.toCameraPosition() =
    CameraPosition.Builder().target(LatLng(this[0], this[1])).zoom(this[2]).bearing(this[3]).tilt(this[4]).build()

/**
 * Draws the whole pill, text included, so it needs no fonts from the network (Step 0.3), and as iOS's MarkerPill lays
 * it out: the code, then the flight count on a badge marked with a plane, pink when [selected], with a soft shadow. The
 * lowest points aren't drawn (the owner's decision, D44). Text follows the font scale; the margin holds the shadow and
 * keeps the pill centred.
 */
private fun pillBitmap(context: Context, marker: DestinationMarker, selected: Boolean, density: Density, colors: PillColors): Bitmap {
    fun dp(value: Float) = value * density.density
    fun text(font: Int, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, font)
        textSize = size * density.density * density.fontScale
        this.color = color
    }
    val ink = if (selected) colors.onAccent else colors.text
    val code = text(R.font.inter_bold, 12f, ink)
    val count = text(R.font.inter_bold, 11f, colors.accent)
    val countText = groupedCount(marker.flightCount)

    val icon = dp(11f)
    val badgeWidth = dp(5f) + icon + dp(2f) + count.measureText(countText) + dp(5f)
    val badgeHeight = maxOf(dp(18f), count.fontSpacing)
    val width = dp(10f) + code.measureText(marker.code) + dp(6f) + badgeWidth + dp(4f)
    val height = maxOf(dp(26f), maxOf(code.fontSpacing, badgeHeight) + dp(8f))
    val margin = dp(4f)
    val bitmap = Bitmap.createBitmap((width + 2 * margin).toInt() + 1, (height + 2 * margin).toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pill = RectF(margin, margin, margin + width, margin + height)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) colors.accent else colors.card
        setShadowLayer(dp(3f), 0f, dp(1f), 0x33000000)
    }
    canvas.drawRoundRect(pill, height / 2, height / 2, fill)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) colors.accent else colors.border
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    val inset = RectF(pill).apply { inset(dp(0.5f), dp(0.5f)) }
    canvas.drawRoundRect(inset, inset.height() / 2, inset.height() / 2, border)

    val middle = pill.centerY()
    fun Paint.baseline() = middle - (ascent() + descent()) / 2
    var x = pill.left + dp(10f)
    canvas.drawText(marker.code, x, code.baseline(), code)
    x += code.measureText(marker.code) + dp(6f)

    val badge = RectF(x, middle - badgeHeight / 2, x + badgeWidth, middle + badgeHeight / 2)
    canvas.drawRoundRect(badge, badgeHeight / 2, badgeHeight / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (selected) colors.onAccent else colors.tint })
    x += dp(5f)
    // The plane points along the line from the left, as on the flight cards.
    ContextCompat.getDrawable(context, R.drawable.ms_flight)?.mutate()?.apply {
        setTint(colors.accent)
        setBounds(x.toInt(), (middle - icon / 2).toInt(), (x + icon).toInt(), (middle + icon / 2).toInt())
        canvas.save()
        canvas.rotate(90f, x + icon / 2, middle)
        draw(canvas)
        canvas.restore()
    }
    x += icon + dp(2f)
    canvas.drawText(countText, x, count.baseline(), count)
    return bitmap
}

/** Runs [action] once the view has a size: now, or after the layout that gives it one. Returns the listener to remove. */
private fun View.doOnceSized(action: () -> Unit): View.OnLayoutChangeListener {
    val listener = object : View.OnLayoutChangeListener {
        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            if (view.width == 0 || view.height == 0) return
            view.removeOnLayoutChangeListener(this)
            action()
        }
    }
    if (width > 0 && height > 0 && !isLayoutRequested) action() else addOnLayoutChangeListener(listener)
    return listener
}

/** UI_SPEC §8: with animations off in the system settings, the camera jumps and views don't fade. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { animationsOff(context) }
}

private fun animationsOff(context: Context) =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * D48: like iOS's muted, flat map with no points of interest (`ExplorerMap.swift`). Hides the style's shops and places
 * (OpenMapTiles' `poi` layer) and its 3D buildings, and gives the sea iOS's blue. The land keeps Liberty's colours,
 * from Natural Earth's relief. Layers are found by kind and OpenMapTiles' layer names, not by the style's own ids.
 */
private fun Style.matchIos() {
    for (layer in layers) {
        when {
            layer is FillExtrusionLayer || (layer is SymbolLayer && layer.sourceLayer == "poi") ->
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            layer is FillLayer && layer.sourceLayer == "water" -> layer.setProperties(PropertyFactory.fillColor(IOS_WATER))
        }
    }
}
