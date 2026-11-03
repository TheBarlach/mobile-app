package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Expense
import dk.sdu.expenseshare.domain.Member
import dk.sdu.expenseshare.domain.Payment
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BalancesRowsTest {
    /** Shaped like the PostgREST response for [GROUP_COLUMNS]. */
    private val response =
        """
        [{
          "id": "g1",
          "name": "Roommates",
          "group_members": [
            {"user_id": "u1", "profiles": {"full_name": "Anna Hansen", "username": "anna_h"}},
            {"user_id": "u2", "profiles": {"full_name": "  ", "username": "john"}},
            {"user_id": "u3", "profiles": null}
          ],
          "expenses": [{
            "id": "e1", "description": "Electricity", "paid_by": "u2", "amount": 30000,
            "expense_shares": [
              {"user_id": "u1", "amount": 10000},
              {"user_id": "u2", "amount": 10000},
              {"user_id": "u3", "amount": 10000}
            ]
          }],
          "payments": [{"id": "p1", "from_user": "u1", "to_user": "u2", "amount": 5000}]
        }]
        """.trimIndent()

    @Test
    fun `decodes the server response into the domain model`() {
        val group = Json.decodeFromString<List<GroupRow>>(response).single().toDomain()

        assertEquals("Roommates", group.name)
        // Falls back to the username when the full name is blank, and to null without a profile.
        assertEquals(listOf(Member("u1", "Anna Hansen"), Member("u2", "john"), Member("u3", null)), group.members)
        assertEquals(
            listOf(
                Expense("e1", "Electricity", "u2", 30_000, mapOf("u1" to 10_000L, "u2" to 10_000L, "u3" to 10_000L))
            ),
            group.expenses
        )
        assertEquals(listOf(Payment("p1", "u1", "u2", 5_000)), group.payments)
    }

    @Test
    fun `rows that break a domain rule are rejected`() {
        val row =
            GroupRow(
                id = "g",
                name = "Trip",
                members = listOf(GroupMemberRow("u1")),
                expenses = listOf(ExpenseRow("e", "Taxi", paidBy = "someone-else", amount = 0))
            )

        assertThrows(IllegalArgumentException::class.java) { row.toDomain() }
    }
}
