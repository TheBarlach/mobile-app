package dk.sdu.expenseshare.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitExpenseTest {
    @Test
    fun `splits an expense equally`() {
        assertEquals(listOf(3000L, 3000L, 3000L), splitExpense(9000, 3))
    }

    @Test
    fun `distributes the remainder deterministically to the first participants`() {
        assertEquals(listOf(3334L, 3333L, 3333L), splitExpense(10000, 3))
    }

    @Test
    fun `preserves the total for uneven splits`() {
        for (participants in 1..17) {
            val shares = splitExpense(10007, participants)
            assertEquals(participants, shares.size)
            assertEquals(10007L, shares.sum())
        }
    }

    @Test
    fun `splits zero into zero shares`() {
        assertEquals(listOf(0L, 0L), splitExpense(0, 2))
    }

    @Test
    fun `gives a single participant the full amount`() {
        assertEquals(listOf(4999L), splitExpense(4999, 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero participants`() {
        splitExpense(1000, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative participants`() {
        splitExpense(1000, -2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative amounts`() {
        splitExpense(-100, 2)
    }
}
