package dev.auxdesign.voz.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.A11yBridge
import dev.auxdesign.voz.data.SecretStore
import dev.auxdesign.voz.util.Permissions

private const val PAGES = 4

/** Four TalkBack-friendly steps: microphone, floating mic, accessibility service, optional cloud key. */
@Composable
fun OnboardingScreen(graph: AppGraph, onFinish: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val bound by A11yBridge.service.collectAsStateWithLifecycle()

    val micGranted = remember(refresh) { Permissions.hasMic(context) }
    val overlayGranted = remember(refresh) { Permissions.canOverlay(context) }
    val serviceOn = remember(refresh, bound) { bound != null || A11yBridge.isEnabled(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onb_step, page + 1, PAGES), style = MaterialTheme.typography.labelLarge)
        when (page) {
            0 -> Step(
                title = stringResource(R.string.onb_title_mic),
                body = stringResource(R.string.onb_body_mic),
                done = micGranted,
            ) {
                Button(onClick = { permissionLauncher.launch(Permissions.runtimeRequest()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onb_btn_mic))
                }
            }
            1 -> Step(
                title = stringResource(R.string.onb_title_overlay),
                body = stringResource(R.string.onb_body_overlay),
                done = overlayGranted,
            ) {
                Button(onClick = { Permissions.open(context, Permissions.overlaySettings(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onb_btn_overlay))
                }
            }
            2 -> Step(
                title = stringResource(R.string.onb_title_a11y),
                body = stringResource(R.string.onb_body_a11y),
                done = serviceOn,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(stringResource(R.string.onb_restricted_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.onb_restricted_steps), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { Permissions.open(context, Permissions.appInfo(context)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onb_btn_app_info))
                    }
                }
                Button(onClick = { Permissions.open(context, Permissions.accessibilitySettings()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onb_btn_a11y))
                }
            }
            else -> CloudKeyStep(graph)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) {
                OutlinedButton(onClick = { page-- }) { Text(stringResource(R.string.onb_btn_back)) }
            }
            Spacer(Modifier.weight(1f))
            if (page < PAGES - 1) {
                Button(onClick = { page++ }) { Text(stringResource(R.string.onb_btn_next)) }
            } else {
                Button(onClick = onFinish) { Text(stringResource(R.string.onb_btn_finish)) }
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, actions: @Composable () -> Unit) {
    Heading(title)
    Text(body, style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(if (done) R.string.status_done else R.string.status_missing),
        style = MaterialTheme.typography.titleMedium,
        color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
    )
    if (!done) actions()
}

@Composable
private fun CloudKeyStep(graph: AppGraph) {
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(graph.secrets.has(SecretStore.GEMINI_API_KEY)) }
    Heading(stringResource(R.string.onb_title_cloud))
    Text(stringResource(R.string.onb_body_cloud), style = MaterialTheme.typography.bodyLarge)
    if (saved) {
        Text(stringResource(R.string.onb_key_saved), style = MaterialTheme.typography.titleMedium)
    } else {
        ApiKeyField(value = key, onChange = { key = it })
        Button(
            onClick = {
                saved = graph.secrets.put(SecretStore.GEMINI_API_KEY, key.trim())
                key = ""
            },
            enabled = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onb_btn_save_key))
        }
    }
}

@Composable
fun ApiKeyField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.onb_key_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
