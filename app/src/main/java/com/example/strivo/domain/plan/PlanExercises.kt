package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.MaxPlannedDropSets
import org.json.JSONArray
import org.json.JSONObject

/** The most exercises a plan that someone else wrote (a trainer, the plan library) can hold. */
const val MAX_PLAN_EXERCISES = 30

/**
 * Reads the exercises of a plan from the cloud or from stored JSON (a list of maps with name, sets, reps, weightKg, dropSets and
 * notes). Every value is clamped to the same ranges as a plan the user makes, so a bad document can never put a broken plan on the
 * phone; an entry without a name is skipped.
 */
fun parsePlanExercises(items: List<*>?): List<Exercise> = items.orEmpty().take(MAX_PLAN_EXERCISES).mapNotNull { item ->
    val map = item as? Map<*, *> ?: return@mapNotNull null
    val name = (map["name"] as? String)?.trim()?.take(80).orEmpty()
    if (name.isEmpty()) return@mapNotNull null
    Exercise(
        planId = -1L,
        name = name,
        sets = ((map["sets"] as? Number)?.toInt() ?: 3).coerceIn(1, 20).toString(),
        reps = ((map["reps"] as? Number)?.toInt() ?: 10).coerceIn(1, 100).toString(),
        weight = ((map["weightKg"] as? Number)?.toDouble()?.takeIf { !it.isNaN() } ?: 0.0).coerceIn(0.0, 1000.0).toString(),
        notes = (map["notes"] as? String)?.trim()?.take(500).orEmpty(),
        dropSets = ((map["dropSets"] as? Number)?.toInt() ?: 0).coerceIn(0, MaxPlannedDropSets),
    )
}

/** The exercises as the JSON the cloud documents use, read back by [parsePlanExercises]. */
fun List<Exercise>.toPlanJson(): JSONArray = JSONArray().also { array ->
    forEach { e ->
        array.put(
            JSONObject()
                .put("name", e.name)
                .put("sets", e.sets.toIntOrNull() ?: 3)
                .put("reps", e.reps.toIntOrNull() ?: 10)
                .put("weightKg", e.weight.toDoubleOrNull() ?: 0.0)
                .put("dropSets", e.dropSets)
                .put("notes", e.notes),
        )
    }
}

/** JSON read from text turned into plain maps and lists, the shape [parsePlanExercises] reads. */
fun JSONObject.toPlainMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> get(key).toPlain() }

private fun Any.toPlain(): Any? = when (this) {
    is JSONObject -> toPlainMap()
    is JSONArray -> List(length()) { get(it).toPlain() }
    JSONObject.NULL -> null
    else -> this
}
