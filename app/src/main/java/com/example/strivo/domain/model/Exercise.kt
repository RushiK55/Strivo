package com.example.strivo.domain.model

import org.json.JSONArray
import org.json.JSONObject

data class ExerciseSet(
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean = false,
    val isStarted: Boolean = false,
    val restTime: String = "",
    val setDuration: String = "",
    val type: SetType = SetType.Normal,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("weight", weight)
        .put("reps", reps)
        .put("isCompleted", isCompleted)
        .put("isStarted", isStarted)
        .put("restTime", restTime)
        .put("setDuration", setDuration)
        .apply { if (type != SetType.Normal) put("type", type.key) }

    companion object {
        fun fromJson(json: JSONObject) = ExerciseSet(
            weight = json.getDouble("weight"),
            reps = json.getInt("reps"),
            isCompleted = json.optBoolean("isCompleted", false),
            isStarted = json.optBoolean("isStarted", false),
            restTime = json.optString("restTime", ""),
            setDuration = json.optString("setDuration", ""),
            type = SetType.fromKey(json.optString("type", "")),
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
    /** Drop sets the plan adds after the working sets, each [DropStepKg] lighter than the set before it. */
    val dropSets: Int = 0,
    /**
     * On an exercise of a weekday's plan that was made from the user's own plan: the id of that plan's exercise. What changes here
     * (the sets, the weights, the reps) is copied there, and that exercise is what the next day starts from. Null when not linked.
     */
    val sourceId: Long? = null,
)
