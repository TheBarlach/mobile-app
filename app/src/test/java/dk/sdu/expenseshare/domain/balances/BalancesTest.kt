package dk.sdu.expenseshare.domain.balances

import dk.sdu.expenseshare.core.money.splitExpense
import dk.sdu.expenseshare.domain.Expense
import dk.sdu.expenseshare.domain.Group
import dk.sdu.expenseshare.domain.Member
import dk.sdu.expenseshare.domain.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancesTest {
    private val me = Member("me", "Me")
    private val alex = Member("alex", "Alex")
    private val sarah = Member("sarah", "Sarah")
    private val john = Member("john", "John")

    private fun equalExpense(id: String, paidBy: Member, amount: Long, participants: List<Member>): Expense {
        val shares = splitExpense(amount, participants.size)
        return Expense(id, id, paidBy.id, amount, participants.map { it.id }.zip(shares).toMap())
    }

    @Test
    fun `net balance is what a member paid minus their share`() {
        val group =
            Group("g", "Trip", listOf(me, alex, sarah), listOf(equalExpense("e", me, 30_000, listOf(me, alex, sarah))))

        assertEquals(mapOf("me" to 20_000L, "alex" to -10_000L, "sarah" to -10_000L), netBalances(group))
    }

    @Test
    fun `payments reduce what the payer owes`() {
        val group =
            Group(
                "g",
                "Trip",
                listOf(me, alex),
                expenses = listOf(equalExpense("e", me, 20_000, listOf(me, alex))),
                payments = listOf(Payment("p", fromMemberId = "alex", toMemberId = "me", amount = 4_000))
            )

        assertEquals(mapOf("me" to 6_000L, "alex" to -6_000L), netBalances(group))
    }

    @Test
    fun `uneven splits keep net balances at zero`() {
        val group =
            Group(
                "g",
                "Trip",
                listOf(me, alex, sarah),
                listOf(equalExpense("e", alex, 10_000, listOf(me, alex, sarah)))
            )

        val balances = netBalances(group)

        assertEquals(0L, balances.values.sum())
        assertEquals(mapOf("me" to -3_334L, "alex" to 6_667L, "sarah" to -3_333L), balances)
    }

    @Test
    fun `a chain of debts is simplified to one transfer`() {
        // Sarah owes Alex 50 and Alex owes me 50: Sarah can pay me directly.
        val transfers = simplifyDebts(mapOf("me" to 5_000L, "alex" to 0L, "sarah" to -5_000L))

        assertEquals(listOf(Transfer("sarah", "me", 5_000)), transfers)
    }

    @Test
    fun `simplified transfers settle every balance`() {
        val balances = mapOf("a" to 7_000L, "b" to 2_500L, "c" to -4_000L, "d" to -3_500L, "e" to -2_000L)

        val transfers = simplifyDebts(balances)

        val settled = balances.toMutableMap()
        transfers.forEach {
            assertTrue(it.amount > 0)
            settled[it.fromMemberId] = settled.getValue(it.fromMemberId) + it.amount
            settled[it.toMemberId] = settled.getValue(it.toMemberId) - it.amount
        }
        assertTrue(settled.values.all { it == 0L })
        assertTrue(transfers.size <= balances.size - 1)
    }

    @Test
    fun `ties are broken by member id so the result is deterministic`() {
        val transfers = simplifyDebts(mapOf("b" to 100L, "a" to 100L, "d" to -100L, "c" to -100L))

        assertEquals(listOf(Transfer("c", "a", 100), Transfer("d", "b", 100)), transfers)
    }

    @Test
    fun `balances that do not add up to zero are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { simplifyDebts(mapOf("a" to 100L, "b" to -50L)) }
    }

    @Test
    fun `everyone settled gives no transfers`() {
        assertEquals(emptyList<Transfer>(), simplifyDebts(mapOf("a" to 0L, "b" to 0L)))
    }

    @Test
    fun `summary shows who owes the user and whom the user owes`() {
        val trip =
            Group(
                "trip",
                "Summer Trip",
                listOf(me, alex, sarah),
                listOf(
                    equalExpense("groceries", me, 30_000, listOf(me, alex, sarah)),
                    equalExpense("museum", me, 10_000, listOf(me, sarah))
                )
            )
        val roommates =
            Group(
                "roommates",
                "Roommates",
                listOf(me, john),
                listOf(equalExpense("power", john, 20_000, listOf(me, john)))
            )

        val summary = userBalanceSummary(listOf(trip, roommates), "me")

        assertEquals(15_000L, summary.totalBalance)
        assertEquals(
            listOf(
                GroupBalance(
                    "trip",
                    "Summer Trip",
                    listOf(
                        UserDebt("sarah", "Sarah", 15_000, DebtDirection.OwesYou),
                        UserDebt("alex", "Alex", 10_000, DebtDirection.OwesYou)
                    )
                ),
                GroupBalance("roommates", "Roommates", listOf(UserDebt("john", "John", 10_000, DebtDirection.YouOwe)))
            ),
            summary.groups
        )
    }

    @Test
    fun `debts between other members are not shown to the user`() {
        val group =
            Group("g", "Trip", listOf(me, alex, sarah), listOf(equalExpense("e", alex, 10_000, listOf(alex, sarah))))

        val summary = userBalanceSummary(listOf(group), "me")

        assertEquals(0L, summary.totalBalance)
        assertEquals(emptyList<UserDebt>(), summary.groups.single().debts)
    }

    @Test
    fun `groups the user is not in are skipped`() {
        val group =
            Group("g", "Other", listOf(alex, sarah), listOf(equalExpense("e", alex, 10_000, listOf(alex, sarah))))

        assertEquals(BalanceSummary(0, emptyList()), userBalanceSummary(listOf(group), "me"))
    }

    @Test
    fun `expenses whose shares do not match the amount are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Expense("e", "Dinner", "me", 10_000, mapOf("me" to 5_000L, "alex" to 4_000L))
        }
    }

    @Test
    fun `negative amounts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Expense("e", "Dinner", "me", -100, mapOf("me" to -100L)) }
        assertThrows(IllegalArgumentException::class.java) { Payment("p", "alex", "me", 0) }
    }

    @Test
    fun `expenses involving people outside the group are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Group("g", "Trip", listOf(me), listOf(equalExpense("e", me, 10_000, listOf(me, alex))))
        }
    }
}
