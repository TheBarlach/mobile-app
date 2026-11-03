package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.data.sync.PendingWrite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [BalancesApi] backed by supabase-kt. Writes call the database functions from
 * supabase/migrations/20261103120000_groups_expenses.sql, which ignore a write whose id already
 * exists, so retries are safe.
 */
class SupabaseBalancesApi(private val client: () -> SupabaseClient = { SupabaseProvider.client }) : BalancesApi {

    override suspend fun fetchGroups(): List<GroupRow> = remoteCall {
        client()
            .from("groups")
            .select(Columns.raw(GROUP_COLUMNS)) { order("name", Order.ASCENDING) }
            .decodeList<GroupRow>()
    }

    override suspend fun send(write: PendingWrite) = remoteCall {
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
                        // Shown in the receiver's notification (20261103140000_notifications.sql).
                        write.amount?.let { put("p_amount", it) }
                    }
                )
        }
        Unit
    }
}
