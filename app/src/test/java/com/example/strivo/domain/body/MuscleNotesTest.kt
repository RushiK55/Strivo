package com.example.strivo.domain.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleNotesTest {
    @Test
    fun aNoteFromTheCloudIsReadAndTidied() {
        val note = MuscleNotes.fromMap(mapOf("subtitle" to "  Back wings ", "about" to " Our own words. ", "updatedAt" to 5L, "updatedBy" to "x"))!!
        assertEquals(MuscleNote("Back wings", "Our own words."), note)
        assertEquals(MuscleNote("Wings", ""), MuscleNotes.fromMap(mapOf("subtitle" to "Wings", "about" to ""))) // a subtitle on its own
        assertEquals(MuscleNote("", "Words"), MuscleNotes.fromMap(mapOf("about" to "Words")))
    }

    @Test
    fun aNoteThatSaysNothingIsNoNote() {
        assertNull(MuscleNotes.fromMap(emptyMap()))
        assertNull(MuscleNotes.fromMap(mapOf("subtitle" to "  ", "about" to "")))
        assertNull(MuscleNotes.fromMap(mapOf("subtitle" to 5, "about" to listOf("x"))))
    }

    @Test
    fun tooLongTextIsCutToWhatTheRulesAllow() {
        val note = MuscleNotes.fromMap(mapOf("subtitle" to "s".repeat(200), "about" to "a".repeat(5000)))!!
        assertEquals(MuscleNotes.MAX_SUBTITLE, note.subtitle.length)
        assertEquals(MuscleNotes.MAX_ABOUT, note.about.length)
    }

    @Test
    fun theNotesSurviveBeingKeptOnThePhone() {
        val notes = mapOf("lats" to MuscleNote("Back wings", "Words"), "chest" to MuscleNote("", "Only words"))
        assertEquals(notes, MuscleNotes.fromJson(MuscleNotes.toJson(notes)))
        assertTrue(MuscleNotes.fromJson("not json").isEmpty())
        assertTrue(MuscleNotes.fromJson(null).isEmpty())
        assertTrue(MuscleNotes.fromJson("""[{"id":"","subtitle":"x"},{"id":"abs"}]""").isEmpty()) // no id, or nothing said
    }
}
