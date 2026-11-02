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
import travel.roame.takehome.domain.GreatCircle
import travel.roame.takehome.domain.MarkerSheetOrder
import travel.roame.takehome.domain.ResultOrder
import travel.roame.takehome.domain.matching
import travel.roame.takehome.domain.toDestinationGroups
import travel.roame.takehome.domain.toFilterOptions
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import java.io.File
import java.security.MessageDigest
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

    // Filters on the real data. The expected counts come from a separate Python script over the same file.

    private fun count(filters: FlightFilters, origin: Set<String>? = null, destination: Set<String>? = null) =
        decoded.matching(filters, origin, destination).size

    @Test
    fun f7_noFiltersShowEveryFixtureFlight() {
        assertEquals(1000, count(FlightFilters()))
    }

    @Test
    fun f1_fixtureDateBoundariesAreInclusive() {
        assertEquals(48, count(FlightFilters(dateStart = "2027-02-10", dateEnd = "2027-02-12")))
        assertEquals(16, count(FlightFilters(dateStart = "2027-02-10", dateEnd = "2027-02-10")))
    }

    @Test
    fun f4_fixturePointsBoundariesAreInclusive() {
        assertEquals(805, count(FlightFilters(maxPoints = 60_000)))
        assertEquals(800, count(FlightFilters(maxPoints = 59_500)))
        assertEquals(1, count(FlightFilters(maxPoints = 9_000)))
        assertEquals(0, count(FlightFilters(maxPoints = 8_500)))
    }

    @Test
    fun f6_fixtureProgramsStaySeparate() {
        assertEquals(203, count(FlightFilters(programs = setOf("Mileage Plan"))))
        assertEquals(199, count(FlightFilters(programs = setOf("MileagePlus"))))
        assertEquals(386, count(FlightFilters(programs = setOf("Avios", "Aeroplan"))))
    }

    @Test
    fun f7_fixtureFiltersAndSearchCombine() {
        val demo = FlightFilters("2027-02-10", "2027-02-12", "Business", 60_000, setOf("Avios", "Aeroplan"))
        assertEquals(2, count(demo))
        assertEquals(16, count(FlightFilters(), setOf("BOS"), setOf("HND", "NRT")))
        assertEquals(7, count(FlightFilters(cabin = "Economy", maxPoints = 60_000), setOf("BOS"), setOf("HND", "NRT")))
    }

    @Test
    fun f8_fixtureOptionsComeFromTheData() {
        assertEquals(
            FilterOptions(
                minDate = "2027-02-01",
                maxDate = "2027-04-01",
                minPoints = 9_000,
                maxPoints = 133_000,
                cabins = listOf("Economy", "Premium Economy", "Business"),
                programs = listOf("Aeroplan", "Avios", "Flying Blue", "Mileage Plan", "MileagePlus"),
            ),
            decoded.toFilterOptions(),
        )
    }

    /** SHA-256 of every id in order, one per line, so a single row out of place fails. */
    private fun orderHash(order: Comparator<Flight>): String {
        val ids = decoded.sortedWith(order).joinToString("\n") { it.id }
        return MessageDigest.getInstance("SHA-256").digest(ids.toByteArray()).toHexString()
    }

    @Test
    fun o1_fixtureSortsTheSameAsAnIndependentSort() {
        assertEquals("41425f759f4d7caa2e1b2e5b76359c98f86a50e29947f08b672156bc32ee6d6f", orderHash(ResultOrder))
    }

    @Test
    fun o2_fixtureSortsTheSameAsAnIndependentSort() {
        assertEquals("8f3196e19d5a757e06c79e5bff8e15a4a0a6d2d26863861e04466238a4f6f359", orderHash(MarkerSheetOrder))
    }

    // The map on the real data.

    @Test
    fun m2_fixtureGivesOneMarkerPerDestination() {
        val groups = decoded.toDestinationGroups()
        assertEquals(24, groups.size)
        assertEquals(1000, groups.sumOf { it.flightCount })
        val expected = mapOf("CDG" to (45 to 13_500), "HKG" to (25 to 23_500), "NRT" to (55 to 19_500), "HND" to (55 to 16_500), "SYD" to (43 to 24_000))
        for ((code, countAndMin) in expected) {
            val group = groups.first { it.destination.code == code }
            assertEquals(countAndMin, group.flightCount to group.minPoints, code)
        }
    }

    @Test
    fun m1_fixtureMarkersFollowTheFilters() {
        val results = decoded.matching(FlightFilters(cabin = "Business", maxPoints = 60_000))
        val groups = results.toDestinationGroups()
        assertEquals(171, results.size)
        assertEquals(22, groups.size)
        assertEquals(171, groups.sumOf { it.flightCount })
        val paris = groups.first { it.destination.code == "CDG" }
        assertEquals(12 to 39_000, paris.flightCount to paris.minPoints)
    }

    @Test
    fun m7_everyFixtureRouteIsContinuousShortAndTheRightLength() {
        val routes = decoded.distinctBy { it.origin.code to it.destination.code }
        assertEquals(192, routes.size)
        assertEquals(68, routes.count { abs(it.destination.longitude - it.origin.longitude) > 180 })
        for (route in routes) {
            val from = GeoPoint(route.origin.latitude, route.origin.longitude)
            val to = GeoPoint(route.destination.latitude, route.destination.longitude)
            val points = GreatCircle.interpolate(from, to)
            val label = "${route.origin.code} to ${route.destination.code}"
            points.zipWithNext { a, b -> assertTrue(abs(b.longitude - a.longitude) < 180.0, label) }
            assertTrue(abs(points.last().longitude - points.first().longitude) <= 180.0, label)
            assertEquals(from.distanceKmTo(to), points.zipWithNext { a, b -> a.distanceKmTo(b) }.sum(), 1e-3, label)
        }
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
