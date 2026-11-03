package dk.sdu.expenseshare.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/** How many notifications are fetched and kept on the device. */
private const val NOTIFICATION_LIMIT = 100L

/**
 * [NotificationsApi] backed by supabase-kt. Row level security limits the query to the signed-in
 * user's notifications; marking as read goes through `mark_notifications_read`.
 */
class SupabaseNotificationsApi(private val client: () -> SupabaseClient = { SupabaseProvider.client }) :
    NotificationsApi {

    override suspend fun fetchNotifications(): List<NotificationRow> = remoteCall {
        client()
            .from("notifications")
            .select(Columns.raw(NOTIFICATION_COLUMNS)) {
                order("created_at", Order.DESCENDING)
                limit(NOTIFICATION_LIMIT)
            }.decodeList<NotificationRow>()
    }

    override suspend fun markRead(ids: Set<String>) = remoteCall {
        client().postgrest.rpc(
            "mark_notifications_read",
            buildJsonObject { putJsonArray("p_ids") { ids.forEach { add(it) } } }
        )
        Unit
    }
}
