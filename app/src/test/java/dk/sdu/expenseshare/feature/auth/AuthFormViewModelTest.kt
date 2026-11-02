package dk.sdu.expenseshare.feature.auth

import dk.sdu.expenseshare.MainDispatcherRule
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.FakeAuthApi
import dk.sdu.expenseshare.data.remote.SignUpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val repository = AuthRepository(api)

    @Test
    fun `login shows field errors and sends no request for invalid input`() {
        val viewModel = LoginViewModel(repository)

        viewModel.submit()

        assertEquals(FieldError.InvalidEmail, viewModel.state.value.fieldErrors.email)
        assertEquals(FieldError.MissingPassword, viewModel.state.value.fieldErrors.password)
        assertTrue(api.signInCalls.isEmpty())
    }

    @Test
    fun `login shows a readable error and allows another attempt after a network failure`() {
        api.signInResult = { throw AuthException(AuthErrorReason.Network) }
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChange("anna@example.com")
        viewModel.onPasswordChange("secret123")

        viewModel.submit()

        assertEquals(AuthErrorReason.Network, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.isSubmitting)

        api.signInResult = {}
        viewModel.submit()

        assertNull(viewModel.state.value.submitError)
        assertEquals(2, api.signInCalls.size)
    }

    @Test
    fun `register shows the confirmation message when email confirmation is required`() {
        api.signUpResponse = { SignUpResponse(hasSession = false, identityCount = 1) }
        val viewModel = RegisterViewModel(repository)
        viewModel.onFullNameChange("Anna Hansen")
        viewModel.onUsernameChange("anna_h")
        viewModel.onEmailChange("anna@example.com")
        viewModel.onPasswordChange("secret123")

        viewModel.submit()

        assertTrue(viewModel.state.value.needsConfirmation)
        assertNull(viewModel.state.value.submitError)
    }

    @Test
    fun `register shows taken usernames as an error`() {
        api.usernameAvailable = { false }
        val viewModel = RegisterViewModel(repository)
        viewModel.onFullNameChange("Anna Hansen")
        viewModel.onUsernameChange("anna_h")
        viewModel.onEmailChange("anna@example.com")
        viewModel.onPasswordChange("secret123")

        viewModel.submit()

        assertEquals(AuthErrorReason.UsernameTaken, viewModel.state.value.submitError)
        assertFalse(viewModel.state.value.needsConfirmation)
    }
}
