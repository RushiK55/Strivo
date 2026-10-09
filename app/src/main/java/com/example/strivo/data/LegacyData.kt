package com.example.strivo.data

import android.content.Context
import com.example.strivo.data.db.StrivoDatabase
import com.example.strivo.data.prefs.SessionPrefs
import com.example.strivo.data.prefs.UserPrefs
import java.io.File

/**
 * Before accounts had their own storage, everything lived in one database and one preferences file shared by
 * whoever was signed in. The first account to open this version takes that data over; every other account starts empty.
 */
object LegacyData {
    private const val OLD_DB_NAME = "strivo.db"

    /** Moves the old data into [uid]'s own storage, once. Safe to call every time a scope is created. */
    fun claim(context: Context, session: SessionPrefs, uid: String) {
        if (session.legacyDataHandled) return
        // Mark first: if a step below fails the data stays where it is rather than being claimed twice.
        session.legacyDataHandled = true

        val oldDb = context.getDatabasePath(OLD_DB_NAME)
        if (oldDb.exists()) {
            val newDb = context.getDatabasePath(StrivoDatabase.fileNameFor(uid))
            // The database and the side files SQLite may have next to it.
            listOf("", "-journal", "-wal", "-shm").forEach { suffix ->
                val from = File(oldDb.path + suffix)
                if (from.exists()) from.renameTo(File(newDb.path + suffix))
            }
        }

        val values = session.takeLegacyUserValues()
        if (values.isNotEmpty()) {
            val editor = context.getSharedPreferences(UserPrefs.fileNameFor(uid), Context.MODE_PRIVATE).edit()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
        }
    }
}
