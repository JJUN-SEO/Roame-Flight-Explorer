package travel.roame.takehome.domain.search

/**
 * Query and index text normalisation (DOMAIN_RULES S1).
 *
 * Lowercases, folds Latin accents (`São` → `sao`, `ß` → `ss`), drops apostrophes, turns every other
 * non-alphanumeric character into a separator, and collapses separators into single spaces.
 * The same function is used for queries and for indexed names, so both sides always agree.
 */
internal object SearchText {

    fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        for (ch in raw) {
            if (ch in APOSTROPHES) continue
            val lower = ch.lowercaseChar()
            val folded = FOLDS[lower] ?: lower.toString()
            for (c in folded) {
                if (c in 'a'..'z' || c in '0'..'9') {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.append(c)
                } else {
                    pendingSpace = true
                }
            }
        }
        return out.toString()
    }

    /** The normalised text with spaces removed, used for whole-name fuzzy comparison. */
    fun compact(normalized: String): String = normalized.replace(" ", "")

    private const val APOSTROPHES = "'’ʼ`´"

    private val FOLDS: Map<Char, String> = buildMap {
        fun fold(chars: String, to: String) = chars.forEach { put(it, to) }
        fold("àáâãäåāăą", "a")
        fold("æ", "ae")
        fold("çćĉċč", "c")
        fold("ďđð", "d")
        fold("èéêëēĕėęě", "e")
        fold("ĝğġģ", "g")
        fold("ĥħ", "h")
        fold("ìíîïĩīĭįı", "i")
        fold("ĵ", "j")
        fold("ķ", "k")
        fold("ĺļľŀł", "l")
        fold("ñńņňŉ", "n")
        fold("òóôõöøōŏő", "o")
        fold("œ", "oe")
        fold("ŕŗř", "r")
        fold("śŝşšș", "s")
        fold("ß", "ss")
        fold("ţťŧț", "t")
        fold("þ", "th")
        fold("ùúûüũūŭůűų", "u")
        fold("ŵ", "w")
        fold("ýÿŷ", "y")
        fold("źżž", "z")
    }
}
