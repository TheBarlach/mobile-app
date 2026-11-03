package dk.sdu.expenseshare.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Holds one object (e.g. a repository) for the signed-in user. It lives as long as the app
 * process, so background work such as queued writes keeps running while the user moves between
 * screens.
 *
 * [create] builds the object for a user, running its background work in the given scope.
 */
class UserScoped<T : Any>(private val create: (userId: String, scope: CoroutineScope) -> T) {
    private var current: Triple<String, T, CoroutineScope>? = null

    /** The object for [userId]. Switching user stops the previous user's background work. */
    @Synchronized
    fun forUser(userId: String): T {
        current?.let { (currentUserId, value) -> if (currentUserId == userId) return value }
        release()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val value = create(userId, scope)
        current = Triple(userId, value, scope)
        return value
    }

    /** Stops background work, e.g. after sign-out. Data saved on the device is kept. */
    @Synchronized
    fun release() {
        current?.third?.cancel()
        current = null
    }
}
