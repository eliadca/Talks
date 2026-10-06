package com.eliadca.talks.speech

import android.content.Context
import android.media.AudioManager

/**
 * Silences the beeps some speech recognisers play between phrases by muting a few audio streams
 * while Talks listens. A note of what was muted is kept on disk so that, should the app die while
 * muted, the next launch restores the sound.
 */
class SoundMuter(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("sound_muter", Context.MODE_PRIVATE)
    private val streams = intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)

    fun mute() {
        if (prefs.getInt(KEY_MASK, 0) != 0) return // already muted by us
        var mask = 0
        for ((i, s) in streams.withIndex()) {
            try {
                if (!audio.isStreamMute(s)) {
                    audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                    mask = mask or (1 shl i)
                }
            } catch (_: SecurityException) {
                // Some streams need Do Not Disturb access; skip them.
            }
        }
        prefs.edit().putInt(KEY_MASK, mask).apply()
    }

    fun unmute() {
        val mask = prefs.getInt(KEY_MASK, 0)
        if (mask == 0) return
        for ((i, s) in streams.withIndex()) {
            if (mask and (1 shl i) != 0) {
                try {
                    audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: SecurityException) {
                }
            }
        }
        prefs.edit().putInt(KEY_MASK, 0).apply()
    }

    /** Call at launch: undoes a mute left behind by a crash. */
    fun restoreIfLeftMuted() = unmute()

    private companion object {
        const val KEY_MASK = "mutedMask"
    }
}
