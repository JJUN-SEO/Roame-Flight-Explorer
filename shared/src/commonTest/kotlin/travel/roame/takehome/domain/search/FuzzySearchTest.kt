package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tier 2 (fuzzy) behaviour of [AirportSearchEngine]: DOMAIN_RULES S6–S10.
 * Grouped by the four ways people mistype a place (typo, sound-alike, partial spelling, accents).
 */
class FuzzySearchTest {

    private enum class Side { O, D }

    private fun engine(side: Side) = if (side == Side.O) TestAirports.originEngine else TestAirports.destinationEngine

    /** Each case: query, side, expected codes (empty = no match). Every case must reach Tier 2. */
    private fun assertFuzzy(cases: List<Triple<String, Side, String>>) {
        val failures = cases.mapNotNull { (query, side, expected) ->
            val result = engine(side).search(query)
            val expectedCodes = expected.split(' ').filter { it.isNotEmpty() }.toSet()
            val expectedTier = if (expectedCodes.isEmpty()) SearchTier.NoMatch else SearchTier.Fuzzy
            if (result.codes == expectedCodes && result.tier == expectedTier) {
                null
            } else {
                "\"$query\" ($side): expected $expectedCodes/$expectedTier, got ${result.codes}/${result.tier}"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun s7_typos() = assertFuzzy(
        listOf(
            Triple("sydeny", Side.D, "SYD"), Triple("bangkk", Side.D, "BKK"), Triple("barcleona", Side.D, "BCN"),
            Triple("amsterdma", Side.D, "AMS"), Triple("frankfrut", Side.D, "FRA"),
            Triple("johannesbrug", Side.D, "JNB"), Triple("melbourme", Side.D, "MEL"), Triple("singpore", Side.D, "SIN"),
            Triple("lodnon", Side.D, "LHR"), Triple("dubln", Side.D, "DUB"), Triple("athnes", Side.D, "ATH"),
            Triple("lisbn", Side.D, "LIS"), Triple("madird", Side.D, "MAD"), Triple("auckalnd", Side.D, "AKL"),
            Triple("hong kogn", Side.D, "HKG"), Triple("seoull", Side.D, "ICN"), Triple("tokoy", Side.D, "HND NRT"),
            Triple("pairs", Side.D, "CDG"), Triple("mexcio city", Side.D, "MEX"), Triple("sao pualo", Side.D, "GRU"),
            Triple("ondon", Side.D, "LHR"),
            Triple("bostno", Side.O, "BOS"), Triple("chciago", Side.O, "ORD"), Triple("seatle", Side.O, "SEA"),
            Triple("torotno", Side.O, "YYZ"), Triple("vancover", Side.O, "YVR"),
            Triple("san fransisco", Side.O, "SFO"), Triple("los angelas", Side.O, "LAX"), Triple("new yrok", Side.O, "JFK"),
        ),
    )

    @Test
    fun s8_soundsLikeSpellings() = assertFuzzy(
        listOf(
            Triple("sidnee", Side.D, "SYD"), Triple("bankok", Side.D, "BKK"), Triple("barselona", Side.D, "BCN"),
            Triple("frankfort", Side.D, "FRA"), Triple("melborn", Side.D, "MEL"), Triple("singapur", Side.D, "SIN"),
            Triple("lundun", Side.D, "LHR"), Triple("dooblin", Side.D, "DUB"), Triple("ateens", Side.D, "ATH"),
            Triple("lizbon", Side.D, "LIS"), Triple("madreed", Side.D, "MAD"), Triple("okland", Side.D, "AKL"),
            Triple("seol", Side.D, "ICN"), Triple("tokio", Side.D, "HND NRT"), Triple("pariss", Side.D, "CDG"),
            Triple("roam", Side.D, "FCO"), Triple("dubay", Side.D, "DXB"), Triple("meksiko", Side.D, "MEX"),
            Triple("sow paulo", Side.D, "GRU"), Triple("yohannesburg", Side.D, "JNB"), Triple("doja", Side.D, "DOH"),
            Triple("shicago", Side.O, "ORD"), Triple("siattle", Side.O, "SEA"), Triple("vankuver", Side.O, "YVR"),
            Triple("tronto", Side.O, "YYZ"), Triple("bostin", Side.O, "BOS"), Triple("sanfrancisko", Side.O, "SFO"),
        ),
    )

    @Test
    fun s7_s9_partlyRightSpellings() = assertFuzzy(
        listOf(
            Triple("johanesburg", Side.D, "JNB"), Triple("johannesberg", Side.D, "JNB"),
            Triple("amstedam", Side.D, "AMS"), Triple("sinapore", Side.D, "SIN"), Triple("aukland", Side.D, "AKL"),
            Triple("sydny", Side.D, "SYD"), Triple("hon kong", Side.D, "HKG"),
        ),
    )

    @Test
    fun s1_s7_accentsAndLocalNames() = assertFuzzy(
        listOf(
            Triple("roma", Side.D, "FCO"), Triple("lisboa", Side.D, "LIS"), Triple("Páriss", Side.D, "CDG"),
        ),
    )

    @Test
    fun s6_s10_unrelatedPlacesMatchNothing() = assertFuzzy(
        listOf(
            Triple("berlin", Side.D, ""), Triple("oslo", Side.D, ""), Triple("miami", Side.D, ""),
            Triple("milan", Side.D, ""), Triple("cairo", Side.D, ""), Triple("vienna", Side.D, ""),
            Triple("porto", Side.D, ""), Triple("lima", Side.D, ""), Triple("nice", Side.D, ""),
            Triple("zzzz", Side.D, ""), Triple("kyoto", Side.D, ""), Triple("perth", Side.D, ""),
            Triple("delhi", Side.D, ""), Triple("munich", Side.D, ""), Triple("manila", Side.D, ""),
            Triple("osaka", Side.D, ""), Triple("sitty", Side.D, ""),
            Triple("denver", Side.O, ""), Triple("atlanta", Side.O, ""), Triple("houston", Side.O, ""),
            Triple("dallas", Side.O, ""),
        ),
    )

    @Test
    fun s12_knownBehaviour_newarkSoundsLikeNewYork() {
        // Newark (EWR) is not in the data; it encodes like "newyork" (NRK), so the UI hint
        // reads "Showing New York (JFK)". Documented in DOMAIN_RULES S12 rather than special-cased.
        val result = TestAirports.originEngine.search("newark")
        assertEquals(SearchTier.Fuzzy, result.tier)
        assertEquals(listOf(MatchKind.Phonetic), result.matches.map { it.kind })
        assertEquals(setOf("JFK"), result.codes)
    }

    @Test
    fun s6_fuzzyNeedsFourLetters() {
        assertEquals(SearchTier.NoMatch, TestAirports.destinationEngine.search("syx").tier)
        assertEquals(SearchTier.NoMatch, TestAirports.originEngine.search("bsx").tier)
    }

    @Test
    fun s6_fuzzyNeverRunsWhenPrefixMatches() {
        val result = TestAirports.destinationEngine.search("dubai")
        assertEquals(SearchTier.Prefix, result.tier)
        assertEquals(setOf("DXB"), result.codes)
    }

    @Test
    fun s6_codesAreNeverFuzzyMatched() {
        // One letter off a code ("sin" → "syn") must not match by typo.
        assertEquals(SearchTier.NoMatch, TestAirports.destinationEngine.search("synx").tier)
    }

    @Test
    fun s10_fuzzyMatchesReportTheirSignal() {
        val kinds = TestAirports.destinationEngine.search("sidnee").matches.map { it.kind }
        assertEquals(listOf(MatchKind.Phonetic), kinds)
        val typo = TestAirports.destinationEngine.search("sydeny").matches.single()
        assertEquals(MatchKind.Typo, typo.kind)
        assertTrue(typo.score < 0.95, "fuzzy scores stay below prefix scores")
    }
}
