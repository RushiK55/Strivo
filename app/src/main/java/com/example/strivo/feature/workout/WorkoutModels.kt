package com.example.strivo.feature.workout

import com.example.strivo.domain.model.Exercise

data class WorkoutState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val isWorkoutRunning: Boolean = false,
    /** The workout was finished (all of it, or early): it is done for the day, and nothing in it can be started again. */
    val isFinished: Boolean = false,
    /** The main button paused the workout: every clock (workout, set and rest) is frozen until it is resumed. */
    val isPaused: Boolean = false,
    val showRestTimer: Boolean = false,
    val isExerciseRest: Boolean = false,
    val activeSetExerciseIndex: Int? = null,
    val activeSetIndex: Int? = null,
    val activeRestExerciseIndex: Int? = null,
    val activeRestSetIndex: Int? = null,
    val expandedExercises: Set<Int> = emptySet(),
    /**
     * The exercise the user is working on: the one they last started a set of (or opened the workout at). The workout stays
     * in it until it is done, then goes back to the earliest exercise still to do. Null once that exercise is finished.
     */
    val currentExerciseIndex: Int? = null,
    /** What the last finished set was saved as, e.g. "Bench Press · Set 2: 40 kg × 10"; cleared when the next set starts. */
    val lastSavedSet: String? = null,
)

enum class MessageKind { Info, Warning, Error }

sealed interface WorkoutEvent {
    data class Message(val text: String, val kind: MessageKind) : WorkoutEvent
    data class ShowSummary(val exerciseIndex: Int) : WorkoutEvent
    data object ShowComplete : WorkoutEvent

    /** Resuming after a pause: bring the full-screen set view back so the workout carries on from there. */
    data object FocusOnNextSet : WorkoutEvent
}
