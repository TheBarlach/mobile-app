package dk.sdu.expenseshare.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.feature.auth.FieldError
import dk.sdu.expenseshare.feature.auth.message
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onFullNameChange = viewModel::onFullNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onNotificationsChange = viewModel::onNotificationsChange,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    onFullNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val saving = state.saveStatus == SaveStatus.Pending

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.profile_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(40.dp))
        ProfileHeader(name = state.form.fullName, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledField(
                label = stringResource(R.string.profile_name),
                value = state.form.fullName,
                onValueChange = onFullNameChange,
                error = state.errors.fullName,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            LabeledField(
                label = stringResource(R.string.profile_email),
                value = state.email.orEmpty(),
                onValueChange = {},
                readOnly = true,
                supportingText = stringResource(R.string.profile_email_hint)
            )
            LabeledField(
                label = stringResource(R.string.profile_phone),
                value = state.form.phone,
                onValueChange = onPhoneChange,
                error = state.errors.phone,
                enabled = !saving,
                placeholder = stringResource(R.string.profile_phone_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.profile_notifications),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.form.notificationsEnabled,
                    onCheckedChange = onNotificationsChange,
                    enabled = !saving
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        SaveButton(saving = saving, onClick = onSave)
        SaveStatusMessage(status = state.saveStatus, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Round avatar with the user's initials above the "My Profile" heading. */
@Composable
private fun ProfileHeader(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.profile_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Bold label above an outlined field, as in the profile design. */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: FieldError? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val errorText = error?.message()
    val helper = errorText ?: supportingText
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            isError = errorText != null,
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = helper?.let { { Text(it) } },
            shape = MaterialTheme.shapes.extraSmall,
            keyboardOptions = keyboardOptions
        )
    }
}

@Composable
private fun SaveButton(saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !saving,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            Text(stringResource(R.string.profile_save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SaveStatusMessage(status: SaveStatus, modifier: Modifier = Modifier) {
    when (status) {
        SaveStatus.Successful -> Text(
            stringResource(R.string.profile_saved),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        is SaveStatus.Failed -> Text(
            status.reason.message(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        SaveStatus.Idle, SaveStatus.Pending -> Unit
    }
}

private val previewState = ProfileUiState(
    email = "johndoe@test.com",
    form = ProfileForm(fullName = "John Doe", phone = "+45 12 34 56 78", notificationsEnabled = true)
)

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    ExpenseShareTheme {
        ProfileContent(previewState, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentErrorPreview() {
    ExpenseShareTheme {
        ProfileContent(
            previewState.copy(
                form = previewState.form.copy(phone = "12"),
                errors = ProfileErrors(phone = FieldError.InvalidPhone),
                saveStatus = SaveStatus.Failed(AuthErrorReason.Network)
            ),
            {},
            {},
            {},
            {}
        )
    }
}
