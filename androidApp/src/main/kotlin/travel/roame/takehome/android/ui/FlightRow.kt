package travel.roame.takehome.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.FlightItem

val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

/**
 * One result (UI_SPEC §2.1), as a white card like Roame's. Tapping it selects it; the heart saves or removes it (V7).
 * At large font scales its parts stack instead of truncating (UI_SPEC §8), as iOS's do.
 */
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
    // UI_SPEC §8: "Toronto to Barcelona, Monday, February 1, Economy, Aeroplan, 16,000 points, saved", in full words.
    val spoken = stringResource(
        if (flight.isSaved) R.string.row_description_saved else R.string.row_description,
        flight.originCity,
        flight.destinationCity,
        spokenDate(flight.departureDate),
        flight.cabin,
        flight.program,
        spokenPoints(flight.points),
    )
    val description = if (isListed) spoken else stringResource(R.string.row_description_not_listed, spoken)
    val stacked = isLargeText()
    // UI_SPEC §8: colour is never the only signal, so a selected row also gets a border and the selected state.
    Surface(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (stacked) {
            StackedRow(flight, isPending, isListed, onToggleSave)
        } else {
            CompactRow(flight, isPending, isListed, onToggleSave)
        }
    }
}

@Composable
private fun CompactRow(flight: FlightItem, isPending: Boolean, isListed: Boolean, onToggleSave: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteConnector(flight.originCode, flight.destinationCode, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                PointsFormat.full(flight.points),
                style = MaterialTheme.typography.titleMedium.merge(TabularDigits),
                fontWeight = FontWeight.SemiBold,
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
            CabinLabel(flight.cabin)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rowDate(flight.departureDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            ProgramLogo(flight.program)
            Spacer(Modifier.weight(1f))
            if (!isListed) NotListedTag()
            // Lines the glyph up with the points above, while keeping the 48 dp touch target.
            SaveHeart(flight.isSaved, isPending, onToggleSave, Modifier.offset(x = 12.dp))
        }
    }
}

/** One part per line, nothing cut short; the heart keeps the bottom corner. */
@Composable
private fun StackedRow(flight: FlightItem, isPending: Boolean, isListed: Boolean, onToggleSave: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteConnector(flight.originCode, flight.destinationCode)
        Text(PointsFormat.full(flight.points), style = MaterialTheme.typography.titleMedium.merge(TabularDigits), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.route_cities, flight.originCity, flight.destinationCity), style = MaterialTheme.typography.bodyMedium, color = secondary)
        CabinLabel(flight.cabin)
        Text(rowDate(flight.departureDate), style = MaterialTheme.typography.bodyMedium, color = secondary)
        ProgramLogo(flight.program)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isListed) NotListedTag()
            Spacer(Modifier.weight(1f))
            SaveHeart(flight.isSaved, isPending, onToggleSave, Modifier.offset(x = 12.dp))
        }
    }
}

/** UI_SPEC §8: at or above this font scale, rows and the card stack their parts, as iOS does at its accessibility sizes. */
@Composable
internal fun isLargeText(): Boolean = LocalDensity.current.fontScale >= 1.5f

/** "25,000 points", spoken in place of "25,000 pts" (UI_SPEC §8). */
@Composable
internal fun spokenPoints(points: Int): String = stringResource(R.string.spoken_points, groupedCount(points))

/** ♡ or ♥, or a spinner while the write runs (V7). */
@Composable
private fun SaveHeart(isSaved: Boolean, isPending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (isSaved) R.string.remove_from_saved else R.string.save_flight)
    IconButton(onClick = onClick, enabled = !isPending, modifier = modifier.semantics { contentDescription = label }) {
        if (isPending) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painterResource(if (isSaved) R.drawable.ms_favorite_filled else R.drawable.ms_favorite),
                contentDescription = null,
                tint = if (isSaved) LocalExtraColors.current.saved else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `ORIGIN ──✈── DEST` (UI_SPEC §3), with the plane in Roame's pink. */
@Composable
fun RouteConnector(origin: String, destination: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // A code never breaks across lines, however large the text (UI_SPEC §8).
        Text(origin, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, softWrap = false)
        Line(Modifier.weight(1f).padding(start = 8.dp))
        Icon(
            painterResource(R.drawable.ms_flight),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 4.dp).size(18.dp).rotate(90f),
            tint = LocalExtraColors.current.brand,
        )
        Line(Modifier.weight(1f).padding(end = 8.dp))
        Text(destination, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, softWrap = false)
    }
}

@Composable
private fun Line(modifier: Modifier) {
    Box(modifier.height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * Roame's seat-class tag: an icon and the name in the class's colour (UI_SPEC §6). A cabin the app doesn't know yet
 * gets the Economy look, so new data can't break it (DM1).
 */
@Composable
fun CabinLabel(cabin: String, modifier: Modifier = Modifier) {
    val extras = LocalExtraColors.current
    val (icon, iconColor, textColor) = when (cabin) {
        "First" -> Triple(R.drawable.ms_sparkles, extras.firstIcon, extras.firstText)
        "Business" -> Triple(R.drawable.ms_crown, extras.businessIcon, extras.businessText)
        "Premium Economy" -> Triple(R.drawable.ms_seat_premium, extras.premiumEconomy, extras.premiumEconomy)
        else -> Triple(R.drawable.ms_seat, extras.economy, extras.economy)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = iconColor)
        Spacer(Modifier.width(4.dp))
        Text(cabin, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1)
    }
}

/**
 * The loyalty program's logo on a white pill, like Roame's program chips; white in dark mode too, so the logos keep
 * their own colours (D34). Programs without a freely licensed logo show their name instead.
 */
@Composable
fun ProgramLogo(program: String, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val logo = when (program) {
        "Aeroplan" -> R.drawable.logo_aeroplan
        "MileagePlus" -> R.drawable.logo_mileageplus
        "Mileage Plan" -> R.drawable.logo_mileage_plan
        else -> null
    }
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(999.dp))
            .border(1.dp, Color(0xFFE8E5E0), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(painterResource(logo), contentDescription = program, modifier = Modifier.height(height), contentScale = ContentScale.FillHeight)
        } else {
            Text(program, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF302D2A), maxLines = 1)
        }
    }
}

/** V4: the latest refresh no longer has this saved flight. */
@Composable
fun NotListedTag() {
    Tag(stringResource(R.string.not_listed), MaterialTheme.colorScheme.onSurfaceVariant)
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
