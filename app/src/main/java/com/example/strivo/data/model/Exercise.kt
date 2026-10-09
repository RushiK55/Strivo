package com.example.strivo.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ExerciseSet(
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean = false,
    val isStarted: Boolean = false,
    val restTime: String = "",
    val setDuration: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("weight", weight)
        .put("reps", reps)
        .put("isCompleted", isCompleted)
        .put("isStarted", isStarted)
        .put("restTime", restTime)
        .put("setDuration", setDuration)

    companion object {
        fun fromJson(json: JSONObject) = ExerciseSet(
            weight = json.getDouble("weight"),
            reps = json.getInt("reps"),
            isCompleted = json.optBoolean("isCompleted", false),
            isStarted = json.optBoolean("isStarted", false),
            restTime = json.optString("restTime", ""),
            setDuration = json.optString("setDuration", ""),
        )
    }
}

fun List<ExerciseSet>.toJsonString(): String =
    JSONArray().also { array -> forEach { array.put(it.toJson()) } }.toString()

fun parseExerciseSets(json: String?): List<ExerciseSet> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        val array = JSONArray(json)
        List(array.length()) { ExerciseSet.fromJson(array.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }
}

data class Exercise(
    val id: Long? = null, // SQLite will auto-increment this
    val planId: Long, // -1 for extra exercises
    val name: String,
    val weight: String, // Summary of the sets (kept for the stored schema)
    val sets: String,
    val reps: String,
    val notes: String,
    val dateTime: String = "",
    val isCheck: Boolean = false,
    val isExtra: Boolean = false,
    val vanishEndOfDay: Boolean = false,
    val restTime: String = "",
    val setsList: List<ExerciseSet> = emptyList(),
    val sortOrder: Int = -1, // position inside the plan; -1 = not placed yet (appended on create)
)
