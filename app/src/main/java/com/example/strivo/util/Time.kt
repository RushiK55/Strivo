package com.example.strivo.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val WeekDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

fun nowIso(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())

fun todayIso(): String = LocalDate.now().toString() // YYYY-MM-DD

fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

fun dayAbbr(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()

/** Monday..Sunday of the week containing [date]. */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return List(7) { monday.plusDays(it.toLong()) }
}

/** HH:MM:SS, hours are not wrapped. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** MM:SS */
fun formatDurationMinSec(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = (totalSeconds / 60)
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** Whole-number-aware formatting of a decimal with one fraction digit (like Dart's toStringAsFixed(1)). */
fun Double.fixed1(): String = "%.1f".format(Locale.US, this)
