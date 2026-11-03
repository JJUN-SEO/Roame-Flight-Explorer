package travel.roame.takehome.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import travel.roame.takehome.android.R
import kotlin.coroutines.resume

private const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

/**
 * ⌖ "Use my location" (D16, UI_SPEC §4): the only place location is asked for, and only when tapped.
 * A denial or a missing reading changes nothing and shows no error; ranking then uses airport volume only (P4).
 */
@Composable
fun MyLocationButton(isShared: Boolean, onLocation: (latitude: Double, longitude: Double) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    val read = {
        scope.launch {
            locating = true
            approximateLocation(context)?.let { onLocation(it.latitude, it.longitude) }
            locating = false
        }
        Unit
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) read() }
    val onClick = { if (context.hasLocationPermission()) read() else askPermission.launch(PERMISSION) }

    val label = stringResource(R.string.use_my_location)
    val state = stringResource(if (isShared) R.string.location_shared else R.string.location_not_shared)
    val modifier = Modifier.size(40.dp).semantics {
        contentDescription = label
        stateDescription = state
    }
    val content: @Composable () -> Unit = {
        if (locating) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("⌖", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
        }
    }
    // UI_SPEC §4: outlined until a location is shared, then filled with the accent.
    if (isShared) {
        FilledIconButton(onClick = onClick, modifier = modifier, enabled = !locating, content = content)
    } else {
        OutlinedIconButton(onClick = onClick, modifier = modifier, enabled = !locating, content = content)
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
