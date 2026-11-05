package travel.roame.takehome.data.remote

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSURLErrorCallIsActive
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet

@OptIn(UnsafeNumber::class)
private val CONNECTIVITY_ERROR_CODES = setOf(
    NSURLErrorCannotFindHost,
    NSURLErrorCannotConnectToHost,
    NSURLErrorNetworkConnectionLost,
    NSURLErrorDNSLookupFailed,
    NSURLErrorNotConnectedToInternet,
    NSURLErrorInternationalRoamingOff,
    NSURLErrorCallIsActive,
    NSURLErrorDataNotAllowed,
)

@OptIn(UnsafeNumber::class)
internal actual fun Throwable.isConnectivityFailure(): Boolean {
    val error = (this as? DarwinHttpRequestException)?.origin ?: return false
    return error.domain == NSURLErrorDomain && error.code in CONNECTIVITY_ERROR_CODES
}
