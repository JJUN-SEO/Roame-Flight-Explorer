package travel.roame.takehome.domain.search

import travel.roame.takehome.vendor.commonscodec.DoubleMetaphone

internal data class SearchableAirport(val code: String, val city: String, val location: GeoPoint)

/** Which step of the tiered search produced the result (DOMAIN_RULES S2, S4, S6). */
internal enum class SearchTier {
    /** Empty query: every airport matches. */
    Blank,

    /** Tier 1: the prefix tree found matches; fuzzy matching did not run. */
    Prefix,

    /** Tier 2: nothing matched by prefix, so fuzzy matching ran and found something. */
    Fuzzy,

    /** Neither tier matched. */
    NoMatch,
}

/** The strongest signal behind one airport match. */
internal enum class MatchKind { All, ExactCode, Prefix, Typo, Phonetic, NGram }

internal data class AirportMatch(
    val airport: SearchableAirport,
    val kind: MatchKind,
    /** Match quality in [0, 1]: exact code 1.0, prefix 0.95, fuzzy below that. */
    val score: Double,
    /** Ranking priority in [0, 1] from location and volume (P1). Orders matches; never filters them. */
    val priority: Double,
)

internal data class AirportSearchResult(
    val normalizedQuery: String,
    val tier: SearchTier,
    /** Ranked: match quality first, then priority, then code. */
    val matches: List<AirportMatch>,
) {
    val codes: Set<String> = matches.mapTo(LinkedHashSet()) { it.airport.code }
}

/** Tuning knobs; the defaults are the values in DOMAIN_RULES S6–S8. */
internal data class SearchConfig(
    /** Fuzzy matching needs at least this many letters (spaces ignored). */
    val minFuzzyLength: Int = 4,
    /** Minimum trigram Dice similarity for an n-gram match. */
    val nGramThreshold: Double = 0.55,
    /** Fuzzy matches scoring more than this below the best fuzzy match are dropped. */
    val fuzzyMargin: Double = 0.10,
    /** Double Metaphone code length (the library default is 4). */
    val metaphoneCodeLength: Int = 6,
    /** Individual words of multi-word city names shorter than this are not fuzzy terms. */
    val minWordLength: Int = 4,
)

/**
 * Airport search for one side of the route (origin or destination; DOMAIN_RULES §2).
 *
 * Tiered: Tier 1 walks a [PrefixTrie] (O(m)). Only when that finds nothing does Tier 2 run fuzzy
 * matching — typo distance, Double Metaphone phonetics and trigram similarity — over candidates
 * pulled from inverted indexes rather than a full scan. Results are ranked with [AirportRanking].
 *
 * Indexes are built once per dataset; [search] is cheap and has no side effects.
 */
