package com.example.strivo.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.strivo.util.todayIso

data class UserProfile(
    val gender: String? = null,
    val age: Int? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val highWeight: Double? = null,
    val lowWeight: Double? = null,
    val isComplete: Boolean = false,
)

data class UserSession(
    val uid: String?,
    val name: String?,
    val email: String?,
)

/** One account's body profile and app flags, kept in SharedPreferences that belong to that account only. */
class UserPrefs(context: Context, uid: String) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(fileNameFor(uid), Context.MODE_PRIVATE)

    // --- Profile ---

    fun saveProfile(gender: String, age: Int, height: Double, weight: Double) {
        prefs.edit()
            .putString(KEY_GENDER, gender)
            .putInt(KEY_AGE, age)
            .putDouble(KEY_HEIGHT, height)
            .apply()
        updateWeightOnly(weight)
        prefs.edit().putBoolean(KEY_PROFILE_COMPLETE, true).putLong(KEY_PROFILE_UPDATED, System.currentTimeMillis()).apply()
    }

    fun updateWeightOnly(weight: Double) {
        val currentHigh = prefs.getDouble(KEY_HIGH_WEIGHT) ?: 0.0
        val currentLow = prefs.getDouble(KEY_LOW_WEIGHT) ?: 1000.0
        prefs.edit().apply {
            putDouble(KEY_WEIGHT, weight)
            putLong(KEY_LAST_WEIGHT_UPDATE, System.currentTimeMillis())
            if (weight > currentHigh) putDouble(KEY_HIGH_WEIGHT, weight)
            if (weight < currentLow) putDouble(KEY_LOW_WEIGHT, weight)
            putLong(KEY_PROFILE_UPDATED, System.currentTimeMillis())
        }.apply()
    }

    // --- Cloud sync of the profile ---

    /** True when the profile is filled in and its latest version has not been uploaded yet. */
    fun profileNeedsUpload(): Boolean {
        if (!prefs.getBoolean(KEY_PROFILE_COMPLETE, false)) return false
        val pushed = prefs.getLong(KEY_PROFILE_PUSHED, 0L)
        return pushed == 0L || prefs.getLong(KEY_PROFILE_UPDATED, 0L) > pushed
    }

    fun profileUpdatedAt(): Long = prefs.getLong(KEY_PROFILE_UPDATED, 0L)

    /** The profile as it is stored in the cloud, or null if it is not filled in. */
    fun exportProfile(): Map<String, Any?>? {
        val profile = getProfile()
        if (!profile.isComplete) return null
        return mapOf(
            "gender" to profile.gender,
            "age" to profile.age?.toLong(),
            "height" to profile.height,
            "weight" to profile.weight,
            "highWeight" to profile.highWeight,
            "lowWeight" to profile.lowWeight,
            "lastWeightUpdate" to prefs.getLong(KEY_LAST_WEIGHT_UPDATE, 0L),
            "updatedAt" to prefs.getLong(KEY_PROFILE_UPDATED, 0L).coerceAtLeast(1L),
        )
    }

    fun markProfileUploaded() {
        prefs.edit().putLong(KEY_PROFILE_PUSHED, prefs.getLong(KEY_PROFILE_UPDATED, 0L).coerceAtLeast(1L)).apply()
    }

    /** Replaces the profile with the one from the cloud. */
    fun importProfile(remote: Map<String, Any?>) {
        val updatedAt = (remote["updatedAt"] as? Number)?.toLong() ?: 1L
        prefs.edit().apply {
            (remote["gender"] as? String)?.let { putString(KEY_GENDER, it) }
            (remote["age"] as? Number)?.let { putInt(KEY_AGE, it.toInt()) }
            (remote["height"] as? Number)?.let { putDouble(KEY_HEIGHT, it.toDouble()) }
            (remote["weight"] as? Number)?.let { putDouble(KEY_WEIGHT, it.toDouble()) }
            (remote["highWeight"] as? Number)?.let { putDouble(KEY_HIGH_WEIGHT, it.toDouble()) }
            (remote["lowWeight"] as? Number)?.let { putDouble(KEY_LOW_WEIGHT, it.toDouble()) }
            (remote["lastWeightUpdate"] as? Number)?.toLong()?.takeIf { it > 0 }?.let { putLong(KEY_LAST_WEIGHT_UPDATE, it) }
            putBoolean(KEY_PROFILE_COMPLETE, true)
            putLong(KEY_PROFILE_UPDATED, updatedAt)
            putLong(KEY_PROFILE_PUSHED, updatedAt) // it came from the cloud, so there is nothing to upload
        }.apply()
    }

    fun getProfile() = UserProfile(
        gender = prefs.getString(KEY_GENDER, null),
        age = if (prefs.contains(KEY_AGE)) prefs.getInt(KEY_AGE, 0) else null,
        height = prefs.getDouble(KEY_HEIGHT),
        weight = prefs.getDouble(KEY_WEIGHT),
        highWeight = prefs.getDouble(KEY_HIGH_WEIGHT),
        lowWeight = prefs.getDouble(KEY_LOW_WEIGHT),
        isComplete = prefs.getBoolean(KEY_PROFILE_COMPLETE, false),
    )

    /** When the weight was last entered, or null if it never was. */
    fun getLastWeightUpdateMillis(): Long? =
        if (prefs.contains(KEY_LAST_WEIGHT_UPDATE)) prefs.getLong(KEY_LAST_WEIGHT_UPDATE, 0L) else null

    /** The weekly weight prompt is due when the weight was never set or is 7+ days old. */
    fun shouldAskWeight(): Boolean {
        if (!prefs.contains(KEY_LAST_WEIGHT_UPDATE)) return true
        val lastUpdate = prefs.getLong(KEY_LAST_WEIGHT_UPDATE, 0L)
        val days = (System.currentTimeMillis() - lastUpdate) / (24 * 60 * 60 * 1000L)
        return days >= 7
    }

    // --- Demo data (debug builds) ---

    fun getDemoSessionIds(): Set<Long> =
        prefs.getStringSet(KEY_DEMO_SESSIONS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setDemoSessionIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_DEMO_SESSIONS, ids.map { it.toString() }.toSet()).apply()
    }

    fun getDemoFoodIds(): Set<Long> =
        prefs.getStringSet(KEY_DEMO_FOOD, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setDemoFoodIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_DEMO_FOOD, ids.map { it.toString() }.toSet()).apply()
    }

    fun getDemoActivityIds(): Set<Long> =
        prefs.getStringSet(KEY_DEMO_ACTIVITY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setDemoActivityIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_DEMO_ACTIVITY, ids.map { it.toString() }.toSet()).apply()
    }

    /** ISO dates of the weigh-ins the demo data added (never ones the user entered). */
    fun getDemoWeightDates(): Set<String> = prefs.getStringSet(KEY_DEMO_WEIGHTS, emptySet()).orEmpty()

    fun setDemoWeightDates(dates: Set<String>) {
        prefs.edit().putStringSet(KEY_DEMO_WEIGHTS, dates).apply()
    }

    // --- Daily reset ---

    /** Returns true (once per calendar day) when the daily reset should run. */
    fun consumeDailyReset(): Boolean {
        val today = todayIso()
        if (prefs.getString(KEY_LAST_DAILY_RESET, null) == today) return false
        prefs.edit().putString(KEY_LAST_DAILY_RESET, today).apply()
        return true
    }

    private fun SharedPreferences.getDouble(key: String): Double? =
        if (contains(key)) Double.fromBits(getLong(key, 0L)) else null

    private fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor =
        putLong(key, value.toRawBits())

    companion object {
        /** The SharedPreferences file for [uid]; also used to find the file when moving old data in. */
        fun fileNameFor(uid: String) = "strivo_user_${uid.filter { it.isLetterOrDigit() || it == '_' }}"

        private const val KEY_GENDER = "user_gender"
        private const val KEY_AGE = "user_age"
        private const val KEY_HEIGHT = "user_height"
        private const val KEY_WEIGHT = "user_weight"
        private const val KEY_HIGH_WEIGHT = "user_high_weight"
        private const val KEY_LOW_WEIGHT = "user_low_weight"
        private const val KEY_LAST_WEIGHT_UPDATE = "last_weight_update"
        private const val KEY_PROFILE_COMPLETE = "is_profile_complete"
        private const val KEY_LAST_DAILY_RESET = "last_daily_reset"
        private const val KEY_DEMO_SESSIONS = "demo_session_ids"
        private const val KEY_DEMO_FOOD = "demo_food_ids"
        private const val KEY_PROFILE_UPDATED = "profile_updated_at"
        private const val KEY_PROFILE_PUSHED = "profile_pushed_at"
        private const val KEY_DEMO_ACTIVITY = "demo_activity_ids"
        private const val KEY_DEMO_WEIGHTS = "demo_weight_dates"
    }
}
