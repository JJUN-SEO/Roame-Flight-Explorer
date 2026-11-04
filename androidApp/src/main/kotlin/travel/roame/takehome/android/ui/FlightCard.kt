package travel.roame.takehome.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.FlightItem

/**
 * The selected flight with Save or Remove (UI_SPEC §2.3, L6), the same in Explore and Saved.
 * [onDebugLongPress] is set in debug builds only: long-pressing the title arms "Fail next DB write" (TEST_PLAN §6).
 */
@Composable
fun FlightCard(
    flight: FlightItem,
    isPending: Boolean,
    onClose: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    isListed: Boolean = true,
    onDebugLongPress: (() -> Unit)? = null,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val debug = if (onDebugLongPress == null) Modifier else Modifier.pointerInput(onDebugLongPress) {
                    detectTapGestures(onLongPress = { onDebugLongPress() })
                }
                Text(
                    stringResource(R.string.selected_flight),
                    Modifier.weight(1f).then(debug),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val close = stringResource(R.string.close)
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = close }) {
                    Icon(painterResource(R.drawable.ms_close), contentDescription = null)
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RouteConnector(flight.originCode, flight.destinationCode)
                Row {
                    Text(flight.originCity, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(flight.destinationCity, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cardDate(flight.departureDate), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    CabinLabel(flight.cabin)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgramLogo(flight.program, height = 18.dp)
                    if (!isListed) {
                        Spacer(Modifier.width(8.dp))
                        NotListedTag()
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PointsFormat.full(flight.points),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall.merge(TabularDigits),
                        fontWeight = FontWeight.SemiBold,
                    )
                    SaveButton(flight.isSaved, isPending, onToggleSave)
                }
            }
        }
    }
}

/** V7: no optimistic change. While the write runs, the button shows a spinner and ignores taps. */
@Composable
private fun SaveButton(isSaved: Boolean, isPending: Boolean, onClick: () -> Unit) {
    val leading: @Composable () -> Unit = {
        if (isPending) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            Icon(
                painterResource(if (isSaved) R.drawable.ms_favorite_filled else R.drawable.ms_favorite),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    val text = stringResource(if (isSaved) R.string.remove else R.string.save_flight)
    if (isSaved) {
        TintedButton(text, onClick, enabled = !isPending, leading = leading)
    } else {
        PrimaryButton(text, onClick, enabled = !isPending, leading = leading)
    }
}
