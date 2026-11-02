package travel.roame.takehome.data.remote

/** True when the server couldn't be reached, or the connection was lost partway through (R9, `Offline`). */
internal expect fun Throwable.isConnectivityFailure(): Boolean
