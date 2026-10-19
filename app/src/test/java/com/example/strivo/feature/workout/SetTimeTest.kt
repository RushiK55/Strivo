package com.example.strivo.feature.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class SetTimeTest {
    @Test
    fun `a time reads back as the seconds it was made from`() {
        for (seconds in listOf(0, 5, 59, 60, 65, 3599)) assertEquals(seconds, setTimeSeconds(setTimeText(seconds)))
    }

    @Test
    fun `a time that is empty or unreadable is no time`() {
        assertEquals(0, setTimeSeconds(""))
        assertEquals(0, setTimeSeconds("abc"))
        assertEquals(0, setTimeSeconds("1:2:3"))
    }
}
