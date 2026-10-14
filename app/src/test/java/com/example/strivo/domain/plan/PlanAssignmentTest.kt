package com.example.strivo.domain.plan

import com.example.strivo.domain.activity.ActivityKind
import com.example.strivo.domain.activity.planOfferAnswered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAssignmentTest {
    private val week = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val minute = 60_000L

    private fun doc(vararg extra: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "planName" to " Push Day ",
        "day" to "Monday",
        "status" to "sent",
        "note" to "Go light on the first set.",
        "sentByName" to "Coach Raj",
        "sentAt" to 1_700_000_000_000L,
        "exercises" to listOf(
            mapOf("name" to "Bench Press", "sets" to 4L, "reps" to 8L, "weightKg" to 60.5, "dropSets" to 1L, "notes" to "Pause on the chest"),
            mapOf("name" to "Push-up", "sets" to 3, "reps" to 15, "weightKg" to 0),
        ),
    ) + extra

    private fun plan(id: String, day: String, sentAt: Long, trainer: String = "Coach Raj", name: String = "Plan $day", note: String = "") =
        PlanAssignment.fromMap(id, doc("planName" to name, "day" to day, "sentAt" to sentAt, "sentByName" to trainer, "note" to note))!!

    // ---- Reading a plan

    @Test
    fun aSentPlanIsReadWithItsExercisesInOrder() {
        val plan = PlanAssignment.fromMap("a1", doc())!!
        assertEquals("Push Day", plan.planName)
        assertEquals("Monday", plan.day)
        assertEquals("Coach Raj", plan.trainerName)
        assertEquals(listOf("Bench Press", "Push-up"), plan.exercises.map { it.name })
        val bench = plan.exercises.first()
        assertEquals("4", bench.sets)
        assertEquals("8", bench.reps)
        assertEquals("60.5", bench.weight)
        assertEquals(1, bench.dropSets)
        assertEquals("Pause on the chest", bench.notes)
        assertEquals("0.0", plan.exercises[1].weight)
    }

    @Test
    fun valuesOutsideTheUsualRangesAreClamped() {
        val plan = PlanAssignment.fromMap(
            "a1",
            doc("exercises" to listOf(mapOf("name" to "Squat", "sets" to 99, "reps" to 0, "weightKg" to 5000, "dropSets" to 40))),
        )!!
        val squat = plan.exercises.single()
        assertEquals("20", squat.sets)
        assertEquals("1", squat.reps)
        assertEquals("1000.0", squat.weight)
        assertEquals(5, squat.dropSets)
    }

    @Test
    fun onlyAPlanStillWaitingForAnAnswerAndWithSomethingInItIsRead() {
        assertNotNull(PlanAssignment.fromMap("a1", doc("status" to "delivered"))) // the phone has it, the user has not answered
        assertNull(PlanAssignment.fromMap("a1", doc("status" to "accepted")))
        assertNull(PlanAssignment.fromMap("a1", doc("status" to "declined")))
        assertNull(PlanAssignment.fromMap("a1", doc("day" to "Someday")))
        assertNull(PlanAssignment.fromMap("a1", doc("planName" to "  ")))
        assertNull(PlanAssignment.fromMap("a1", doc("exercises" to listOf(mapOf("name" to "")))))
        assertNull(PlanAssignment.fromMap("a1", doc("exercises" to "not a list")))
        assertNotNull(PlanAssignment.fromMap("a1", doc("day" to "Prebuilt")))
    }

    @Test
    fun offersSurviveBeingStoredOnThePhone() {
        val plans = listOf(plan("a1", "Monday", 5L, note = "Light"), plan("a2", "Prebuilt", 6L, trainer = ""))
        val restored = PlanAssignment.listFromJson(PlanAssignment.listToJson(plans))
        assertEquals(plans, restored)
        assertEquals(emptyList<PlanAssignment>(), PlanAssignment.listFromJson("not json"))
        assertEquals(emptyList<PlanAssignment>(), PlanAssignment.listFromJson(null))
    }

    // ---- A week's plans are one offer

    @Test
    fun sevenPlansSentTogetherAreOneOffer() {
        val sent = week.mapIndexed { i, day -> plan("p$i", day, 1_000_000L + i * minute) }
        val offers = groupOffers(sent)
        assertEquals(1, offers.size)
        val offer = offers.single()
        assertEquals(7, offer.plans.size)
        assertEquals(sent.map { it.id }, offer.ids)
        assertEquals(week, offer.days)
        assertEquals("Mon to Sun", offer.where)
        assertEquals("Coach Raj sent you 7 plans", offer.title)
    }

    @Test
    fun plansFromDifferentTrainersOrFarApartAreSeparateOffersNewestFirst() {
        val offers = groupOffers(
            listOf(
                plan("a", "Monday", 1_000_000L),
                plan("b", "Tuesday", 1_000_000L + 5 * minute),
                plan("c", "Wednesday", 1_000_000L + 6 * minute, trainer = "Coach Mia"), // another trainer, in between
                plan("d", "Thursday", 1_000_000L + 3 * 60 * minute), // three hours later
            ),
        )
        assertEquals(listOf(listOf("d"), listOf("c"), listOf("a", "b")), offers.map { it.ids })
    }

    @Test
    fun theGapIsBetweenNeighboursSoALongWeekOfSteadySendingStaysOneOffer() {
        val sent = (0 until 7).map { plan("p$it", week[it], it * 50 * minute) } // 50 minutes apart: 5 hours in all, still one offer
        assertEquals(1, groupOffers(sent).size)
        assertEquals(2, groupOffers(listOf(plan("x", "Monday", 0), plan("y", "Tuesday", OFFER_GAP_MILLIS + 1))).size)
        assertEquals(emptyList<PlanOffer>(), groupOffers(emptyList()))
    }

    @Test
    fun anOfferTitleAndPlaceFitHowManyPlansAndWhere() {
        val one = PlanOffer(listOf(plan("a", "Friday", 1L, trainer = "", name = "Legs")))
        assertEquals("Your trainer sent you a plan", one.title)
        assertEquals("Friday", one.where)
        assertEquals("Legs", one.names())
        val library = PlanOffer(listOf(plan("a", "Prebuilt", 1L)))
        assertEquals("Plan library", library.where)
        val many = PlanOffer(week.take(5).mapIndexed { i, day -> plan("p$i", day, 1L, name = "Day ${i + 1}") })
        assertEquals("Day 1, Day 2, Day 3 +2 more", many.names())
        assertEquals("Day 1, Day 2, Day 3, Day 4, Day 5", many.names(limit = 10))
    }

    @Test
    fun theFirstNoteTheTrainerWroteIsTheOffersNote() {
        val offer = PlanOffer(listOf(plan("a", "Monday", 1L), plan("b", "Tuesday", 2L, note = "Warm up well"), plan("c", "Wednesday", 3L, note = "Other")))
        assertEquals("Warm up well", offer.note)
        assertEquals("", PlanOffer(listOf(plan("a", "Monday", 1L))).note)
    }

    @Test
    fun daysAreDescribedForPeople() {
        assertEquals("Monday", describeDays(listOf("Monday")))
        assertEquals("Mon · Tue", describeDays(listOf("Monday", "Tuesday")))
        assertEquals("Mon to Wed", describeDays(listOf("Monday", "Tuesday", "Wednesday")))
        assertEquals("Mon · Wed · Fri", describeDays(listOf("Monday", "Wednesday", "Friday")))
        assertEquals("Mon to Sun", describeDays(week))
        assertEquals("Fri to Sun · Plan library", describeDays(listOf("Friday", "Saturday", "Sunday", "Prebuilt")))
        assertEquals("Plan library", describeDays(listOf("Prebuilt")))
        assertEquals("", describeDays(emptyList()))
    }

    @Test
    fun theUserIsToldWhatAcceptingDid() {
        val weekOffer = PlanOffer(week.mapIndexed { i, day -> plan("p$i", day, 1L) })
        assertEquals("Added 7 plans to your week", acceptedNotice(weekOffer, 7))
        assertEquals("Added 1 plan to Monday", acceptedNotice(PlanOffer(listOf(plan("a", "Monday", 1L))), 1))
        assertEquals("Added 1 plan to your plan library", acceptedNotice(PlanOffer(listOf(plan("a", "Prebuilt", 1L))), 1))
        assertEquals("Added 3 plans to your week", acceptedNotice(weekOffer, 3)) // some were already added
    }

    // ---- The activity line

    @Test
    fun theActivityLineSaysWhatWasAnsweredAndFromWhom() {
        val one = PlanOffer(listOf(plan("a", "Monday", 1L, name = "Push Day")))
        val accepted = planOfferAnswered(one, accepted = true, at = 5L)
        assertEquals(ActivityKind.PlanAssigned, accepted.type)
        assertEquals("Accepted plan \"Push Day\" (Monday) from Coach Raj", accepted.summary)
        assertEquals("Declined plan \"Push Day\" (Monday) from Coach Raj", planOfferAnswered(one, accepted = false, at = 5L).summary)
        val weekOffer = PlanOffer(week.mapIndexed { i, day -> plan("p$i", day, 1L, trainer = "") })
        assertEquals("Accepted 7 plans from their trainer (Mon to Sun)", planOfferAnswered(weekOffer, accepted = true, at = 5L).summary)
        val library = PlanOffer(listOf(plan("a", "Prebuilt", 1L, name = "Core")))
        assertEquals("Declined plan \"Core\" (library) from Coach Raj", planOfferAnswered(library, accepted = false, at = 5L).summary)
    }

    // ---- What was added, remembered

    @Test
    fun acceptedPlansSurviveBeingStored() {
        val plans = listOf(
            ReceivedPlan("a", 1, "Push", "Monday", receivedAt = 10),
            ReceivedPlan("b", 2, "Pull", "Tuesday", receivedAt = 20),
        )
        val restored = ReceivedPlan.listFromJson(ReceivedPlan.listToJson(plans))
        assertEquals(plans, restored)
        assertTrue(ReceivedPlan.listFromJson("not json").isEmpty())
        // a list stored by the first version, with the extra fields it had, is still read
        val old = """[{"id":"x","planId":3,"planName":"Old","day":"Friday","trainerName":"Coach","note":"n","receivedAt":7,"dismissed":true}]"""
        assertEquals(listOf(ReceivedPlan("x", 3, "Old", "Friday", receivedAt = 7)), ReceivedPlan.listFromJson(old))
    }
}
