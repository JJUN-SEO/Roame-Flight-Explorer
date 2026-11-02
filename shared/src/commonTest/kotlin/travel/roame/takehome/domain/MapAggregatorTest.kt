package travel.roame.takehome.domain

import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES M1–M6. */
class MapAggregatorTest {

    private fun to(destination: String, points: Int, id: String = "$destination-$points") =
        flight(id = id, destination = destination, points = points)

    private val flights = listOf(
        to("NRT", 30_000),
        to("CDG", 40_000),
        to("HND", 20_000),
        to("CDG", 25_000),
        to("NRT", 50_000),
        to("CDG", 60_000),
    )

    @Test
    fun m2_oneGroupPerDestinationCode() {
        assertEquals(setOf("CDG", "HND", "NRT"), flights.toDestinationGroups().map { it.destination.code }.toSet())
        assertEquals(3, flights.toDestinationGroups().size)
    }

    @Test
    fun m2_tokyosTwoAirportsStaySeparate() {
        val tokyo = listOf(
            flight(id = "a", destination = "NRT").let { it.copy(destination = it.destination.copy(city = "Tokyo")) },
            flight(id = "b", destination = "HND").let { it.copy(destination = it.destination.copy(city = "Tokyo")) },
        )
        assertEquals(listOf("HND", "NRT"), tokyo.toDestinationGroups().map { it.destination.code })
    }

    @Test
    fun m2_positionAndCityComeFromTheData() {
        val paris = Airport("CDG", "Paris", 49.0097, 2.5479)
        val group = listOf(flight().copy(destination = paris)).toDestinationGroups().single()
        assertEquals(paris, group.destination)
    }

    @Test
    fun m3_countAndLowestPointsAreOverThatDestinationsFlights() {
        val cdg = flights.toDestinationGroups().first { it.destination.code == "CDG" }
        assertEquals(3, cdg.flightCount)
        assertEquals(25_000, cdg.minPoints)
    }

    @Test
    fun m3_lowestPointsDoNotDependOnTheOrderPassedIn() {
        val cdg = flights.sortedByDescending { it.points }.toDestinationGroups().first { it.destination.code == "CDG" }
        assertEquals(25_000, cdg.minPoints)
    }

    @Test
    fun m4_groupsAreOrderedByCode() {
        assertEquals(listOf("CDG", "HND", "NRT"), flights.toDestinationGroups().map { it.destination.code })
    }

    @Test
    fun m5_eachGroupKeepsTheOrderPassedIn() {
        // The ids sort the opposite way from the points, so any other order would show.
        val sheet = listOf(to("CDG", 40_000, id = "b"), to("CDG", 60_000, id = "a"), to("CDG", 25_000, id = "c"))
            .sortedWith(MarkerSheetOrder)
        assertEquals(listOf("c", "b", "a"), sheet.toDestinationGroups().single().flights.map(Flight::id))
    }

    @Test
    fun m6_noFlightsMeansNoGroups() {
        assertEquals(emptyList(), emptyList<Flight>().toDestinationGroups())
    }

    @Test
    fun m1_countsAddUpToTheResultsUnderEveryFilter() {
        val filtersToTry = listOf(
            FlightFilters(),
            FlightFilters(maxPoints = 40_000),
            FlightFilters(maxPoints = 20_000),
            FlightFilters(maxPoints = 1),
        )
        for (filters in filtersToTry) {
            val results = flights.matching(filters)
            assertEquals(results.size, results.toDestinationGroups().sumOf { it.flightCount }, "$filters")
        }
    }

    @Test
    fun m1_aFilterChangesTheCountAndLowestPointsAndRemovesEmptyDestinations() {
        val groups = flights.matching(FlightFilters(maxPoints = 30_000)).toDestinationGroups()
        assertEquals(listOf("CDG", "HND", "NRT"), groups.map { it.destination.code })
        assertEquals(listOf(1, 1, 1), groups.map { it.flightCount })
        assertEquals(listOf(25_000, 20_000, 30_000), groups.map { it.minPoints })

        val cheapest = flights.matching(FlightFilters(maxPoints = 20_000)).toDestinationGroups()
        assertEquals(listOf("HND"), cheapest.map { it.destination.code })
    }
}
