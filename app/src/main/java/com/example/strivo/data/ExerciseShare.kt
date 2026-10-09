package com.example.strivo.data

import android.util.Base64
import com.example.strivo.data.model.Exercise
import org.json.JSONArray
import org.json.JSONObject

/** What a shared message holds: some exercises, and the plan name when a whole plan was shared. */
data class SharedContent(val planName: String?, val exercises: List<Exercise>)

/**
 * Turns exercises or a whole plan into a text message that can be sent through any app, and back again.
 *
 * The message has a readable summary for people and a `STRIVO1:` code line that Strivo reads when it is
 * pasted. Only the plan values are shared (name, sets, reps, weight, notes), never workout logs.
 */
object ExerciseShare {
    private const val PREFIX = "STRIVO1:"
    private const val MAX_EXERCISES = 30
    private const val MAX_CODE_LENGTH = 20_000
    private val CODE = Regex("""STRIVO1:([A-Za-z0-9_-]+)""")

    fun encodeExercise(exercise: Exercise): String = encode(planName = null, exercises = listOf(exercise))

    fun encodePlan(planName: String, exercises: List<Exercise>): String = encode(planName, exercises)

    private fun encode(planName: String?, exercises: List<Exercise>): String {
        val items = JSONArray()
        exercises.forEach {
            items.put(
                JSONObject()
                    .put("n", it.name)
                    .put("s", it.sets)
                    .put("r", it.reps)
                    .put("w", it.weight)
                    .put("t", it.notes)
            )
        }
        val payload = JSONObject().put("e", items).apply { if (planName != null) put("p", planName) }
        val code = Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val summary = exercises.joinToString("\n") { "• ${it.name} - ${it.sets} sets × ${it.reps} reps @ ${it.weight} kg" }
        return buildString {
            append(if (planName != null) "Strivo plan: $planName\n" else "Strivo workout\n")
            append(summary).append("\n\n")
            append(
                if (planName != null) {
                    "In Strivo: open Add plan, tap the paste icon and paste this whole message.\n"
                } else {
                    "In Strivo: open a plan, tap the paste icon and paste this whole message.\n"
                }
            )
            append(PREFIX).append(code)
        }
    }

    /** Reads a shared message; null if it holds no valid exercise. Values are clamped to sane ranges. */
    fun decode(text: String): SharedContent? {
        val code = CODE.find(text)?.groupValues?.get(1) ?: return null
        if (code.length > MAX_CODE_LENGTH) return null
        return try {
            val json = String(Base64.decode(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            val payload = JSONObject(json)
            val array = payload.getJSONArray("e")
            val exercises = List(minOf(array.length(), MAX_EXERCISES)) { array.getJSONObject(it) }.mapNotNull { item ->
                val name = item.optString("n").trim().take(80)
                if (name.isEmpty()) return@mapNotNull null
                Exercise(
                    planId = -1L,
                    name = name,
                    sets = item.optString("s").toIntOrNull()?.coerceIn(1, 20)?.toString() ?: "3",
                    reps = item.optString("r").toIntOrNull()?.coerceIn(1, 100)?.toString() ?: "10",
                    weight = item.optString("w").toDoubleOrNull()?.coerceIn(0.0, 1000.0)?.toString() ?: "0.0",
                    notes = item.optString("t").take(500),
                )
            }
            if (exercises.isEmpty()) return null
            SharedContent(
                planName = payload.optString("p").trim().take(60).ifEmpty { null },
                exercises = exercises,
            )
        } catch (e: Exception) {
            null
        }
    }
}
