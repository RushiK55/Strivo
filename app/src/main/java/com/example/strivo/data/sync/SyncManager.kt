package com.example.strivo.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.strivo.data.db.StrivoDatabase
import com.example.strivo.data.prefs.UserPrefs
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SyncStatus(
    /** Rows changed on this phone that have not reached the cloud yet. */
    val pendingChanges: Int = 0,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncMillis: Long? = null,
    /** A short reason the last attempt failed, or null. */
    val error: String? = null,
)

/**
 * Keeps one account's data in Cloud Firestore and on the phone.
 *
 * The phone's SQLite database is what the app reads and writes, so everything works offline. Every change is noted
 * in a log by the database itself; whenever the phone is online the log is uploaded, then the cloud is read back to
 * restore anything this phone lacks (a new phone, a reinstall) and to pick up changes made elsewhere.
 * Firestore's own offline cache is switched off: the local database already plays that part, and
 * keeping two queues would only duplicate writes.
 */
class SyncManager(
    private val context: Context,
    private val uid: String,
    private val db: StrivoDatabase,
    private val prefs: UserPrefs,
    private val onCloudDataApplied: () -> Unit,
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    private val mutex = Mutex()
    private val wake = Channel<Boolean>(Channel.CONFLATED) // true = also read the cloud
    private var job: Job? = null
    private var failures = 0
    private var retryNotBefore = 0L

    private val firestore: FirebaseFirestore
        get() = firestoreInstance()

    private fun userDoc(): DocumentReference = firestore.collection("users").document(uid)

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            publish(error = null) // the number of changes waiting, read off the main thread
            // A poll finds changes the database logged since the last look; it costs one small local query.
            launch {
                while (true) {
                    delay(POLL_MILLIS)
                    wake.trySend(false)
                }
            }
            var pullNext = true
            for (alsoPull in wake) {
                pullNext = pullNext || alsoPull
                if (!isOnline()) {
                    publish(error = null)
                    continue
                }
                if (System.currentTimeMillis() < retryNotBefore && !alsoPull) continue
                if (!pullNext && db.pendingChangeCount() == 0 && !prefs.profileNeedsUpload()) continue
                val succeeded = syncOnce(pull = pullNext)
                if (succeeded) pullNext = false
            }
        }
        requestSync(pull = true)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Asks for a sync as soon as possible; [pull] also reads the cloud. Resets the back-off after failures. */
    fun requestSync(pull: Boolean = false) {
        if (pull) retryNotBefore = 0L
        wake.trySend(pull)
    }

    /** One pass: upload what changed here, then read the cloud. Returns whether it all worked. */
    private suspend fun syncOnce(pull: Boolean): Boolean = mutex.withLock {
        _status.update { it.copy(isSyncing = true) }
        try {
            push()
            if (pull) pullAll()
            failures = 0
            retryNotBefore = 0L
            publish(error = null, syncedNow = true)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures++
            // Wait longer after each failure: 15 s, 30 s, 1 min ... up to 10 min.
            retryNotBefore = System.currentTimeMillis() + minOf(POLL_MILLIS shl minOf(failures, 6), 10 * 60_000L)
            publish(error = describe(e))
            false
        }
    }

    // --- Phone to cloud ---

    private suspend fun push() {
        val changes = db.pendingChanges()
        if (changes.isNotEmpty()) {
            val specs = SyncTables.associateBy { it.table }
            val rows = SyncPlanner.collapse(changes).entries.toList()
            rows.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = firestore.batch()
                val logIds = mutableListOf<Long>()
                chunk.forEach { (key, ids) ->
                    val spec = specs[key.first] ?: return@forEach
                    val ref = userDoc().collection(spec.collection).document(key.second)
                    val row = db.readRowAsMap(spec, key.second)
                    // The row still exists: upload its latest version. It is gone: delete it in the cloud too.
                    if (row != null) batch.set(ref, row) else batch.delete(ref)
                    logIds += ids
                }
                withTimeout(WRITE_TIMEOUT_MILLIS) { batch.commit().await() }
                // Only the entries that were uploaded; a change made meanwhile has a newer entry and stays queued.
                db.clearChanges(logIds)
            }
        }
        if (prefs.profileNeedsUpload()) {
            prefs.exportProfile()?.let { profile ->
                withTimeout(WRITE_TIMEOUT_MILLIS) { userDoc().collection("meta").document("profile").set(profile).await() }
                prefs.markProfileUploaded()
            }
        }
    }

    // --- Cloud to phone ---

    private suspend fun pullAll() {
        var applied = false
        SyncTables.forEach { spec ->
            val snapshot = withTimeout(READ_TIMEOUT_MILLIS) {
                userDoc().collection(spec.collection).get(Source.SERVER).await()
            }
            val remote = snapshot.documents.associate { it.id to (it.data ?: emptyMap()) }
            val plan = SyncPlanner.planPull(
                remote = remote.keys,
                local = db.localRowIds(spec),
                pending = db.pendingRowIds(spec.table),
            )
            if (plan.upsert.isNotEmpty() || plan.deleteLocal.isNotEmpty()) {
                db.applyRemote(spec, plan.upsert.mapNotNull { remote[it] }, plan.deleteLocal)
                applied = true
            }
            if (plan.requeue.isNotEmpty()) {
                db.enqueueRows(spec.table, plan.requeue)
                wake.trySend(false)
            }
        }
        if (pullProfile()) applied = true
        if (applied) onCloudDataApplied()
    }

    /** Takes the cloud's profile when it is newer than this phone's and this phone has no profile change of its own waiting. */
    private suspend fun pullProfile(): Boolean {
        if (prefs.profileNeedsUpload() && prefs.profileUpdatedAt() > 0) return false
        val remote = withTimeout(READ_TIMEOUT_MILLIS) {
            userDoc().collection("meta").document("profile").get(Source.SERVER).await()
        }.data ?: return false
        val remoteUpdated = (remote["updatedAt"] as? Number)?.toLong() ?: 0L
        if (remoteUpdated <= prefs.profileUpdatedAt() && prefs.getProfile().isComplete) return false
        prefs.importProfile(remote)
        return true
    }

    /**
     * For a freshly signed-in account: fetches its profile from the cloud if this phone has none, so someone who
     * signs in on a new phone is not asked to fill it in again. Gives up quietly after [timeoutMillis].
     */
    suspend fun restoreProfile(timeoutMillis: Long): Boolean {
        if (prefs.getProfile().isComplete || !isOnline()) return false
        return try {
            withTimeoutOrNull(timeoutMillis) { mutex.withLock { pullProfile() } } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    // --- Helpers ---

    private fun publish(error: String?, syncedNow: Boolean = false) {
        _status.update {
            it.copy(
                pendingChanges = db.pendingChangeCount(),
                isOnline = isOnline(),
                isSyncing = false,
                lastSyncMillis = if (syncedNow) System.currentTimeMillis() else it.lastSyncMillis,
                error = error,
            )
        }
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun describe(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "The cloud database refused access. Check that Firestore is enabled and its rules allow signed-in users."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Could not reach the cloud."
        e is kotlinx.coroutines.TimeoutCancellationException -> "The cloud did not answer in time."
        else -> e.localizedMessage ?: "Sync failed."
    }

    private companion object {
        const val POLL_MILLIS = 15_000L
        const val BATCH_SIZE = 400 // Firestore allows 500 writes per batch
        const val WRITE_TIMEOUT_MILLIS = 30_000L
        const val READ_TIMEOUT_MILLIS = 30_000L

        @Volatile
        private var configured = false

        /** Firestore settings can only be set before first use, and only once per process. */
        @Synchronized
        fun firestoreInstance(): FirebaseFirestore {
            val instance = FirebaseFirestore.getInstance()
            if (!configured) {
                instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
                configured = true
            }
            return instance
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
