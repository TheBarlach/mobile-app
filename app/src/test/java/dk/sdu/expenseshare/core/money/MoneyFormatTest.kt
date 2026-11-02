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
}
