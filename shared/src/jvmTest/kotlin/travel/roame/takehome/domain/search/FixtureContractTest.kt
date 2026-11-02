package travel.roame.takehome.domain.search

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.data.remote.KtorFlightsApi
import travel.roame.takehome.model.Flight
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the test data and docs honest. The airports and flight counts in [TestAirports] must match
 * `fixtures/flights.json`, `fixtures/airports.json` must cover exactly those airports, and the real
 * fixture must decode to the facts in DOMAIN_RULES §0.
 */
class FixtureContractTest {

    private val fixtures = File("../fixtures")

    private fun objects(file: File): List<Map<String, String>> =
        Regex("""\{[^{}]*\}""").findAll(file.readText()).map { obj ->
            Regex(""""(\w+)"\s*:\s*(?:"([^"]*)"|(-?[0-9.]+))""").findAll(obj.value)
                .associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
        }.toList()

    private val flights by lazy { objects(File(fixtures, "flights.json")).filter { "id" in it } }

    private fun airportsOn(side: String) = flights.map {
        TestAirports.airport(
            it.getValue(side),
            it.getValue("${side}City"),
            it.getValue("${side}Latitude").toDouble(),
            it.getValue("${side}Longitude").toDouble(),
        )
    }.toSet()

    private val fixtureText by lazy { File(fixtures, "flights.json").readText() }

    /** The real fixture through the real decoding path; only the network is fake. */
    private val decoded: List<Flight> by lazy {
        val engine = MockEngine {
            respond(fixtureText, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = runBlocking { KtorFlightsApi(engine, "http://api.test").fetchFlights() }
        (result as FetchResult.Success).flights
    }

    @Test
    fun fixtureEnvelopeHasOnlyFlights_andEveryRecordHasAll13Fields() {
        val envelope = Json.parseToJsonElement(fixtureText).jsonObject
        assertEquals(setOf("flights"), envelope.keys)
        val records = envelope.getValue("flights").jsonArray.map { it.jsonObject }
        assertEquals(1000, records.size)
        val fields = setOf(
            "id", "origin", "originCity", "originLatitude", "originLongitude",
            "destination", "destinationCity", "destinationLatitude", "destinationLongitude",
            "departureDate", "program", "points", "cabin",
        )
        assertTrue(records.all { it.keys == fields })
        assertTrue(records.none { record -> record.values.any { it is JsonNull } })
    }

    @Test
    fun fixtureDecodesTo1000FlightsWithUniqueOpaqueIds() {
        assertEquals(1000, decoded.size)
        assertEquals(1000, decoded.map { it.id }.toSet().size)
        assertEquals(
            setOf("f101", "f102", "f103", "f104", "f105", "f106"),
            decoded.map { it.id }.filterNot { it.startsWith("fx-") }.toSet(),
        )
    }

    @Test
    fun fixtureDatesCoverEveryDayFromFeb1ToApr1InOrder() {
        val dates = decoded.map { it.departureDate }
        assertEquals(dates.sorted(), dates)
        assertEquals(LocalDate(2027, 2, 1), dates.first())
        assertEquals(LocalDate(2027, 4, 1), dates.last())
        assertEquals(60, dates.toSet().size)
        assertEquals(59, dates.first().daysUntil(dates.last()))
    }

    @Test
    fun fixtureAirportsAndRoutes() {
        val origins = decoded.map { it.origin.code }.toSet()
        val destinations = decoded.map { it.destination.code }.toSet()
        assertEquals(setOf("BOS", "JFK", "LAX", "ORD", "SEA", "SFO", "YVR", "YYZ"), origins)
        assertEquals(24, destinations.size)
        assertTrue(origins.intersect(destinations).isEmpty())

        val everyOccurrence = decoded.flatMap { listOf(it.origin, it.destination) }.toSet()
        assertEquals(32, everyOccurrence.size, "a code's city and coordinates are the same everywhere")

        assertEquals(192, decoded.map { it.origin.code to it.destination.code }.toSet().size)
        assertEquals(
            1000,
            decoded.map { Triple(it.origin.code, it.destination.code, it.departureDate) }.toSet().size,
            "at most one flight per route per day",
        )
    }

    @Test
    fun fixtureCabinsProgramsAndPoints() {
        assertEquals(
            mapOf("Economy" to 467, "Business" to 344, "Premium Economy" to 189),
            decoded.groupingBy { it.cabin }.eachCount(),
        )
        assertEquals(
            mapOf("Flying Blue" to 212, "Mileage Plan" to 203, "MileagePlus" to 199, "Avios" to 196, "Aeroplan" to 190),
            decoded.groupingBy { it.program }.eachCount(),
        )
        assertEquals(9_000, decoded.minOf { it.points })
        assertEquals(133_000, decoded.maxOf { it.points })
        assertTrue(decoded.all { it.points % 500 == 0 })
    }

    @Test
    fun fixtureTraps() {
        assertEquals(
            setOf("HND", "NRT"),
            decoded.map { it.destination }.filter { it.city == "Tokyo" }.map { it.code }.toSet(),
        )
        assertTrue(decoded.any { it.destination.city == "Sao Paulo" })
        assertEquals(364, decoded.count { abs(it.destination.longitude - it.origin.longitude) > 180 })

        val perDestination = decoded.groupingBy { it.destination.code }.eachCount()
        assertEquals(55, perDestination["NRT"])
        assertEquals(55, perDestination["HND"])
        assertEquals(54, perDestination["MAD"])
        assertEquals(55, perDestination.values.max())
    }

    @Test
    fun testAirportsMatchFlightsJson() {
        assertEquals(1000, flights.size)
        assertEquals(airportsOn("origin"), TestAirports.origins.toSet())
        assertEquals(airportsOn("destination"), TestAirports.destinations.toSet())
        assertEquals(flights.groupingBy { it.getValue("origin") }.eachCount(), TestAirports.originFlightCounts)
        assertEquals(flights.groupingBy { it.getValue("destination") }.eachCount(), TestAirports.destinationFlightCounts)
    }

    @Test
    fun p3_airportsJsonCoversEveryFixtureAirportOnce() {
        val stats = objects(File(fixtures, "airports.json")).filter { "code" in it }
        val codes = stats.map { it.getValue("code") }
        assertEquals(codes.size, codes.toSet().size, "no duplicate codes")
        val fixtureCodes = (TestAirports.origins + TestAirports.destinations).map { it.code }.toSet()
        assertEquals(fixtureCodes, codes.toSet())
        assertTrue(stats.all { it.getValue("annualPassengers").toLong() > 0 })
    }
}
