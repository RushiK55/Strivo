package com.example.strivo.data.local.db

import android.content.ContentValues
import android.database.Cursor
import com.example.strivo.domain.activity.ActivityEvent
import com.example.strivo.domain.analytics.FoodEntry
import com.example.strivo.domain.analytics.Meal
import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.LibraryExercise
import com.example.strivo.domain.model.Plan
import com.example.strivo.domain.model.WorkoutSession
import com.example.strivo.domain.model.parseExerciseSets
import com.example.strivo.domain.model.toJsonString
import java.time.LocalDateTime

// Turning rows into objects and objects into rows, kept apart from the queries that use them.

internal fun FoodEntry.toValues() = ContentValues().apply {
    put("date", date.toString())
    put("meal", meal.name)
    put("name", name)
    put("calories", calories)
    if (proteinG != null) put("protein", proteinG) else putNull("protein")
    if (carbsG != null) put("carbs", carbsG) else putNull("carbs")
    if (fatG != null) put("fat", fatG) else putNull("fat")
}

internal fun Cursor.toFoodEntry(): FoodEntry? = runCatching {
    fun optional(column: String): Double? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }
    FoodEntry(
        id = getLong(getColumnIndexOrThrow("id")),
        date = java.time.LocalDate.parse(getString(getColumnIndexOrThrow("date"))),
        meal = Meal.valueOf(getString(getColumnIndexOrThrow("meal"))),
        name = getString(getColumnIndexOrThrow("name")),
        calories = getInt(getColumnIndexOrThrow("calories")),
        proteinG = optional("protein"),
        carbsG = optional("carbs"),
        fatG = optional("fat"),
    )
}.getOrNull()
internal fun Plan.toValues() = ContentValues().apply {
    planId?.let { put("planId", it) }
    put("planName", planName)
    put("planDay", planDay)
}

internal fun Cursor.toPlan() = Plan(
    planId = getLong(getColumnIndexOrThrow("planId")),
    planName = getStringOrEmpty("planName"),
    planDay = getStringOrEmpty("planDay"),
)

internal fun Exercise.toValues() = ContentValues().apply {
    id?.let { put("id", it) }
    put("planId", planId)
    put("name", name)
    put("weight", weight)
    put("sets", sets)
    put("reps", reps)
    put("notes", notes)
    put("dateTime", dateTime)
    put("isCheck", if (isCheck) 1 else 0)
    put("isExtra", if (isExtra) 1 else 0)
    put("vanishEndOfDay", if (vanishEndOfDay) 1 else 0)
    put("restTime", restTime)
    put("setsData", setsList.toJsonString())
    if (sortOrder >= 0) put("sortOrder", sortOrder)
    put("dropSets", dropSets)
}

internal fun Cursor.toExercise() = Exercise(
    id = getLong(getColumnIndexOrThrow("id")),
    planId = getColumnIndexOrThrow("planId").let { if (isNull(it)) -1L else getLong(it) },
    name = getStringOrEmpty("name"),
    weight = getStringOrEmpty("weight", "0"),
    sets = getStringOrEmpty("sets", "0"),
    reps = getStringOrEmpty("reps", "0"),
    notes = getStringOrEmpty("notes"),
    dateTime = getStringOrEmpty("dateTime"),
    isCheck = getIntOrZero("isCheck") == 1,
    isExtra = getIntOrZero("isExtra") == 1,
    vanishEndOfDay = getIntOrZero("vanishEndOfDay") == 1,
    restTime = getStringOrEmpty("restTime"),
    setsList = parseExerciseSets(getStringOrEmpty("setsData")),
    sortOrder = getIntOrZero("sortOrder"),
    dropSets = getIntOrZero("dropSets"),
)

internal fun Cursor.toSession() = WorkoutSession(
    id = getLong(getColumnIndexOrThrow("id")),
    planName = getStringOrEmpty("planName"),
    date = LocalDateTime.parse(getStringOrEmpty("date")),
    totalTime = getStringOrEmpty("totalTime"),
    exercises = WorkoutSession.exercisesFromJson(getStringOrEmpty("exercises")),
)
internal fun Cursor.getStringOrEmpty(column: String, default: String = ""): String {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) default else getString(index)
}

internal fun Cursor.getIntOrZero(column: String): Int {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) 0 else getInt(index)
}

internal fun ActivityEvent.toValues() = ContentValues().apply {
    put("at", at)
    put("type", type)
    put("summary", summary)
}

internal fun Cursor.toActivityEvent() = ActivityEvent(
    id = getLong(getColumnIndexOrThrow("id")),
    at = getLong(getColumnIndexOrThrow("at")),
    type = getStringOrEmpty("type"),
    summary = getStringOrEmpty("summary"),
)

internal fun LibraryExercise.toValues() = ContentValues().apply {
    put("id", id)
    put("name", name)
    put("muscleGroup", muscleGroup)
    put("equipment", equipment)
    put("level", level)
    put("instructions", instructions)
    put("tips", tips)
    put("defaultSets", defaultSets)
    put("defaultReps", defaultReps)
    put("defaultWeightKg", defaultWeightKg)
    put("videoUrl", videoUrl)
    put("active", if (active) 1 else 0)
    put("updatedAt", updatedAt)
}

internal fun Cursor.toLibraryExercise() = LibraryExercise(
    id = getStringOrEmpty("id"),
    name = getStringOrEmpty("name"),
    muscleGroup = getStringOrEmpty("muscleGroup"),
    equipment = getStringOrEmpty("equipment"),
    level = getStringOrEmpty("level"),
    instructions = getStringOrEmpty("instructions"),
    tips = getStringOrEmpty("tips"),
    defaultSets = getIntOrZero("defaultSets"),
    defaultReps = getIntOrZero("defaultReps"),
    defaultWeightKg = getDouble(getColumnIndexOrThrow("defaultWeightKg")),
    videoUrl = getStringOrEmpty("videoUrl"),
    active = getIntOrZero("active") == 1,
    updatedAt = getLong(getColumnIndexOrThrow("updatedAt")),
)
