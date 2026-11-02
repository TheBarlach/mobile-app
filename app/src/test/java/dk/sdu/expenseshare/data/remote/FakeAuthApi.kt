package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.ProfileUpdate
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AuthApi] that records calls and lets each test choose the responses. */
class FakeAuthApi : AuthApi {
    data class SignUpCall(val email: String, val password: String, val username: String, val fullName: String)

    override val sessionState = MutableStateFlow<SessionState>(SessionState.Loading)

    var usernameAvailable: () -> Boolean = { true }
    var signUpResponse: () -> SignUpResponse = { SignUpResponse(hasSession = true, identityCount = 1) }
    var signInResult: () -> Unit = {}
    var profileResult: (String) -> Profile? = { Profile(id = it) }

    var updateProfileResult: suspend (String, ProfileUpdate) -> Profile? = { id, update ->
        Profile(
            id = id,
            fullName = update.fullName,
            phone = update.phone,
            notificationsEnabled = update.notificationsEnabled
        )
    }

    val checkedUsernames = mutableListOf<String>()
    val signUpCalls = mutableListOf<SignUpCall>()
    val signInCalls = mutableListOf<Pair<String, String>>()
    val profileRequests = mutableListOf<String>()
    val profileUpdates = mutableListOf<Pair<String, ProfileUpdate>>()
    var signOutCalls = 0

    override suspend fun isUsernameAvailable(username: String): Boolean {
        checkedUsernames += username
        return usernameAvailable()
    }

    override suspend fun signUp(email: String, password: String, username: String, fullName: String): SignUpResponse {
        signUpCalls += SignUpCall(email, password, username, fullName)
        return signUpResponse()
    }

    override suspend fun signIn(email: String, password: String) {
        signInCalls += email to password
        signInResult()
    }

    override suspend fun signOut() {
        signOutCalls++
        sessionState.value = SessionState.SignedOut
    }

    override suspend fun fetchProfile(userId: String): Profile? {
        profileRequests += userId
        return profileResult(userId)
    }

    override suspend fun updateProfile(userId: String, update: ProfileUpdate): Profile? {
        profileUpdates += userId to update
        return updateProfileResult(userId, update)
    }
}
