package travel.roame.takehome.data.remote

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight

/** The API's response body. No field has a default, so a missing one fails the whole response (DM3). */
@Serializable
internal data class FlightsEnvelopeDto(val flights: List<FlightDto>)

@Serializable
internal data class FlightDto(
    val id: String,
    val origin: String,
    val originCity: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val destination: String,
    val destinationCity: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val departureDate: String,
    val program: String,
    val points: Int,
    val cabin: String,
)

/** Trims strings and uppercases airport codes (DM2). Throws [IllegalArgumentException] on a bad date (DM3). */
internal fun FlightDto.toDomain() = Flight(
    id = id.trim(),
    origin = Airport(origin.trim().uppercase(), originCity.trim(), originLatitude, originLongitude),
    destination = Airport(destination.trim().uppercase(), destinationCity.trim(), destinationLatitude, destinationLongitude),
    departureDate = LocalDate.parse(departureDate.trim()),
    program = program.trim(),
    points = points,
    cabin = cabin.trim(),
)
