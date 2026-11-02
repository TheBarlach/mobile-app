package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.SignInInput
import dk.sdu.expenseshare.domain.SignUpInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {
    private val api = FakeAuthApi()
    private val repository = AuthRepository(api)

    private val input =
        SignUpInput(
            fullName = "  Anna Hansen ",
            username = " Anna_H ",
            email = " Anna@Example.com ",
            password = "secret123"
        )

    private suspend fun expectFailure(block: suspend () -> Unit): AuthErrorReason {
        try {
            block()
        } catch (e: AuthException) {
            return e.reason
        }
        fail("Expected an AuthException")
        error("unreachable")
    }

    @Test
    fun `sign up sends normalized profile data as metadata for the database trigger`() = runTest {
        assertEquals(SignUpResult.SignedIn, repository.signUp(input))

        assertEquals(listOf("anna_h"), api.checkedUsernames)
        assertEquals(
            listOf(FakeAuthApi.SignUpCall("anna@example.com", "secret123", "anna_h", "Anna Hansen")),
            api.signUpCalls
        )
    }

    @Test
    fun `sign up does not create the user when the username is taken`() = runTest {
        api.usernameAvailable = { false }

        assertEquals(AuthErrorReason.UsernameTaken, expectFailure { repository.signUp(input) })
        assertTrue(api.signUpCalls.isEmpty())
    }

    @Test
    fun `sign up reports when email confirmation is required`() = runTest {
        api.signUpResponse = { SignUpResponse(hasSession = false, identityCount = 1) }

        assertEquals(SignUpResult.ConfirmationRequired, repository.signUp(input))
    }

    @Test
    fun `sign up detects an existing email hidden by Supabase`() = runTest {
        api.signUpResponse = { SignUpResponse(hasSession = false, identityCount = 0) }

        assertEquals(AuthErrorReason.UserAlreadyExists, expectFailure { repository.signUp(input) })
    }

    @Test
    fun `sign in normalizes the email`() = runTest {
        repository.signIn(SignInInput(email = " Anna@Example.com", password = "secret123"))

        assertEquals(listOf("anna@example.com" to "secret123"), api.signInCalls)
    }

    @Test
    fun `sign in passes readable failures through`() = runTest {
        api.signInResult = { throw AuthException(AuthErrorReason.Network) }

        assertEquals(
            AuthErrorReason.Network,
            expectFailure { repository.signIn(SignInInput(email = "a@b.dk", password = "x")) }
        )
    }

    @Test
    fun `fetch profile loads the row for the user`() = runTest {
        val profile = Profile(id = "user-1", username = "anna_h", fullName = "Anna Hansen")
        api.profileResult = { profile }

        assertEquals(profile, repository.fetchProfile("user-1"))
        assertEquals(listOf("user-1"), api.profileRequests)
    }

    @Test
    fun `fetch profile fails clearly when the row is missing`() = runTest {
        api.profileResult = { null }

        assertEquals(AuthErrorReason.ProfileNotFound, expectFailure { repository.fetchProfile("user-1") })
    }

    @Test
    fun `supabase error codes map to readable reasons`() {
        assertEquals(AuthErrorReason.InvalidCredentials, authErrorReasonFor("invalid_credentials"))
        assertEquals(AuthErrorReason.EmailNotConfirmed, authErrorReasonFor("email_not_confirmed"))
        assertEquals(AuthErrorReason.UserAlreadyExists, authErrorReasonFor("user_already_exists"))
        assertEquals(AuthErrorReason.UserAlreadyExists, authErrorReasonFor("email_exists"))
        assertEquals(AuthErrorReason.WeakPassword, authErrorReasonFor("weak_password"))
        assertEquals(AuthErrorReason.RateLimited, authErrorReasonFor("over_request_rate_limit"))
        assertEquals(AuthErrorReason.RateLimited, authErrorReasonFor("over_email_send_rate_limit"))
        assertEquals(AuthErrorReason.Unknown, authErrorReasonFor("something_new"))
        assertEquals(AuthErrorReason.Unknown, authErrorReasonFor(null))
    }
}
