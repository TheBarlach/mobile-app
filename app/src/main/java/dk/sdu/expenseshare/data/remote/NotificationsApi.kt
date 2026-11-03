package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.AppNotification
import dk.sdu.expenseshare.domain.NotificationType
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Thin wrapper around the backend for notifications. It contains no business rules, so the
 * repository can be tested with a fake.
 *
 * Implementations throw [RemoteRequestException] for failed requests.
 */
interface NotificationsApi {
    /** The signed-in user's newest notifications, newest first. */
    suspend fun fetchNotifications(): List<NotificationRow>

    /** Marks the user's notifications in [ids] as read. Calling it again has no further effect. */
    suspend fun markRead(ids: Set<String>)
}

const val NOTIFICATION_COLUMNS = "id, type, group_name, actor_name, description, amount, read_at, created_at"

/** A `notifications` row (supabase/migrations/20261103140000_notifications.sql). */
@Serializable
data class NotificationRow(
    val id: String,
    val type: String,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    val description: String? = null,
    val amount: Long? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

private val notificationTypes =
    mapOf(
        "expense_added" to NotificationType.ExpenseAdded,
        "payment_reminder" to NotificationType.PaymentReminder,
        "payment_received" to NotificationType.PaymentReceived,
        "member_joined" to NotificationType.MemberJoined,
        "added_to_group" to NotificationType.AddedToGroup
    )

/**
 * Converts the row into the domain model, or returns null for a type this app version does not
 * know, so a newer backend never breaks the list.
 *
 * @throws IllegalArgumentException if the data is invalid, e.g. an unreadable timestamp.
 */
fun NotificationRow.toDomainOrNull(): AppNotification? {
    val type = notificationTypes[type] ?: return null
    val createdAtMillis =
        try {
            OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid created_at of notification $id: $createdAt", e)
        }
    return AppNotification(
        id = id,
        type = type,
        groupName = groupName,
        actorName = actorName?.takeIf { it.isNotBlank() },
        description = description,
        amount = amount,
        createdAtMillis = createdAtMillis,
        isRead = readAt != null
    )
}
