package dev.auxdesign.voz.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Audio focus while VOZ listens (exclusive: videos pause so the mic hears the user)
 * and while it speaks (other audio ducks).
 */
class AudioFocus(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var current: AudioFocusRequest? = null

    @Synchronized
    fun request(exclusive: Boolean) {
        release()
        val am = audioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val gain = if (exclusive) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val request = AudioFocusRequest.Builder(gain).setAudioAttributes(attrs).build()
        am.requestAudioFocus(request)
        current = request
    }

    @Synchronized
    fun release() {
        val request = current ?: return
        audioManager?.abandonAudioFocusRequest(request)
        current = null
    }
}

/** Short non-verbal cues: start listening, stop listening, error. */
class Earcons {
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()

    fun start() {
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun stop() {
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun error() {
        tone?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }

    private companion object {
        const val VOLUME = 70
    }
}
