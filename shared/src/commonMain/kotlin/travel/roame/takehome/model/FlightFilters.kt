package travel.roame.takehome.model

/** The user's filter choices (DOMAIN_RULES §3). Null or empty means no limit. Dates are ISO `yyyy-MM-dd`. */
data class FlightFilters(
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val cabin: String? = null,
    val maxPoints: Int? = null,
    val programs: Set<String> = emptySet(),
)

/** What the whole cache offers, so options don't disappear while filtering (F8). Bounds are null when it's empty. */
data class FilterOptions(
    val minDate: String? = null,
    val maxDate: String? = null,
    val minPoints: Int? = null,
    val maxPoints: Int? = null,
    /** By rank (DM1). */
    val cabins: List<String> = emptyList(),
    /** Alphabetical (F6). */
    val programs: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = minDate == null
}

/** A filter that can be cleared on its own (C3). The order here is the order the chips appear in (C2). */
enum class FilterKind { Dates, Cabin, MaxPoints, Programs }

/** One chip's data (C2). The apps format the text. */
sealed class ActiveFilter {
    abstract val kind: FilterKind

    /** A null bound shows as the dataset's own limit (F2). */
    data class Dates(val start: String?, val end: String?) : ActiveFilter() {
        override val kind get() = FilterKind.Dates
    }

    data class Cabin(val cabin: String) : ActiveFilter() {
        override val kind get() = FilterKind.Cabin
    }

    data class MaxPoints(val points: Int) : ActiveFilter() {
        override val kind get() = FilterKind.MaxPoints
    }

    /** Alphabetical. */
    data class Programs(val programs: List<String>) : ActiveFilter() {
        override val kind get() = FilterKind.Programs
    }
}
