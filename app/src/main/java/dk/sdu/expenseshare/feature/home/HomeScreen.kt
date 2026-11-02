package dk.sdu.expenseshare.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.core.money.formatDkk
import dk.sdu.expenseshare.core.money.splitExpense
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.feature.auth.message
import dk.sdu.expenseshare.ui.components.PrimaryButton
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

private const val EXAMPLE_AMOUNT_ORE = 10_000L
private const val EXAMPLE_PARTICIPANTS = 3

@Composable
fun HomeScreen(
    email: String?,
    profile: Profile?,
    profileError: AuthErrorReason?,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exampleShares = remember { splitExpense(EXAMPLE_AMOUNT_ORE, EXAMPLE_PARTICIPANTS) }
    val displayName = profile?.fullName ?: profile?.username ?: email

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text =
            if (displayName != null) {
                stringResource(R.string.home_welcome_name, displayName)
            } else {
                stringResource(R.string.home_welcome)
            },
            style = MaterialTheme.typography.headlineMedium
        )

        ProfileCard(email = email, profile = profile, profileError = profileError, onRetry = onRetryProfile)

        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_split_example), style = MaterialTheme.typography.titleSmall)
                exampleShares.forEach { share -> Text(formatDkk(share)) }
            }
        }

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_log_out))
        }
    }
}

@Composable
private fun ProfileCard(
    email: String?,
    profile: Profile?,
    profileError: AuthErrorReason?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missing = stringResource(R.string.profile_missing_value)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile != null) {
                ProfileRow(stringResource(R.string.profile_name), profile.fullName ?: missing)
                ProfileRow(
                    stringResource(R.string.profile_username),
                    profile.username?.let { stringResource(R.string.profile_username_value, it) } ?: missing
                )
                ProfileRow(stringResource(R.string.profile_email), email ?: missing)
            }
            when {
                profileError != null -> {
                    Text(
                        profileError.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PrimaryButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
                }
                profile == null -> Text(
                    stringResource(R.string.profile_loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ExpenseShareTheme {
        HomeScreen(
            email = "anna@example.com",
            profile = Profile(id = "1", username = "anna_h", fullName = "Anna Hansen"),
            profileError = null,
            onRetryProfile = {},
            onSignOut = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenProfileErrorPreview() {
    ExpenseShareTheme {
        HomeScreen(
            email = "anna@example.com",
            profile = null,
            profileError = AuthErrorReason.Network,
            onRetryProfile = {},
            onSignOut = {}
        )
    }
}
