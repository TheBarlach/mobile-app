package dk.sdu.expenseshare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.SignUpResult
import dk.sdu.expenseshare.domain.SignUpInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val form: SignUpInput = SignUpInput(fullName = "", username = "", email = "", password = ""),
    val fieldErrors: SignUpErrors = SignUpErrors(),
    val submitError: AuthErrorReason? = null,
    val isSubmitting: Boolean = false,
    val needsConfirmation: Boolean = false
)

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onFullNameChange(value: String) = updateForm { it.copy(fullName = value) }

    fun onUsernameChange(value: String) = updateForm { it.copy(username = value) }

    fun onEmailChange(value: String) = updateForm { it.copy(email = value) }

    fun onPasswordChange(value: String) = updateForm { it.copy(password = value) }

    private fun updateForm(transform: (SignUpInput) -> SignUpInput) =
        _state.update { it.copy(form = transform(it.form)) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val errors = validateSignUp(current.form)
        _state.update { it.copy(fieldErrors = errors, submitError = null) }
        if (errors.hasErrors) return

        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                // SignedIn is handled by the session state, which shows the app.
                val result = repository.signUp(current.form)
                if (result == SignUpResult.ConfirmationRequired) {
                    _state.update { it.copy(needsConfirmation = true) }
                }
            } catch (e: AuthException) {
                _state.update { it.copy(submitError = e.reason) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
