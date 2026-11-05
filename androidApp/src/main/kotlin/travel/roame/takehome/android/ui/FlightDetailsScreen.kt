package travel.roame.takehome.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.FlightDetails

/**
 * The flight details page (Step 6.3f, D47), like Roame's and drawn the same as iOS's: the destination's photo with
 * "From Toronto to Barcelona", then a card with the flight and what the data says about it (DT1). The data has no times,
 * aircraft or travel time, so those show as placeholders, and the page says so (DT2). It follows the live state, so
 * saving updates it.
 */
@Composable
fun FlightDetailsScreen(details: FlightDetails, data: DataStatus, isPending: Boolean, onClose: () -> Unit, onToggleSave: () -> Unit) {
    BackHandler(onBack = onClose)
    val flight = details.flight
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DetailsHero(details.photoKey, flight.originCity, flight.destinationCity)
                // Over the photo's bottom edge, as in Roame's.
                DetailsCard(details, data as? DataStatus.Ready, Modifier.padding(start = 16.dp, top = HeroHeight - 48.dp, end = 16.dp, bottom = 24.dp))
            }
            val back = stringResource(R.string.back)
            Box(
                Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp)
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onClose)
                    .clearAndSetSemantics {
                        contentDescription = back
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = null, tint = Color(0xFF302D2A))
            }
        }
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            SaveButton(flight.isSaved, isPending, onToggleSave, Modifier.fillMaxWidth())
        }
    }
}

private val HeroHeight = 340.dp

/** The destination's photo, darker at the top and bottom so the white text reads, or Roame's gradient without one. */
@Composable
private fun DetailsHero(photoKey: String, origin: String, destination: String) {
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        val photo = DestinationPhotos[photoKey]
        if (photo != null) {
            Image(painterResource(photo), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            NoPhoto()
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))))
        val description = stringResource(R.string.details_from_to_description, origin, destination)
        // The photo's height is fixed, so the title, already 40 sp, grows only so far, as on iOS; TalkBack reads it in full.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
            Column(
                Modifier.align(Alignment.Center).padding(start = 24.dp, top = 24.dp, end = 24.dp)
                    .clearAndSetSemantics {
                        contentDescription = description
                        heading()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.details_from_to, origin).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = MaterialTheme.typography.labelLarge.fontSize * 0.1f),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    destination,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Roame's "Flight Details" card: the departure, the program, the stops, and the points with what the data says of them. */
@Composable
private fun DetailsCard(details: FlightDetails, data: DataStatus.Ready?, modifier: Modifier) {
    val flight = details.flight
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val stacked = isLargeText()
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.details_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (data != null) {
                Text(
                    stringResource(R.string.details_updated, syncTime(data.lastSuccessAt, System.currentTimeMillis())),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

            val departure = stringResource(R.string.details_departure_description, spokenCardDate(flight.departureDate))
            Text(
                stringResource(R.string.details_departure, cardDate(flight.departureDate)),
                Modifier.semantics { contentDescription = departure },
                style = MaterialTheme.typography.titleMedium,
            )
            // At large font scales the logo goes above, so the words don't break mid-word beside it, as on iOS.
            val programText: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val aircraft = stringResource(R.string.details_program_aircraft_description, flight.program)
                    Text(
                        stringResource(R.string.details_program_aircraft, flight.program),
                        Modifier.semantics { contentDescription = aircraft },
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                    )
                    CabinLabel(flight.cabin)
                }
            }
            if (stacked) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgramLogo(flight.program, height = 18.dp)
                    programText()
                }
            } else {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgramLogo(flight.program, height = 18.dp)
                    programText()
                }
            }

            Column(Modifier.padding(top = 16.dp)) {
                val departs = stringResource(R.string.details_place, flight.originCity, flight.originCode)
                val arrives = stringResource(R.string.details_place, flight.destinationCity, flight.destinationCode)
                Stop(departs, stringResource(R.string.details_stop_departure_description, departs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DottedLine()
                    val travel = stringResource(R.string.details_travel_time_description)
                    Text(
                        stringResource(R.string.details_travel_time),
                        Modifier.semantics { contentDescription = travel },
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                    )
                }
                Stop(arrives, stringResource(R.string.details_stop_arrival_description, arrives))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

            val points = spokenPoints(flight.points)
            Text(
                PointsFormat.full(flight.points),
                Modifier.semantics { contentDescription = points },
                style = MaterialTheme.typography.headlineSmall.merge(TabularDigits),
            )
            if (details.percentBelowTypical > 0) {
                Text(
                    stringResource(R.string.details_below_typical, details.percentBelowTypical, flight.cabin),
                    Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.padding(top = 12.dp)) {
                Fact(stringResource(R.string.details_distance), stringResource(R.string.details_distance_value, groupedCount(details.distanceKm)), stacked)
                details.estimatedValueUsd?.let { value ->
                    Fact(stringResource(R.string.details_value), stringResource(R.string.details_value_amount, groupedCount(value)), stacked)
                }
            }

            Text(
                stringResource(R.string.details_footnote),
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
        }
    }
}

/** A stop on the timeline: an open circle, the time, and the place. */
@Composable
private fun Stop(place: String, description: String) {
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 2.dp).size(16.dp).border(3.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.details_time_placeholder), style = MaterialTheme.typography.titleMedium)
            Text(place, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The dotted line between the stops. */
@Composable
private fun DottedLine() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.width(16.dp).height(44.dp)) {
        drawLine(
            color,
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, size.height),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** A label on the left and its value on the right, or the value under it at large font scales. */
@Composable
private fun Fact(label: String, value: String, stacked: Boolean) {
    val modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
    }
}
