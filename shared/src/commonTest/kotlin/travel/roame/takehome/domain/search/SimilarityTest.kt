package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimilarityTest {

    @Test
    fun s7_editDistanceCountsAdjacentTranspositionAsOne() {
        assertEquals(1, editDistance("pairs", "paris", maxDistance = 2))
        assertEquals(1, editDistance("tokoy", "tokyo", maxDistance = 2))
    }

    @Test
    fun s7_editDistanceCountsInsertDeleteSubstitute() {
        assertEquals(1, editDistance("dubln", "dublin", maxDistance = 2))
        assertEquals(1, editDistance("seoull", "seoul", maxDistance = 2))
        assertEquals(1, editDistance("roma", "rome", maxDistance = 2))
        assertEquals(0, editDistance("rome", "rome", maxDistance = 2))
        assertEquals(4, editDistance("", "rome", maxDistance = 4))
    }

    @Test
    fun s7_editDistanceStopsEarlyPastTheBudget() {
        assertEquals(2, editDistance("berlin", "dublin", maxDistance = 1))
        assertEquals(2, editDistance("a", "abcdef", maxDistance = 1))
    }

    @Test
    fun s9_trigramsArePaddedAtTheEdges() {
        assertEquals(setOf("  r", " ro", "rom", "ome", "me "), Trigrams.of("rome"))
        assertTrue(Trigrams.of("").isEmpty())
    }

    @Test
    fun s9_trigramIndexScoresSharedGramsWithDice() {
        val index = TrigramIndex(listOf("johannesburg", "rome"))
        val scores = index.similarities("johanesburg")
        assertTrue(scores.getValue(0) >= 0.55, "johanesburg ~ johannesburg: ${scores[0]}")
        assertEquals(null, scores[1])
        assertEquals(1.0, index.similarities("rome").getValue(1))
    }
}
