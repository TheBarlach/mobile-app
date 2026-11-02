package dk.sdu.expenseshare.feature.profile

import dk.sdu.expenseshare.feature.auth.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun `accepts a Danish number written with spaces`() {
        val errors = validateProfile(ProfileForm(fullName = "John Doe", phone = "+45 12 34 56 78"))

        assertFalse(errors.hasErrors)
        assertEquals("+4512345678", normalizePhone("+45 12 34 56 78"))
    }

    @Test
    fun `phone is optional and a blank number is stored as null`() {
        assertNull(validateProfile(ProfileForm(fullName = "John Doe", phone = "   ")).phone)
        assertNull(normalizePhone("   "))
    }

    @Test
    fun `rejects letters and numbers that are too short or too long`() {
        listOf("12345", "+45 12 ab 56 78", "1234567890123456", "++4512345678").forEach { phone ->
            assertEquals(phone, FieldError.InvalidPhone, validateProfile(ProfileForm("John", phone)).phone)
        }
    }

    @Test
    fun `requires a name`() {
        assertEquals(FieldError.MissingName, validateProfile(ProfileForm(fullName = "  ")).fullName)
    }
}
