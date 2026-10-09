package com.example.strivo.data.remote

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthUser(val uid: String, val name: String?, val email: String?)

/** Thrown with a message that is safe to show to the user. */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Account handling on Firebase Authentication (email + password).
 * Needs `app/google-services.json` from the Firebase console; see README.
 */
class AuthService(private val context: Context) {

    private val auth: FirebaseAuth
        get() {
            if (FirebaseApp.getApps(context).isEmpty()) {
                throw AuthException("Firebase is not configured. Add google-services.json to the app/ folder.")
            }
            return FirebaseAuth.getInstance()
        }

    /** The signed-in user restored by Firebase from the previous run, if any. */
    fun currentUser(): AuthUser? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAuth.getInstance().currentUser?.toAuthUser()

    suspend fun login(email: String, password: String): AuthUser = firebaseCall {
        auth.signInWithEmailAndPassword(email, password).await().user?.toAuthUser()
            ?: throw AuthException("Login failed")
    }

    suspend fun register(name: String, email: String, password: String): AuthUser = firebaseCall {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: throw AuthException("Registration failed")
        user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        AuthUser(uid = user.uid, name = name, email = user.email ?: email)
    }

    fun logout() {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance().signOut()
    }

    private fun FirebaseUser.toAuthUser() =
        AuthUser(uid = uid, name = displayName?.ifEmpty { null }, email = email)

    private inline fun <T> firebaseCall(block: () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthUserCollisionException) {
        throw AuthException("This email is already registered. Try logging in.", e)
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw AuthException("Password is too weak. Use at least 6 characters.", e)
    } catch (e: FirebaseAuthInvalidUserException) {
        throw AuthException("Incorrect email or password.", e)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw AuthException("Incorrect email or password.", e)
    } catch (e: FirebaseNetworkException) {
        throw AuthException("No internet connection.", e)
    } catch (e: Exception) {
        throw AuthException(e.localizedMessage ?: "Something went wrong. Please try again.", e)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
