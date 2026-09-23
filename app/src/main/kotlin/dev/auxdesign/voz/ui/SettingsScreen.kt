package dev.auxdesign.voz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.data.BubbleSize
import dev.auxdesign.voz.data.SecretStore
import dev.auxdesign.voz.data.VozSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(graph: AppGraph, settings: VozSettings, onBack: () -> Unit, onSetup: () -> Unit, onAbout: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun update(transform: (VozSettings) -> VozSettings) {
        scope.launch { graph.settingsStore.update(transform) }
    }
    // The Keystore can be slow (StrongBox, first key generation): never touch it on the main thread.
    var hasKey by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { graph.secrets.has(SecretStore.GEMINI_API_KEY) } }
    // The key stays in memory only (never in saved instance state): rotating the phone clears the field.
    var keyInput by remember { mutableStateOf("") }
    var askConsent by rememberSaveable { mutableStateOf(false) }
    var askDeleteKey by rememberSaveable { mutableStateOf(false) }

    val title = stringResource(R.string.settings_title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title, onBack)

        SectionHeading(stringResource(R.string.settings_group_voice))
        Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.selectableGroup()) {
            listOf(
                null to stringResource(R.string.settings_language_system),
                Lang.ES to stringResource(R.string.lang_es),
                Lang.EN to stringResource(R.string.lang_en),
                Lang.NL to stringResource(R.string.lang_nl),
            ).forEach { (lang, label) ->
                RadioRow(label, selected = settings.language == lang) { update { it.copy(language = lang) } }
            }
        }
        val rate = settings.speechRate
        Stepper(
            label = stringResource(R.string.settings_speech_rate_label),
            value = stringResource(R.string.settings_rate_value, rate),
            decreaseLabel = stringResource(R.string.rate_decrease),
            increaseLabel = stringResource(R.string.rate_increase),
            canDecrease = rate > VozSettings.MIN_RATE + 0.01f,
            canIncrease = rate < VozSettings.MAX_RATE - 0.01f,
            onDecrease = { update { it.copy(speechRate = step(it.speechRate, -RATE_STEP)) } },
            onIncrease = { update { it.copy(speechRate = step(it.speechRate, RATE_STEP)) } },
        )

        SectionHeading(stringResource(R.string.settings_group_bubble))
        Text(stringResource(R.string.settings_bubble_size), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            listOf(
                BubbleSize.SMALL to R.string.bubble_small,
                BubbleSize.MEDIUM to R.string.bubble_medium,
                BubbleSize.LARGE to R.string.bubble_large,
            ).forEach { (size, label) ->
                RadioRow(stringResource(label), selected = settings.bubbleSize == size) { update { it.copy(bubbleSize = size) } }
            }
        }

        SectionHeading(stringResource(R.string.settings_group_display))
        SwitchRow(
            label = stringResource(R.string.settings_high_contrast),
            description = stringResource(R.string.settings_high_contrast_desc),
            checked = settings.highContrast,
            onChange = { on -> update { it.copy(highContrast = on) } },
        )

        if (graph.engines.cloudBuilt) {
            CloudGroup(
                settings = settings,
                hasKey = hasKey,
                keyInput = keyInput,
                onKeyInput = { keyInput = it },
                onAskConsent = { askConsent = true },
                onAskDelete = { askDeleteKey = true },
                onSave = { key ->
                    keyInput = ""
                    scope.launch { hasKey = withContext(Dispatchers.IO) { graph.secrets.put(SecretStore.GEMINI_API_KEY, key) } }
                },
                update = { transform -> update(transform) },
            )
        }

        SectionHeading(stringResource(R.string.settings_group_more))
        NavRow(stringResource(R.string.settings_setup_row), onSetup)
        NavRow(stringResource(R.string.settings_about_row), onAbout)
        Spacer(Modifier.heightIn(min = 24.dp))
    }

    if (askConsent) {
        CloudConsentDialog(
            onAccept = {
                askConsent = false
                update { it.copy(cloudEnabled = true, cloudConsent = VozSettings.CLOUD_CONSENT_VERSION) }
            },
            onDecline = { askConsent = false },
        )
    }
    if (askDeleteKey) {
        AlertDialog(
            onDismissRequest = { askDeleteKey = false },
            title = { Text(stringResource(R.string.settings_key_delete_title)) },
            text = { Text(stringResource(R.string.settings_key_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askDeleteKey = false
                    update { it.copy(cloudEnabled = false) }
                    scope.launch {
                        withContext(Dispatchers.IO) { graph.secrets.remove(SecretStore.GEMINI_API_KEY) }
                        hasKey = false
                    }
                }) { Text(stringResource(R.string.settings_key_delete)) }
            },
            dismissButton = { TextButton(onClick = { askDeleteKey = false }) { Text(stringResource(R.string.settings_key_keep)) } },
        )
    }
}

/** Developer and pilot builds only (Gemini API terms: professional use, adults, paid keys in Europe). */
@Composable
private fun CloudGroup(
    settings: VozSettings,
    hasKey: Boolean?,
    keyInput: String,
    onKeyInput: (String) -> Unit,
    onAskConsent: () -> Unit,
    onAskDelete: () -> Unit,
    onSave: (String) -> Unit,
    update: ((VozSettings) -> VozSettings) -> Unit,
) {
    SectionHeading(stringResource(R.string.settings_group_cloud))
    Text(stringResource(R.string.settings_cloud_pilot), style = MaterialTheme.typography.bodyMedium)
    val keySaved = hasKey == true
    SwitchRow(
        label = stringResource(R.string.settings_cloud),
        description = stringResource(
            when {
                !keySaved -> R.string.settings_cloud_no_key
                settings.cloudAllowed -> R.string.settings_cloud_on_status
                else -> R.string.settings_cloud_off_status
            },
        ),
        checked = settings.cloudAllowed && keySaved,
        enabled = keySaved,
        onChange = { on ->
            when {
                !on -> update { it.copy(cloudEnabled = false) }
                settings.cloudConsent >= VozSettings.CLOUD_CONSENT_VERSION -> update { it.copy(cloudEnabled = true) }
                else -> onAskConsent()
            }
        },
    )
    when (hasKey) {
        true -> {
            Text(stringResource(R.string.settings_key_saved), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onAskDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_key_delete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
        false -> {
            Text(stringResource(R.string.settings_key_how), style = MaterialTheme.typography.bodyMedium)
            ApiKeyField(value = keyInput, onChange = onKeyInput)
            Button(
                onClick = { onSave(keyInput.trim()) },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.onb_btn_save_key), style = MaterialTheme.typography.labelLarge) }
        }
        null -> Unit
    }
}

/**
 * Shown once before the first cloud use: what is sent, who may read it, and what never leaves the phone.
 * The buttons name the action; declining keeps everything on the phone.
 */
@Composable
private fun CloudConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.consent_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.consent_what))
                Text(stringResource(R.string.consent_who))
                Text(stringResource(R.string.consent_never))
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.consent_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.consent_decline)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val RATE_STEP = 0.1f

private fun step(rate: Float, delta: Float): Float =
    ((rate + delta) * 10).roundToInt().div(10f).coerceIn(VozSettings.MIN_RATE, VozSettings.MAX_RATE)
