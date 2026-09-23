package dev.auxdesign.voz.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.A11yBridge
import dev.auxdesign.voz.util.Permissions

private const val PAGES = 3

/**
 * Three steps in the order that matters: microphone and accessibility service (required), then the optional
 * floating mic. Next is never blocked; system Back goes to the previous step.
 */
@Composable
fun OnboardingScreen(startStep: Int, canLeave: Boolean, onFinish: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    var page by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, PAGES - 1)) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val bound by A11yBridge.service.collectAsStateWithLifecycle()

    val micGranted = remember(refresh) { Permissions.hasMic(context) }
    val overlayGranted = remember(refresh) { Permissions.canOverlay(context) }
    val serviceOn = remember(refresh, bound) { bound != null || A11yBridge.isEnabled(context) }
    var micBlocked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        val activity = hostActivity
        micBlocked = result[Manifest.permission.RECORD_AUDIO] != true && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(enabled = page > 0 || canLeave) { if (page > 0) page-- else onLeave() }

    val title = stringResource(
        when (page) {
            SetupStep.MIC -> R.string.onb_title_mic
            SetupStep.ACCESSIBILITY -> R.string.onb_title_a11y
            else -> R.string.onb_title_overlay
        },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (page == 0) Text(stringResource(R.string.onb_intro), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.onb_step, page + 1, PAGES), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(if (page == SetupStep.BUBBLE) R.string.onb_optional else R.string.onb_required),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Heading(title)
        when (page) {
            SetupStep.MIC -> {
                Text(stringResource(R.string.onb_body_mic), style = MaterialTheme.typography.bodyLarge)
                PermissionStatus(micGranted)
                if (!micGranted) {
                    if (micBlocked) {
                        Text(stringResource(R.string.banner_mic_blocked_body), style = MaterialTheme.typography.bodyLarge)
                        FullWidthButton(stringResource(R.string.onb_btn_app_info)) { Permissions.open(context, Permissions.appInfo(context)) }
                    } else {
                        FullWidthButton(stringResource(R.string.onb_btn_mic)) { permissionLauncher.launch(Permissions.runtimeRequest()) }
                    }
                }
            }
            SetupStep.ACCESSIBILITY -> {
                Text(stringResource(R.string.onb_body_a11y), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.onb_a11y_warning), style = MaterialTheme.typography.bodyMedium)
                PermissionStatus(serviceOn)
                if (!serviceOn) {
                    FullWidthButton(stringResource(R.string.onb_btn_a11y)) { Permissions.open(context, Permissions.accessibilitySettings()) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RestrictedSettingsHelp()
                }
            }
            else -> {
                Text(stringResource(R.string.onb_body_overlay), style = MaterialTheme.typography.bodyLarge)
                PermissionStatus(overlayGranted)
                if (!overlayGranted) {
                    FullWidthButton(stringResource(R.string.onb_btn_overlay)) { Permissions.open(context, Permissions.overlaySettings(context)) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (page > 0 || canLeave) {
                OutlinedButton(
                    onClick = { if (page > 0) page-- else onLeave() },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.onb_btn_back), style = MaterialTheme.typography.labelLarge) }
            }
            Button(
                onClick = { if (page < PAGES - 1) page++ else onFinish() },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (page < PAGES - 1) R.string.onb_btn_next else R.string.onb_btn_finish), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Android 13+ blocks accessibility services of apps installed outside an app store until the user allows
 * "restricted settings". The menu item only appears after Android has blocked one attempt, so the steps
 * start with that attempt. Starts expanded: without it VOZ can't work on those phones.
 */
@Composable
private fun RestrictedSettingsHelp() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(true) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Expander(stringResource(R.string.onb_restricted_title), open, onToggle = { open = !open }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.onb_restricted_1, R.string.onb_restricted_2, R.string.onb_restricted_3, R.string.onb_restricted_4)
                        .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
                    OutlinedButton(
                        onClick = { Permissions.open(context, Permissions.appInfo(context)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.onb_btn_app_info), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun FullWidthButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Password-style field: no autocorrect, no suggestions, masked. */
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
