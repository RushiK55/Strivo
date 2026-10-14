package com.example.strivo.domain.plan

import com.example.strivo.domain.model.Exercise
import com.example.strivo.domain.model.MaxPlannedDropSets
import com.example.strivo.domain.model.PREBUILT_DAY
import org.json.JSONArray
import org.json.JSONObject

/** The days a trainer can send a plan to: a weekday, or the user's library of plans ([PREBUILT_DAY]). */
val AssignableDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", PREBUILT_DAY)

/**
 * A plan a trainer sent from the dashboard (users/{uid}/assigned/{id}). The app does not add it by itself: it is held as an
 * offer (see [PlanOffer]) until the user accepts it, and then it goes onto the day the trainer chose. Values are clamped to
 * the same ranges as a plan the user makes, so a bad document can never put a broken plan on the phone.
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
    /** The same shape as the cloud document, so a stored offer is read back by the same code that reads one from the cloud. */
    fun toJson(): JSONObject = JSONObject()
        .put("planName", planName)
        .put("day", day)
        .put("status", STATUS_SENT)
        .put("note", note)
        .put("sentByName", trainerName)
        .put("sentAt", sentAt)
        .put(
            "exercises",
            JSONArray().also { array ->
                exercises.forEach { e ->
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
            },
        )

    companion object {
        /** A plan the trainer has sent that the app has not shown yet. */
        const val STATUS_SENT = "sent"

        /** The app has the plan and is waiting for the user to answer. */
        const val STATUS_DELIVERED = "delivered"

        /** The user accepted it and it is in their plans. */
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        private const val MAX_EXERCISES = 30

        /** A plan the user has not answered yet. */
        fun isWaiting(status: Any?): Boolean = status == STATUS_SENT || status == STATUS_DELIVERED

        /** Reads a document from the cloud; null when it is not a plan waiting for an answer, or has nothing in it. */
        fun fromMap(id: String, data: Map<String, Any?>): PlanAssignment? {
            if (id.isBlank() || !isWaiting(data["status"])) return null
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

        /** The offers kept on the phone, as one text; read back with [listFromJson]. */
        fun listToJson(plans: List<PlanAssignment>): String = JSONArray().also { array ->
            plans.forEach { array.put(it.toJson().put("id", it.id)) }
        }.toString()

        /** Reads the stored offers; entries that cannot be read are skipped. */
        fun listFromJson(text: String?): List<PlanAssignment> = runCatching {
            val array = JSONArray(text ?: return emptyList())
            List(array.length()) { array.getJSONObject(it) }.mapNotNull { json -> fromMap(json.optString("id"), json.toPlainMap()) }
        }.getOrDefault(emptyList())

        private fun JSONObject.toPlainMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> get(key).toPlain() }

        private fun Any.toPlain(): Any? = when (this) {
            is JSONObject -> toPlainMap()
            is JSONArray -> List(length()) { get(it).toPlain() }
            JSONObject.NULL -> null
            else -> this
        }
    }
}

/**
 * Plans one trainer sent together (a week's worth, say). The Home screen asks about them as one, so seven plans for seven days
 * are one card with one Accept, not seven cards.
 */
data class PlanOffer(val plans: List<PlanAssignment>) {
    val ids: List<String> = plans.map { it.id }
    val trainerName: String = plans.firstOrNull { it.trainerName.isNotBlank() }?.trainerName.orEmpty()

    /** The first note the trainer wrote (usually the same on every plan of a week). */
    val note: String = plans.map { it.note }.firstOrNull { it.isNotBlank() }.orEmpty()
    val sentAt: Long = plans.maxOfOrNull { it.sentAt } ?: 0L

    /** The days it covers, in week order, the plan library last. */
    val days: List<String> = plans.map { it.day }.distinct().sortedBy { AssignableDays.indexOf(it) }

    /** "Coach Raj sent you a plan" or "Coach Raj sent you 7 plans". */
    val title: String
        get() {
            val from = trainerName.ifEmpty { "Your trainer" }
            return if (plans.size == 1) "$from sent you a plan" else "$from sent you ${plans.size} plans"
        }

