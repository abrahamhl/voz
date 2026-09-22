package dev.auxdesign.voz.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Serializable
data class LogEntry(val at: Long, val kind: Kind, val text: String) {
    enum class Kind { HEARD, PLAN, DONE, FAILED, CONFIRM, BLOCKED }
}

/** On-device audit trail of what VOZ heard and did. Never leaves the phone. */
class ActionLog(
    private val file: File?,
    private val max: Int = DEFAULT_MAX,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the file is written; a single background thread by default, so callers never block on disk. */
    private val io: Executor = Executors.newSingleThreadExecutor(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())
    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    @Synchronized
    fun add(kind: LogEntry.Kind, text: String) {
        val entry = LogEntry(clock(), kind, text.replace('\n', ' ').take(MAX_TEXT))
        val list = (state.value + entry).takeLast(max)
        state.value = list
        persist(list)
    }

    @Synchronized
    fun clear() {
        state.value = emptyList()
        persist(emptyList())
    }

    private fun load(): List<LogEntry> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line -> runCatching { json.decodeFromString(LogEntry.serializer(), line) }.getOrNull() }
        }.getOrDefault(emptyList()).takeLast(max)
    }

    private fun persist(list: List<LogEntry>) {
        val f = file ?: return
        io.execute {
            runCatching { f.writeText(list.joinToString("\n") { json.encodeToString(LogEntry.serializer(), it) }) }
        }
    }

    companion object {
        const val DEFAULT_MAX = 200
        const val MAX_TEXT = 500
    }
}
