package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.data.sync.PendingWrite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

/**
 * [BalancesApi] backed by supabase-kt. Writes call the database functions from
 * supabase/migrations/20261103120000_groups_expenses.sql, which ignore a write whose id already
 * exists, so retries are safe.
 */
class SupabaseBalancesApi(private val client: () -> SupabaseClient = { SupabaseProvider.client }) : BalancesApi {

    override suspend fun fetchGroups(): List<GroupRow> = call {
        client()
            .from("groups")
            .select(Columns.raw(GROUP_COLUMNS)) { order("name", Order.ASCENDING) }
            .decodeList<GroupRow>()
    }

    override suspend fun send(write: PendingWrite) = call {
        when (write) {
            is PendingWrite.RecordPayment ->
                client().postgrest.rpc(
                    "record_payment",
                    buildJsonObject {
                        put("p_id", write.id)
                        put("p_group_id", write.groupId)
                        put("p_to_user", write.toUserId)
                        put("p_amount", write.amount)
                    }
                )
            is PendingWrite.SendReminder ->
                client().postgrest.rpc(
                    "send_reminder",
                    buildJsonObject {
                        put("p_id", write.id)
                        put("p_group_id", write.groupId)
                        put("p_to_user", write.toUserId)
                    }
                )
        }
        Unit
    }

    /** Translates client exceptions into [RemoteRequestException]s. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RestException) {
        throw RemoteRequestException(retryable = isRetryable(e.statusCode), message = e.error, cause = e)
    } catch (e: IOException) {
        // No connection or a timeout (supabase-kt's HttpRequestException is an IOException).
        throw RemoteRequestException(retryable = true, message = "Network request failed", cause = e)
    } catch (e: IllegalStateException) {
        // Thrown by SupabaseProvider when supabase.url / supabase.publishableKey are missing.
        throw RemoteRequestException(retryable = false, message = "Supabase is not configured", cause = e)
    }

    private fun isRetryable(statusCode: Int): Boolean = statusCode >= HTTP_SERVER_ERROR ||
        statusCode == HTTP_REQUEST_TIMEOUT ||
        statusCode == HTTP_TOO_MANY_REQUESTS ||
        // An expired token while offline; the Auth plugin refreshes it once the connection is back.
        statusCode == HTTP_UNAUTHORIZED
}
