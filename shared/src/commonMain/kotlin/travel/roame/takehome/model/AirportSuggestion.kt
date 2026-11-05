package travel.roame.takehome.model

/**
 * One suggestion row, one airport in a hint, or a field's pick (DOMAIN_RULES S13, S14).
 * The apps write the text, for example "Tokyo (HND)", or "Tokyo (all airports)" for a city row.
 */
data class AirportSuggestion(
    val city: String,
    /** In ranked order. One code, except for a city row. */
    val codes: List<String>,
    val isCityRow: Boolean,
)
