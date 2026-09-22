package dev.auxdesign.voz.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.auxdesign.voz.core.model.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One-shot speech recognition. Prefers on-device recognition and retries online if the language pack is missing. */
class SpeechController(private val context: Context, private val focus: AudioFocus) {

    sealed interface Result {
        data class Heard(val texts: List<String>) : Result
        data object NoMatch : Result
        data class Error(val code: Int) : Result {
            val isPermission: Boolean get() = code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            val isUnavailable: Boolean get() = code == ERROR_UNAVAILABLE
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var active: SpeechRecognizer? = null
    private val partialText = MutableStateFlow("")
    val partial: StateFlow<String> = partialText.asStateFlow()

    suspend fun listen(lang: Lang): Result {
        val first = listenOnce(lang, preferOffline = true)
        return if (first is Result.Error && first.code in OFFLINE_RETRY) listenOnce(lang, preferOffline = false) else first
    }

    fun cancel() {
        main.post {
            active?.let {
                it.cancel()
                it.destroy()
            }
            active = null
        }
    }

    private suspend fun listenOnce(lang: Lang, preferOffline: Boolean): Result = withContext(Dispatchers.Main.immediate) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return@withContext Result.Error(ERROR_UNAVAILABLE)
        focus.request(exclusive = true)
        try {
            // Some recognition services never call back if they die: never stay stuck listening.
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) { recognize(lang, preferOffline) } ?: Result.NoMatch
        } finally {
            focus.release()
        }
    }

    private suspend fun recognize(lang: Lang, preferOffline: Boolean): Result =
        suspendCancellableCoroutine<Result> { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            active = recognizer
            partialText.value = ""
            fun finish(result: Result) {
                recognizer.destroy()
                if (active === recognizer) active = null
                if (cont.isActive) cont.resume(result)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) =
                    finish(if (error in SILENCE) Result.NoMatch else Result.Error(error))

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partialText.value = it }
                }

                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                    finish(if (texts.isEmpty()) Result.NoMatch else Result.Heard(texts))
                }
            })
            cont.invokeOnCancellation {
                main.post {
                    recognizer.cancel()
                    recognizer.destroy()
                    if (active === recognizer) active = null
                }
            }
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
            )
        }

    companion object {
        const val ERROR_UNAVAILABLE = -1
        private const val MAX_RESULTS = 3
        private const val LISTEN_TIMEOUT_MS = 20_000L

        // The user said nothing, or nothing recognisable: that is "I didn't catch that", not a failure.
        private val SILENCE = setOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH)

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31) and network errors.
        private val OFFLINE_RETRY = setOf(12, 13, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER)
    }
}
