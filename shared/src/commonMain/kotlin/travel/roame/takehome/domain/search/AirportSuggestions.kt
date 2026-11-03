package travel.roame.takehome.domain.search

import travel.roame.takehome.model.AirportSuggestion

internal const val MAX_SUGGESTIONS = 5

/**
 * S14: one row per matched airport, in ranked order, with a city row directly above the first of
 * two or more matches that share a city. The cap counts city rows. A blank query or no match gives none.
 */
internal fun AirportSearchResult.toSuggestions(limit: Int = MAX_SUGGESTIONS): List<AirportSuggestion> {
    if (tier == SearchTier.Blank || tier == SearchTier.NoMatch) return emptyList()
    val codesByCity = matches.groupBy({ it.airport.city }, { it.airport.code })
    val citiesShown = HashSet<String>()
    val rows = ArrayList<AirportSuggestion>()
    for (match in matches) {
        val city = match.airport.city
        val cityCodes = codesByCity.getValue(city)
        if (cityCodes.size > 1 && citiesShown.add(city)) rows += AirportSuggestion(city, cityCodes, isCityRow = true)
        rows += AirportSuggestion(city, listOf(match.airport.code), isCityRow = false)
    }
    return rows.take(limit)
}

/** S13: every fuzzy match, in ranked order. Nothing for any other tier. */
internal fun AirportSearchResult.toHint(): List<AirportSuggestion> =
    if (tier != SearchTier.Fuzzy) emptyList() else matches.map { AirportSuggestion(it.airport.city, listOf(it.airport.code), isCityRow = false) }
