package dev.auxdesign.voz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.data.BubbleSize
import dev.auxdesign.voz.data.SecretStore
import dev.auxdesign.voz.data.VozSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(graph: AppGraph, settings: VozSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun update(transform: (VozSettings) -> VozSettings) {
        scope.launch { graph.settingsStore.update(transform) }
    }
    var hasKey by remember { mutableStateOf(graph.secrets.has(SecretStore.GEMINI_API_KEY)) }
    var keyInput by remember { mutableStateOf("") }
    var depth by remember(settings.commentDepth) { mutableFloatStateOf(settings.commentDepth.toFloat()) }
    var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Heading(stringResource(R.string.settings_title))

        Section(stringResource(R.string.settings_language))
        val languages = listOf(
            null to stringResource(R.string.settings_language_system),
            Lang.ES to stringResource(R.string.lang_es),
            Lang.EN to stringResource(R.string.lang_en),
            Lang.NL to stringResource(R.string.lang_nl),
        )
        Column(Modifier.selectableGroup()) {
            languages.forEach { (lang, label) ->
                RadioRow(label, selected = settings.language == lang) { update { it.copy(language = lang) } }
            }
        }
        HorizontalDivider()

        SwitchRow(
            label = stringResource(R.string.settings_cloud),
            description = stringResource(if (hasKey) R.string.settings_cloud_desc else R.string.settings_cloud_no_key),
            checked = settings.cloudEnabled && hasKey,
            enabled = hasKey,
            onChange = { on -> update { it.copy(cloudEnabled = on) } },
        )
        if (hasKey) {
            Text(stringResource(R.string.settings_key_saved), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                graph.secrets.remove(SecretStore.GEMINI_API_KEY)
                hasKey = false
                update { it.copy(cloudEnabled = false) }
            }) { Text(stringResource(R.string.settings_key_delete)) }
        } else {
            ApiKeyField(value = keyInput, onChange = { keyInput = it })
            Button(
                onClick = {
                    hasKey = graph.secrets.put(SecretStore.GEMINI_API_KEY, keyInput.trim())
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank(),
            ) { Text(stringResource(R.string.onb_btn_save_key)) }
        }
        HorizontalDivider()

        Section(stringResource(R.string.settings_comment_depth, depth.roundToInt()))
        Slider(
            value = depth,
            onValueChange = { depth = it },
            onValueChangeFinished = { update { it.copy(commentDepth = depth.roundToInt()) } },
            valueRange = VozSettings.MIN_COMMENT_DEPTH.toFloat()..VozSettings.MAX_COMMENT_DEPTH.toFloat(),
            steps = VozSettings.MAX_COMMENT_DEPTH - VozSettings.MIN_COMMENT_DEPTH - 1,
        )

        Section(stringResource(R.string.settings_speech_rate, rate))
        Slider(
            value = rate,
            onValueChange = { rate = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { update { it.copy(speechRate = rate) } },
            valueRange = VozSettings.MIN_RATE..VozSettings.MAX_RATE,
        )
        HorizontalDivider()

        Section(stringResource(R.string.settings_bubble_size))
        Column(Modifier.selectableGroup()) {
            listOf(
                BubbleSize.SMALL to R.string.bubble_small,
                BubbleSize.MEDIUM to R.string.bubble_medium,
                BubbleSize.LARGE to R.string.bubble_large,
            ).forEach { (size, label) ->
                RadioRow(stringResource(label), selected = settings.bubbleSize == size) { update { it.copy(bubbleSize = size) } }
            }
        }
        HorizontalDivider()

        SwitchRow(
            label = stringResource(R.string.settings_high_contrast),
            checked = settings.highContrast,
            onChange = { on -> update { it.copy(highContrast = on) } },
        )
        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
