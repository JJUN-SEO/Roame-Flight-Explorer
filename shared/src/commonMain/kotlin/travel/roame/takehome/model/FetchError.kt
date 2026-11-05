package travel.roame.takehome.model

/** Why a refresh failed (DOMAIN_RULES R9). The apps show each kind's own copy (UI_SPEC §4). */
sealed class FetchError {
    data object Offline : FetchError()

    data object Timeout : FetchError()

    data class Server(val status: Int) : FetchError()

    data object InvalidData : FetchError()

    data object Unknown : FetchError()
}
