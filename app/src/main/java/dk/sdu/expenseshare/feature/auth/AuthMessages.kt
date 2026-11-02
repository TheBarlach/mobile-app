package dk.sdu.expenseshare.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.data.remote.AuthErrorReason

@Composable
fun FieldError.message(): String = when (this) {
    FieldError.InvalidEmail -> stringResource(R.string.error_invalid_email)
    FieldError.MissingPassword -> stringResource(R.string.error_missing_password)
    FieldError.MissingName -> stringResource(R.string.error_missing_name)
    FieldError.InvalidUsername -> stringResource(R.string.error_invalid_username)
    FieldError.PasswordTooShort -> stringResource(R.string.error_password_too_short, MIN_PASSWORD_LENGTH)
}

@Composable
fun AuthErrorReason.message(): String = stringResource(
    when (this) {
        AuthErrorReason.InvalidCredentials -> R.string.error_invalid_credentials
        AuthErrorReason.EmailNotConfirmed -> R.string.error_email_not_confirmed
        AuthErrorReason.UserAlreadyExists -> R.string.error_user_already_exists
        AuthErrorReason.UsernameTaken -> R.string.error_username_taken
        AuthErrorReason.UsernameCheckFailed -> R.string.error_username_check_failed
        AuthErrorReason.WeakPassword -> R.string.error_weak_password
        AuthErrorReason.RateLimited -> R.string.error_rate_limited
        AuthErrorReason.Network -> R.string.error_network
        AuthErrorReason.NotConfigured -> R.string.error_not_configured
        AuthErrorReason.ProfileLoadFailed -> R.string.error_profile_load_failed
        AuthErrorReason.ProfileNotFound -> R.string.error_profile_not_found
        AuthErrorReason.Unknown -> R.string.error_unknown
    }
)
