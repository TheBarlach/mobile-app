package dk.sdu.expenseshare.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

private const val EXAMPLE_AMOUNT_ORE = 10_000L
private const val EXAMPLE_PARTICIPANTS = 3

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val exampleShares = remember { splitExpense(EXAMPLE_AMOUNT_ORE, EXAMPLE_PARTICIPANTS) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_split_example), style = MaterialTheme.typography.titleSmall)
                exampleShares.forEach { share -> Text(formatDkk(share)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ExpenseShareTheme { HomeScreen() }
}
