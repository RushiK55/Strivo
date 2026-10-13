package com.example.strivo.feature.workout

import android.os.SystemClock

class Stopwatch {
    private var startedAt = 0L
    private var accumulated = 0L
    var isRunning = false
        private set

    val elapsedMillis: Long
        get() = accumulated + if (isRunning) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start() {
        if (isRunning) return
        startedAt = SystemClock.elapsedRealtime()
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        accumulated += SystemClock.elapsedRealtime() - startedAt
        isRunning = false
    }

    fun reset() {
        accumulated = 0L
        startedAt = SystemClock.elapsedRealtime()
    }
}
