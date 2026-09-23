package dev.auxdesign.voz.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.A11yBridge
import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.overlay.BubbleService
import dev.auxdesign.voz.util.Permissions
import dev.auxdesign.voz.util.effectiveLang
import dev.auxdesign.voz.voice.OrbState
import dev.auxdesign.voz.voice.Turn
import dev.auxdesign.voz.voice.VoiceSession
import dev.auxdesign.voz.voice.busy
import dev.auxdesign.voz.voice.orbState

/** Onboarding pages, also used by "Fix" actions to open the right step. */
object SetupStep {
    const val MIC = 0
    const val ACCESSIBILITY = 1
    const val BUBBLE = 2
}

@Composable
fun HomeScreen(graph: AppGraph, onSettings: () -> Unit, onHistory: () -> Unit, onSetup: (Int) -> Unit) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    val session = graph.session
    val turn by session.turn.collectAsStateWithLifecycle()
    val micOpen by session.micOpen.collectAsStateWithLifecycle()
    val partial by session.partial.collectAsStateWithLifecycle()
    val level by session.level.collectAsStateWithLifecycle()
    val phase by session.phase.collectAsStateWithLifecycle()
    val speechOutput by session.speechOutput.collectAsStateWithLifecycle()
    val bound by A11yBridge.service.collectAsStateWithLifecycle()
    val bubbleOn by BubbleService.running.collectAsStateWithLifecycle()

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val micGranted = remember(refresh) { Permissions.hasMic(context) }
    val serviceOn = remember(refresh, bound) { bound != null || A11yBridge.isEnabled(context) }
    val canBubble = remember(refresh) { BubbleService.canStart(context) }
    val speechAvailable = remember(refresh) { SpeechRecognizer.isRecognitionAvailable(context) }
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    var bubbleFailed by remember { mutableStateOf(false) }
    var examplesOpen by rememberSaveable { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            micBlocked = false
            session.start()
        } else {
            // No rationale after a denial = "Don't allow" twice: Android won't ask again, only App info can fix it.
            val activity = hostActivity
            micBlocked = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
    }
    val openAppInfo = { Permissions.open(context, Permissions.appInfo(context)) }

    val state = orbState(turn, micOpen, phase == VoiceSession.Phase.SPEAKING)
    val uiLang = remember { effectiveLang(null) }
    val acting = turn as? Turn.Acting
    val stepText = acting?.let { stringResource(R.string.orb_sd_acting, it.index + 1, it.steps.size) }
    val stateLine = when (state) {
        OrbState.IDLE -> stringResource(R.string.orb_idle)
        OrbState.STARTING -> stringResource(R.string.orb_starting)
        OrbState.LISTENING -> stringResource(R.string.orb_listening)
        OrbState.UNDERSTANDING -> stringResource(R.string.orb_understanding)
        OrbState.ACTING -> acting?.let {
            stringResource(R.string.orb_acting, it.index + 1, it.steps.size, session.text.describe(it.steps[it.index], uiLang))
        } ?: stringResource(R.string.orb_understanding)
        OrbState.SPEAKING -> stringResource(R.string.orb_speaking)
        OrbState.CONFIRMING -> stringResource(R.string.orb_confirming)
    }
    val onToggle = {
        when {
            session.isBusy || micGranted -> session.toggle()
            micBlocked -> openAppInfo()
            else -> micLauncher.launch(Permissions.runtimeRequest())
        }
    }
    val finished = turn as? Turn.Finished
    val showExamples = examplesOpen || finished?.result == Turn.Result.NOT_UNDERSTOOD

    val header: @Composable ColumnScope.() -> Unit = {
        Heading(stringResource(R.string.app_name))
        Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodyLarge)
        if (!micGranted) {
            StatusBanner(
                kind = BannerKind.ATTENTION,
                title = stringResource(R.string.banner_mic_title),
                body = stringResource(if (micBlocked) R.string.banner_mic_blocked_body else R.string.banner_mic_body),
                action = stringResource(if (micBlocked) R.string.onb_btn_app_info else R.string.onb_btn_mic),
                live = true,
                onAction = { if (micBlocked) openAppInfo() else micLauncher.launch(Permissions.runtimeRequest()) },
            )
        }
        if (!serviceOn) {
            StatusBanner(
                kind = BannerKind.ATTENTION,
                title = stringResource(R.string.banner_a11y_title),
                body = stringResource(R.string.banner_a11y_body),
                action = stringResource(R.string.banner_turn_on),
                live = true,
                onAction = { onSetup(SetupStep.ACCESSIBILITY) },
            )
        }
        if (!speechAvailable) {
            StatusBanner(BannerKind.ERROR, stringResource(R.string.banner_speech_title), stringResource(R.string.banner_speech_body))
        }
        if (!speechOutput) {
            StatusBanner(BannerKind.ERROR, stringResource(R.string.banner_tts_title), stringResource(R.string.banner_tts_body))
        }
        if (bubbleFailed && !bubbleOn) {
            StatusBanner(
                kind = BannerKind.ATTENTION,
                title = stringResource(R.string.banner_bubble_title),
                body = stringResource(R.string.home_bubble_needs),
                action = stringResource(R.string.fix_permissions),
                onAction = { onSetup(SetupStep.BUBBLE) },
            )
        }
    }

    val orb: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { diameter ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            VoiceOrb(state = state, level = level, stepText = stepText, diameter = diameter, onToggle = onToggle)
            OrbStateLine(stateLine, if (state.busy) stringResource(R.string.home_stop_hint) else null)
            if (micOpen && partial.isNotBlank() && turn !is Turn.Confirming) {
                Text(
                    stringResource(R.string.home_partial, partial),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 600.dp),
                )
            }
        }
    }

    val details: @Composable ColumnScope.() -> Unit = {
        (turn as? Turn.Confirming)?.let { confirming ->
            ConfirmSheet(
                what = confirming.what,
                partial = if (micOpen) partial else "",
                onYes = { session.answerConfirmation(true) },
                onNo = { session.answerConfirmation(false) },
            )
        }
        LastCommandCard(
            turn = turn,
            describe = { steps -> session.text.describe(steps, uiLang) },
            describeSource = { source -> session.text.source(source, uiLang) },
            onRetry = onToggle,
        )
        if (!state.busy) {
            Expander(stringResource(R.string.examples_title), showExamples, onToggle = { examplesOpen = !showExamples }) {
                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stringArrayResource(R.array.home_examples).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Spacer(Modifier.heightIn(min = 8.dp))
                    Text(stringResource(R.string.home_stop_tip), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.home_a11y_button_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SwitchRow(
                label = stringResource(R.string.home_bubble_switch),
                description = stringResource(if (canBubble || bubbleOn) R.string.home_bubble_switch_desc else R.string.home_bubble_needs_short),
                checked = bubbleOn,
                onChange = { on ->
                    if (!on) {
                        BubbleService.stop(context)
                    } else if (canBubble) {
                        bubbleFailed = !BubbleService.start(context)
                    } else {
                        onSetup(SetupStep.BUBBLE)
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onHistory, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    VozIcon(R.drawable.ic_history, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_history), style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(onClick = onSettings, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    VozIcon(R.drawable.ic_settings, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_settings), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    val title = stringResource(R.string.app_name)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().semantics { paneTitle = title }) {
        val diameter = (if (maxWidth < maxHeight) maxWidth else maxHeight).times(0.45f).coerceIn(120.dp, 200.dp)
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { orb(diameter) }
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    header()
                    details()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                header()
                orb(diameter)
                details()
            }
        }
    }
}

/** What was heard, understood, done and the result: only rows whose data exists. */
@Composable
private fun LastCommandCard(
    turn: Turn,
    describe: (List<Action>) -> String,
    describeSource: (PlanSource) -> String,
    onRetry: () -> Unit,
) {
    val heard: String?
    val steps: List<Action>
    val source: PlanSource?
    when (turn) {
        is Turn.Understanding -> { heard = turn.heard; steps = emptyList(); source = null }
        is Turn.Acting -> { heard = turn.heard; steps = turn.steps; source = turn.source }
        is Turn.Confirming -> { heard = turn.heard; steps = emptyList(); source = null }
        is Turn.Finished -> { heard = turn.heard; steps = turn.steps; source = turn.source }
        Turn.Idle, Turn.Listening -> return
    }
    val voz = LocalVozColors.current
    VozCard {
        Text(
            stringResource(R.string.card_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (!heard.isNullOrBlank()) CardRow(stringResource(R.string.card_you_said), heard)
        if (steps.isNotEmpty()) {
            CardRow(stringResource(R.string.card_understood), describe(steps))
            if (source != null) {
                CardRow(
                    stringResource(R.string.card_where),
                    describeSource(source),
                    icon = if (source == PlanSource.CLOUD) R.drawable.ic_cloud else R.drawable.ic_smartphone,
                )
            }
        }
        if (turn is Turn.Finished) {
            val failure = turn.result in FAILURES
            val (icon, tint) = when {
                turn.result == Turn.Result.DONE -> R.drawable.ic_check_circle to voz.success
                failure -> R.drawable.ic_error to MaterialTheme.colorScheme.error
                turn.result == Turn.Result.BLOCKED -> R.drawable.ic_block to MaterialTheme.colorScheme.onSurfaceVariant
                else -> R.drawable.ic_info to MaterialTheme.colorScheme.onSurfaceVariant
            }
            val label = stringResource(resultLabel(turn.result))
            val reply = turn.reply
            CardRow(
                stringResource(R.string.card_result),
                if (reply.isNullOrBlank() || turn.result == Turn.Result.DONE && reply == label) label else "$label. $reply",
                icon = icon,
                iconTint = tint,
            )
            if (failure) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.try_again), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val FAILURES = setOf(
    Turn.Result.FAILED, Turn.Result.CLOUD_ERROR, Turn.Result.SPEECH_ERROR, Turn.Result.MIC_DENIED, Turn.Result.SPEECH_UNAVAILABLE,
)

private fun resultLabel(result: Turn.Result): Int = when (result) {
    Turn.Result.DONE -> R.string.result_done
    Turn.Result.FAILED -> R.string.result_failed
    Turn.Result.CANCELLED -> R.string.result_cancelled
    Turn.Result.STOPPED -> R.string.result_stopped
    Turn.Result.BLOCKED -> R.string.result_blocked
    Turn.Result.NOT_HEARD -> R.string.result_not_heard
    Turn.Result.NOT_UNDERSTOOD -> R.string.result_not_understood
    Turn.Result.CLOUD_ERROR -> R.string.result_cloud_error
    Turn.Result.MIC_DENIED -> R.string.result_mic_denied
    Turn.Result.SPEECH_UNAVAILABLE -> R.string.result_speech_unavailable
    Turn.Result.SPEECH_ERROR -> R.string.result_speech_error
}
