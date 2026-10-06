package com.eliadca.talks.ui.home

/** What the editor's menus can do to the open speech; wired up by the home screen. */
class SpeechActions(
    val togglePin: () -> Unit,
    val duplicate: () -> Unit,
    val moveToTrash: () -> Unit,
    val moveToFolder: (Long?) -> Unit,
    val setLabel: (Int) -> Unit,
    val setTargetMinutes: (Int) -> Unit,
    val exportPdf: () -> Unit,
    val shareText: () -> Unit,
    val saveVersion: (String) -> Unit,
    val restoreVersion: (Long) -> Unit,
    val deleteVersion: (Long) -> Unit,
)

/** Formats a length of time for display: "45 s", "3 min", "1 h 05 min". */
fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return when {
        s < 60 -> "$s s"
        s < 3600 -> {
            val m = s / 60
            val rest = s % 60
            if (rest == 0) "$m min" else "$m min ${"%02d".format(rest)} s"
        }
        else -> "${s / 3600} h ${"%02d".format((s % 3600) / 60)} min"
    }
}
