package dk.sdu.expenseshare.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

/** Full-width button that shows a progress indicator and ignores clicks while [loading]. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    Button(onClick = onClick, enabled = !loading, modifier = modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            Text(text)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    ExpenseShareTheme { PrimaryButton(text = "Log in", onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonLoadingPreview() {
    ExpenseShareTheme { PrimaryButton(text = "Log in", onClick = {}, loading = true) }
}
