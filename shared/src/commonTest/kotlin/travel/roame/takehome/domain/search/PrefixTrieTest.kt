package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefixTrieTest {

    private val trie = PrefixTrie.Builder()
        .add("new york", 0)
        .add("york", 0)
        .add("newark", 1)
        .add("new orleans", 2)
        .add("orleans", 2)
        .add("nairobi", 3)
        .build()

    @Test
    fun s4_traversalReturnsEveryEntryUnderThePrefix() {
        assertContentEquals(intArrayOf(0, 1, 2), trie.idsWithPrefix("new"))
        assertContentEquals(intArrayOf(0, 2), trie.idsWithPrefix("new "))
        assertContentEquals(intArrayOf(0, 1, 2, 3), trie.idsWithPrefix("n"))
    }

    @Test
    fun s4_wordStartPhrasesMatchLaterWords() {
        assertContentEquals(intArrayOf(0), trie.idsWithPrefix("yor"))
        assertContentEquals(intArrayOf(2), trie.idsWithPrefix("orl"))
    }

    @Test
    fun s4_unknownOrEmptyPrefixMatchesNothing() {
        assertTrue(trie.idsWithPrefix("newy").isEmpty())
        assertTrue(trie.idsWithPrefix("x").isEmpty())
        assertTrue(trie.idsWithPrefix("").isEmpty())
    }

    @Test
    fun s4_completionsBranchIntoFullPhrasesShortestFirst() {
        assertEquals(listOf("newark", "new york", "new orleans"), trie.completions("new"))
        assertEquals(listOf("newark"), trie.completions("new", limit = 1))
        assertEquals(emptyList(), trie.completions("zzz"))
    }

    @Test
    fun s4_completionsShowLabelsNotIndexKeys() {
        val labelled = PrefixTrie.Builder()
            .add("new york", 0)
            .add("newyork", 0, label = "new york")
            .add("york", 0, label = "new york")
            .build()
        assertEquals(listOf("new york"), labelled.completions("new"))
        assertEquals(listOf("new york"), labelled.completions("yo"))
    }

    @Test
    fun s4_duplicateEntriesAreStoredOnce() {
        val duplicated = PrefixTrie.Builder().add("rome", 7).add("rome", 7).build()
        assertContentEquals(intArrayOf(7), duplicated.idsWithPrefix("ro"))
    }
}