    /** Where they go: "Monday", "Mon to Sun", "Mon · Wed · Fri" or "Plan library". */
    val where: String get() = describeDays(days)

    /** The plans' names, "Push, Pull, Legs +4 more": the first [limit] of them, and how many are left. */
    fun names(limit: Int = 3): String {
        val all = plans.map { it.planName }.distinct()
        return if (all.size <= limit) all.joinToString(", ") else all.take(limit).joinToString(", ") + " +${all.size - limit} more"
    }
}

/** What to tell the user after they accept: "Added 7 plans to your week", "Added 1 plan to Monday", "Added 1 plan to your plan library". */
fun acceptedNotice(offer: PlanOffer, added: Int): String {
    val what = if (added == 1) "1 plan" else "$added plans"
    val weekdays = offer.days.filter { it != PREBUILT_DAY }
    val to = when {
        weekdays.isEmpty() -> "your plan library"
        weekdays.size == 1 && offer.days.size == 1 -> weekdays.single()
        else -> "your week"
    }
    return "Added $what to $to"
}

/** Longest gap between two plans of one trainer for them to count as sent together: an hour. */
const val OFFER_GAP_MILLIS = 60 * 60_000L

/**
 * Puts plans into offers: those from the same trainer, each sent within [gapMillis] of the one before, are one offer. Newest
 * offer first; the plans in an offer in the order they were sent.
 */
fun groupOffers(assignments: List<PlanAssignment>, gapMillis: Long = OFFER_GAP_MILLIS): List<PlanOffer> {
    val groups = mutableListOf<MutableList<PlanAssignment>>()
    for (plan in assignments.sortedWith(compareBy({ it.sentAt }, { it.id }))) {
        val open = groups.lastOrNull { group -> group.last().trainerName == plan.trainerName && plan.sentAt - group.last().sentAt <= gapMillis }
        if (open != null) open += plan else groups += mutableListOf(plan)
    }
    return groups.map { PlanOffer(it) }.sortedByDescending { it.sentAt }
}

private val ShortDay = mapOf(
    "Monday" to "Mon", "Tuesday" to "Tue", "Wednesday" to "Wed", "Thursday" to "Thu", "Friday" to "Fri", "Saturday" to "Sat", "Sunday" to "Sun",
)

/**
 * The days a set of plans covers, for people: one weekday in full ("Monday"); three or more in a row as a range ("Mon to Sun");
 * others as a list ("Mon · Wed · Fri"); the library as "Plan library". [days] are in week order.
 */
fun describeDays(days: List<String>): String {
    val weekdays = days.filter { it in ShortDay }
    val library = PREBUILT_DAY in days
    val head = when {
        weekdays.isEmpty() -> null
        weekdays.size == 1 -> weekdays.single()
        else -> {
            val index = weekdays.map { AssignableDays.indexOf(it) }
            val inARow = index.zipWithNext().all { (a, b) -> b == a + 1 }
            if (inARow && weekdays.size >= 3) "${ShortDay.getValue(weekdays.first())} to ${ShortDay.getValue(weekdays.last())}"
            else weekdays.joinToString(" · ") { ShortDay.getValue(it) }
        }
    }
    return listOfNotNull(head, if (library) "Plan library" else null).joinToString(" · ")
}

/** A plan from the trainer that the user accepted and that was added to this phone: remembered so it is never added twice. */
data class ReceivedPlan(
    val id: String,
    val planId: Long,
    val planName: String,
    val day: String,
    val receivedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("planId", planId)
        .put("planName", planName)
        .put("day", day)
        .put("receivedAt", receivedAt)

    companion object {
        fun from(assignment: PlanAssignment, planId: Long, at: Long) = ReceivedPlan(
            id = assignment.id,
            planId = planId,
            planName = assignment.planName,
            day = assignment.day,
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
                    receivedAt = json.optLong("receivedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }
}
