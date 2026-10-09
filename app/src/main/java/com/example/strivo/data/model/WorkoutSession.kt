package com.example.strivo.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class WorkoutSession(
    val id: Long? = null,
    val planName: String,
    val date: LocalDateTime,
    val totalTime: String,
    val exercises: List<PerformedExercise>,
) {
    fun exercisesJson(): String =
        JSONArray().also { array -> exercises.forEach { array.put(it.toJson()) } }.toString()

    companion object {
        fun exercisesFromJson(json: String): List<PerformedExercise> {
            val array = JSONArray(json)
            return List(array.length()) { PerformedExercise.fromJson(array.getJSONObject(it)) }
        }
    }
}

data class PerformedExercise(
    val name: String,
    val sets: List<ExerciseSet>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("sets", JSONArray().also { array -> sets.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): PerformedExercise {
            val array = json.getJSONArray("sets")
            return PerformedExercise(
                name = json.getString("name"),
                sets = List(array.length()) { ExerciseSet.fromJson(array.getJSONObject(it)) },
            )
        }
    }
}
