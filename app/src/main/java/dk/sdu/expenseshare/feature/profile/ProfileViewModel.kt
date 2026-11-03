package dk.sdu.expenseshare.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.ProfileUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the latest save request. */
sealed interface SaveStatus {
    data object Idle : SaveStatus

    data object Pending : SaveStatus

    data object Successful : SaveStatus

    data class Failed(val reason: AuthErrorReason) : SaveStatus
}

data class ProfileUiState(
    val email: String?,
    val form: ProfileForm,
    val errors: ProfileErrors = ProfileErrors(),
    val saveStatus: SaveStatus = SaveStatus.Idle
)

/**
 * Edits the signed-in user's profile. [onSaved] is called with the stored row so the rest
 * of the app (e.g. the Home greeting) shows the new values.
 */
class ProfileViewModel(
    private val repository: AuthRepository,
    profile: Profile,
    email: String?,
    private val onSaved: (Profile) -> Unit = {}
) : ViewModel() {
    private val userId = profile.id

    private val _state = MutableStateFlow(ProfileUiState(email = email, form = profile.toForm()))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onFullNameChange(value: String) = updateForm { it.copy(fullName = value) }

    fun onPhoneChange(value: String) = updateForm { it.copy(phone = value) }

    fun onNotificationsChange(enabled: Boolean) = updateForm { it.copy(notificationsEnabled = enabled) }

    private fun updateForm(transform: (ProfileForm) -> ProfileForm) = _state.update {
        // Editing after a save clears the old result so it is not mistaken for the new values.
        val status = if (it.saveStatus == SaveStatus.Pending) it.saveStatus else SaveStatus.Idle
        it.copy(form = transform(it.form), saveStatus = status)
    }

    fun save() {
        val current = _state.value
        // Ignore repeated taps so the same change is not written twice.
        if (current.saveStatus == SaveStatus.Pending) return

        val errors = validateProfile(current.form)
        _state.update { it.copy(errors = errors) }
        if (errors.hasErrors) return

        val update =
            ProfileUpdate(
                fullName = current.form.fullName.trim(),
                phone = normalizePhone(current.form.phone),
                notificationsEnabled = current.form.notificationsEnabled
            )
        _state.update { it.copy(saveStatus = SaveStatus.Pending) }
        viewModelScope.launch {
            try {
                val saved = repository.updateProfile(userId, update)
                _state.update { it.copy(saveStatus = SaveStatus.Successful) }
                onSaved(saved)
            } catch (e: AuthException) {
                // The form keeps the user's input so they can simply press save again.
                _state.update { it.copy(saveStatus = SaveStatus.Failed(e.reason)) }
            }
        }
    }
}

private fun Profile.toForm() = ProfileForm(
    fullName = fullName.orEmpty(),
    phone = phone.orEmpty(),
    notificationsEnabled = notificationsEnabled
)
