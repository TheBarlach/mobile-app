package dk.sdu.expenseshare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.SessionState
import dk.sdu.expenseshare.domain.Profile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val session: SessionState = SessionState.Loading,
    val profile: Profile? = null,
    val profileError: AuthErrorReason? = null
)

/**
 * App-wide session state: who is signed in and their profile. The root navigation
 * uses it to choose between the auth screens and the main app.
 */
class SessionViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            repository.sessionState.collect(::onSessionChanged)
        }
    }

    private fun onSessionChanged(session: SessionState) {
        val previousUserId = (_state.value.session as? SessionState.SignedIn)?.userId
        val userId = (session as? SessionState.SignedIn)?.userId

        if (userId != previousUserId) {
            // Never show a previous user's profile.
            profileJob?.cancel()
            _state.update { it.copy(session = session, profile = null, profileError = null) }
            if (userId != null) loadProfile(userId)
        } else {
            _state.update { it.copy(session = session) }
        }
    }

    fun reloadProfile() {
        val userId = (_state.value.session as? SessionState.SignedIn)?.userId ?: return
        loadProfile(userId)
    }

    /** Shows a profile saved elsewhere (e.g. the Profile screen) without reloading it. */
    fun onProfileUpdated(profile: Profile) {
        val userId = (_state.value.session as? SessionState.SignedIn)?.userId
        if (profile.id == userId) _state.update { it.copy(profile = profile, profileError = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            // A failed sign-out (e.g. offline) must not crash the app; the user stays signed in
            // and can press "Log out" again.
            runCatching { repository.signOut() }
        }
    }

    private fun loadProfile(userId: String) {
        profileJob?.cancel()
        profileJob =
            viewModelScope.launch {
                _state.update { it.copy(profileError = null) }
                try {
                    val profile = repository.fetchProfile(userId)
                    _state.update { it.copy(profile = profile, profileError = null) }
                } catch (e: AuthException) {
                    // Keep an already loaded profile visible and show the error next to it.
                    _state.update { it.copy(profileError = e.reason) }
                }
            }
    }
}
