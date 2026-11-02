package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.SignInInput
import dk.sdu.expenseshare.domain.SignUpInput
import dk.sdu.expenseshare.feature.auth.normalizeEmail
import dk.sdu.expenseshare.feature.auth.normalizeUsername
import kotlinx.coroutines.flow.Flow

enum class SignUpResult {
    SignedIn,

    /** Returned when "Confirm email" is enabled in the Supabase project. */
    ConfirmationRequired
}

/**
 * Authentication and profile rules on top of [AuthApi]: input normalisation, the
 * username availability check and detection of existing accounts.
 *
 * All functions throw [AuthException] on failure.
 */
class AuthRepository(private val api: AuthApi) {
    val sessionState: Flow<SessionState> get() = api.sessionState

    /**
     * Creates the auth user. The profile row is created by the `handle_new_user` database
     * trigger from the metadata passed here, so it is stored even when email confirmation
     * means there is no session yet.
     */
    suspend fun signUp(input: SignUpInput): SignUpResult {
        val username = normalizeUsername(input.username)
        if (!api.isUsernameAvailable(username)) {
            throw AuthException(AuthErrorReason.UsernameTaken)
        }

        val response =
            api.signUp(
                email = normalizeEmail(input.email),
                password = input.password,
                username = username,
                fullName = input.fullName.trim()
            )

        if (response.identityCount == 0) {
            throw AuthException(AuthErrorReason.UserAlreadyExists)
        }
        return if (response.hasSession) SignUpResult.SignedIn else SignUpResult.ConfirmationRequired
    }

    suspend fun signIn(input: SignInInput) {
        api.signIn(email = normalizeEmail(input.email), password = input.password)
    }

    suspend fun signOut() {
        api.signOut()
    }

    suspend fun fetchProfile(userId: String): Profile =
        api.fetchProfile(userId) ?: throw AuthException(AuthErrorReason.ProfileNotFound)
}
