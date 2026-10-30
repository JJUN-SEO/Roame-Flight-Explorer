package travel.roame.takehome.domain.search

import kotlin.math.abs
import kotlin.math.min

/** Character trigrams with boundary padding: "rome" → {"  r", " ro", "rom", "ome", "me "}. */
internal object Trigrams {

    fun of(text: String): Set<String> {
        if (text.isEmpty()) return emptySet()
        val padded = "  $text "
        return (0..padded.length - 3).mapTo(HashSet()) { padded.substring(it, it + 3) }
    }

    /** Dice coefficient 2·|A∩B| / (|A|+|B|) from the shared count and the two set sizes. */
    fun dice(shared: Int, sizeA: Int, sizeB: Int): Double =
        if (sizeA + sizeB == 0) 0.0 else 2.0 * shared / (sizeA + sizeB)
}

/**
 * Inverted index from trigram to term ids, so fuzzy candidates are found by looking up the
 * query's few trigrams instead of comparing against every term.
 */
internal class TrigramIndex(terms: List<String>) {

    private val postings: Map<String, IntArray>
    private val gramCounts: IntArray = IntArray(terms.size)

    init {
        val building = HashMap<String, MutableList<Int>>()
        terms.forEachIndexed { termId, term ->
            val grams = Trigrams.of(term)
            gramCounts[termId] = grams.size
            grams.forEach { building.getOrPut(it) { ArrayList() }.add(termId) }
        }
        postings = building.mapValues { it.value.toIntArray() }
    }

    /** Dice similarity to [query] for every term sharing at least one trigram with it. */
    fun similarities(query: String): Map<Int, Double> {
        val queryGrams = Trigrams.of(query)
        val shared = HashMap<Int, Int>()
        for (gram in queryGrams) {
            postings[gram]?.forEach { shared[it] = (shared[it] ?: 0) + 1 }
        }
        return shared.mapValues { (termId, count) -> Trigrams.dice(count, queryGrams.size, gramCounts[termId]) }
    }
}

/**
 * Optimal-string-alignment distance: insertions, deletions, substitutions and adjacent
 * transpositions each cost 1 ("pairs" → "paris" = 1). Returns [maxDistance] + 1 as soon as the
 * distance is known to exceed [maxDistance].
 */
internal fun editDistance(a: String, b: String, maxDistance: Int): Int {
    if (abs(a.length - b.length) > maxDistance) return maxDistance + 1
    var twoBack = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                value = min(value, twoBack[j - 2] + 1)
            }
            current[j] = value
            rowMin = min(rowMin, value)
        }
        if (rowMin > maxDistance) return maxDistance + 1
        val recycled = twoBack
        twoBack = previous
        previous = current
        current = recycled
    }
    return min(previous[b.length], maxDistance + 1)
}
