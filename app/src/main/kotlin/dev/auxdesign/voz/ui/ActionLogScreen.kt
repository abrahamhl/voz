package dev.auxdesign.voz.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.data.LogEntry
import java.util.Date

@Composable
fun ActionLogScreen(graph: AppGraph, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by graph.actionLog.entries.collectAsStateWithLifecycle()
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val newestFirst = remember(entries) { entries.asReversed() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            OutlinedButton(onClick = { graph.actionLog.clear() }, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.log_clear))
            }
        }
        Heading(stringResource(R.string.log_title))
        if (entries.isEmpty()) {
            Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(newestFirst) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(
                            text = "${timeFormat.format(Date(entry.at))} · ${stringResource(kindLabel(entry.kind))}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun kindLabel(kind: LogEntry.Kind): Int = when (kind) {
    LogEntry.Kind.HEARD -> R.string.log_kind_heard
    LogEntry.Kind.PLAN -> R.string.log_kind_plan
    LogEntry.Kind.DONE -> R.string.log_kind_done
    LogEntry.Kind.FAILED -> R.string.log_kind_failed
    LogEntry.Kind.CONFIRM -> R.string.log_kind_confirm
    LogEntry.Kind.BLOCKED -> R.string.log_kind_blocked
}
