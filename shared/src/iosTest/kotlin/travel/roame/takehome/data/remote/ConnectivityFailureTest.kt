package travel.roame.takehome.data.remote

import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorBadServerResponse
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import travel.roame.takehome.model.FetchError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Darwin engine, which iOS ships (R9). */
@OptIn(UnsafeNumber::class)
class ConnectivityFailureTest {

    private fun urlError(code: Long, domain: String = NSURLErrorDomain!!) =
        DarwinHttpRequestException(NSError.errorWithDomain(domain, code, null))

    @Test
    fun r9_connectionErrorCodes_areConnectivityFailures() {
        val codes = listOf(
            NSURLErrorCannotFindHost,
            NSURLErrorCannotConnectToHost,
            NSURLErrorNetworkConnectionLost,
            NSURLErrorDNSLookupFailed,
            NSURLErrorNotConnectedToInternet,
        )
        for (code in codes) assertTrue(urlError(code).isConnectivityFailure(), "code $code")
    }

    @Test
    fun r9_otherFailures_areNotConnectivityFailures() {
        assertFalse(urlError(NSURLErrorBadServerResponse).isConnectivityFailure())
        assertFalse(urlError(NSURLErrorCannotConnectToHost, domain = "SomeOtherDomain").isConnectivityFailure())
        assertFalse(IllegalStateException("boom").isConnectivityFailure())
    }

    /** Assumes nothing listens on port 1, the same way a stopped API refuses connections. */
    @Test
    fun r9_realConnectionRefused_isOffline() = runTest {
        val api = KtorFlightsApi(Darwin.create(), "http://127.0.0.1:1")
        assertEquals(FetchResult.Failure(FetchError.Offline), api.fetchFlights())
    }
}
