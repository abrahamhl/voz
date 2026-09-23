package dev.auxdesign.voz

import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dev.auxdesign.voz.a11y.ActionExecutor
import dev.auxdesign.voz.core.route.Router
import dev.auxdesign.voz.data.ActionLog
import dev.auxdesign.voz.data.AesGcmCipher
import dev.auxdesign.voz.data.EncryptedSecretStore
import dev.auxdesign.voz.data.InstalledApps
import dev.auxdesign.voz.data.KeystoreKeys
import dev.auxdesign.voz.data.LogEntry
import dev.auxdesign.voz.data.SecretStore
import dev.auxdesign.voz.data.SettingsStore
import dev.auxdesign.voz.data.SharedPrefsKeyValueStore
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.engine.CloudEngineFactory
import dev.auxdesign.voz.engine.EngineProvider
import dev.auxdesign.voz.engine.JevEngine
import dev.auxdesign.voz.engine.LocalEngine
import dev.auxdesign.voz.flows.YouTubeFlows
import dev.auxdesign.voz.util.Strings
import dev.auxdesign.voz.voice.AudioFocus
import dev.auxdesign.voz.voice.Earcons
import dev.auxdesign.voz.voice.SpeechController
import dev.auxdesign.voz.voice.TtsController
import dev.auxdesign.voz.voice.VoiceSession
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

class VozApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as VozApp).graph

/** Hand-written dependency graph (no DI framework on purpose). */
class AppGraph(app: Application) {
    val actionLog = ActionLog(File(app.filesDir, "action_log.jsonl"))

    /** Last-resort handler: an unexpected error ends the voice turn and is logged, instead of crashing the app. */
    private val crashGuard = CoroutineExceptionHandler { _, e ->
        actionLog.add(LogEntry.Kind.FAILED, "unexpected ${e.javaClass.simpleName}")
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)

    val settingsStore = SettingsStore(app)
    val settings: StateFlow<VozSettings?> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null)

    val secrets: SecretStore = EncryptedSecretStore(
        SharedPrefsKeyValueStore(app.getSharedPreferences("voz_secrets", Context.MODE_PRIVATE)),
        AesGcmCipher { KeystoreKeys.getOrCreate("voz_secrets_key") },
    )

    val installedApps = InstalledApps(app)
    val strings = Strings(app)

    private val audioFocus = AudioFocus(app)
    val tts = TtsController(app, audioFocus)
    val speech = SpeechController(app, audioFocus)
    val earcons = Earcons()

    private val cloudFactory = CloudEngineFactory(secrets)
    val engines = EngineProvider(LocalEngine(), JevEngine(), app.resources.getBoolean(R.bool.cloud_brain_available), cloudFactory::engine)

    val executor = ActionExecutor(app, strings, installedApps, YouTubeFlows())

    val session = VoiceSession(
        scope = scope,
        speech = speech,
        tts = tts,
        earcons = earcons,
        settings = settings,
        strings = strings,
        router = Router(),
        engines = engines,
        executor = executor,
        apps = installedApps,
        log = actionLog,
        screenReaderOn = { app.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true },
    )
}
