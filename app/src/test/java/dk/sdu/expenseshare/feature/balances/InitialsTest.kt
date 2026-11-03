package dk.sdu.expenseshare.feature.balances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialsTest {
    @Test
    fun `uses first and last name`() {
        assertEquals("AH", initialsOf("Anna Marie Hansen"))
    }

    @Test
    fun `uses one letter for a single name`() {
        assertEquals("J", initialsOf("  john "))
    }

    @Test
    fun `returns null without a name`() {
        assertNull(initialsOf(null))
        assertNull(initialsOf("   "))
    }
}
