package com.eliadca.talks.notesai

/** UTF-8 byte arrays keep accents and emoji intact across JNI. Loaded only when IA is used. */
internal object NativeNotesAi {
    init { System.loadLibrary("talks_notes_ai") }
    external fun newRequest(): Long
    external fun cancel(request: Long)
    external fun freeRequest(request: Long)
    external fun generate(path: String, prompt: ByteArray, request: Long): ByteArray?
}
