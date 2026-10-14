package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.MaxPlannedDropSets
import com.example.strivo.domain.model.PREBUILT_DAY
import org.json.JSONArray
import org.json.JSONObject

/** The days a trainer can send a plan to: a weekday, or the user's library of plans ([PREBUILT_DAY]). */
val AssignableDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", PREBUILT_DAY)

/**
 * A plan a trainer sent from the dashboard (users/{uid}/assigned/{id}). The app adds it to the user's plans on the next
 * sync and marks it delivered. Values are clamped to the same ranges as a plan the user makes, so a bad document can
 * never put a broken plan on the phone.
 */
data class PlanAssignment(
    val id: String,
    val planName: String,
    val day: String,
    val exercises: List<Exercise>,
    val note: String,
    val trainerName: String,
    val sentAt: Long,
) {
    companion object {
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        private const val MAX_EXERCISES = 30

        /** Reads a document from the cloud; null when it is not a plan waiting to be delivered, or has nothing in it. */
        fun fromMap(id: String, data: Map<String, Any?>): PlanAssignment? {
            if (id.isBlank() || data["status"] != STATUS_SENT) return null
            val planName = (data["planName"] as? String)?.trim()?.take(60).orEmpty()
            val day = (data["day"] as? String)?.trim().orEmpty()
            if (planName.isEmpty() || day !in AssignableDays) return null
            val exercises = (data["exercises"] as? List<*>).orEmpty().take(MAX_EXERCISES).mapNotNull { item ->
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
            if (exercises.isEmpty()) return null
            return PlanAssignment(
                id = id,
                planName = planName,
                day = day,
                exercises = exercises,
                note = (data["note"] as? String)?.trim()?.take(500).orEmpty(),
                trainerName = (data["sentByName"] as? String)?.trim()?.take(100).orEmpty(),
                sentAt = (data["sentAt"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

/** A plan from the trainer that was added to this phone: what the Home screen tells the user about. */
data class ReceivedPlan(
    val id: String,
    val planId: Long,
    val planName: String,
    val day: String,
    val trainerName: String,
    val note: String,
    val receivedAt: Long,
    val dismissed: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("planId", planId)
        .put("planName", planName)
        .put("day", day)
        .put("trainerName", trainerName)
        .put("note", note)
        .put("receivedAt", receivedAt)
        .put("dismissed", dismissed)

    companion object {
        fun from(assignment: PlanAssignment, planId: Long, at: Long) = ReceivedPlan(
            id = assignment.id,
            planId = planId,
            planName = assignment.planName,
            day = assignment.day,
            trainerName = assignment.trainerName,
            note = assignment.note,
            receivedAt = at,
        )

        fun listToJson(plans: List<ReceivedPlan>): String = JSONArray().also { array -> plans.forEach { array.put(it.toJson()) } }.toString()

        /** Reads the stored list; entries that cannot be read are skipped. */
        fun listFromJson(text: String?): List<ReceivedPlan> = runCatching {
            val array = JSONArray(text ?: return emptyList())
            List(array.length()) { array.getJSONObject(it) }.mapNotNull { json ->
                val id = json.optString("id")
                if (id.isEmpty()) return@mapNotNull null
                ReceivedPlan(
                    id = id,
                    planId = json.optLong("planId", -1L),
                    planName = json.optString("planName"),
                    day = json.optString("day"),
                    trainerName = json.optString("trainerName"),
                    note = json.optString("note"),
                    receivedAt = json.optLong("receivedAt", 0L),
                    dismissed = json.optBoolean("dismissed", false),
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** What the Home screen shows: plans received in the last [days] days that the user has not dismissed, newest first. */
fun List<ReceivedPlan>.toShow(now: Long, days: Int = 14): List<ReceivedPlan> =
    filter { !it.dismissed && now - it.receivedAt <= days * 86_400_000L }.sortedByDescending { it.receivedAt }
