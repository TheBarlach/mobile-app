package dk.sdu.expenseshare.data.local

import dk.sdu.expenseshare.data.remote.NotificationRow
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The notifications from the last successful fetch, when they were fetched, and the ids the user
 * marked as read that have not reached the backend yet.
 */
@Serializable
data class CachedNotifications(
    val notifications: List<NotificationRow>,
    @SerialName("fetched_at_millis") val fetchedAtMillis: Long,
    @SerialName("pending_read_ids") val pendingReadIds: Set<String> = emptySet()
)

/**
 * Keeps notifications on the device so they stay visible offline and unsent read marks survive a
 * restart. A broken or unreadable cache is treated as empty rather than crashing the app.
 */
class NotificationsCache(private val store: TextStore) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): CachedNotifications? = try {
        store.read()?.let { json.decodeFromString<CachedNotifications>(it) }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Returns false if the cache could not be written; the app keeps working without it. */
    suspend fun save(notifications: CachedNotifications): Boolean = try {
        store.write(json.encodeToString(notifications))
        true
    } catch (_: IOException) {
        false
    }
}
