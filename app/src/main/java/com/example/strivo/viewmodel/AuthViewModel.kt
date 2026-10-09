package com.example.strivo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.strivo.StrivoApp
import com.example.strivo.data.remote.AuthException
import com.example.strivo.data.remote.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthState(
    val uid: String? = null,
    val userName: String? = null,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val isProfileComplete: Boolean = false,
) {
    val isAuthenticated: Boolean get() = uid != null
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StrivoApp
    private val authService = app.authService
    private val session = app.session

    private val _state = MutableStateFlow(loadSession())
    val state: StateFlow<AuthState> = _state

    init {
        // A profile that arrives from the cloud later (slow connection) still takes the user past the setup screens.
        viewModelScope.launch { app.cloudDataVersion.collect { refreshProfileStatus() } }
    }

    /** Firebase restores the signed-in user itself; a stale local session without one is dropped. */
    private fun loadSession(): AuthState {
        val user = authService.currentUser()
        if (user == null) {
            session.clearSession()
            app.setActiveUser(null)
            return AuthState()
        }
        app.setActiveUser(user.uid)
        session.saveSession(user.uid, user.name, user.email)
        return user.toState(isLoading = false)
    }

    // Whether the profile is filled in is a fact about this account, not about the phone.
    private fun AuthUser.toState(isLoading: Boolean) = AuthState(
        uid = uid,
        userName = name,
        userEmail = email,
        isLoading = isLoading,
        isProfileComplete = app.userPrefsFor(uid).getProfile().isComplete,
    )

    fun refreshProfileStatus() {
        val uid = _state.value.uid ?: return
        _state.update { it.copy(isProfileComplete = app.userPrefsFor(uid).getProfile().isComplete) }
    }

    /** Returns null on success, otherwise a message to show the user. */
    suspend fun login(email: String, password: String): String? =
        authenticate { authService.login(email, password) }

    /** Returns null on success, otherwise a message to show the user. */
    suspend fun register(name: String, email: String, password: String): String? =
        authenticate { authService.register(name, email, password) }

    private suspend fun authenticate(block: suspend () -> AuthUser): String? {
        _state.update { it.copy(isLoading = true) }
        return try {
            val user = block()
            // Point the app at this account's storage before any screen reads anything.
            app.setActiveUser(user.uid)
            // Someone signing in on a new phone has a profile in the cloud: fetch it so they are not asked again.
            app.syncFor(user.uid).restoreProfile(PROFILE_RESTORE_TIMEOUT_MILLIS)
            session.saveSession(user.uid, user.name, user.email)
            _state.value = user.toState(isLoading = false)
            null
        } catch (e: AuthException) {
            _state.update { it.copy(isLoading = false) }
            e.message ?: "Something went wrong. Please try again."
        }
    }

    /** Signs out. The account's data stays on the device and is there again when it signs back in. */
    fun logout() {
        authService.logout()
        session.clearSession()
        app.setActiveUser(null)
        _state.value = AuthState()
    }
}

private const val PROFILE_RESTORE_TIMEOUT_MILLIS = 6_000L
