package dk.sdu.expenseshare

import android.app.Application
import dk.sdu.expenseshare.data.balances.BalancesRepositories
import dk.sdu.expenseshare.data.balances.BalancesRepository
import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.local.FileTextStore
import dk.sdu.expenseshare.data.local.NotificationsCache
import dk.sdu.expenseshare.data.notifications.NotificationsRepositories
import dk.sdu.expenseshare.data.notifications.NotificationsRepository
import dk.sdu.expenseshare.data.remote.SupabaseBalancesApi
import dk.sdu.expenseshare.data.remote.SupabaseNotificationsApi
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.networkAvailability
import java.io.File

/** Creates the app-wide objects that must outlive a single screen. */
class ExpenseShareApplication : Application() {
    private val network by lazy { networkAvailability(this) }

    val balancesRepositories: BalancesRepositories by lazy {
        val api = SupabaseBalancesApi()
        BalancesRepositories { userId, scope ->
            val folder = userFolder("balances", userId)
            BalancesRepository(
                api = api,
                cache = BalancesCache(FileTextStore(File(folder, "groups.json"))),
                outbox = Outbox(FileTextStore(File(folder, "outbox.json")), api::send, scope),
                scope = scope,
                networkAvailable = network
            )
        }
    }

    val notificationsRepositories: NotificationsRepositories by lazy {
        val api = SupabaseNotificationsApi()
        NotificationsRepositories { userId, scope ->
            NotificationsRepository(
                api = api,
                cache = NotificationsCache(
                    FileTextStore(File(userFolder("notifications", userId), "notifications.json"))
                ),
                scope = scope,
                networkAvailable = network
            )
        }
    }

    /** One folder per user, so a different account never sees another user's cached data. */
    private fun userFolder(feature: String, userId: String) =
        File(filesDir, "$feature/${userId.filter { it.isLetterOrDigit() || it == '-' }}")
}
