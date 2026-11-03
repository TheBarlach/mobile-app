package dk.sdu.expenseshare.data

import dk.sdu.expenseshare.data.remote.NotificationRow
import dk.sdu.expenseshare.data.remote.NotificationsApi
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import java.io.IOException

/**
 * Notifications backend stand-in. It keeps read state like the real `mark_notifications_read`
 * function: marking is idempotent and only sets `read_at` on unread rows.
 */
class FakeNotificationsApi(var rows: List<NotificationRow> = emptyList()) : NotificationsApi {
    var online = true

    /** Overrides the result of [markRead], e.g. to simulate the server rejecting it. */
    var markReadResult: (suspend (Set<String>) -> Unit)? = null

    val markReadCalls = mutableListOf<Set<String>>()
    var fetchCount = 0

    override suspend fun fetchNotifications(): List<NotificationRow> {
        fetchCount++
        if (!online) throw offline()
        return rows
    }

    override suspend fun markRead(ids: Set<String>) {
        if (!online) throw offline()
        markReadResult?.invoke(ids)
        markReadCalls += ids
        rows = rows.map { if (it.id in ids && it.readAt == null) it.copy(readAt = "2026-11-03T12:00:00Z") else it }
    }

    private fun offline() = RemoteRequestException(retryable = true, message = "offline", cause = IOException())
}

/** Alex added "Dinner" (600 DKK) to Summer Trip. Unread. */
val dinnerNotification =
    NotificationRow(
        id = "n-dinner",
        type = "expense_added",
        groupName = "Summer Trip",
        actorName = "Alex",
        description = "Dinner",
        amount = 60_000,
        createdAt = "2026-11-03T10:00:00.123456+00:00"
    )

/** John reminds the user that they owe him 100 DKK. Unread. */
val reminderNotification =
    NotificationRow(
        id = "n-reminder",
        type = "payment_reminder",
        groupName = "Roommates",
        actorName = "John",
        amount = 10_000,
        createdAt = "2026-11-02T10:00:00+00:00"
    )

/** Sarah joined Summer Trip. Already read. */
val joinedNotification =
    NotificationRow(
        id = "n-joined",
        type = "member_joined",
        groupName = "Summer Trip",
        actorName = "Sarah",
        readAt = "2026-11-01T11:00:00+00:00",
        createdAt = "2026-11-01T10:00:00+00:00"
    )
