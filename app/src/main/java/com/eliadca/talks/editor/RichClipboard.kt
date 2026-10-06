package com.eliadca.talks.editor

import com.eliadca.talks.core.doc.RichDoc

/**
 * What was last copied in the editor, with its formatting. The system clipboard only carries the
 * text, so pasting it back into Talks looks it up here to keep the formatting.
 */
internal object RichClipboard {

    private var copied: RichDoc? = null

    fun remember(doc: RichDoc) {
        copied = doc
    }

    /** The copied document whose text is [text], if what is being pasted came from the editor. */
    fun documentFor(text: String): RichDoc? = copied?.takeIf { it.text == text.replace("\r\n", "\n") }
}
