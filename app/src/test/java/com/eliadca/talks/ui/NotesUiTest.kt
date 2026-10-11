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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.ui.home.SidebarLayout
import com.eliadca.talks.ui.notes.FloatingPassage
import com.eliadca.talks.ui.notes.NotesWorkspace
import com.eliadca.talks.ui.notes.TranscriptEntry
import org.junit.Assert.assertEquals
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
        compose.onNodeWithTag("fragment-1").performTextInputSelection(TextRange(3, 10))
        compose.onNodeWithText("Añadir selección").performClick()
        assertEquals("primera", selectedText)
        compose.onNodeWithTag("note-body").assertTextContains("primera")
        screenshot("notes-tablet")
    }

    @Test fun aPhoneCanSwitchPanelsWithoutDiscardingTheTranscript() {
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
        compose.onNodeWithText("Añadir todo").performClick()
        assertEquals(setOf(1L, 2L), added)
        compose.onNodeWithText("Mi nota").performClick()
        compose.onNodeWithTag("note-body").assertTextContains("Mis ideas guardadas")
        compose.onNodeWithText("Transcripción").performClick()
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
        compose.onNodeWithTag("fragment-12").assertIsDisplayed()
        compose.runOnIdle { transcript = transcript + TranscriptEntry(13, "Última frase confirmada") }
        compose.onNodeWithTag("fragment-13").assertIsDisplayed()
    }
}
