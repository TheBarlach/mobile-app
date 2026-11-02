package dk.sdu.expenseshare.feature.auth

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.ui.components.FormTextField
import dk.sdu.expenseshare.ui.components.PrimaryButton
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

@Composable
fun RegisterScreen(viewModel: RegisterViewModel, onNavigateToLogin: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onFullNameChange = viewModel::onFullNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier
    )
}

@Composable
fun RegisterContent(
    state: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    AuthFormLayout(modifier) {
        Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineMedium)

        if (state.needsConfirmation) {
            Text(stringResource(R.string.register_confirm_email), style = MaterialTheme.typography.bodyLarge)
        } else {
            FormTextField(
                label = stringResource(R.string.field_full_name),
                value = state.form.fullName,
                onValueChange = onFullNameChange,
                error = state.fieldErrors.fullName?.message(),
                keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            )
            FormTextField(
                label = stringResource(R.string.field_username),
                value = state.form.username,
                onValueChange = onUsernameChange,
                error = state.fieldErrors.username?.message(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next)
            )
            FormTextField(
                label = stringResource(R.string.field_email),
                value = state.form.email,
                onValueChange = onEmailChange,
                error = state.fieldErrors.email?.message(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            FormTextField(
                label = stringResource(R.string.field_password),
                value = state.form.password,
                onValueChange = onPasswordChange,
                error = state.fieldErrors.password?.message(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() })
            )

            state.submitError?.let { SubmitError(it.message()) }

            PrimaryButton(
                text = stringResource(R.string.register_submit),
                onClick = onSubmit,
                loading = state.isSubmitting
            )
        }

        TextButton(onClick = onNavigateToLogin) { Text(stringResource(R.string.register_to_login)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterContentPreview() {
    ExpenseShareTheme {
        RegisterContent(
            state = RegisterUiState(fieldErrors = SignUpErrors(username = FieldError.InvalidUsername)),
            onFullNameChange = {},
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterConfirmationPreview() {
    ExpenseShareTheme {
        RegisterContent(
            state = RegisterUiState(needsConfirmation = true),
            onFullNameChange = {},
            onUsernameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}
