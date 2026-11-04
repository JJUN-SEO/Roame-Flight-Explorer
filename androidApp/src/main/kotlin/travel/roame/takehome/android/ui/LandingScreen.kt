package travel.roame.takehome.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.presentation.DataStatus
import travel.roame.takehome.presentation.DestinationDeal
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ProgramOffer

/**
 * Explore's landing page, like Roame's home (Step 6.3b, UI_SPEC §2.0): the search bar, a banner, the destination deck
 * and each program's best offer, drawn the same as iOS's LandingScreen (D38). Flights appear only after a search (D36).
 */
@Composable
fun LandingScreen(
    state: ExplorerState,
    scrollState: ScrollState,
    onSearch: () -> Unit,
    onFlexible: () -> Unit,
    onDestination: (DestinationDeal) -> Unit,
    onOffer: (ProgramOffer) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.app_name_full),
            Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = LocalExtraColors.current.brand,
        )
        SearchPill(state, onSearch, Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        when (val data = state.data) {
            DataStatus.Loading -> LandingPlaceholders()
            is DataStatus.Unavailable -> Box(Modifier.heightIn(min = 400.dp)) { UnavailableMessage(data.error, onRetry) }
            is DataStatus.Ready -> {
                Spacer(Modifier.height(8.dp))
                StaleBanner(data)
                RefreshProgress(data)
                if (state.options.isEmpty) {
                    // UI_SPEC §4: a successful empty response is not an error (R4).
                    Box(Modifier.heightIn(min = 400.dp)) {
                        EmptyState(R.drawable.ms_flight, stringResource(R.string.synced_empty_title), stringResource(R.string.synced_empty_body))
                    }
                } else {
                    HeroCard(onFlexible, Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
                    val home = state.landing.homeAirport
                    val from = if (home != null) stringResource(R.string.landing_from_home, home.city, home.code) else stringResource(R.string.landing_from_everywhere)
                    // New deals start from their first card. Otherwise, when the location arrives and reorders them, the
                    // rows keep the old first card in view and the new best deal is hidden off to the left.
                    key(home?.code) {
                        SectionHeader(stringResource(R.string.landing_destinations_title), from)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.landing.destinations, key = { it.city }) { deal -> DestinationCard(deal) { onDestination(deal) } }
                        }
                        SectionHeader(stringResource(R.string.landing_offers_title), from)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.landing.offers, key = { it.program }) { offer -> OfferCard(offer) { onOffer(offer) } }
                        }
                    }
                }
            }
        }
    }
}

