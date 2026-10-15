package com.example.strivo

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.example.strivo.core.ui.components.WheelSound
import com.example.strivo.data.local.LegacyData
import com.example.strivo.data.local.db.StrivoDatabase
import com.example.strivo.data.local.prefs.SessionPrefs
import com.example.strivo.data.local.prefs.UserPrefs
import com.example.strivo.data.remote.AuthService
import com.example.strivo.data.repository.StrivoRepository
import com.example.strivo.data.sync.SyncManager
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.plan.LibraryPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One account's storage: its own database and preferences, and the sync that keeps them in the cloud. */
class UserScope(val prefs: UserPrefs, val repository: StrivoRepository, val sync: SyncManager)

class StrivoApp : Application() {
    val session by lazy { SessionPrefs(this) }
    val authService by lazy { AuthService(this) }
    val wheelSound by lazy { WheelSound(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scopes = mutableMapOf<String, UserScope>()

    /** The account that is signed in, or null when nobody is. Set by the login flow before any screen reads data. */
    @Volatile
    var activeUid: String? = null
        private set

    /** Goes up whenever data from the cloud was written into the phone's database, so screens know to reload. */
    private val _cloudDataVersion = MutableStateFlow(0)
    val cloudDataVersion: StateFlow<Int> = _cloudDataVersion

    override fun onCreate() {
        super.onCreate()
        // When the connection comes back, upload what was saved offline and read what changed elsewhere.
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeScope()?.sync?.requestSync(pull = true)
            }
        })
    }

    fun setActiveUser(uid: String?) {
        val previous = activeUid
        if (previous == uid) return
        previous?.let { scopeFor(it).sync.stop() }
        activeUid = uid
        uid?.let { scopeFor(it).sync.start(appScope) }
    }

    /** Called when the app comes to the foreground. */
    fun syncNow() {
        activeScope()?.sync?.requestSync(pull = true)
    }

    private fun activeScope(): UserScope? = activeUid?.let { scopeFor(it) }

    /** The signed-in account's storage. Reading data while signed out is a bug, so it fails loudly. */
    val userPrefs: UserPrefs get() = scopeFor(requireActiveUid()).prefs
    val repository: StrivoRepository get() = scopeFor(requireActiveUid()).repository
    val sync: SyncManager get() = scopeFor(requireActiveUid()).sync

    /** Preferences of a specific account, e.g. to check its profile right after it signs in. */
    fun userPrefsFor(uid: String): UserPrefs = scopeFor(uid).prefs

    fun syncFor(uid: String): SyncManager = scopeFor(uid).sync

    private fun requireActiveUid(): String = checkNotNull(activeUid) { "No account is signed in" }

    @Synchronized
    private fun scopeFor(uid: String): UserScope = scopes.getOrPut(uid) {
        // The first account to open this version takes over data saved before accounts had their own storage.
        LegacyData.claim(this, session, uid)
        val prefs = UserPrefs(this, uid)
        val db = StrivoDatabase(this, uid)
        val repository = StrivoRepository(db, prefs)
        // The exercise library works from the first launch, offline; the cloud's library replaces this one when it arrives.
        appScope.launch {
            repository.seedLibraryIfEmpty(starterLibrary())
            repository.seedPlanLibraryIfEmpty(starterPlans())
        }
        UserScope(
            prefs = prefs,
            repository = repository,
            sync = SyncManager(this, uid, db, prefs, onCloudDataApplied = { _cloudDataVersion.update { it + 1 } }),
        )
    }

    /** The plans that ship with the app (assets/plan_library.json, the same file the admin dashboard can import). */
    private fun starterPlans(): List<LibraryPlan> =
        runCatching { assets.open("plan_library.json").bufferedReader().use { LibraryPlan.listFromJson(it.readText()) } }
            .getOrDefault(emptyList())

    /** The exercises that ship with the app (assets/library.json, the same file the admin dashboard can import). */
    private fun starterLibrary(): List<LibraryExercise> =
        runCatching { assets.open("library.json").bufferedReader().use { LibraryExercise.listFromJson(it.readText()) } }
            .getOrDefault(emptyList())
}
