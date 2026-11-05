package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tier 1 (prefix) behaviour of [AirportSearchEngine]: DOMAIN_RULES S2–S5. */
class PrefixSearchTest {

    private val origin = TestAirports.originEngine
    private val destination = TestAirports.destinationEngine

    private fun assertPrefix(engine: AirportSearchEngine, query: String, vararg codes: String) {
        val result = engine.search(query)
        assertEquals(SearchTier.Prefix, result.tier, "tier for \"$query\"")
        assertEquals(codes.toSet(), result.codes, "codes for \"$query\"")
    }

    @Test
    fun s2_blankQueryMatchesEveryAirport() {
        val result = destination.search("   ")
        assertEquals(SearchTier.Blank, result.tier)
        assertEquals(24, result.codes.size)
    }

    @Test
    fun s3_originAndDestinationIndexesAreIndependent() {
        assertEquals(SearchTier.NoMatch, origin.search("paris").tier)
        assertPrefix(destination, "paris", "CDG")
        assertEquals(SearchTier.NoMatch, destination.search("boston").tier)
    }

    @Test
    fun s4_matchesCodeCityAndWordPrefixes() {
        assertPrefix(origin, "  BOS ", "BOS")
        assertPrefix(origin, "bo", "BOS")
        assertPrefix(origin, "york", "JFK")
        assertPrefix(origin, "new   york", "JFK")
        assertPrefix(origin, "angeles", "LAX")
        assertPrefix(origin, "san", "SFO")
        assertPrefix(destination, "tokyo", "HND", "NRT")
        assertPrefix(destination, "nrt", "NRT")
        assertPrefix(destination, "hongkong", "HKG")
    }

    @Test
    fun s4_shortPrefixesBranchToEveryMatchingAirport() {
        assertPrefix(destination, "s", "SIN", "SYD", "GRU", "ICN")
        assertPrefix(destination, "me", "MEL", "MEX")
        assertPrefix(destination, "dub", "DUB", "DXB")
        assertPrefix(destination, "to", "HND", "NRT")
    }

    @Test
    fun s4_accentedQueriesMatchUnaccentedNames() {
        assertPrefix(destination, "São Paulo", "GRU")
        assertPrefix(destination, "MÉXICO", "MEX")
        assertPrefix(destination, "Dublín", "DUB")
        assertPrefix(origin, "  BOSTÓN ", "BOS")
    }

    @Test
    fun s4_nonPrefixSubstringsDoNotMatchInTierOne() {
        assertTrue(destination.search("ondon").tier != SearchTier.Prefix)
    }

    @Test
    fun s5_exactCodeRanksFirst() {
        val result = destination.search("syd")
        assertEquals(MatchKind.ExactCode, result.matches.first().kind)
        assertEquals("SYD", result.matches.first().airport.code)
    }

    @Test
    fun s4_completionsOfferFullPhrases() {
        assertEquals(listOf("new york"), origin.completions("new"))
        assertEquals(listOf("new york"), origin.completions("yor"), "word keys complete to the full name")
        assertEquals(listOf("hong kong"), destination.completions("hongk"), "no-space keys complete to the full name")
        assertTrue("san francisco" in origin.completions("sa"))
    }
}
