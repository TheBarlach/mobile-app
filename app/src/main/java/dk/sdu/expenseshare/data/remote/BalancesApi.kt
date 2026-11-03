package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.data.sync.PendingWrite

/**
 * A failed backend request. [retryable] is true when trying again later may succeed (no
 * connection, timeout, server error) and false when the server rejected the request.
 */
class RemoteRequestException(val retryable: Boolean, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Thin wrapper around the backend for groups and balances. It contains no business rules, so
 * the repository can be tested with a fake.
 *
 * Implementations throw [RemoteRequestException] for failed requests.
 */
interface BalancesApi {
    /** All groups the signed-in user is a member of. */
    suspend fun fetchGroups(): List<GroupRow>

    /** Sends [write]. Sending the same write twice must have the same effect as sending it once. */
    suspend fun send(write: PendingWrite)
}
