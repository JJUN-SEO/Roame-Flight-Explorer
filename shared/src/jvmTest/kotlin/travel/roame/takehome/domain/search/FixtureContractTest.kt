package travel.roame.takehome.domain.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the search test data honest: the airports and flight counts in [TestAirports] must match
 * `fixtures/flights.json`, and `fixtures/airports.json` must cover exactly those airports.
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
