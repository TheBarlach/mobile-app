package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.data.dinnerNotification
import dk.sdu.expenseshare.data.joinedNotification
import dk.sdu.expenseshare.domain.AppNotification
import dk.sdu.expenseshare.domain.NotificationType
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRowsTest {
    @Test
    fun `decodes a row as returned by PostgREST`() {
        val json =
            """
            {"id": "n1", "type": "payment_reminder", "group_name": "Roommates", "actor_name": "John",
             "description": null, "amount": 10000, "read_at": null,
             "created_at": "2026-11-03T10:15:30.123456+00:00"}
            """.trimIndent()

        val notification = Json.decodeFromString<NotificationRow>(json).toDomainOrNull()

        assertEquals(
            AppNotification(
                id = "n1",
                type = NotificationType.PaymentReminder,
                groupName = "Roommates",
                actorName = "John",
                description = null,
                amount = 10_000,
                createdAtMillis = Instant.parse("2026-11-03T10:15:30.123456Z").toEpochMilli(),
                isRead = false
            ),
            notification
        )
    }

    @Test
    fun `maps every type the database can create`() {
        val types =
            listOf("expense_added", "payment_reminder", "payment_received", "member_joined", "added_to_group")
                .map { dinnerNotification.copy(type = it).toDomainOrNull()?.type }

        assertEquals(NotificationType.entries.toList(), types)
    }

    @Test
    fun `a row with read_at is read`() {
        assertTrue(joinedNotification.toDomainOrNull()?.isRead == true)
    }

    @Test
    fun `an unknown type from a newer backend is skipped`() {
        assertNull(dinnerNotification.copy(type = "group_deleted").toDomainOrNull())
    }

    @Test
    fun `a blank actor name counts as unknown`() {
        assertNull(dinnerNotification.copy(actorName = " ").toDomainOrNull()?.actorName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unreadable timestamp is rejected`() {
        dinnerNotification.copy(createdAt = "yesterday").toDomainOrNull()
    }
}
