package dk.sdu.expenseshare.feature.auth

import dk.sdu.expenseshare.domain.SignInInput
import dk.sdu.expenseshare.domain.SignUpInput

const val MIN_PASSWORD_LENGTH = 8

// Must match the profiles_username_format constraint in the database.
private val USERNAME_PATTERN = Regex("^[a-z0-9_]{3,30}$")
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Validation problems for a single form field. The UI maps each value to a string resource. */
enum class FieldError {
    InvalidEmail,
    MissingPassword,
    MissingName,
    InvalidUsername,
    PasswordTooShort
}

data class SignInErrors(val email: FieldError? = null, val password: FieldError? = null) {
    val hasErrors: Boolean get() = email != null || password != null
}

data class SignUpErrors(
    val fullName: FieldError? = null,
    val username: FieldError? = null,
    val email: FieldError? = null,
    val password: FieldError? = null
) {
    val hasErrors: Boolean get() = listOf(fullName, username, email, password).any { it != null }
}

fun normalizeEmail(email: String): String = email.trim().lowercase()

fun normalizeUsername(username: String): String = username.trim().lowercase()

private fun isValidEmail(email: String) = EMAIL_PATTERN.matches(normalizeEmail(email))

fun validateSignIn(input: SignInInput): SignInErrors = SignInErrors(
    email = if (isValidEmail(input.email)) null else FieldError.InvalidEmail,
    password = if (input.password.isEmpty()) FieldError.MissingPassword else null
)

fun validateSignUp(input: SignUpInput): SignUpErrors = SignUpErrors(
    fullName = if (input.fullName.isBlank()) FieldError.MissingName else null,
    username = if (USERNAME_PATTERN.matches(normalizeUsername(input.username))) null else FieldError.InvalidUsername,
    email = if (isValidEmail(input.email)) null else FieldError.InvalidEmail,
    password = if (input.password.length < MIN_PASSWORD_LENGTH) FieldError.PasswordTooShort else null
)
