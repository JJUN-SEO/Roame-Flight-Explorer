package travel.roame.takehome.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

/**
 * The device's location for ranking origins (D16, D30). Call the returned function when From gets focus:
 * - The first time, Android asks for approximate location. If allowed, the location is read straight away.
 * - Once allowed, each launch reads it again without asking.
 * - After a denial the app never asks again by itself. Nothing changes and no error shows: ranking stays volume-only (P4).
 */
@Composable
fun rememberOriginLocation(onLocation: (latitude: Double, longitude: Double) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val latestOnLocation by rememberUpdatedState(onLocation)
    // Saveable, so rotation neither asks again nor re-reads; a new launch starts fresh.
    var asked by rememberSaveable { mutableStateOf(false) }
    var located by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }

    val read = {
        if (!located && !reading) {
            reading = true
            scope.launch {
                approximateLocation(context)?.let {
                    latestOnLocation(it.latitude, it.longitude)
                    located = true
                }
                reading = false
            }
        }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) read() }

    // A later launch, with the permission already granted: read quietly.
    LaunchedEffect(Unit) { if (context.hasLocationPermission()) read() }

    return {
        when {
            context.hasLocationPermission() -> read()
            // Android shows a rationale flag only after a denial, so "false" here means it hasn't been asked yet.
            !asked && activity?.shouldShowRequestPermissionRationale(PERMISSION) == false -> {
                asked = true
                askPermission.launch(PERMISSION)
            }
        }
    }
}

private fun Context.hasLocationPermission() = checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

/** One reading at balanced power, which is approximate (ARCHITECTURE §9). Null when there's no permission or no fix. */
private suspend fun approximateLocation(context: Context): Location? {
    if (!context.hasLocationPermission()) return null
    val cancel = CancellationTokenSource()
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel.cancel() }
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
