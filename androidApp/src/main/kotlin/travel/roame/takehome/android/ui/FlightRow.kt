package travel.roame.takehome.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.FlightItem

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

/** One result (UI_SPEC §2.1). */
@Composable
fun FlightRow(flight: FlightItem, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.row_description,
        flight.originCity,
        flight.destinationCity,
        rowDate(flight.departureDate),
        flight.cabin,
        flight.program,
        PointsFormat.full(flight.points),
    )
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Text(
                "${rowDate(flight.departureDate)} · ${flight.program}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** A tinted capsule: grey Economy, teal Premium Economy, indigo Business (UI_SPEC §6). */
@Composable
fun CabinTag(cabin: String) {
    val extras = LocalExtraColors.current
    val color = when (cabin) {
        "Premium Economy" -> extras.premiumEconomy
        "Business" -> extras.business
        else -> extras.economy
    }
    Text(
        cabin,
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
    )
}
