package dev.auxdesign.voz.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.auxdesign.voz.R

/** What VOZ does, what stays on the phone, what may leave it, and why each permission exists. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val title = stringResource(R.string.about_title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = title }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title, onBack)
        Section(R.string.about_what_title, R.string.about_what_body)
        Section(R.string.about_phone_title, R.string.about_phone_body)
        Section(R.string.about_speech_title, R.string.about_speech_body)
        Section(R.string.about_cloud_title, R.string.about_cloud_body)
        SectionHeading(stringResource(R.string.about_permissions_title))
        listOf(R.string.about_perm_mic, R.string.about_perm_a11y, R.string.about_perm_overlay, R.string.about_perm_notif)
            .forEach { Body(stringResource(it)) }
        Section(R.string.about_limits_title, R.string.about_limits_body)
        SectionHeading(stringResource(R.string.about_version_title))
        Body(stringResource(R.string.about_version, version))
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes body: Int) {
    SectionHeading(stringResource(title))
    Body(stringResource(body))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 600.dp))
}
