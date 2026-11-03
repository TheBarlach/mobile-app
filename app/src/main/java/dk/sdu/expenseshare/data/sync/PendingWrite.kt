package dk.sdu.expenseshare.data.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A change made by the signed-in user that must reach the backend. [id] is generated on the
 * device and sent with every attempt, so the backend can ignore duplicates when a retry repeats
 * a request that already succeeded.
 */
@Serializable
sealed interface PendingWrite {
    val id: String
    val groupId: String

    /** The other member the write is about. */
    val toUserId: String

    /** The signed-in user paid [toUserId] [amount] øre. */
    @Serializable
    @SerialName("payment")
    data class RecordPayment(
        override val id: String,
        override val groupId: String,
        override val toUserId: String,
        val amount: Long
    ) : PendingWrite {
        init {
            require(amount > 0) { "Payment amount must be positive, was $amount" }
        }
    }

    /** Asks [toUserId] to pay the signed-in user back. */
    @Serializable
    @SerialName("reminder")
    data class SendReminder(override val id: String, override val groupId: String, override val toUserId: String) :
        PendingWrite
}

@Serializable
enum class RequestStatus { Pending, Successful, Failed }

@Serializable
data class OutboxEntry(
    val write: PendingWrite,
    val status: RequestStatus = RequestStatus.Pending,
    /** Failed attempts since the write was queued or last retried by the user. */
    val failedAttempts: Int = 0,
    /** False when the server rejected the write; it is then only retried when the user asks. */
    val retryable: Boolean = true,
    /** True once any attempt has failed, also before a restart or a retry by the user. */
    val hadFailure: Boolean = false
) {
    /** Still trying, after at least one failed attempt (usually because the device is offline). */
    val isWaitingToRetry: Boolean get() = status == RequestStatus.Pending && failedAttempts > 0
}

/** A write that reached the backend. [afterFailure] is true if earlier attempts failed. */
data class CompletedWrite(val write: PendingWrite, val afterFailure: Boolean)

/**
 * Exponential backoff with a bounded number of attempts: wait [initialDelay], then twice as
 * long after each further failure, never more than [maxDelay]. After [maxAttempts] failed
 * attempts the write is marked failed until connectivity returns or the user retries.
 */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelay: Duration = 2.seconds,
    val maxDelay: Duration = 30.seconds
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1, was $maxAttempts" }
        require(initialDelay.isPositive() && maxDelay >= initialDelay) { "Invalid delays" }
    }

    /** How long to wait after [failedAttempts] failures (1 or more) before trying again. */
    fun delayAfter(failedAttempts: Int): Duration {
        require(failedAttempts >= 1) { "failedAttempts must be at least 1, was $failedAttempts" }
        val doublings = (failedAttempts - 1).coerceAtMost(MAX_DOUBLINGS)
        return (initialDelay * (1 shl doublings)).coerceAtMost(maxDelay)
    }

    private companion object {
        // Keeps the multiplication far from overflowing; the result is capped by maxDelay anyway.
        const val MAX_DOUBLINGS = 20
    }
}
