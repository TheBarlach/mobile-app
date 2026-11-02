package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.ProfileUpdate
import kotlinx.coroutines.flow.Flow

/** Who is signed in, as seen by the backend client. */
sealed interface SessionState {
    /** The stored session is still being read on app start. */
    data object Loading : SessionState

    /** A stored session exists but could not be refreshed, usually because the device is offline. */
    data object Reconnecting : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val userId: String, val email: String?) : SessionState
}

/** Readable reasons for failed auth requests. The UI maps each one to a string resource. */
enum class AuthErrorReason {
    InvalidCredentials,
    EmailNotConfirmed,
    UserAlreadyExists,
    UsernameTaken,
    UsernameCheckFailed,
    WeakPassword,
    RateLimited,
    Network,
    NotConfigured,
    ProfileLoadFailed,
    ProfileNotFound,
    ProfileSaveFailed,
    Unknown
}

class AuthException(val reason: AuthErrorReason, cause: Throwable? = null) : Exception(reason.name, cause)

/** Maps a Supabase Auth error code (e.g. `invalid_credentials`) to a readable reason. */
fun authErrorReasonFor(code: String?): AuthErrorReason = when (code) {
    "invalid_credentials" -> AuthErrorReason.InvalidCredentials
    "email_not_confirmed" -> AuthErrorReason.EmailNotConfirmed
    "user_already_exists", "email_exists" -> AuthErrorReason.UserAlreadyExists
    "weak_password" -> AuthErrorReason.WeakPassword
    "over_request_rate_limit", "over_email_send_rate_limit" -> AuthErrorReason.RateLimited
    else -> AuthErrorReason.Unknown
}

/** Result of creating an auth user. */
data class SignUpResponse(
    /** True when Supabase signed the user in straight away (email confirmation disabled). */
    val hasSession: Boolean,
    /**
     * Number of identities on the returned user. With email confirmation on, Supabase hides an
     * existing account by returning a user with no identities instead of an error.
     */
    val identityCount: Int?
)

/**
 * Thin wrapper around the backend client. It contains no business rules, so
 * [AuthRepository] can be unit tested with a fake implementation.
 *
 * Implementations throw [AuthException] for failed requests.
 */
interface AuthApi {
    val sessionState: Flow<SessionState>

    suspend fun isUsernameAvailable(username: String): Boolean

    suspend fun signUp(email: String, password: String, username: String, fullName: String): SignUpResponse

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()

    suspend fun fetchProfile(userId: String): Profile?

    /** Returns the updated row, or null when no row was updated. */
    suspend fun updateProfile(userId: String, update: ProfileUpdate): Profile?
}
