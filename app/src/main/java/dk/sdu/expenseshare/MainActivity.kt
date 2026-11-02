package dk.sdu.expenseshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dk.sdu.expenseshare.ui.navigation.ExpenseShareApp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseShareTheme {
                ExpenseShareApp()
            }
        }
    }
}
