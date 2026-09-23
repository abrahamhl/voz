package dev.auxdesign.voz.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.auxdesign.voz.core.model.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-to-speech with suspend "speak and wait until finished". A binding whose engine failed to start or died
 * (e.g. the TTS app was updated) is replaced, spaced by [RebuildPolicy], so VOZ does not stay mute for good.
 */
class TtsController(context: Context, private val focus: AudioFocus) {

    private val appContext = context.applicationContext

    /** Completed with true when spoken or stopped, false when the engine reported an error. */
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val rebuilds = RebuildPolicy(clock = SystemClock::elapsedRealtime)

    @Volatile
    private var engine = Engine()

    /** One TextToSpeech binding. */
    private inner class Engine : TextToSpeech.OnInitListener {
        val ready = CompletableDeferred<Boolean>()
        val tts = TextToSpeech(appContext, this)

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

                    override fun onDone(utteranceId: String?) = finish(utteranceId, true)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finish(utteranceId, false)

                    override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)

                    override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, true)
                })
            }
            ready.complete(ok)
        }
    }

    /**
     * Speaks [text] (split into engine-sized chunks) and suspends until it is finished, stopped or times out.
     * Returns false if it could not be spoken: the caller must not assume the user heard it.
     */
    suspend fun speak(text: String, lang: Lang, rate: Float): Boolean {
        if (text.isBlank()) return true
        val e = readyEngine() ?: return false
        stopped = false
        e.tts.setLanguage(Locale.forLanguageTag(lang.tag))
        e.tts.setSpeechRate(rate)
        val limit = minOf(TextToSpeech.getMaxSpeechInputLength(), CHUNK_CHARS)
        focus.request(exclusive = false)
        try {
            for (chunk in chunks(text, limit)) {
                val id = UUID.randomUUID().toString()
                val done = CompletableDeferred<Boolean>()
                waiters[id] = done
                try {
                    // A dead binding makes speak() return ERROR; it never reconnects on its own.
                    if (e.tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) return failed(e)
                    val spoken = withTimeoutOrNull(maxDurationMs(chunk, rate)) { done.await() }
                    if (stopped) return true
                    if (spoken == false) return failed(e)
                } finally {
                    waiters.remove(id)
                }
            }
        } finally {
            focus.release()
        }
        rebuilds.onSuccess()
        return true
    }

    @Volatile
    private var stopped = false

    fun stop() {
        stopped = true
        val e = engine
        if (e.ready.isCompleted) e.tts.stop()
        waiters.values.forEach { it.complete(true) }
        waiters.clear()
        focus.release()
    }

    fun shutdown() {
        stop()
        engine.tts.shutdown()
    }

    /** The current engine once initialised; a failed one is replaced once (if the policy allows it now). */
    private suspend fun readyEngine(): Engine? {
        val e = engine
        if (e.awaitReady()) return e
        if (!replace(e)) return null
        return engine.takeIf { it.awaitReady() }
    }

    private suspend fun Engine.awaitReady(): Boolean = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } == true

    private fun failed(e: Engine): Boolean {
        replace(e)
        return false
    }

    /** Swaps [failed] for a fresh binding. False if it is too soon to try again. */
    private fun replace(failed: Engine): Boolean = synchronized(this) {
        if (engine !== failed) return true
        if (!rebuilds.tryNow()) return false
        engine = Engine()
        runCatching { failed.tts.shutdown() }
        true
    }

    private fun finish(id: String?, ok: Boolean) {
        if (id != null) waiters.remove(id)?.complete(ok)
    }

    private fun maxDurationMs(text: String, rate: Float): Long = (2_000 + text.length * 120 / rate.coerceAtLeast(0.5f)).toLong()

    companion object {
        private const val CHUNK_CHARS = 3_500
        private const val INIT_TIMEOUT_MS = 5_000L

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

/**
 * Spaces rebuilds of a failed speech engine: the first right away, then [backoffMs] apart (the last value
 * repeats), so a broken engine is retried without a rebuild storm. A successful utterance resets it.
 */
class RebuildPolicy(private val clock: () -> Long, private val backoffMs: List<Long> = listOf(2_000L, 10_000L, 30_000L)) {
    private var rebuilds = 0
    private var last = 0L

    @Synchronized
    fun tryNow(): Boolean {
        val now = clock()
        if (rebuilds > 0 && now - last < backoffMs[(rebuilds - 1).coerceAtMost(backoffMs.lastIndex)]) return false
        rebuilds++
        last = now
        return true
    }

    @Synchronized
    fun onSuccess() {
        rebuilds = 0
    }
}
