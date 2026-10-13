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
import org.junit.Before
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
    @Before fun resetWorkspacePreferences() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("notes-workspace",android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("notes-pencil",android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }
    @get:Rule val compose = createComposeRule()
    private val entries = listOf(TranscriptEntry(1, "La primera idea importante."), TranscriptEntry(2, "Otra idea que merece una nota."))

    @Test fun tappingToolsRevealsLivePropertiesAndLongPressOpensTheToolGroup() {
        val state=com.eliadca.talks.ui.notes.NoteEditorState().apply {toolbarSide=true}
        compose.setContent {MaterialTheme {HandwritingNotePanel(TextFieldValue("Una idea"),{},InkDocument(),{},false,false,{},{},Modifier.fillMaxSize(),state=state)}}
        compose.onNodeWithContentDescription("Goma de borrar").performClick()
        compose.onNodeWithText("Borrar solo lo que toco").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tamaño de goma").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(40f)}
        compose.runOnIdle {assertEquals(40f,state.eraserRadius)}
        compose.onNodeWithContentDescription("Lápiz").performClick()
        compose.onNodeWithContentDescription("Grosor del lápiz").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lápiz").performTouchInput {longClick()}
        compose.onAllNodesWithText("Pluma").onLast().performClick()
        compose.runOnIdle {assertEquals(com.eliadca.talks.core.ink.PenKind.FOUNTAIN,state.pen)}
        screenshot("notes-studio-pen")
    }

    @Test fun visibleTypographyControlsFormatMovableTextAndPersistTheFontAndSize() {
        val state=com.eliadca.talks.ui.notes.NoteEditorState().apply {tool=com.eliadca.talks.ui.notes.InkTool.SELECT;toolbarSide=true;selected=setOf(1L)}
        var ink by mutableStateOf(InkDocument().addTextBox("Una idea editable").copy(textBoxes=listOf(com.eliadca.talks.core.ink.NoteTextBox(1,"Una idea editable",80f,160f,size=32f)),nextId=2))
        compose.setContent {MaterialTheme {HandwritingNotePanel(TextFieldValue("Texto principal"),{},ink,{ink=it},false,false,{},{},Modifier.fillMaxSize(),state=state)}}
        compose.onNodeWithContentDescription("Familia tipográfica").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lora").performClick()
        compose.onNodeWithContentDescription("Tamaño del texto").performTextReplacement("72")
        compose.onNodeWithContentDescription("Tamaño del texto").performImeAction()
        compose.runOnIdle {
            val style=ink.textBoxes.single().formatting.styleAt(0)
            assertEquals(com.eliadca.talks.core.notes.NoteFont.LORA,style.font)
            assertEquals(72f,style.size*ink.textBoxes.single().size/46f,.01f)
            assertTrue(ink.formatting.spans.isEmpty())
        }
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("text-box-1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.single().getLineBaseline(1)-layouts.single().getLineBaseline(0)>45f)
        screenshot("notes-studio-typography")
        compose.onNodeWithContentDescription("Transformar Ancho").performTextReplacement("360")
        compose.onNodeWithContentDescription("Transformar Ancho").performImeAction()
        compose.runOnIdle {
            assertEquals(360f,ink.textBoxes.single().width)
            assertEquals(32f,ink.textBoxes.single().size)
            assertEquals(72f,ink.textBoxes.single().formatting.styleAt(0).size*32f/46f,.01f)
        }
    }

    @Test fun numericTextPropertiesKeepTheSelectedWordsWhenAndroidCollapsesTheFieldSelection() {
        var body by mutableStateOf(TextFieldValue("Ideas para estudiar",TextRange(0,5)))
        var ink by mutableStateOf(InkDocument())
        compose.setContent {MaterialTheme {HandwritingNotePanel(body,{body=it},ink,{ink=it},false,false,{},{},Modifier.fillMaxSize(),
            onFormat={transform->ink=ink.copy(formatting=ink.formatting.apply(body.selection.min,body.selection.max,transform))},textStyle=ink.formatting.styleAt(body.selection.min))}}
        compose.onNodeWithTag("note-body").performTextInputSelection(TextRange(0,5))
        compose.onNodeWithContentDescription("Familia tipográfica").performClick()
        compose.onNodeWithText("Lora").performClick()
        compose.onNodeWithContentDescription("Tamaño del texto").performTextReplacement("72")
        compose.runOnIdle {body=body.copy(selection=TextRange(body.text.length))}
        compose.onNodeWithContentDescription("Tamaño del texto").performImeAction()
        compose.runOnIdle {
            assertEquals(com.eliadca.talks.core.notes.NoteFont.LORA,ink.formatting.styleAt(0).font)
            assertEquals(72f,ink.formatting.styleAt(0).size)
            assertEquals(46f,ink.formatting.styleAt(7).size)
        }
    }

    @Test fun workspacePlacementAndZoomSurviveClosingAndReopeningTheEditor() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("notes-pencil",0).edit().putFloat("zoom",1.4f).putBoolean("toolbar-side",true).commit()
        context.getSharedPreferences("notes-workspace",0).edit().putBoolean("panel-open",true).putString("dock","BOTTOM").commit()
        var generation by mutableIntStateOf(0)
        compose.setContent {key(generation) {MaterialTheme {NotesWorkspace(entries,"",TextFieldValue("Una idea"),false,true,{},{},{},Modifier.fillMaxSize())}}}
        val page=compose.onNodeWithTag("note-page-scroll").fetchSemanticsNode().boundsInRoot
        val panel=compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(page.left>=112f);assertTrue(panel.top>=page.bottom)
        compose.runOnIdle {generation++}
        compose.onNodeWithTag("voice-panel").assertExists()
        compose.onNodeWithContentDescription("Tamaño de hoja y zoom").performScrollTo().performClick()
        compose.onNodeWithText("Zoom: 140%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar inspector").performClick()
        screenshot("notes-advanced-side-tools")
    }

    @Test fun vectorShapesAndPageTemplatesAreUsableFromTheToolbar() {
        var ink by mutableStateOf(InkDocument())
        compose.setContent {MaterialTheme {HandwritingNotePanel(TextFieldValue("Una idea"),{},ink,{ink=it},false,false,{},{},Modifier.fillMaxSize())}}
        compose.onNodeWithContentDescription("Formas y líneas").performScrollTo().performClick()
        compose.onNodeWithTag("shape-menu-RECTANGLE").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput {swipe(Offset(80f,120f),Offset(250f,280f),200)}
        compose.runOnIdle {assertEquals(1,ink.strokes.size);assertTrue(ink.strokes.single().geometric)}
        compose.onNodeWithContentDescription("Plantillas de hojas").performScrollTo().performClick()
        compose.onNodeWithText("Puntos").performClick()
        screenshotNode("notes-studio-template-gallery","template-gallery")
        compose.onNodeWithTag("apply-template").performClick()
        compose.runOnIdle {assertTrue(ink.dotted);assertEquals(1,ink.strokes.size)}
        screenshot("notes-vector-shape-template")
    }

    @Test fun textSelectionDoesNotOfferVectorOnlyActionsThatCannotApply() {
        val state=com.eliadca.talks.ui.notes.NoteEditorState().apply {tool=com.eliadca.talks.ui.notes.InkTool.SELECT;selected=setOf(InkDocument.MAIN_TEXT_ID)}
        compose.setContent {MaterialTheme {HandwritingNotePanel(TextFieldValue("Una idea"),{},InkDocument(bodyFrame=com.eliadca.talks.core.ink.NoteTextFrame()),{},false,false,{},{},Modifier.fillMaxSize(),state=state)}}
        compose.onNodeWithText("APARIENCIA").assertDoesNotExist()
        compose.onNodeWithContentDescription("Transformar X").assertExists()
        compose.onNodeWithContentDescription("Duplicar trazos").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Eliminar trazos seleccionados").assertIsNotEnabled()

    }

    @Test fun imagesTemplatesAndGradientVectorsShareAFullTabletWorkspace() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("notes-pencil",0).edit().putBoolean("toolbar-side",true).commit()
        context.getSharedPreferences("notes-workspace",0).edit().putBoolean("panel-open",true).commit()
        val photo=Bitmap.createBitmap(240,140,Bitmap.Config.ARGB_8888);val canvas=Canvas(photo)
        canvas.drawColor(android.graphics.Color.rgb(186,223,246))
        val paint=android.graphics.Paint().apply {color=android.graphics.Color.rgb(64,117,95)}
        val mountain=android.graphics.Path().apply {moveTo(0f,140f);lineTo(85f,35f);lineTo(140f,100f);lineTo(200f,50f);lineTo(240f,140f);close()};canvas.drawPath(mountain,paint)
        val bytes=java.io.ByteArrayOutputStream().apply {photo.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();photo.recycle()
        var ink by mutableStateOf(InkDocument(dotted=true,
            images=listOf(com.eliadca.talks.core.ink.NoteImage(1,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),x=80f,y=230f,width=420f,height=245f)),
            strokes=listOf(com.eliadca.talks.core.ink.InkStroke(2,com.eliadca.talks.core.ink.PenKind.BALLPOINT,0xff1565c0.toInt(),4f,
                com.eliadca.talks.core.ink.shapePoints(com.eliadca.talks.core.ink.NoteShape.RECTANGLE,com.eliadca.talks.core.ink.InkPoint(80f,540f),com.eliadca.talks.core.ink.InkPoint(730f,790f)),geometric=true,fillColor=0xffb2dfdb.toInt(),gradientColor=0xffbbdefb.toInt())),
            textBoxes=listOf(com.eliadca.talks.core.ink.NoteTextBox(3,"Ideas importantes\n• Organizar los objetivos\n• Diseñar con claridad",120f,580f,width=550f,size=34f)),nextId=4))
        compose.setContent {MaterialTheme {NotesWorkspace(entries,"",TextFieldValue("Proyecto de estudio\nImágenes, formas y notas en una misma hoja."),false,true,{},{},{},Modifier.fillMaxSize(),ink=ink,onInkChange={ink=it})}}
        compose.onNodeWithTag("voice-panel").assertExists();compose.onNodeWithTag("text-box-3").assertExists()
        screenshot("notes-high-end-workspace")
    }

    private fun screenshotNode(name:String,tag:String) {
        val dir=System.getenv("TALKS_SCREENSHOT_DIR") ?: return
        compose.onNodeWithTag(tag).assertExists()
        val output=File(dir,"$name.png").also {it.parentFile!!.mkdirs()}
        compose.runOnUiThread {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }

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
        compose.onNodeWithTag("transcript-text").performTextInputSelection(TextRange(3, 10))
        compose.onNodeWithText("Añadir selección").performClick()
        assertEquals("primera", selectedText)
        compose.onNodeWithTag("note-body").assertTextContains("primera")
        screenshot("notes-tablet")
    }

    @Test fun selectionCanSpanTwoRecognitionPhrasesAndAiReceivesOnlyTheSelection() {
        var inserted="";var aiFragment=""
        val text=entries.joinToString(" "){it.text}
        compose.setContent {MaterialTheme {NotesWorkspace(entries,"",TextFieldValue(""),false,true,{}, {},{inserted=it},Modifier.fillMaxSize(),aiInstalled=true,onAi={all,a,b->aiFragment=all.substring(a,b)})}}
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        val start=text.indexOf("importante");val end=text.indexOf("que merece")
        compose.onNodeWithTag("transcript-text").performTextInputSelection(TextRange(start,end))
        compose.onNodeWithText("Revisar con IA").performClick()
        assertEquals(text.substring(start,end),aiFragment)
        compose.onNodeWithText("Añadir selección").performClick()
        assertEquals(text.substring(start,end),inserted)
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
        compose.onNodeWithTag("transcript-text").assertTextContains(entries[0].text,substring=true)
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
        compose.onNodeWithTag("transcript-text").assertTextContains("Fragmento 12",substring=true)
        compose.runOnIdle { transcript = transcript + TranscriptEntry(13, "Última frase confirmada") }
        compose.onNodeWithTag("transcript-text").assertTextContains("Última frase confirmada",substring=true)
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
        compose.onNodeWithContentDescription("Modo lápiz").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(70f, 100f), Offset(220f, 150f), 200) }
        compose.runOnIdle { assertEquals(1, ink.strokes.size) }
        compose.onNodeWithContentDescription("Deshacer escritura").performClick()
        compose.runOnIdle { assertTrue(ink.strokes.isEmpty()) }
        compose.onNodeWithContentDescription("Rehacer escritura").performClick()
        compose.onNodeWithContentDescription("Resaltador").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Color #1565C0").performClick()
        compose.onNodeWithContentDescription("Cerrar inspector").performClick()
        compose.runOnIdle { assertTrue(ink.strokes.isNotEmpty()) }
        compose.onNodeWithContentDescription("Renglones").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Renglones en toda la página").performClick()
        compose.runOnIdle { assertTrue(ink.wholePageRuled) }
        compose.onNodeWithText("Elegir una zona de la página").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(60f, 200f), Offset(350f, 390f), 200) }
        compose.runOnIdle { assertEquals(1, ink.areas.size); assertTrue(!ink.wholePageRuled) }
        compose.onNodeWithTag("note-body").assertTextContains("Ideas de la reunión")
        screenshot("notes-handwriting")
    }

    @Test fun existingTypedTextMovesAndAFreeTextBoxRemainsEditableOnThePage() {
        var ink by mutableStateOf(InkDocument().addTextBox("Texto libre",350f))
        compose.setContent {MaterialTheme {HandwritingNotePanel(TextFieldValue("Texto anterior"),{},ink,{ink=it},false,false,{},{},Modifier.width(580.dp).fillMaxHeight())}}
        compose.onNodeWithContentDescription("Mover y ajustar texto").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput {swipe(Offset(120f,48f),Offset(150f,180f),200)}
        compose.runOnIdle {assertTrue(ink.bodyFrame!=null);assertTrue(ink.bodyFrame!!.y>40f)}
        compose.onNodeWithTag("note-body").assertTextContains("Texto anterior")
        compose.onNodeWithContentDescription("Modo teclado").performClick()
        compose.onNodeWithTag("text-box-1").performTextInput(" ampliado")
        compose.runOnIdle {assertTrue(ink.textBoxes.single().text.contains("ampliado"))}
        compose.onNodeWithTag("text-box-1").performTextInputSelection(TextRange(0,4))
        compose.onNodeWithContentDescription("Negrita").performClick()
        compose.runOnIdle {assertTrue(ink.textBoxes.single().formatting.styleAt(0).bold);assertTrue(ink.formatting.spans.isEmpty())}
        if(compose.onAllNodesWithTag("studio-inspector").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithContentDescription("Mostrar u ocultar inspector").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Lista de tareas").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(ink.textBoxes.single().text.startsWith("☐ "))}
        screenshot("notes-movable-text")
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
        val root = compose.onNodeWithTag("studio-canvas").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("Abrir transcripción").fetchSemanticsNode().boundsInRoot
        assertTrue(button.center.x > root.left+root.width * .8f && button.center.y > root.top+root.height * .8f)
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
        compose.onNodeWithTag("transcript-text").assertTextContains("Efesios 2:8")
        compose.onNodeWithContentDescription("Ver transcripción original").performClick()
        compose.onNodeWithTag("transcript-text").assertTextContains("Efecios 2:8")
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
        compose.onNodeWithContentDescription("Modo lápiz").performClick()
        compose.onNodeWithTag("ink-page").performTouchInput { swipe(Offset(40f, 38f), Offset(180f, 42f), 200) }
        compose.runOnIdle { assertEquals(1, ink.strokes.size); assertEquals(null, opened) }
    }
    @Test fun theAudioPanelDocksAboveBelowAndFloatsWithMovementResizeAndTxtExport() {
        var exports = 0
        compose.setContent { MaterialTheme { NotesWorkspace(entries, "", TextFieldValue("Mi nota"), false, true, {}, {}, {},
            Modifier.fillMaxSize(), onExport = { exports++ }) } }
        compose.onNodeWithContentDescription("Abrir transcripción").performClick()
        compose.onNodeWithContentDescription("Exportar transcripción a TXT").performClick(); assertEquals(1, exports)
        compose.onNodeWithContentDescription("Ubicación y tamaño del panel").performClick()
        compose.onNodeWithText("Debajo de la nota").performClick()
        var panel = compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        var page = compose.onNodeWithTag("note-page-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= page.bottom)
        compose.onNodeWithContentDescription("Ubicación y tamaño del panel").performClick()
        compose.onNodeWithText("Encima de la nota").performClick()
        panel = compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        page = compose.onNodeWithTag("note-page-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.bottom <= page.top)
        compose.onNodeWithContentDescription("Ubicación y tamaño del panel").performClick()
        compose.onNodeWithText("Flotante sobre la nota").performClick()
        val before = compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Transcripción").performTouchInput { swipe(center, center + Offset(50f, 40f), 200) }
        val after = compose.onNodeWithTag("voice-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(after.left > before.left && after.top > before.top)
        compose.onNodeWithContentDescription("Ubicación y tamaño del panel").performClick()
        compose.onNodeWithText("Cambiar tamaño").performClick()
        compose.onNodeWithContentDescription("Ancho del panel de voz").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(520f) }
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithTag("voice-panel").assertWidthIsEqualTo(520.dp)
        screenshot("notes-floating-audio")
    }

    @Test fun aMovableTextBoxAndLargerSheetUseTheSameSavedDocument() {
        var ink by mutableStateOf(InkDocument())
        compose.setContent { MaterialTheme { HandwritingNotePanel(TextFieldValue("Texto principal"), {}, ink, { ink = it }, false, false, {}, {}, Modifier.fillMaxSize()) } }
        compose.onNodeWithContentDescription("Añadir cuadro de texto móvil").performClick()
        compose.onNodeWithText("Texto del cuadro").performTextInput("Una anotación móvil")
        compose.onNodeWithText("Guardar").performClick()
        compose.runOnIdle { assertEquals("Una anotación móvil", ink.textBoxes.single().text) }
        compose.onNodeWithContentDescription("Duplicar trazos").performClick()
        compose.runOnIdle { assertEquals(2, ink.textBoxes.size) }
        compose.onNodeWithContentDescription("Tamaño de hoja y zoom").performClick()
        compose.onNodeWithContentDescription("Transformar Ancho de hoja").performTextReplacement("1600")
        compose.onNodeWithContentDescription("Transformar Ancho de hoja").performImeAction()
        compose.onNodeWithContentDescription("Transformar Alto de hoja").performTextReplacement("3000")
        compose.onNodeWithContentDescription("Transformar Alto de hoja").performImeAction()
        compose.runOnIdle { assertEquals(1600f, ink.pageWidth); assertEquals(3000f, ink.height) }
        screenshot("notes-large-paper")
    }

}
