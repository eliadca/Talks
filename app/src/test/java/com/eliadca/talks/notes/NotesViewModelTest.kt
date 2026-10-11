package com.eliadca.talks.notes

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.eliadca.talks.TalksApp
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.ink.InkPoint
import com.eliadca.talks.core.ink.PenKind
import com.eliadca.talks.speech.*
import com.eliadca.talks.ui.notes.NotesViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesViewModelTest {
    private val app get() = ApplicationProvider.getApplicationContext<TalksApp>()
    private class Engine : SpeechEngine {
        val flow = MutableSharedFlow<SpeechEvent>(replay = 16, extraBufferCapacity = 32)
        override val events: SharedFlow<SpeechEvent> = flow
        override val name = "Test"
        var released = false
        override fun start() { flow.tryEmit(SpeechEvent.State(EngineState.LISTENING)) }
        override fun stop() {}
        override fun release() { released = true }
        fun partial(text: String) { flow.tryEmit(SpeechEvent.Partial(text)) }
        fun final(text: String) { flow.tryEmit(SpeechEvent.Final(text)) }
    }
    private fun until(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            check(System.nanoTime() < deadline) { "Timed out waiting for note state" }
            Thread.sleep(10)
        }
    }

    @Test fun assistedSelectionAutoCaptureAndPauseSurviveReopeningWithoutLosingEditedText() {
        val vm = NotesViewModel(app)
        val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine()
        app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }
            vm.title("Notas de reunión")
            val written = vm.ink.addStroke(PenKind.PENCIL, 0xff1965a3.toInt(), 6f,
                listOf(InkPoint(200f, 300f, .2f), InkPoint(500f, 350f, .8f)))
                .addArea(InkPoint(100f, 600f), InkPoint(900f, 1000f))
            vm.editInk(written)
            vm.undoInk(); assertTrue(vm.ink.strokes.isEmpty())
            vm.redoInk(); assertEquals(written, vm.ink)
            vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.partial("Una idea inicial"); until { vm.partial.isNotEmpty() }
            assertTrue(vm.entries.isEmpty()); assertTrue(vm.body.text.isEmpty())
            engine.final("Una idea inicial"); until { vm.entries.size == 1 }
            assertTrue(vm.body.text.isEmpty())
            vm.insert("idea"); assertEquals("idea", vm.body.text)
            vm.edit(TextFieldValue("Texto que estoy editando", TextRange(2, 8)))
            vm.automatic(true)
            engine.final("Una segunda idea"); until { vm.entries.size == 2 }
            assertEquals("Texto que estoy editando\n\nUna segunda idea", vm.body.text)
            assertEquals(TextRange(2, 8), vm.body.selection)
            assertTrue(vm.entries.last().added)
            engine.partial("Últimas palabras"); until { vm.partial.isNotEmpty() }
            vm.pause()
            assertTrue(engine.released)
            assertFalse(vm.entries.last().confirmed)
            assertFalse(vm.body.text.contains("Últimas palabras"))
            val id = vm.note!!.id
            val expected = vm.body.text
            vm.backToList(); vm.open(id); until { vm.note != null }
            assertEquals(expected, vm.body.text)
            assertEquals(3, vm.entries.size)
            assertTrue(vm.note!!.automatic)
            assertEquals(written, vm.ink)
            assertFalse(vm.canUndoInk)
            assertEquals(expected, runBlocking { app.container.notes.load(id) }!!.body)
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null
        }
    }

    @Test fun finalSegmentsDetectASpokenCitationAcrossTheRecognitionBoundary() {
        val vm = NotesViewModel(app)
        val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine()
        app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }
            vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.final("Leamos Juan capítulo tres"); until { vm.entries.size == 1 }
            engine.final("versículo dieciséis"); until { vm.detected.isNotEmpty() }
            assertEquals(listOf(BibleReference(43, 3, 16)), vm.detected)
            vm.toggleCitations(false)
            engine.final("Génesis uno uno"); until { vm.entries.size == 3 }
            assertTrue(vm.detected.isEmpty())
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null
        }
    }
}
