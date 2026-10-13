package com.eliadca.talks.notesai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the actual device ABI and exercises JNI lifecycle without downloading model weights. */
@RunWith(AndroidJUnit4::class)
class NotesAiRuntimeTest {
    @Test fun nativeLibraryLoadsAndCancelledRequestsReleaseCleanly() {
        val request=NativeNotesAi.newRequest()
        try { assertTrue(request!=0L);NativeNotesAi.cancel(request) } finally {NativeNotesAi.freeRequest(request)}
    }
    @Test fun aMissingModelIsARecoverableErrorWithUtf8Input() {
        val request=NativeNotesAi.newRequest()
        try {
            try { NativeNotesAi.generate("/missing-notes-model.gguf","Jehová 🙏".toByteArray(Charsets.UTF_8),request);fail("Missing model should fail") }
            catch(expected: IllegalStateException) {assertTrue(expected.message!!.contains("modelo"))}
        } finally {NativeNotesAi.freeRequest(request)}
    }
}
