package com.resukisu.resukisu.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SulogFile(
    val name: String,
    val path: String,
)

enum class SulogEventType {
    RootExecve,
    SuCompat,
    IoctlGrantRoot,
    DaemonEvent,
    Dropped,
    Unknown,
}

enum class SulogEventFilter(val eventType: SulogEventType?) {
    RootExecve(SulogEventType.RootExecve),
    SuCompat(SulogEventType.SuCompat),
    IoctlGrantRoot(SulogEventType.IoctlGrantRoot),
    DaemonEvent(SulogEventType.DaemonEvent),
}

data class SulogEntry(
    val key: String,
    val eventType: SulogEventType,
    val rawLine: String,
    val timestampText: String?,
    val fields: Map<String, String>,
) {
    val searchableText: String by lazy {
        buildString {
            append(rawLine)
            append('\n')
            timestampText?.let {
                append(it)
                append('\n')
            }
            fields.values.forEach {
                append(it)
                append(' ')
            }
        }.lowercase()
    }
}

data class SulogState(
    val status: String = "",
    val enabled: Boolean = false,
    val files: List<SulogFile> = emptyList(),
    val selectedFilePath: String? = null,
    val entries: List<SulogEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

fun defaultSulogEventFilters(): Set<SulogEventFilter> = SulogEventFilter.entries.toSet()

fun String.toSulogDisplayName(): String =
    if (startsWith("sulog-") && endsWith(".log")) {
        removePrefix("sulog-").removeSuffix(".log")
    } else {
        this
    }

fun filterSulogEntries(
    entries: List<SulogEntry>,
    searchText: String,
    selectedFilters: Set<SulogEventFilter>,
): List<SulogEntry> {
    val normalizedQuery = searchText.trim().lowercase()
    val activeEventTypes = selectedFilters.mapNotNull { it.eventType }.toSet()
    return entries.asReversed().filter { entry ->
        val alwaysVisible =
            entry.eventType == SulogEventType.Dropped || entry.eventType == SulogEventType.Unknown
        val filterMatches = alwaysVisible || entry.eventType in activeEventTypes
        val queryMatches =
            normalizedQuery.isEmpty() || entry.searchableText.contains(normalizedQuery)
        filterMatches && queryMatches
    }
}

fun parseSulogLines(
    lines: List<String>,
    currentTimeMillis: Long,
    uptimeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<SulogEntry> = lines.mapNotNull { line ->
    line.takeIf { it.isNotBlank() }?.let {
        parseSulogLine(it, currentTimeMillis, uptimeMillis, zoneId)
    }
}

fun parseSulogLine(
    line: String,
    currentTimeMillis: Long,
    uptimeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): SulogEntry {
    val fields = parseKeyValueLine(line)
    val eventType = when (fields["type"]) {
        "root_execve" -> SulogEventType.RootExecve
        "sucompat" -> SulogEventType.SuCompat
        "ioctl_grant_root" -> SulogEventType.IoctlGrantRoot
        "daemon_restart", "daemon_start" -> SulogEventType.DaemonEvent
        "dropped" -> SulogEventType.Dropped
        else -> SulogEventType.Unknown
    }
    val key = when (eventType) {
        SulogEventType.DaemonEvent -> "daemon_restart_${fields["restart"]}_${fields["boot_id"]}"
        else -> fields["seq"] ?: line
    }
    return SulogEntry(
        key = key,
        eventType = eventType,
        rawLine = line,
        timestampText = parseSulogTimestampText(
            timestampNs = fields["ts_ns"],
            currentTimeMillis = currentTimeMillis,
            uptimeMillis = uptimeMillis,
            zoneId = zoneId,
        ),
        fields = fields,
    )
}

private fun parseKeyValueLine(line: String): Map<String, String> = buildMap {
    var index = 0
    while (index < line.length) {
        while (index < line.length && line[index].isWhitespace()) index++
        if (index >= line.length) break

        val keyStart = index
        while (index < line.length && line[index] != '=' && !line[index].isWhitespace()) index++
        if (index >= line.length || line[index] != '=') {
            while (index < line.length && !line[index].isWhitespace()) index++
            continue
        }

        val key = line.substring(keyStart, index++)
        val (value, nextIndex) = if (index < line.length && line[index] == '"') {
            parseQuotedValue(line, index + 1)
        } else {
            parseUnquotedValue(line, index)
        }
        if (key.isNotEmpty()) put(key, value)
        index = nextIndex
    }
}

private fun parseQuotedValue(line: String, startIndex: Int): Pair<String, Int> {
    val value = StringBuilder()
    var index = startIndex
    while (index < line.length) {
        when (val char = line[index]) {
            '"' -> return value.toString() to (index + 1)
            '\\' -> {
                val next = line.getOrNull(index + 1)
                when (next) {
                    '\\', '"' -> value.append(next)
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    else -> if (next != null) value.append(next) else value.append('\\')
                }
                index += if (next == null) 1 else 2
            }

            else -> {
                value.append(char)
                index++
            }
        }
    }
    return value.toString() to index
}

private fun parseUnquotedValue(line: String, startIndex: Int): Pair<String, Int> {
    var index = startIndex
    while (index < line.length && !line[index].isWhitespace()) index++
    return line.substring(startIndex, index) to index
}

private fun parseSulogTimestampText(
    timestampNs: String?,
    currentTimeMillis: Long,
    uptimeMillis: Long,
    zoneId: ZoneId,
): String? {
    val timestampNanos = timestampNs?.toLongOrNull() ?: return null
    if (timestampNanos < 0 || uptimeMillis < 0) return null
    val eventTimeMillis = currentTimeMillis - uptimeMillis + timestampNanos / 1_000_000L
    if (eventTimeMillis < 0) return null
    return Instant.ofEpochMilli(eventTimeMillis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
}
