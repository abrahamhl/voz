package dev.auxdesign.voz.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.A11yBridge
import dev.auxdesign.voz.overlay.BubbleService
import dev.auxdesign.voz.util.Permissions
import dev.auxdesign.voz.voice.VoiceSession

@Composable
fun HomeScreen(graph: AppGraph, onSettings: () -> Unit, onLog: () -> Unit, onSetup: () -> Unit) {
    val context = LocalContext.current
    val phase by graph.session.phase.collectAsStateWithLifecycle()
    val heard by graph.session.heard.collectAsStateWithLifecycle()
    val reply by graph.session.reply.collectAsStateWithLifecycle()
    val bound by A11yBridge.service.collectAsStateWithLifecycle()
    val bubbleOn by BubbleService.running.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val serviceOn = remember(refresh, bound) { bound != null || A11yBridge.isEnabled(context) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[android.Manifest.permission.RECORD_AUDIO] == true) graph.session.start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading(stringResource(R.string.app_name))
        Text(
            text = stringResource(if (serviceOn) R.string.home_service_on else R.string.home_service_off),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        MicButton(phase) {
            if (graph.session.isBusy || Permissions.hasMic(context)) graph.session.toggle() else micLauncher.launch(Permissions.runtimeRequest())
        }

        if (heard.isNotBlank()) Text(stringResource(R.string.home_you_said, heard), style = MaterialTheme.typography.bodyLarge)
        if (reply.isNotBlank()) Text(stringResource(R.string.home_voz_said, reply), style = MaterialTheme.typography.bodyLarge)

        val canBubble = remember(refresh) { BubbleService.canStart(context) }
        OutlinedButton(
            onClick = { if (bubbleOn) BubbleService.stop(context) else if (canBubble) BubbleService.start(context) else onSetup() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (bubbleOn) R.string.home_bubble_hide else R.string.home_bubble_show))
        }
        if (!bubbleOn && !canBubble) Text(stringResource(R.string.home_bubble_needs), style = MaterialTheme.typography.bodyMedium)

        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_settings)) }
        OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_log)) }
        OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_setup)) }

        Text(stringResource(R.string.home_try_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.home_try_examples), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MicButton(phase: VoiceSession.Phase, onClick: () -> Unit) {
    val label = stringResource(
        when (phase) {
            VoiceSession.Phase.IDLE -> R.string.home_tap_to_speak
            VoiceSession.Phase.LISTENING -> R.string.home_listening
            VoiceSession.Phase.WORKING -> R.string.home_thinking
            VoiceSession.Phase.SPEAKING -> R.string.home_speaking
            VoiceSession.Phase.CONFIRMING -> R.string.home_confirming
        },
    )
    val busy = phase != VoiceSession.Phase.IDLE
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(220.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (busy) colorResource(R.color.voz_listening) else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(80.dp))
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
    }
}
