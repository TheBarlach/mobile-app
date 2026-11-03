package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.ProfileUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PROFILE_COLUMNS =
    "id, username, full_name, avatar_url, phone, notifications_enabled, created_at, updated_at"

/**
 * [AuthApi] backed by supabase-kt. The client is requested lazily through [client] so a
 * missing configuration surfaces as [AuthErrorReason.NotConfigured] instead of a crash.
 */
class SupabaseAuthApi(private val client: () -> SupabaseClient = { SupabaseProvider.client }) : AuthApi {

    override val sessionState: Flow<SessionState> =
        flow { emitAll(client().auth.sessionStatus) }
            .scan<SessionStatus, SessionState>(SessionState.Loading) { previous, status ->
                when (status) {
                    is SessionStatus.Initializing -> SessionState.Loading
                    is SessionStatus.NotAuthenticated -> SessionState.SignedOut
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user ?: client().auth.currentUserOrNull()
                        if (user == null) SessionState.SignedOut else SessionState.SignedIn(user.id, user.email)
                    }
                    // Keep a signed-in user signed in while offline; supabase-kt retries the refresh.
                    is SessionStatus.RefreshFailure ->
                        previous as? SessionState.SignedIn ?: SessionState.Reconnecting
                }
            }
            .catch { error ->
                if (error is IllegalStateException) emit(SessionState.SignedOut) else throw error
            }

    override suspend fun isUsernameAvailable(username: String): Boolean =
        call(serverFailure = AuthErrorReason.UsernameCheckFailed) {
            client().postgrest
                .rpc("is_username_available", buildJsonObject { put("p_username", username) })
                .decodeAs<Boolean>()
        }

    override suspend fun signUp(email: String, password: String, username: String, fullName: String): SignUpResponse =
        call {
            val auth = client().auth
            // Returns the user only when email confirmation is required; otherwise the session is saved.
            val user =
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data =
                        buildJsonObject {
                            put("username", username)
                            put("full_name", fullName)
                        }
                }
            SignUpResponse(
                hasSession = auth.currentSessionOrNull() != null,
                identityCount = user?.identities?.size
            )
        }

    override suspend fun signIn(email: String, password: String) = call {
        client().auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() = call { client().auth.signOut() }

    override suspend fun fetchProfile(userId: String): Profile? =
        call(serverFailure = AuthErrorReason.ProfileLoadFailed) {
            client()
                .from("profiles")
                .select(Columns.raw(PROFILE_COLUMNS)) {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<Profile>()
        }

    override suspend fun updateProfile(userId: String, update: ProfileUpdate): Profile? =
        call(serverFailure = AuthErrorReason.ProfileSaveFailed) {
            client()
                .from("profiles")
                .update(update) {
                    select(Columns.raw(PROFILE_COLUMNS))
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<Profile>()
        }

    /** Translates client exceptions into [AuthException]s with a readable reason. */
    private suspend fun <T> call(serverFailure: AuthErrorReason? = null, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        throw AuthException(authErrorReasonFor(e.errorCode?.value ?: e.error), e)
    } catch (e: RestException) {
        throw AuthException(serverFailure ?: authErrorReasonFor(e.error), e)
    } catch (e: IOException) {
        throw AuthException(AuthErrorReason.Network, e)
    } catch (e: IllegalStateException) {
        // Thrown by SupabaseProvider when supabase.url / supabase.publishableKey are missing.
        throw AuthException(AuthErrorReason.NotConfigured, e)
    }
}
