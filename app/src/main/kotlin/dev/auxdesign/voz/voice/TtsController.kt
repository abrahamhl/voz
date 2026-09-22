package dev.auxdesign.voz.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.auxdesign.voz.core.model.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Text-to-speech with suspend "speak and wait until finished". */
class TtsController(context: Context, private val focus: AudioFocus) : TextToSpeech.OnInitListener {

    private val ready = CompletableDeferred<Boolean>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS
        if (ok) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finish(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
            })
        }
        ready.complete(ok)
    }

    /** Speaks [text] (split into engine-sized chunks) and suspends until it is finished, stopped or times out. */
    suspend fun speak(text: String, lang: Lang, rate: Float) {
        if (text.isBlank() || !ready.await()) return
        stopped = false
        tts.setLanguage(Locale.forLanguageTag(lang.tag))
        tts.setSpeechRate(rate)
        val limit = minOf(TextToSpeech.getMaxSpeechInputLength(), CHUNK_CHARS)
        focus.request(exclusive = false)
        try {
            for (chunk in chunks(text, limit)) {
                val id = UUID.randomUUID().toString()
                val done = CompletableDeferred<Unit>()
                waiters[id] = done
                try {
                    if (tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) return
                    withTimeoutOrNull(maxDurationMs(chunk, rate)) { done.await() }
                    if (stopped) return
                } finally {
                    waiters.remove(id)
                }
            }
        } finally {
            focus.release()
        }
    }

    @Volatile
    private var stopped = false

    fun stop() {
        stopped = true
        if (ready.isCompleted) tts.stop()
        waiters.values.forEach { it.complete(Unit) }
        waiters.clear()
        focus.release()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun finish(id: String?) {
        if (id != null) waiters.remove(id)?.complete(Unit)
    }

    private fun maxDurationMs(text: String, rate: Float): Long = (2_000 + text.length * 120 / rate.coerceAtLeast(0.5f)).toLong()

    companion object {
        private const val CHUNK_CHARS = 3_500

        /** Splits on sentence ends (then spaces) so no chunk exceeds [limit] characters. */
        fun chunks(text: String, limit: Int): List<String> {
            val clean = text.trim()
            if (clean.length <= limit) return listOf(clean)
            val out = ArrayList<String>()
            var rest = clean
            while (rest.length > limit) {
                val window = rest.substring(0, limit)
                val sentence = maxOf(window.lastIndexOf(". "), window.lastIndexOf("? "), window.lastIndexOf("! "))
                val cut = when {
                    sentence > limit / 2 -> sentence + 1
                    window.lastIndexOf(' ') > 0 -> window.lastIndexOf(' ')
                    else -> limit
                }
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out += rest
            return out
        }
    }
}
