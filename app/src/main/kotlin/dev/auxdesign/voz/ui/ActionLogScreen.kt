package dev.auxdesign.voz.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.auxdesign.voz.AppGraph
import dev.auxdesign.voz.R
import dev.auxdesign.voz.data.LogEntry
import java.util.Calendar
import java.util.Date

/** History: newest first, grouped by day, one human sentence per entry; clearing it asks first. */
@Composable
fun ActionLogScreen(graph: AppGraph, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by graph.actionLog.entries.collectAsStateWithLifecycle()
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val days = remember(entries) { entries.asReversed().groupBy { dayStart(it.at) }.toList() }
    var askClear by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.log_title)

    Column(
        modifier = Modifier.fillMaxSize().semantics { paneTitle = title }.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title, onBack)
        Text(stringResource(R.string.log_privacy), style = MaterialTheme.typography.bodyMedium)
        if (entries.isEmpty()) {
            Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { (day, dayEntries) ->
                    item(key = "day-$day") {
                        Text(
                            dayLabel(context, day),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                        )
                    }
                    items(dayEntries) { entry -> LogRow(entry, timeFormat.format(Date(entry.at))) }
                }
                item(key = "clear") {
                    // Kept at the end of the list, far from Back, and behind a confirmation.
                    OutlinedButton(onClick = { askClear = true }, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.log_clear), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    graph.actionLog.clear()
                }) { Text(stringResource(R.string.log_clear)) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.log_clear_keep)) } },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry, time: String) {
    val voz = LocalVozColors.current
    val scheme = MaterialTheme.colorScheme
    val (icon, tint: Color) = when (entry.kind) {
        LogEntry.Kind.HEARD -> R.drawable.ic_mic to scheme.onSurfaceVariant
        LogEntry.Kind.PLAN -> R.drawable.ic_info to scheme.onSurfaceVariant
        LogEntry.Kind.DONE -> R.drawable.ic_check_circle to voz.success
        LogEntry.Kind.FAILED -> R.drawable.ic_error to scheme.error
        LogEntry.Kind.CONFIRM -> R.drawable.ic_check_circle to scheme.primary
        LogEntry.Kind.BLOCKED -> R.drawable.ic_block to scheme.onSurfaceVariant
    }
    val kind = stringResource(kindLabel(entry.kind))
    val spoken = stringResource(R.string.log_entry_cd, kind, time, entry.text)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clearAndSetSemantics { contentDescription = spoken },
    ) {
        VozIcon(icon, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$time · $kind", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun dayStart(at: Long): Long = Calendar.getInstance().apply {
    timeInMillis = at
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(context: android.content.Context, day: Long): String {
    val today = dayStart(System.currentTimeMillis())
    return when (day) {
        today -> context.getString(R.string.log_today)
        dayStart(today - 1) -> context.getString(R.string.log_yesterday)
        else -> DateUtils.formatDateTime(context, day, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
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
