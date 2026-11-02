package travel.roame.takehome.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal data class Airport(
    val code: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
)

/** One award flight (DOMAIN_RULES §1). [cabin] and [program] stay plain strings so new values can't break decoding (DM1). */
internal data class Flight(
    val id: String,
    val origin: Airport,
    val destination: Airport,
    val departureDate: LocalDate,
    val program: String,
    val points: Int,
    val cabin: String,
)

internal data class SavedFlight(val flight: Flight, val savedAt: Instant)
