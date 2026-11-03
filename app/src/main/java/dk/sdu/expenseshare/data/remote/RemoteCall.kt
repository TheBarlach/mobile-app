package dk.sdu.expenseshare.data.remote

import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

/**
 * Runs a supabase-kt request and translates its exceptions into [RemoteRequestException]s, so
 * callers only need to know whether trying again later can help.
 */
internal suspend fun <T> remoteCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RestException) {
    throw RemoteRequestException(retryable = isRetryableStatus(e.statusCode), message = e.error, cause = e)
} catch (e: IOException) {
    // No connection or a timeout (supabase-kt's HttpRequestException is an IOException).
    throw RemoteRequestException(retryable = true, message = "Network request failed", cause = e)
} catch (e: IllegalStateException) {
    // Thrown by SupabaseProvider when supabase.url / supabase.publishableKey are missing.
    throw RemoteRequestException(retryable = false, message = "Supabase is not configured", cause = e)
}

internal fun isRetryableStatus(statusCode: Int): Boolean = statusCode >= HTTP_SERVER_ERROR ||
    statusCode == HTTP_REQUEST_TIMEOUT ||
    statusCode == HTTP_TOO_MANY_REQUESTS ||
    // An expired token while offline; the Auth plugin refreshes it once the connection is back.
    statusCode == HTTP_UNAUTHORIZED
