package dk.sdu.expenseshare.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingWriteTest {
    @Test
    fun `a reminder saved before reminders had an amount still loads`() {
        val saved = """{"type": "reminder", "id": "r1", "groupId": "trip", "toUserId": "alex"}"""

        val write = Json.decodeFromString<PendingWrite>(saved)

        assertEquals(PendingWrite.SendReminder("r1", "trip", "alex", amount = null), write)
    }

    @Test
    fun `a reminder keeps its amount when saved and loaded`() {
        val reminder: PendingWrite = PendingWrite.SendReminder("r1", "trip", "alex", amount = 10_000)

        assertEquals(reminder, Json.decodeFromString<PendingWrite>(Json.encodeToString(reminder)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a reminder amount must be positive`() {
        PendingWrite.SendReminder("r1", "trip", "alex", amount = 0)
    }
}
