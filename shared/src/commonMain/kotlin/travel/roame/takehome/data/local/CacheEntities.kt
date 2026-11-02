package travel.roame.takehome.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight

@Entity(tableName = "cached_flight")
internal data class CachedFlightEntity(
    @PrimaryKey val id: String,
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

/** A single row with id 0. No row means the app has never synced (R8). */
@Entity(tableName = "sync_meta")
internal data class SyncMetaEntity(
    @PrimaryKey val id: Int = 0,
    /** Epoch milliseconds. */
    val lastSuccessAt: Long,
)

internal fun Flight.toEntity() = CachedFlightEntity(
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

internal fun CachedFlightEntity.toDomain() = Flight(
    id = id,
    origin = Airport(origin, originCity, originLat, originLon),
    destination = Airport(destination, destinationCity, destinationLat, destinationLon),
    departureDate = LocalDate.parse(departureDate),
    program = program,
    points = points,
    cabin = cabin,
)
