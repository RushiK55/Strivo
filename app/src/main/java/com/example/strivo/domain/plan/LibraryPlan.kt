package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import org.json.JSONArray
import org.json.JSONObject

/**
 * A plan in the plan library: what an admin publishes (the cloud's planLibrary/{id}) next to the exercise library, or a starter plan that
 * ships with the app. Every user can browse it and add a plan to a day; it then becomes the user's own plan (see Plan.sourcePlanId), so
 * nothing the user changes ever touches the library. [id] is stable, so a plan keeps its identity when an admin edits it.
 */
data class LibraryPlan(
    val id: String,
    val name: String,
    val description: String,
    val level: String,
    val exercises: List<Exercise>,
    val active: Boolean,
    /** Epoch milliseconds of the last change in the cloud; 0 for the plans that ship with the app. */
    val updatedAt: Long = 0,
) {
    /** What a plan made from this one is marked with, to tell that the user already has it (see Plan.origin). */
    val origin: String get() = ORIGIN_PREFIX + id

    /** The exercises as a plan of the user's, at their end. */
    fun exercisesFor(planId: Long): List<Exercise> = exercises.map { it.copy(id = null, planId = planId) }

    fun exercisesJson(): String = exercises.toPlanJson().toString()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("description", description)
        .put("level", level)
        .put("exercises", exercises.toPlanJson())
        .put("active", active)
        .put("updatedAt", updatedAt)

    companion object {
        const val ORIGIN_PREFIX = "library:"
        const val MAX_EXERCISES = MAX_PLAN_EXERCISES

        /** Reads a plan from the cloud (its id and fields). Null when it has no name or no exercise, so one bad plan never hides the rest. */
        fun fromMap(id: String, data: Map<String, Any?>): LibraryPlan? {
            fun text(key: String, max: Int) = (data[key] as? String)?.trim()?.take(max).orEmpty()
            val name = text("name", 60)
            val exercises = parsePlanExercises(data["exercises"] as? List<*>)
            if (id.isBlank() || name.isEmpty() || exercises.isEmpty()) return null
            return LibraryPlan(
                id = id,
                name = name,
                description = text("description", 500),
                level = text("level", 40),
                exercises = exercises,
                active = data["active"] as? Boolean ?: true,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            )
        }

        fun fromJson(json: JSONObject): LibraryPlan? = fromMap(json.optString("id"), json.toPlainMap())

        /** The exercises of a stored plan (text written by [exercisesJson]). */
        fun exercisesFromJson(text: String?): List<Exercise> = runCatching {
            val array = JSONArray(text ?: return emptyList())
            parsePlanExercises(List(array.length()) { array.getJSONObject(it).toPlainMap() })
        }.getOrDefault(emptyList())

        /** Reads a list; entries that cannot be read are skipped. */
        fun listFromJson(text: String): List<LibraryPlan> = runCatching {
            val array = JSONArray(text)
            List(array.length()) { fromJson(array.getJSONObject(it)) }.filterNotNull()
        }.getOrDefault(emptyList())
    }
}
