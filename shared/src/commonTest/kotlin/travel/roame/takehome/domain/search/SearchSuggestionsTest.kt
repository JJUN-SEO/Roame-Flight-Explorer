package travel.roame.takehome.domain.search

import travel.roame.takehome.model.AirportSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rows under a search field and the hint line: DOMAIN_RULES S13, S14. */
class SearchSuggestionsTest {

    private val destinationRanking = AirportRanking.build(
        TestAirports.destinations, AIRPORT_PASSENGERS, TestAirports.destinationFlightCounts, null, RankingWeights.DESTINATION,
    )

    private fun destinationRows(query: String) = TestAirports.destinationEngine.search(query, destinationRanking).toSuggestions()

    private fun airport(city: String, code: String) = AirportSuggestion(city, listOf(code), isCityRow = false)

    @Test
    fun s14_tokyoOffersTheWholeCityAndThenEachAirport() {
        assertEquals(
            listOf(
                AirportSuggestion("Tokyo", listOf("HND", "NRT"), isCityRow = true),
                airport("Tokyo", "HND"),
                airport("Tokyo", "NRT"),
            ),
            destinationRows("tokyo"),
        )
    }

    @Test
    fun s14_oneAirportGivesNoCityRow() {
        assertEquals(listOf(airport("Tokyo", "NRT")), destinationRows("nrt"))
    }

    @Test
    fun s14_rowsFollowTheRankedMatches() {
        // Both are prefix matches; Dubai has more passengers, so it ranks first (P6).
        assertEquals(listOf(airport("Dubai", "DXB"), airport("Dublin", "DUB")), destinationRows("du"))
    }

    @Test
    fun s5_anExactCodeRanksFirstInTheRows() {
        assertEquals(listOf(airport("Dublin", "DUB"), airport("Dubai", "DXB")), destinationRows("dub"))
    }

    @Test
    fun s14_aFuzzyQueryStillGivesARow() {
        assertEquals(listOf(airport("Sydney", "SYD")), destinationRows("sydeny"))
    }

    @Test
    fun s14_aBlankQueryOrNoMatchGivesNoRows() {
        for (query in listOf("", "   ", "berlin")) assertEquals(emptyList(), destinationRows(query), "'$query'")
    }

    @Test
    fun s14_eachFieldOnlySuggestsItsOwnSide() {
        assertEquals(emptyList(), TestAirports.originEngine.search("paris").toSuggestions())
        assertEquals(listOf(airport("Paris", "CDG")), destinationRows("paris"))
    }

    @Test
    fun s14_theFiveRowCapCountsCityRows() {
        val engine = AirportSearchEngine(
            listOf(
                TestAirports.airport("AAA", "Alpha", 0.0, 0.0),
                TestAirports.airport("AAB", "Alpha", 0.0, 1.0),
                TestAirports.airport("ALB", "Alba", 0.0, 2.0),
                TestAirports.airport("ALD", "Alder", 0.0, 3.0),
                TestAirports.airport("ALT", "Altona", 0.0, 4.0),
            ),
        )
        assertEquals(
            listOf(
                AirportSuggestion("Alpha", listOf("AAA", "AAB"), isCityRow = true),
                airport("Alpha", "AAA"),
                airport("Alpha", "AAB"),
                airport("Alba", "ALB"),
                airport("Alder", "ALD"),
            ),
            engine.search("al").toSuggestions(),
        )
    }

    @Test
    fun s13_theHintOnlyAppearsForFuzzyMatches() {
        assertEquals(listOf(airport("Sydney", "SYD")), TestAirports.destinationEngine.search("sydeny").toHint())
        for (query in listOf("syd", "", "berlin")) {
            assertEquals(emptyList(), TestAirports.destinationEngine.search(query).toHint(), "'$query'")
        }
    }

    @Test
    fun s13_theHintListsEveryFuzzyMatchInRankedOrder() {
        val hint = TestAirports.destinationEngine.search("tokio", destinationRanking).toHint()
        assertEquals(listOf(airport("Tokyo", "HND"), airport("Tokyo", "NRT")), hint)
    }
}
