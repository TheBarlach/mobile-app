package dk.sdu.expenseshare.data.balances

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Holds the [BalancesRepository] of the signed-in user. It lives as long as the app process, so
 * queued writes keep being retried while the user moves between screens.
 *
 * [create] builds a repository for a user, running in the given scope.
 */
class BalancesRepositories(private val create: (userId: String, scope: CoroutineScope) -> BalancesRepository) {
    private var current: Triple<String, BalancesRepository, CoroutineScope>? = null

    /** The repository for [userId]. Switching user stops the previous user's background work. */
    @Synchronized
    fun forUser(userId: String): BalancesRepository {
        current?.let { (currentUserId, repository) -> if (currentUserId == userId) return repository }
        release()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = create(userId, scope)
        current = Triple(userId, repository, scope)
        return repository
    }

    /** Stops background work, e.g. after sign-out. Unsent writes stay saved for the next sign-in. */
    @Synchronized
    fun release() {
        current?.third?.cancel()
        current = null
    }
}
