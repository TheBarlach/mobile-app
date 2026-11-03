package dk.sdu.expenseshare.data.local

import dk.sdu.expenseshare.data.remote.GroupRow
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The groups from the last successful fetch and when they were fetched. */
@Serializable
data class CachedGroups(val groups: List<GroupRow>, @SerialName("fetched_at_millis") val fetchedAtMillis: Long)

/**
 * Keeps the last fetched groups on the device so balances stay visible offline. A broken or
 * unreadable cache is treated as empty rather than crashing the app.
 */
class BalancesCache(private val store: TextStore) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): CachedGroups? = try {
        store.read()?.let { json.decodeFromString<CachedGroups>(it) }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Returns false if the cache could not be written; the app keeps working without it. */
    suspend fun save(groups: CachedGroups): Boolean = try {
        store.write(json.encodeToString(groups))
        true
    } catch (_: IOException) {
        false
    }
}
