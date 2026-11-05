package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES S11: the examples table, row for row, so the doc and the engine can't drift apart. */
class SearchExamplesTest {

    private data class Example(val origin: Boolean, val query: String, val tier: SearchTier, val codes: Set<String>)

    private val examples = listOf(
        Example(origin = true, "  BOS ", SearchTier.Prefix, setOf("BOS")),
        Example(origin = true, "york", SearchTier.Prefix, setOf("JFK")),
        Example(origin = false, "tokyo", SearchTier.Prefix, setOf("HND", "NRT")),
        Example(origin = false, "dub", SearchTier.Prefix, setOf("DUB", "DXB")),
        Example(origin = false, "São Paulo", SearchTier.Prefix, setOf("GRU")),
        Example(origin = false, "hongkong", SearchTier.Prefix, setOf("HKG")),
        Example(origin = false, "ondon", SearchTier.Fuzzy, setOf("LHR")),
        Example(origin = false, "sydeny", SearchTier.Fuzzy, setOf("SYD")),
        Example(origin = false, "sidnee", SearchTier.Fuzzy, setOf("SYD")),
        Example(origin = true, "shicago", SearchTier.Fuzzy, setOf("ORD")),
        Example(origin = false, "okland", SearchTier.Fuzzy, setOf("AKL")),
        Example(origin = false, "johanesburg", SearchTier.Fuzzy, setOf("JNB")),
        Example(origin = false, "roma", SearchTier.Fuzzy, setOf("FCO")),
        Example(origin = false, "berlin", SearchTier.NoMatch, emptySet()),
        Example(origin = false, "sitty", SearchTier.NoMatch, emptySet()),
        Example(origin = true, "paris", SearchTier.NoMatch, emptySet()),
    )

    @Test
    fun s11_everyRowOfTheExamplesTableHolds() {
        for (example in examples) {
            val engine = if (example.origin) TestAirports.originEngine else TestAirports.destinationEngine
            val result = engine.search(example.query)
            assertEquals(example.tier to example.codes, result.tier to result.codes, "'${example.query}'")
        }
    }
}
