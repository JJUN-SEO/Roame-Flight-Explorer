package travel.roame.takehome.data.remote

import java.io.IOException

/** OkHttp reports refused connections, DNS failures and dropped connections as [IOException]s. */
internal actual fun Throwable.isConnectivityFailure(): Boolean = this is IOException
