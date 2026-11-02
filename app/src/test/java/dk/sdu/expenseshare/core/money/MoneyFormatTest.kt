package dk.sdu.expenseshare.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test
    fun `formats whole and fractional kroner`() {
        assertEquals("33.34 DKK", formatDkk(3334))
        assertEquals("100.00 DKK", formatDkk(10000))
    }

    @Test
    fun `pads single-digit ore`() {
        assertEquals("0.05 DKK", formatDkk(5))
    }

    @Test
    fun `formats negative amounts`() {
        assertEquals("-12.50 DKK", formatDkk(-1250))
    }

    @Test
    fun `short format leaves out zero ore`() {
        assertEquals("150 DKK", formatDkkShort(15000))
        assertEquals("150.50 DKK", formatDkkShort(15050))
        assertEquals("-100 DKK", formatDkkShort(-10000))
    }

    @Test
    fun `signed format shows the direction of a balance`() {
        assertEquals("+ 150 DKK", formatSignedDkk(15000))
        assertEquals("- 33.34 DKK", formatSignedDkk(-3334))
        assertEquals("0 DKK", formatSignedDkk(0))
    }
}