/** Roame's search bar: the current search, or "Where to?", and its dates and cabin. Tapping it opens the search. */
@Composable
private fun SearchPill(state: ExplorerState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val origin = state.originPick?.let { airportLabel(it) } ?: state.originQuery.takeIf { it.isNotBlank() }
    val destination = state.destinationPick?.let { airportLabel(it) } ?: state.destinationQuery.takeIf { it.isNotBlank() }
    val anywhere = stringResource(R.string.landing_anywhere)
    val title = if (origin == null && destination == null) stringResource(R.string.landing_search_title)
    else stringResource(R.string.landing_search_route, origin ?: anywhere, destination ?: anywhere)
    val start = state.filters.dateStart ?: state.options.minDate
    val end = state.filters.dateEnd ?: state.options.maxDate
    val dates = if (start != null && end != null) stringResource(R.string.date_range, chipDate(start), chipDate(end)) else null
    val cabin = state.filters.cabin ?: stringResource(R.string.landing_any_cabin)
    val details = if (dates != null) stringResource(R.string.landing_search_details, dates, cabin) else cabin
    val description = stringResource(R.string.landing_search_description, title, details)
    Row(
        modifier.fillMaxWidth()
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_search), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Roame's banner: a landscape with a line of copy and "I'm flexible", which shows every flight. */
@Composable
private fun HeroCard(onFlexible: () -> Unit, modifier: Modifier = Modifier) {
    val extras = LocalExtraColors.current
    Box(modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.hero_landscape), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.landing_hero_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.landing_hero_button),
                Modifier.heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(extras.gradientStart, extras.gradientEnd)))
                    .clickable(role = Role.Button, onClick = onFlexible)
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 16.dp, top = 28.dp, end = 16.dp, bottom = 12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** B8: a photo of the city (D33), its lowest points, and how far its best flight is below typical. */
@Composable
private fun DestinationCard(deal: DestinationDeal, onClick: () -> Unit) {
    val points = PointsFormat.full(deal.minPoints)
    val description = if (deal.percentBelowTypical > 0) {
        stringResource(R.string.landing_destination_description_below, deal.city, groupedCount(deal.minPoints), deal.percentBelowTypical)
    } else {
        stringResource(R.string.landing_destination_description, deal.city, groupedCount(deal.minPoints))
    }
    Box(
        Modifier.width(260.dp).aspectRatio(3f / 2f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        val photo = DestinationPhotos[deal.photoKey]
        if (photo != null) {
            Image(painterResource(photo), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            NoPhoto()
        }
        // Darker towards the bottom, so the white text reads on any photo.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
        if (deal.percentBelowTypical > 0) {
            Text(
                stringResource(R.string.landing_up_to_below, deal.percentBelowTypical),
                Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(LocalExtraColors.current.brand, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(deal.city, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.landing_from_points, points), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** D33: a city the app has no photo for gets a drawn card in Roame's gradient. */
@Composable
private fun NoPhoto() {
    val extras = LocalExtraColors.current
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(extras.gradientStart, extras.gradientEnd))), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ms_flight), contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.6f))
    }
}

/** B7: a program's best-value flight, under the program's logo, like Roame's program deals. */
@Composable
private fun OfferCard(offer: ProgramOffer, onClick: () -> Unit) {
    val flight = offer.flight
    val points = PointsFormat.full(flight.points)
    val value = offer.estimatedValueUsd?.let { groupedCount(it) }
    val description = stringResource(
        R.string.landing_offer_description,
        offer.program, flight.originCity, flight.destinationCity, rowDate(flight.departureDate), flight.cabin, groupedCount(flight.points),
    ) + (if (offer.percentBelowTypical > 0) stringResource(R.string.landing_offer_below, offer.percentBelowTypical) else "") +
        (value?.let { stringResource(R.string.landing_offer_value, it) } ?: "")
    Column(Modifier.width(300.dp)) {
        ProgramLogo(offer.program, height = 18.dp)
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rowDate(flight.departureDate), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    CabinLabel(flight.cabin)
                }
                RouteConnector(flight.originCode, flight.destinationCode)
                Text(
                    stringResource(R.string.route_cities, flight.originCity, flight.destinationCity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (offer.percentBelowTypical > 0) {
                        Text(
                            stringResource(R.string.landing_below_typical, offer.percentBelowTypical),
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(points, style = MaterialTheme.typography.titleMedium.merge(TabularDigits))
                        if (value != null) {
                            Text(stringResource(R.string.landing_value, value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** UI_SPEC §4 first load: grey shapes where the banner and the deck will be. */
@Composable
private fun LandingPlaceholders() {
    val grey = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(grey, RoundedCornerShape(16.dp)))
        Box(Modifier.width(260.dp).aspectRatio(3f / 2f).background(grey, RoundedCornerShape(16.dp)))
    }
}

/** D33: the bundled photos, by the shared photo key. Listed here, not looked up by name, so a build can't drop one. */
private val DestinationPhotos: Map<String, Int> = mapOf(
    "dest_amsterdam" to R.drawable.dest_amsterdam,
    "dest_athens" to R.drawable.dest_athens,
    "dest_auckland" to R.drawable.dest_auckland,
    "dest_bangkok" to R.drawable.dest_bangkok,
    "dest_barcelona" to R.drawable.dest_barcelona,
    "dest_doha" to R.drawable.dest_doha,
    "dest_dubai" to R.drawable.dest_dubai,
    "dest_dublin" to R.drawable.dest_dublin,
    "dest_frankfurt" to R.drawable.dest_frankfurt,
    "dest_hong_kong" to R.drawable.dest_hong_kong,
    "dest_johannesburg" to R.drawable.dest_johannesburg,
    "dest_lisbon" to R.drawable.dest_lisbon,
    "dest_london" to R.drawable.dest_london,
    "dest_madrid" to R.drawable.dest_madrid,
    "dest_melbourne" to R.drawable.dest_melbourne,
    "dest_mexico_city" to R.drawable.dest_mexico_city,
    "dest_paris" to R.drawable.dest_paris,
    "dest_rome" to R.drawable.dest_rome,
    "dest_sao_paulo" to R.drawable.dest_sao_paulo,
    "dest_seoul" to R.drawable.dest_seoul,
    "dest_singapore" to R.drawable.dest_singapore,
    "dest_sydney" to R.drawable.dest_sydney,
    "dest_tokyo" to R.drawable.dest_tokyo,
)
