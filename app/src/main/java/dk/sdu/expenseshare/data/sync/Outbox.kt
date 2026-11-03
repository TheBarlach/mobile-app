package dk.sdu.expenseshare.data.sync

import dk.sdu.expenseshare.data.local.TextStore
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Queue of writes that are sent to the backend in the background, in order.
 *
 * - Writes that are not yet sent are saved in [store], so they survive an app restart.
 * - Temporary failures are retried with [retryPolicy]'s backoff. When the attempts run out, the
 *   write is marked failed and the queue pauses. Failed writes are tried again after [syncNow]
 *   (e.g. connectivity returned), an app restart, or [retry].
 * - Writes the server rejected are marked failed and skipped until the user calls [retry].
 * - Every write keeps its id across attempts, so a retry cannot create a duplicate.
 *
 * The queue runs in [scope]; cancel the scope to stop it.
 */
class Outbox(
    private val store: TextStore,
    private val send: suspend (PendingWrite) -> Unit,
    scope: CoroutineScope,
    private val retryPolicy: RetryPolicy = RetryPolicy()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val skipBackoff = Channel<Unit>(Channel.CONFLATED)
    private val requeueRequested = AtomicBoolean(false)

    private val _entries = MutableStateFlow<List<OutboxEntry>>(emptyList())

    /** All writes of this session plus unsent writes from earlier sessions, oldest first. */
    val entries: StateFlow<List<OutboxEntry>> = _entries.asStateFlow()

    private val _completed = MutableSharedFlow<CompletedWrite>(extraBufferCapacity = 64)

    /** Emits each write once it has reached the backend. */
    val completed: SharedFlow<CompletedWrite> = _completed.asSharedFlow()

    init {
        scope.launch {
            restore()
            for (signal in wakeUp) sendPending()
        }
    }

    suspend fun enqueue(write: PendingWrite) {
        restored.await()
        mutex.withLock {
            _entries.update { it + OutboxEntry(write) }
            persist()
        }
        wakeUp.trySend(Unit)
    }

    /** Sends a failed write again, whatever the reason it failed. */
    suspend fun retry(writeId: String) {
        restored.await()
        updateEntries { entry ->
            if (entry.write.id == writeId && entry.status == RequestStatus.Failed) {
                entry.copy(status = RequestStatus.Pending, failedAttempts = 0, retryable = true)
            } else {
                entry
            }
        }
        wakeUp.trySend(Unit)
    }

    /**
     * Tries again now, e.g. because connectivity returned: writes that failed for a temporary
     * reason are queued again and a running backoff delay is cut short.
     */
    fun syncNow() {
        requeueRequested.set(true)
        skipBackoff.trySend(Unit)
        wakeUp.trySend(Unit)
    }

    private suspend fun restore() {
        val saved =
            try {
                store.read()?.let { json.decodeFromString<List<OutboxEntry>>(it) }.orEmpty()
            } catch (_: IOException) {
                emptyList()
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        // Everything that was not rejected by the server gets a fresh set of attempts.
        _entries.value =
            saved.map { if (it.retryable) it.copy(status = RequestStatus.Pending, failedAttempts = 0) else it }
        restored.complete(Unit)
        wakeUp.trySend(Unit)
    }

    private suspend fun sendPending() {
        // A stale "skip" signal must not cut the first backoff of this run short.
        skipBackoff.tryReceive()
        // Writes that ran out of attempts are only tried again when asked to, not whenever
        // another write is queued.
        if (requeueRequested.getAndSet(false)) requeueTemporaryFailures()
        while (true) {
            val next = _entries.value.firstOrNull { it.status == RequestStatus.Pending } ?: return
            val keepGoing = sendWithRetries(next)
            if (!keepGoing) return
        }
    }

    /** Returns false when the queue should pause because the backend is unreachable. */
    private suspend fun sendWithRetries(entry: OutboxEntry): Boolean {
        var failedAttempts = entry.failedAttempts
        var hadFailure = entry.hadFailure
        while (true) {
            try {
                send(entry.write)
                setEntry(entry.write.id) { it.copy(status = RequestStatus.Successful) }
                _completed.emit(CompletedWrite(entry.write, afterFailure = hadFailure))
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAttempts++
                hadFailure = true
                val retryable = (e as? RemoteRequestException)?.retryable ?: false
                when {
                    !retryable -> {
                        setEntry(entry.write.id) {
                            it.copy(
                                status = RequestStatus.Failed,
                                failedAttempts = failedAttempts,
                                retryable = false,
                                hadFailure = true
                            )
                        }
                        return true
                    }
                    failedAttempts >= retryPolicy.maxAttempts -> {
                        setEntry(entry.write.id) {
                            it.copy(status = RequestStatus.Failed, failedAttempts = failedAttempts, hadFailure = true)
                        }
                        return false
                    }
                    else -> {
                        setEntry(entry.write.id) { it.copy(failedAttempts = failedAttempts, hadFailure = true) }
                        withTimeoutOrNull(retryPolicy.delayAfter(failedAttempts)) { skipBackoff.receive() }
                    }
                }
            }
        }
    }

    private suspend fun requeueTemporaryFailures() {
        updateEntries { entry ->
            if (entry.status == RequestStatus.Failed && entry.retryable) {
                entry.copy(status = RequestStatus.Pending, failedAttempts = 0)
            } else {
                entry
            }
        }
    }

    private suspend fun setEntry(writeId: String, transform: (OutboxEntry) -> OutboxEntry) =
        updateEntries { if (it.write.id == writeId) transform(it) else it }

    private suspend fun updateEntries(transform: (OutboxEntry) -> OutboxEntry) = mutex.withLock {
        _entries.update { entries -> entries.map(transform) }
        persist()
    }

    /** Saves the writes that still have to be sent. Must be called with [mutex] held. */
    private suspend fun persist() {
        val unsent = _entries.value.filter { it.status != RequestStatus.Successful }
        try {
            store.write(json.encodeToString(unsent))
        } catch (_: IOException) {
            // The queue keeps working in memory; only a restart before sending would lose the write.
        }
    }
}
