package com.eliadca.talks.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.InkHistory
import com.eliadca.talks.ui.notes.HandwritingNotePanel
import com.eliadca.talks.ui.home.SidebarLayout
import com.eliadca.talks.ui.notes.FloatingPassage
import com.eliadca.talks.ui.notes.NotesWorkspace
import com.eliadca.talks.ui.notes.TranscriptEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1200dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class NotesUiTest {
    @get:Rule val compose = createComposeRule()
    private val entries = listOf(TranscriptEntry(1, "La primera idea importante."), TranscriptEntry(2, "Otra idea que merece una nota."))

    private fun screenshot(name: String) {
        val dir = System.getenv("TALKS_SCREENSHOT_DIR") ?: return
        val output = File(dir, "$name.png").also { it.parentFile!!.mkdirs() }
        compose.runOnUiThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun aSelectedWordCanBeAddedWithoutCopyingTheWholeRecognitionSegment() {
        var selectedText = ""
        compose.setContent {
            var body by remember { mutableStateOf(TextFieldValue("")) }
            MaterialTheme {
                NotesWorkspace(entries, "Escuchando una frase nueva…", body, false, true, { body = it }, {},
                    { selectedText = it; body = TextFieldValue(it) }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithTag("fragment-1").performTextInputSelection(TextRange(3, 10))
        compose.onNodeWithText("Añadir selección").performClick()
        assertEquals("primera", selectedText)
        compose.onNodeWithTag("note-body").assertTextContains("primera")
        screenshot("notes-tablet")
    }

    @Test fun aPhoneCanCloseAndReopenTheRightPanelWithoutDiscardingTheTranscript() {
        var added = emptySet<Long>()
        compose.setContent {
            var body by remember { mutableStateOf(TextFieldValue("")) }
            MaterialTheme {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    NotesWorkspace(entries, "", body, false, false, { body = it },
                        { added = it; body = TextFieldValue("Mis ideas guardadas") }, {}, Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithTag("voice-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithText("Añadir todo").performClick()
        assertEquals(setOf(1L, 2L), added)
        compose.onNodeWithContentDescription("Cerrar transcripción").performClick()
        compose.onNodeWithTag("note-body").assertTextContains("Mis ideas guardadas")
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithTag("fragment-1").assertTextContains(entries[0].text)
        screenshot("notes-phone")
    }

    @Test fun aCitationFloatsAndCanCollapseExpandAndAddWithoutReplacingTheWorkspace() {
        var added = 0
        compose.setContent {
            var expanded by remember { mutableStateOf(true) }
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("La nota sigue visible", Modifier.testTag("behind-citation"))
                    FloatingPassage(BiblePassage(BibleReference(43, 3, 16), "Texto de prueba para el versículo.", "Biblia de Estudio"),
                        expanded, { expanded = it }, {}, { added++ }, Modifier.width(420.dp))
                }
            }
        }
        compose.onNodeWithText("Añadir a nota").performClick(); assertEquals(1, added)
        compose.onNodeWithContentDescription("Contraer cita").performClick()
        compose.onNodeWithText("Texto de prueba para el versículo.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expandir cita").performClick()
        compose.onNodeWithText("Texto de prueba para el versículo.").assertIsDisplayed()
        compose.onNodeWithTag("behind-citation").assertIsDisplayed()
        screenshot("citation-card")
    }

    @Test fun expandedMenuStaysAtTheLeftAndLeavesTheContentVisible() {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                SidebarLayout(expanded, { expanded = false }, true,
                    menu = { TextButton(onClick = { expanded = false }) { Text("Cerrar menú") } },
                    rail = { TextButton(onClick = { expanded = true }) { Text("Menú") } }) { modifier ->
                    Text("Contenido visible", modifier.testTag("content"))
                }
            }
        }
        compose.onNodeWithText("Menú").performClick()
        compose.onNodeWithTag("left-menu").assertWidthIsEqualTo(280.dp)
        compose.onNodeWithTag("content").assertIsDisplayed()
        compose.onNodeWithText("Cerrar menú").performClick()
        compose.onNodeWithTag("left-menu").assertWidthIsEqualTo(88.dp)
    }

    @Test fun aLongTranscriptShowsTheLatestConfirmedPhraseAsNewSpeechArrives() {
        var transcript by mutableStateOf((1L..12L).map { TranscriptEntry(it, "Fragmento $it") })
        compose.setContent {
            MaterialTheme {
                NotesWorkspace(transcript, "", TextFieldValue(""), false, true, {}, {}, {}, Modifier.fillMaxWidth().height(320.dp))
            }
        }
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithTag("fragment-12").assertIsDisplayed()
        compose.runOnIdle { transcript = transcript + TranscriptEntry(13, "Última frase confirmada") }
        compose.onNodeWithTag("fragment-13").assertIsDisplayed()
    }

    @Test fun handwritingToolsDrawOverTextAndSaveWholePageOrSelectedAreaRuling() {
        val history = InkHistory()
        var ink by mutableStateOf(InkDocument())
        compose.setContent {
            MaterialTheme {
                HandwritingNotePanel(TextFieldValue("Ideas de la reunión"), {}, ink,
                    { history.record(it); ink = history.document }, history.canUndo, history.canRedo,
                    { ink = history.undo() }, { ink = history.redo() }, Modifier.width(580.dp).fillMaxHeight())
            }
        }
        compose.onNodeWithText("Lápiz").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(70f, 100f), Offset(220f, 150f), 200) }
        compose.runOnIdle { assertEquals(1, ink.strokes.size) }
        compose.onNodeWithContentDescription("Deshacer escritura").performClick()
        compose.runOnIdle { assertTrue(ink.strokes.isEmpty()) }
        compose.onNodeWithContentDescription("Rehacer escritura").performClick()
        compose.onNodeWithContentDescription("Tipo, grosor y color del lápiz").performScrollTo().performClick()
        compose.onNodeWithText("Resaltador").performClick()
        compose.onNodeWithContentDescription("Color #1565C0").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Resaltador · grosor 5 · lápiz o dedo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Renglones").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Renglones en toda la página").performClick()
        compose.runOnIdle { assertTrue(ink.wholePageRuled) }
        compose.onNodeWithText("Elegir una zona de la página").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(60f, 200f), Offset(350f, 390f), 200) }
        compose.runOnIdle { assertEquals(1, ink.areas.size); assertTrue(!ink.wholePageRuled) }
        compose.onNodeWithTag("note-body").assertTextContains("Ideas de la reunión")
        screenshot("notes-handwriting")
    }

    @Test fun aLongTypedNoteExpandsTheSamePageUsedForHandwriting() {
        val text = (1..200).joinToString("\n") { "Idea $it de la reunión" }
        compose.setContent {
            MaterialTheme {
                HandwritingNotePanel(TextFieldValue(text), {}, InkDocument(), {}, false, false, {}, {},
                    Modifier.width(400.dp).fillMaxHeight())
            }
        }
        compose.onNodeWithTag("ink-page").assertHeightIsAtLeast(4000.dp)
        compose.onNodeWithText("Añadir espacio a la página").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("note-body").assertTextContains("Idea 200 de la reunión", substring = true)
    }

    @Test fun thePageStartsFullWidthAndVoiceOnlyStartsFromInsideTheRightPanel() {
        var starts = 0
        compose.setContent {
            var listening by remember { mutableStateOf(false) }
            MaterialTheme {
                NotesWorkspace(entries, "", TextFieldValue("Mis ideas"), false, true, {}, {}, {}, Modifier.fillMaxSize(),
                    listening = listening, onToggleListen = { starts++; listening = !listening })
            }
        }
        compose.onNodeWithTag("voice-panel").assertDoesNotExist()
        compose.onNodeWithText("Escuchar").assertDoesNotExist()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("Abrir transcripción").fetchSemanticsNode().boundsInRoot
        assertTrue(button.center.x > root.width * .8f && button.center.y > root.height * .8f)
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        assertEquals(0, starts)
        val note = compose.onNodeWithTag("note-body").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("transcription must be to the right of the note", panel.left >= note.right)
        compose.onNodeWithText("Escuchar").performClick(); assertEquals(1, starts)
        compose.onNodeWithContentDescription("Cerrar transcripción").performClick()
        compose.onNodeWithTag("voice-panel").assertDoesNotExist()
        assertEquals("closing the panel must not stop capture", 1, starts)
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithText("Pausar").assertIsDisplayed()
        screenshot("notes-right-panel")
    }

    @Test fun correctedBibleNamesKeepTheOriginalRecognitionAvailable() {
        val fixed = TranscriptEntry(1, "Efesios 2:8", originalText = "Efecios 2:8")
        compose.setContent {
            MaterialTheme { NotesWorkspace(listOf(fixed), "", TextFieldValue(""), false, true, {}, {}, {}, Modifier.fillMaxSize()) }
        }
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithText("Nombre bíblico corregido · ver original").performClick()
        compose.onNodeWithText("Efecios 2:8").assertIsDisplayed()
        compose.onNodeWithTag("fragment-1").assertTextContains("Efesios 2:8")
    }

    @Test fun drawingOverAReferenceUsesThePenWithoutOpeningTheBible() {
        var ink by mutableStateOf(InkDocument())
        var opened: BibleReference? = null
        compose.setContent {
            MaterialTheme {
                HandwritingNotePanel(TextFieldValue("Juan 3:16"), {}, ink, { ink = it }, false, false, {}, {},
                    Modifier.width(580.dp).fillMaxHeight(), onReference = { opened = it })
            }
        }
        compose.onNodeWithText("Lápiz").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(40f, 38f), Offset(180f, 42f), 200) }
        compose.runOnIdle { assertEquals(1, ink.strokes.size); assertEquals(null, opened) }
    }
}
