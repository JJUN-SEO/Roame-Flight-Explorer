package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prioritisation by location and volume: DOMAIN_RULES P1–P6. */
class AirportRankingTest {

    private val boston = GeoPoint(42.36, -71.01)
    private val seattle = GeoPoint(47.61, -122.33)

    /** Approximate annual passengers (millions) for a few destinations; test data only. */
    private val passengers = mapOf(
        "ICN" to 71_200_000L, "SIN" to 67_700_000L, "GRU" to 43_600_000L, "SYD" to 41_400_000L,
    )

    private fun ranking(
        airports: List<SearchableAirport>,
        annualPassengers: Map<String, Long> = emptyMap(),
        counts: Map<String, Int> = emptyMap(),
        location: GeoPoint? = null,
        weights: RankingWeights = RankingWeights.DESTINATION,
    ) = AirportRanking.build(airports, annualPassengers, counts, location, weights)

    @Test
    fun p1_rankingOrdersMatchesButNeverChangesWhichAirportsMatch() {
        val engine = TestAirports.destinationEngine
        val unranked = engine.search("s")
        val ranked = engine.search("s", ranking(TestAirports.destinations, passengers))
        assertEquals(unranked.codes, ranked.codes.toSet())
        assertEquals(listOf("ICN", "SIN", "GRU", "SYD"), ranked.matches.map { it.airport.code })
    }

    @Test
    fun p2_withoutLocationPriorityIsVolumeOnly() {
        val r = ranking(TestAirports.destinations, passengers)
        assertEquals(1.0, r.priorityOf("ICN"))
        assertEquals(41_400_000.0 / 71_200_000.0, r.priorityOf("SYD"), 1e-9)
        assertEquals(false, r.usesLocation)
    }

    @Test
    fun p2_locationPromotesNearbyAirports() {
        val origins = TestAirports.origins
        val r = ranking(origins, counts = TestAirports.originFlightCounts, location = boston, weights = RankingWeights.ORIGIN)
        val order = origins.map { it.code }.sortedByDescending { r.priorityOf(it) }
        assertEquals("BOS", order[0])
        assertEquals("JFK", order[1])
        assertTrue(r.usesLocation)
    }

    @Test
    fun p2_locationCanFlipTheOrderOfMatches() {
        val counts = TestAirports.originFlightCounts // SFO 123 flights, SEA 121
        val noLocation = TestAirports.originEngine.search("s", ranking(TestAirports.origins, counts = counts, weights = RankingWeights.ORIGIN))
        assertEquals(listOf("SFO", "SEA"), noLocation.matches.map { it.airport.code })

        val inSeattle = TestAirports.originEngine.search(
            "s",
            ranking(TestAirports.origins, counts = counts, location = seattle, weights = RankingWeights.ORIGIN),
        )
        assertEquals(listOf("SEA", "SFO"), inSeattle.matches.map { it.airport.code })
    }

    @Test
    fun p3_passengerStatsAreTheVolumeSourceWhenComplete() {
        val all = TestAirports.destinations.associate { it.code to 1_000_000L }
        assertEquals(VolumeSource.PassengerStats, ranking(TestAirports.destinations, all).volumeSource)
    }

    @Test
    fun p4_missingAirportsFileFallsBackToFlightCountsFromFlightsJson() {
        val r = ranking(TestAirports.destinations, emptyMap(), TestAirports.destinationFlightCounts)
        assertEquals(VolumeSource.FlightCountFallback, r.volumeSource)
        assertEquals(1.0, r.priorityOf("NRT")) // 55 flights, the most
        assertEquals(25.0 / 55.0, r.priorityOf("HKG"), 1e-9)

        val result = TestAirports.destinationEngine.search("to", r)
        assertEquals(setOf("HND", "NRT"), result.codes, "search still works with only the original data")
    }

    @Test
    fun p4_partialStatsMixBothSources() {
        val r = ranking(TestAirports.destinations, passengers, TestAirports.destinationFlightCounts)
        assertEquals(VolumeSource.Mixed, r.volumeSource)
        assertTrue(r.priorityOf("MAD") > 0.0, "MAD has no stats, so it uses its flight count")
    }

    @Test
    fun p4_noVolumeDataAtAllStillSearches() {
        val r = ranking(TestAirports.destinations)
        assertEquals(VolumeSource.None, r.volumeSource)
        assertEquals(0.0, r.priorityOf("CDG"))
        assertEquals(setOf("CDG"), TestAirports.destinationEngine.search("paris", r).codes)
    }

    @Test
    fun p6_tiesFallBackToCodeOrder() {
        val result = TestAirports.destinationEngine.search("to")
        assertEquals(listOf("HND", "NRT"), result.matches.map { it.airport.code })
    }

    @Test
    fun p1_distanceIsGreatCircleKilometres() {
        val jfk = GeoPoint(40.63945, -73.77932)
        val lhr = GeoPoint(51.47075, -0.45991)
        assertEquals(5540.0, jfk.distanceKmTo(lhr), 15.0)
        assertEquals(0.0, jfk.distanceKmTo(jfk), 1e-9)
    }
}
