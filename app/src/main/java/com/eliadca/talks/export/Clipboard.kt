package com.eliadca.talks.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Plain text in and out of the system clipboard. */
object Clipboard {

    fun copy(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** What is on the clipboard as text (every item, one per line), or null when there is none. */
    fun readText(context: Context): String? {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        val sb = StringBuilder()
        for (i in 0 until clip.itemCount) {
            val t = clip.getItemAt(i).coerceToText(context) ?: continue
            if (t.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(t)
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }
}
