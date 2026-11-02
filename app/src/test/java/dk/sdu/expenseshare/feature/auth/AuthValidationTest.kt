package dk.sdu.expenseshare.feature.auth

import dk.sdu.expenseshare.domain.SignInInput
import dk.sdu.expenseshare.domain.SignUpInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    private val validSignUp =
        SignUpInput(fullName = "Anna Hansen", username = "anna_h", email = "anna@example.com", password = "secret123")

    @Test
    fun `normalizers trim and lowercase email and username`() {
        assertEquals("anna@example.com", normalizeEmail("  Anna@Example.COM "))
        assertEquals("anna_h", normalizeUsername(" Anna_H "))
    }

    @Test
    fun `sign up accepts valid input`() {
        assertFalse(validateSignUp(validSignUp).hasErrors)
    }

    @Test
    fun `sign up accepts a mixed-case username because it is stored lowercase`() {
        assertNull(validateSignUp(validSignUp.copy(username = "Anna_H")).username)
    }

    @Test
    fun `sign up rejects invalid usernames`() {
        listOf("ab", "has space", "æøå", "a".repeat(31)).forEach { username ->
            assertNotNull(username, validateSignUp(validSignUp.copy(username = username)).username)
        }
    }

    @Test
    fun `sign up rejects empty name, invalid email and short password`() {
        val errors =
            validateSignUp(SignUpInput(fullName = "  ", username = "anna", email = "anna@", password = "short"))

        assertEquals(
            SignUpErrors(
                fullName = FieldError.MissingName,
                email = FieldError.InvalidEmail,
                password = FieldError.PasswordTooShort
            ),
            errors
        )
    }

    @Test
    fun `sign up accepts a password of exactly the minimum length`() {
        assertNull(validateSignUp(validSignUp.copy(password = "x".repeat(MIN_PASSWORD_LENGTH))).password)
    }

    @Test
    fun `sign in requires email and password`() {
        assertEquals(
            SignInErrors(email = FieldError.InvalidEmail, password = FieldError.MissingPassword),
            validateSignIn(SignInInput(email = "", password = ""))
        )
    }

    @Test
    fun `sign in accepts valid input`() {
        assertFalse(validateSignIn(SignInInput(email = "anna@example.com", password = "x")).hasErrors)
    }
}
