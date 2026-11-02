package dk.sdu.expenseshare.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.domain.SignInInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val form: SignInInput = SignInInput(email = "", password = ""),
    val fieldErrors: SignInErrors = SignInErrors(),
    val submitError: AuthErrorReason? = null,
    val isSubmitting: Boolean = false
)

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(form = it.form.copy(email = value)) }

    fun onPasswordChange(value: String) = _state.update { it.copy(form = it.form.copy(password = value)) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val errors = validateSignIn(current.form)
        _state.update { it.copy(fieldErrors = errors, submitError = null) }
        if (errors.hasErrors) return

        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                // On success the session state changes and the app is shown.
                repository.signIn(current.form)
            } catch (e: AuthException) {
                _state.update { it.copy(submitError = e.reason) }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}
