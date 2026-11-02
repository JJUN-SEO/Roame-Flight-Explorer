package travel.roame.takehome.data.remote

import java.io.IOException

/** Same as Android, so the tests' OkHttp engine behaves like the shipped one. */
internal actual fun Throwable.isConnectivityFailure(): Boolean = this is IOException
