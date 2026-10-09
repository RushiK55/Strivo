package com.example.strivo.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.strivo.R

/** The soft "gear" tick played while a [WheelPicker] scrolls. */
class WheelSound(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundId = soundPool.load(context.applicationContext, R.raw.gear, 1)
    private var loaded = false
    private var streamId = 0

    init {
        soundPool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
    }

    fun play() {
        if (!loaded) return
        if (streamId != 0) soundPool.stop(streamId)
        streamId = soundPool.play(soundId, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        const val VOLUME = 0.4f
    }
}
