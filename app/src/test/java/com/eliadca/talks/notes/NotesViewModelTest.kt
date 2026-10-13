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
import android.database.sqlite.SQLiteDatabase
import java.io.File

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
            assertEquals("Texto que estoy editando Una segunda idea", vm.body.text)
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

    @Test fun aMisheardBookIsCorrectedOfflineAndItsPassageCanStillBeOpenedWithVoiceDetectionOff() {
        val file = File(app.filesDir, "study-bible.sqlite"); file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE verses (book INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
            db.execSQL("INSERT INTO verses VALUES (49,2,8,'Texto de prueba para Efesios')")
            db.execSQL("CREATE TABLE edition (title TEXT, verses INTEGER)")
            db.execSQL("INSERT INTO edition VALUES ('Biblia de Estudio',1)")
        }
        val vm = NotesViewModel(app)
        val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine(); app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }
            vm.automatic(true); vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.final("Leamos Efecios capítulo dos versículo ocho")
            until { vm.passage != null }
            assertEquals("Leamos Efesios capítulo dos versículo ocho", vm.entries.single().text)
            assertEquals("Leamos Efecios capítulo dos versículo ocho", vm.entries.single().originalText)
            assertTrue(vm.body.text.contains("Efesios")); assertFalse(vm.body.text.contains("Efecios"))
            vm.toggleCitations(false); assertNull(vm.passage)
            vm.showReference(BibleReference(49, 2, 8)); until { vm.passage != null }
            assertTrue(vm.passage!!.text.contains("Texto de prueba para Efesios"))
            val id = vm.note!!.id
            vm.backToList(); vm.open(id); until { vm.note != null }
            assertEquals("Leamos Efecios capítulo dos versículo ocho", vm.entries.single().originalText)
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null; file.delete()
        }
    }

    @Test fun ambiguousSpokenNamesStayUnchangedAndOfferChoicesInsteadOfOpeningAPassage() {
        val vm = NotesViewModel(app)
        val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine(); app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }
            vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.final("jon capítulo uno versículo uno")
            until { vm.referenceSuggestions.isNotEmpty() }
            assertTrue(vm.detected.isEmpty()); assertNull(vm.passage)
            assertEquals("jon capítulo uno versículo uno", vm.entries.single().text)
            assertNull(vm.entries.single().originalText)
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null
        }
    }
    @Test fun captureContinuesInItsOriginalNoteWhileTheLibraryOrAnotherNoteIsOpen() {
        val vm = NotesViewModel(app); val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine(); app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }; vm.title("Grabación"); vm.automatic(true)
            val recording = vm.note!!.id
            vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.final("Primera frase"); until { vm.entries.size == 1 }
            vm.backToList(); assertTrue(vm.listening); assertFalse(engine.released)
            engine.final("Desde la biblioteca")
            until { runBlocking { app.container.notes.load(recording) }!!.body.contains("Desde la biblioteca") }
            vm.create(); until { vm.note != null }
            val other = vm.note!!.id
            vm.edit(TextFieldValue("Texto de otra nota"))
            engine.final("Desde otra nota")
            until { runBlocking { app.container.notes.load(recording) }!!.body.contains("Desde otra nota") }
            assertEquals("Texto de otra nota", vm.body.text); assertTrue(vm.entries.isEmpty()); assertEquals("", vm.partial)
            vm.open(recording); until { vm.note?.id == recording }
            assertEquals(3, vm.entries.size); assertTrue(vm.listening)
            engine.partial("Último fragmento pendiente"); until { vm.partial.isNotBlank() }
            assertTrue(vm.exportTranscript().contains("Último fragmento pendiente"))
            val export = File(app.filesDir, "transcripción.txt")
            vm.exportTranscript(android.net.Uri.fromFile(export), vm.exportTranscript())
            until { export.exists() && export.readText().contains("Último fragmento pendiente") }
            assertTrue(export.readText().contains("Desde otra nota"))
            vm.pause(); assertTrue(engine.released)
            assertFalse(vm.entries.last().confirmed)
            assertEquals("Texto de otra nota", runBlocking { app.container.notes.load(other) }!!.body)
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null
        }
    }

    @Test fun reviewedContinuousTextAppendsNewSpeechAndMovingRichTextIsOneUndo() {
        val vm=NotesViewModel(app);val store=ViewModelStore().also {it.put("notes",vm)}
        val engine=Engine();app.container.speechEngineFactory={engine}
        try {
            vm.create();until {vm.note!=null};val id=vm.note!!.id
            vm.start();until {vm.engineState==EngineState.LISTENING}
            engine.final("primera idea");until {vm.entries.size==1};engine.final("segunda idea");until {vm.entries.size==2}
            assertEquals("primera idea segunda idea",vm.transcriptText)
            vm.editTranscript("Primera idea. Segunda idea.",assisted=true)
            engine.final("Y seguimos");until {vm.entries.size==3}
            assertEquals("Primera idea. Segunda idea. Y seguimos",vm.exportTranscript())
            assertEquals("primera idea segunda idea Y seguimos",vm.originalTranscript)
            vm.edit(TextFieldValue("Mi idea importante",TextRange(3,7)));vm.formatText {it.copy(bold=true)}
            vm.moveTextSelection();assertEquals("Mi  importante",vm.body.text)
            assertEquals("idea",vm.ink.textBoxes.single().text);assertTrue(vm.ink.textBoxes.single().formatting.styleAt(0).bold)
            vm.undoInk();assertEquals("Mi idea importante",vm.body.text);assertTrue(vm.ink.textBoxes.isEmpty())
            vm.redoText();assertEquals("idea",vm.ink.textBoxes.single().text)
            vm.pause();vm.backToList();vm.open(id);until {vm.note?.id==id}
            assertEquals("Primera idea. Segunda idea. Y seguimos",vm.transcriptText)
        } finally {store.clear();runBlocking {app.container.notes.flush()};app.container.scope.cancel();app.container.database.close();app.container.speechEngineFactory=null}
    }
    @Test fun richTextUndoRedoAndFormattingSurviveReopeningAlongsideDrawing() {
        val vm = NotesViewModel(app); val store = ViewModelStore().also { it.put("notes", vm) }
        try {
            vm.create(); until { vm.note != null }; val id = vm.note!!.id
            vm.edit(TextFieldValue("Mi idea importante", TextRange(3, 7)))
            vm.formatText { it.copy(bold = true, size = 60f) }
            assertTrue(vm.ink.formatting.styleAt(4).bold)
            vm.undoText(); assertFalse(vm.ink.formatting.styleAt(4).bold)
            vm.redoText(); assertTrue(vm.ink.formatting.styleAt(4).bold)
            vm.editInk(vm.ink.addStroke(PenKind.PENCIL, 1, 3f, listOf(InkPoint(100f, 100f))))
            vm.undoInk(); assertTrue(vm.ink.strokes.isEmpty()); assertTrue(vm.ink.formatting.styleAt(4).bold)
            vm.redoInk(); assertTrue(vm.ink.formatting.styleAt(4).bold)
            vm.backToList(); vm.open(id); until { vm.note?.id == id }
            assertEquals("Mi idea importante", vm.body.text); assertEquals(60f, vm.ink.formatting.styleAt(4).size)
            assertEquals(1, vm.ink.strokes.size)
            vm.edit(TextFieldValue(vm.body.text, TextRange(0)))
            vm.listText(true); assertTrue(vm.body.text.startsWith("☐ "))
        } finally {
            store.clear(); runBlocking { app.container.notes.flush() }; app.container.scope.cancel(); app.container.database.close()
        }
    }

    @Test fun pausingDuringAStaleDatabaseReadKeepsTheLastProvisionalWordsOnReopening() {
        val real = app.container.database.notes()
        var blockRead = false
        val readStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = object : com.eliadca.talks.data.db.NoteDao by real {
            override suspend fun get(id: Long): com.eliadca.talks.data.db.NoteEntity? {
                val old = real.get(id)
                if (blockRead) { readStarted.complete(Unit); releaseRead.await() }
                return old
            }
        }
        val repo = com.eliadca.talks.data.NotesRepository(dao, app.container.scope)
        val vm = NotesViewModel(app, repo); val store = ViewModelStore().also { it.put("notes", vm) }
        val engine = Engine(); app.container.speechEngineFactory = { engine }
        try {
            vm.create(); until { vm.note != null }; val id = vm.note!!.id
            vm.start(); until { vm.engineState == EngineState.LISTENING }
            engine.final("Palabras confirmadas"); until { vm.entries.size == 1 }
            engine.partial("Palabras antes de pausar"); until { vm.partial.isNotBlank() }
            vm.backToList(); blockRead = true; vm.open(id); until { readStarted.isCompleted }
            vm.pause(); releaseRead.complete(Unit); until { vm.note?.id == id }
            assertEquals(listOf("Palabras confirmadas", "Palabras antes de pausar"), vm.entries.map { it.text })
            assertFalse(vm.entries.last().confirmed); assertNull(vm.recordingNoteId)
            vm.title("Conservar las últimas palabras"); vm.flushEdits()
            blockRead = false
            assertTrue(runBlocking { repo.load(id) }!!.transcript.contains("Palabras antes de pausar"))
        } finally {
            releaseRead.complete(Unit); store.clear(); runBlocking { repo.flush() }
            app.container.scope.cancel(); app.container.database.close(); app.container.speechEngineFactory = null
        }
    }

}
