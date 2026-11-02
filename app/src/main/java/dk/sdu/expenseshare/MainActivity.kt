package dk.sdu.expenseshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.SupabaseAuthApi
import dk.sdu.expenseshare.ui.navigation.ExpenseShareApp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseShareTheme {
                ExpenseShareApp(
                    authRepository = authRepository,
                    balancesRepositories = (application as ExpenseShareApplication).balancesRepositories
                )
            }
        }
    }

    private companion object {
        // One repository for the app process; the Supabase client itself is a singleton.
        val authRepository by lazy { AuthRepository(SupabaseAuthApi()) }
    }
}
