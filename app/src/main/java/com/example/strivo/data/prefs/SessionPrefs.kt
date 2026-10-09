package com.example.strivo.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Who is signed in, and whether the data from before accounts had their own storage has been handed to one.
 * This is the only preference store shared by every account on the device; everything else is per account.
 */
class SessionPrefs(context: Context) {
    // The file name is the one older versions used for everything, which is how the old data is found.
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getSession() = UserSession(
        uid = prefs.getString(KEY_UID, null),
        name = prefs.getString(KEY_NAME, null),
        email = prefs.getString(KEY_EMAIL, null),
    )

    fun saveSession(uid: String, name: String?, email: String?) {
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name ?: "")
            .putString(KEY_EMAIL, email ?: "")
            .apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_UID).remove(KEY_NAME).remove(KEY_EMAIL).apply()
    }

    /** True once the data from before per-account storage has been given to an account (or there was none). */
    var legacyDataHandled: Boolean
        get() = prefs.getBoolean(KEY_LEGACY_HANDLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LEGACY_HANDLED, value).apply()

    /** Older versions kept the profile and flags in this same file; hands them over and removes them from it. */
    fun takeLegacyUserValues(): Map<String, Any?> {
        val keep = setOf(KEY_UID, KEY_NAME, KEY_EMAIL, KEY_LEGACY_HANDLED)
        val legacy = prefs.all.filterKeys { it !in keep }
        if (legacy.isNotEmpty()) {
            prefs.edit().apply { legacy.keys.forEach { remove(it) } }.apply()
        }
        return legacy
    }

    companion object {
        const val FILE = "strivo_prefs"
        private const val KEY_UID = "user_uid"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_LEGACY_HANDLED = "legacy_data_handled"
    }
}
