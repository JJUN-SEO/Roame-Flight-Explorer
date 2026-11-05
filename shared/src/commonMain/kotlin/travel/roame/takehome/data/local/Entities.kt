package travel.roame.takehome.data.local

import androidx.room.Embedded
import androidx.room.Entity
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SavedFlight
import kotlin.time.Instant

/** Every flight column. Both flight tables embed this, so they can't drift apart. */
internal data class FlightColumns(
    val id: String,
    val origin: String,
    val originCity: String,
    val originLat: Double,
    val originLon: Double,
    val destination: String,
    val destinationCity: String,
    val destinationLat: Double,
    val destinationLon: Double,
    /** ISO `yyyy-MM-dd`: a calendar date, never an instant. */
    val departureDate: String,
    val program: String,
    val points: Int,
    val cabin: String,
)

@Entity(tableName = "cached_flight", primaryKeys = ["id"])
internal data class CachedFlightEntity(@Embedded val flight: FlightColumns)

/** A full copy of the flight, so it outlives the cache (V1). */
@Entity(tableName = "saved_flight", primaryKeys = ["id"])
internal data class SavedFlightEntity(
    @Embedded val flight: FlightColumns,
    /** Epoch milliseconds. */
    val savedAt: Long,
)

/** A single row with id 0. No row means the app has never synced (R8). */
@Entity(tableName = "sync_meta", primaryKeys = ["id"])
internal data class SyncMetaEntity(
    val id: Int = 0,
    /** Epoch milliseconds. */
    val lastSuccessAt: Long,
)

private fun Flight.toColumns() = FlightColumns(
    id = id,
    origin = origin.code,
    originCity = origin.city,
    originLat = origin.latitude,
    originLon = origin.longitude,
    destination = destination.code,
    destinationCity = destination.city,
    destinationLat = destination.latitude,
    destinationLon = destination.longitude,
    departureDate = departureDate.toString(),
    program = program,
    points = points,
    cabin = cabin,
)

private fun FlightColumns.toDomain() = Flight(
    id = id,
    origin = Airport(origin, originCity, originLat, originLon),
    destination = Airport(destination, destinationCity, destinationLat, destinationLon),
    departureDate = LocalDate.parse(departureDate),
    program = program,
    points = points,
    cabin = cabin,
)

internal fun Flight.toCachedEntity() = CachedFlightEntity(toColumns())

internal fun CachedFlightEntity.toDomain() = flight.toDomain()

internal fun Flight.toSavedEntity(savedAt: Long) = SavedFlightEntity(toColumns(), savedAt)

internal fun SavedFlightEntity.toDomain() = SavedFlight(flight.toDomain(), Instant.fromEpochMilliseconds(savedAt))
