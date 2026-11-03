package travel.roame.takehome.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.FlightItem

val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

/** One result (UI_SPEC §2.1). Tapping it selects it; the heart saves or removes it (V7). */
@Composable
fun FlightRow(
    flight: FlightItem,
    isSelected: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    isListed: Boolean = true,
) {
    val description = stringResource(
        if (flight.isSaved) R.string.row_description_saved else R.string.row_description,
        flight.originCity,
        flight.destinationCity,
        rowDate(flight.departureDate),
        flight.cabin,
        flight.program,
        PointsFormat.full(flight.points),
    )
    // UI_SPEC §8: colour is never the only signal, so a selected row also gets a border and the selected state.
    Surface(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteConnector(flight.originCode, flight.destinationCode, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    PointsFormat.full(flight.points),
                    style = MaterialTheme.typography.titleMedium.merge(TabularDigits),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.route_cities, flight.originCity, flight.destinationCity),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                CabinTag(flight.cabin)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rowDate(flight.departureDate)} · ${flight.program}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isListed) NotListedTag()
                // Lines the glyph up with the points above, while keeping the 48 dp touch target.
                SaveHeart(flight.isSaved, isPending, onToggleSave, Modifier.offset(x = 12.dp))
            }
        }
    }
}

/** ♡ or ♥, or a spinner while the write runs (V7). */
@Composable
private fun SaveHeart(isSaved: Boolean, isPending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (isSaved) R.string.remove_from_saved else R.string.save_flight)
    IconButton(onClick = onClick, enabled = !isPending, modifier = modifier.semantics { contentDescription = label }) {
        if (isPending) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                if (isSaved) "♥" else "♡",
                Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.titleLarge,
                color = if (isSaved) LocalExtraColors.current.saved else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `ORIGIN ●──✈──● DEST` (UI_SPEC §3). */
@Composable
fun RouteConnector(origin: String, destination: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(origin, style = MaterialTheme.typography.titleMedium)
        Line(Modifier.weight(1f).padding(start = 8.dp))
        Text("✈", Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.primary)
        Line(Modifier.weight(1f).padding(end = 8.dp))
        Text(destination, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Line(modifier: Modifier) {
    Box(modifier.height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Grey Economy, teal Premium Economy, indigo Business (UI_SPEC §6). */
@Composable
fun CabinTag(cabin: String) {
    val extras = LocalExtraColors.current
    val color = when (cabin) {
        "Premium Economy" -> extras.premiumEconomy
        "Business" -> extras.business
        else -> extras.economy
    }
    Tag(cabin, color)
}

/** V4: the latest refresh no longer has this saved flight. */
@Composable
fun NotListedTag() {
    Tag(stringResource(R.string.not_listed), LocalExtraColors.current.economy)
}

/** A tinted capsule with full-colour text (UI_SPEC §6). */
@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
    )
}
