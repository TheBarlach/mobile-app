package dk.sdu.expenseshare.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.ui.components.FormTextField
import dk.sdu.expenseshare.ui.components.PrimaryButton
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

@Composable
fun LoginScreen(viewModel: LoginViewModel, onNavigateToRegister: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    AuthFormLayout(modifier) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)

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

        PrimaryButton(text = stringResource(R.string.login_submit), onClick = onSubmit, loading = state.isSubmitting)

        TextButton(onClick = onNavigateToRegister) { Text(stringResource(R.string.login_to_register)) }
    }
}

/** Centered, scrollable column shared by the login and registration screens. */
@Composable
internal fun AuthFormLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
internal fun SubmitError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    ExpenseShareTheme {
        LoginContent(
            state = LoginUiState(submitError = AuthErrorReason.InvalidCredentials),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onNavigateToRegister = {}
        )
    }
}
