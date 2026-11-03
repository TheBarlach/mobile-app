package dk.sdu.expenseshare

import android.app.Application
import dk.sdu.expenseshare.data.balances.BalancesRepositories
import dk.sdu.expenseshare.data.balances.BalancesRepository
import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.local.FileTextStore
import dk.sdu.expenseshare.data.remote.SupabaseBalancesApi
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.networkAvailability
import java.io.File

/** Creates the app-wide objects that must outlive a single screen. */
class ExpenseShareApplication : Application() {
    val balancesRepositories: BalancesRepositories by lazy {
        val api = SupabaseBalancesApi()
        val network = networkAvailability(this)
        BalancesRepositories { userId, scope ->
            // One folder per user, so a different account never sees another user's cached data.
            val folder = File(filesDir, "balances/${userId.filter { it.isLetterOrDigit() || it == '-' }}")
            BalancesRepository(
                api = api,
                cache = BalancesCache(FileTextStore(File(folder, "groups.json"))),
                outbox = Outbox(FileTextStore(File(folder, "outbox.json")), api::send, scope),
                scope = scope,
                networkAvailable = network
            )
        }
    }
}