internal class AirportSearchEngine(
    airports: Collection<SearchableAirport>,
    private val config: SearchConfig = SearchConfig(),
) {
    private class FuzzyTerm(
        val text: String,
        val airportId: Int,
        /** True for the whole city name (spaces removed), false for one word of a multi-word name. */
        val isWholeName: Boolean,
        val phoneticCodes: Set<String>,
    )

    private val airports: List<SearchableAirport> = airports.distinctBy { it.code }.sortedBy { it.code }
    private val metaphone = DoubleMetaphone(config.metaphoneCodeLength)
    private val codeIndex: Map<String, Int>
    private val trie: PrefixTrie
    private val terms: List<FuzzyTerm>
    private val trigramIndex: TrigramIndex
    private val phoneticIndex: Map<String, IntArray>

    init {
        val trieBuilder = PrefixTrie.Builder()
        val codes = HashMap<String, Int>()
        val fuzzyTerms = ArrayList<FuzzyTerm>()

        this.airports.forEachIndexed { id, airport ->
            val code = SearchText.normalize(airport.code)
            val city = SearchText.normalize(airport.city)
            val compactCity = SearchText.compact(city)
            val words = city.split(' ').filter { it.isNotEmpty() }

            codes[code] = id
            trieBuilder.add(code, id).add(city, id).add(compactCity, id, label = city)
            for (start in 1 until words.size) {
                trieBuilder.add(words.drop(start).joinToString(" "), id, label = city) // "york" → "new york"
            }

            if (compactCity.isNotEmpty()) {
                fuzzyTerms += FuzzyTerm(compactCity, id, isWholeName = true, phoneticCodes = phoneticCodes(compactCity))
            }
            if (words.size > 1) {
                words.filter { it.length >= config.minWordLength }.distinct().forEach { word ->
                    fuzzyTerms += FuzzyTerm(word, id, isWholeName = false, phoneticCodes = phoneticCodes(word))
                }
            }
        }

        codeIndex = codes
        trie = trieBuilder.build()
        terms = fuzzyTerms
        trigramIndex = TrigramIndex(fuzzyTerms.map { it.text })
        phoneticIndex = HashMap<String, MutableList<Int>>().also { index ->
            fuzzyTerms.forEachIndexed { termId, term ->
                term.phoneticCodes.forEach { index.getOrPut(it) { ArrayList() }.add(termId) }
            }
        }.mapValues { it.value.toIntArray() }
    }

    fun search(rawQuery: String, ranking: AirportRanking = AirportRanking.NONE): AirportSearchResult {
        val query = SearchText.normalize(rawQuery)

        if (query.isEmpty()) {
            val all = airports.map { AirportMatch(it, MatchKind.All, 1.0, ranking.priorityOf(it.code)) }
            return AirportSearchResult(query, SearchTier.Blank, all.sortedWith(RANKED))
        }

        // Tier 1: prefix tree.
        val prefixIds = trie.idsWithPrefix(query)
        if (prefixIds.isNotEmpty()) {
            val exactId = codeIndex[query]
            val matches = prefixIds.map { id ->
                val airport = airports[id]
                if (id == exactId) {
                    AirportMatch(airport, MatchKind.ExactCode, EXACT_SCORE, ranking.priorityOf(airport.code))
                } else {
                    AirportMatch(airport, MatchKind.Prefix, PREFIX_SCORE, ranking.priorityOf(airport.code))
                }
            }
            return AirportSearchResult(query, SearchTier.Prefix, matches.sortedWith(RANKED))
        }

        // Tier 2: fuzzy, only because Tier 1 found nothing.
        val fuzzy = fuzzyMatches(query, ranking)
        return AirportSearchResult(query, if (fuzzy.isEmpty()) SearchTier.NoMatch else SearchTier.Fuzzy, fuzzy)
    }

    /** Full phrases the prefix tree can complete [rawQuery] into, e.g. "new" → "new york". */
    fun completions(rawQuery: String, limit: Int = 10): List<String> =
        trie.completions(SearchText.normalize(rawQuery), limit)

    private fun fuzzyMatches(query: String, ranking: AirportRanking): List<AirportMatch> {
        val compact = SearchText.compact(query)
        if (compact.length < config.minFuzzyLength) return emptyList()

        val budget = typoBudget(compact.length)
        val similarities = trigramIndex.similarities(compact)
        val phoneticHits = HashSet<Int>()
        for (code in phoneticCodes(compact)) {
            phoneticIndex[code]?.forEach { termId ->
                // Short codes are too common to trust against a single word ("sitty" vs "city").
                if (code.length >= 3 || terms[termId].isWholeName) phoneticHits += termId
            }
        }

        val bestByAirport = HashMap<Int, Pair<Double, MatchKind>>()
        for (termId in similarities.keys + phoneticHits) {
            val term = terms[termId]
            var score = 0.0
            var kind: MatchKind? = null
            fun consider(candidate: Double, candidateKind: MatchKind) {
                if (candidate > score) {
                    score = candidate
                    kind = candidateKind
                }
            }

            if (budget > 0) {
                val distance = editDistance(compact, term.text, budget)
                if (distance <= budget) consider(TYPO_SCORE - TYPO_PENALTY * distance, MatchKind.Typo)
                if (compact.length >= PARTIAL_TYPO_MIN_LENGTH && isPartialTypo(compact, term.text)) {
                    consider(PARTIAL_TYPO_SCORE, MatchKind.Typo)
                }
            }
            if (termId in phoneticHits) consider(PHONETIC_SCORE, MatchKind.Phonetic)
            val dice = similarities[termId] ?: 0.0
            if (dice >= config.nGramThreshold) consider(NGRAM_WEIGHT * dice, MatchKind.NGram)

            val matchedKind = kind ?: continue
            val previous = bestByAirport[term.airportId]
            if (previous == null || score > previous.first) bestByAirport[term.airportId] = score to matchedKind
        }

        if (bestByAirport.isEmpty()) return emptyList()
        val top = bestByAirport.values.maxOf { it.first }
        return bestByAirport
            .filterValues { it.first >= top - config.fuzzyMargin - EPSILON }
            .map { (id, best) -> AirportMatch(airports[id], best.second, best.first, ranking.priorityOf(airports[id].code)) }
            .sortedWith(RANKED)
    }

    /** "bostn" while typing "boston": the query is within 1 edit of the term's first |q|±1 letters. */
    private fun isPartialTypo(query: String, term: String): Boolean =
        (query.length - 1..query.length + 1).any { length ->
            length in 1..term.length && editDistance(query, term.substring(0, length), 1) <= 1
        }

    private fun typoBudget(length: Int): Int = when {
        length < 4 -> 0
        length <= 7 -> 1
        else -> 2
    }

    private fun phoneticCodes(text: String): Set<String> =
        setOfNotNull(metaphone.doubleMetaphone(text), metaphone.doubleMetaphone(text, alternate = true))
            .filterTo(HashSet()) { it.isNotBlank() }

    private companion object {
        const val EXACT_SCORE = 1.0
        const val PREFIX_SCORE = 0.95
        const val TYPO_SCORE = 0.9
        const val TYPO_PENALTY = 0.05
        const val PARTIAL_TYPO_SCORE = 0.8
        const val PARTIAL_TYPO_MIN_LENGTH = 5
        const val PHONETIC_SCORE = 0.85
        const val NGRAM_WEIGHT = 0.9
        const val EPSILON = 1e-9

        val RANKED: Comparator<AirportMatch> = compareByDescending<AirportMatch> { it.score }
            .thenByDescending { it.priority }
            .thenBy { it.airport.code }
    }
}
