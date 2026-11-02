package dk.sdu.expenseshare.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

@Composable
fun ExploreScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.explore_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun ExploreScreenPreview() {
    ExpenseShareTheme { ExploreScreen() }
}
