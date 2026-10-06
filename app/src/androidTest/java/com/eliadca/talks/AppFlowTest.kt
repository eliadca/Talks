package com.eliadca.talks

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.SpanType
import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.editor.RichEditText
import com.eliadca.talks.ui.talks.ReaderView
import com.eliadca.talks.speech.EngineState
import com.eliadca.talks.speech.SpeechEngine
import com.eliadca.talks.speech.SpeechEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/** Drives the real app on an emulator: library, editor, tablet layout and Talks mode. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** On failure, dump the failing line, the semantics tree and a coarse screenshot: the CI log is all that can be read remotely. */
    @get:Rule(order = 2)
    val onFailure = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            Log.i("TALKS_TEST", "FAILED ${description.methodName}: ${e.message?.lineSequence()?.take(6)?.joinToString(" | ")}")
            e.stackTrace.filter { it.className.startsWith("com.eliadca.talks") }.take(4).forEach { Log.i("TALKS_TEST", "  at $it") }
            logFocus("failure")
            try {
                val a = compose.activity
                Log.i("TALKS_TEST", "activity state=${a.lifecycle.currentState} finishing=${a.isFinishing} hasFocus=${a.hasWindowFocus()}")
            } catch (t: Throwable) { Log.i("TALKS_TEST", "activity unavailable: ${t.message?.take(200)}") }
            shell("logcat -d -t 600").lineSequence()
                .filter { Regex("ANR in|isn't responding|FATAL EXCEPTION|has died|am_crash|am_anr|Force finishing").containsMatchIn(it) }
                .take(12).forEach { Log.i("TALKS_TEST", "sys: ${it.take(260)}") }
            try { compose.onRoot().printToLog("TALKS_TREE_FAIL") } catch (t: Throwable) { Log.i("TALKS_TEST", "no tree: ${t.message?.take(200)}") }
            try { Shots.take("failure") } catch (_: Throwable) {}
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val container get() = context.container

    private class FakeEngine : SpeechEngine {
        val flow = MutableSharedFlow<SpeechEvent>(replay = 16, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        override val events: SharedFlow<SpeechEvent> = flow
        override val name = "Fake"
        // Like the real engines: announce that the recogniser is up and listening.
        override fun start() {
            flow.tryEmit(SpeechEvent.State(EngineState.STARTING))
            flow.tryEmit(SpeechEvent.State(EngineState.LISTENING))
        }
        override fun stop() {}
        override fun release() {}
        fun hear(text: String, final: Boolean = false) {
            flow.tryEmit(if (final) SpeechEvent.Final(text) else SpeechEvent.Partial(text))
        }
    }

    @Before
    fun prepare() {
        runBlocking(Dispatchers.IO) {
            // Wait for the first-launch sample content, then start from an empty library.
            withTimeout(20_000) { while (!container.settings.settings.first().seeded) delay(100) }
            container.database.clearAllTables()
            container.settings.update { AppSettings(seeded = true, templateSeeded = true, engine = EngineKind.ANDROID) }
        }
        val focused = runCatching { compose.waitUntil(20_000) { compose.activity.hasWindowFocus() }; true }.getOrDefault(false)
        Log.i("TALKS_TEST", "start of test: window focus=$focused")
        if (!focused) logFocus("before")
    }

    @After
    fun cleanUp() {
        container.speechEngineFactory = null
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    private fun waitForText(text: String, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("text '$text' did not appear within ${timeoutMs}ms")
        }
    }

    private fun shell(cmd: String): String = try {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    } catch (t: Throwable) {
        "shell failed: ${t.message}"
    }

    /** Which window the system thinks has the focus; an ANR or system dialog on top breaks Espresso. */
    private fun logFocus(tag: String) {
        shell("dumpsys window").lineSequence()
            .filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") }
            .map { it.trim().take(200) }.distinct().take(3)
            .forEach { Log.i("TALKS_TEST", "[$tag] $it") }
    }

    /** The emulator tablet is landscape by nature; rotating by 90 degrees would make it portrait. */
    private fun forceLandscape(): Boolean {
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        val metrics = { compose.activity.resources.displayMetrics }
        for (rotation in intArrayOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90)) {
            ua.setRotation(rotation)
            val ok = runCatching { compose.waitUntil(6_000) { metrics().widthPixels > metrics().heightPixels }; true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** Rotating by 90 degrees from the natural landscape makes this tablet portrait. */
    private fun forcePortrait(): Boolean {
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        val metrics = { compose.activity.resources.displayMetrics }
        for (rotation in intArrayOf(UiAutomation.ROTATION_FREEZE_90, UiAutomation.ROTATION_FREEZE_270, UiAutomation.ROTATION_FREEZE_0)) {
            ua.setRotation(rotation)
            val ok = runCatching { compose.waitUntil(6_000) { metrics().widthPixels < metrics().heightPixels }; true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun findReader(v: android.view.View): ReaderView? {
        if (v is ReaderView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findReader(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Scroll state of the reader, read on the main thread. */
    private fun readerState(): String {
        var out = "no reader"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val r = findReader(compose.activity.window.decorView)
            if (r != null) {
                out = "scrollY=${r.scrollY} height=${r.height} content=${r.getChildAt(0).height} " +
                    "readingLine=${r.readingLineOffset()} manual=${r.manual} follow=${r.autoFollow}"
            }
        }
        return out
    }

    /** Clicks the on-screen clickable node with [text] (the closed menu holds off-screen twins). */
    private fun clickVisible(text: String) {
        val width = compose.activity.resources.displayMetrics.widthPixels
        val nodes = compose.onAllNodes(hasText(text) and hasClickAction())
        val all = nodes.fetchSemanticsNodes()
        val i = all.indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= width + 1 }
        assertTrue("no visible '$text' among ${all.size}", i >= 0)
        nodes[i].performClick()
    }

    private fun assertShown(text: String) {
        val n = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
        assertTrue("'$text' should be on screen (found $n)", n > 0)
    }

    private fun <T> eventually(timeoutMs: Long = 8_000, block: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            val v = block()
            if (v != null) return v
            if (System.currentTimeMillis() > end) throw AssertionError("condition not reached in ${timeoutMs}ms")
            Thread.sleep(100)
        }
    }

    // ------------------------------------------------------------------------------------------

    @Test
    fun startsUpAndShowsTheLibrary() {
        val id = runBlocking { container.speeches.create("Mi primera charla", Markup.parse("Hola a todos")) }
        waitForText("Mi primera charla")
        compose.onRoot().printToLog("TALKS_TREE_HOME")
        Shots.take("library")
        assertTrue(id > 0)
    }

    @Test
    fun editingInTheRealEditorIsSavedWithItsFormatting() {
        runBlocking { container.speeches.create("Para editar", Markup.parse("Hola")) }
        waitForText("Para editar")
        compose.onAllNodesWithText("Para editar")[0].performClick()

        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))

        onView(isAssignableFrom(RichEditText::class.java)).perform(click(), typeText(" mundo"))
        compose.onNodeWithContentDescription("Negrita (Ctrl+B)").performClick()
        onView(isAssignableFrom(RichEditText::class.java)).perform(typeText(" fuerte"), closeSoftKeyboard())
        Shots.take("editor-typed")

        val saved = eventually(10_000) {
            runBlocking(Dispatchers.IO) {
                val items = container.speeches.observeActive().first()
                items.firstOrNull { it.title == "Para editar" }?.let { container.speeches.load(it.id) }
            }?.takeIf { it.doc.text.contains("fuerte") }
        }
        Log.i("TALKS_TEST", "saved text='${saved.doc.text}' spans=${saved.doc.spans}")
        assertTrue(saved.doc.text.contains("mundo"))
        val bold = saved.doc.spans.firstOrNull { it.type == SpanType.BOLD }
        assertTrue("bold span saved: ${saved.doc.spans}", bold != null && saved.doc.text.substring(bold.start, bold.end).contains("fuerte"))
    }

    @Test
    fun tabletLandscapeShowsTheMenuRailTheListAndTheEditorSideBySide() {
        runBlocking { container.speeches.create("Charla A", SampleContent.welcome) }
        waitForText("Charla A")
        val landscape = forceLandscape()
        val metrics = compose.activity.resources.displayMetrics
        compose.waitForIdle()
        Log.i("TALKS_TEST", "landscape=$landscape size=${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.density}")
        assertTrue("the tablet must be in landscape", landscape)
        compose.onRoot().printToLog("TALKS_TREE_TABLET")
        // The folded menu (rail), the list and the (empty) editor are all visible at once.
        assertShown("Todos")
        assertShown("Ajustes")
        assertShown("Elige un discurso")
        compose.onAllNodesWithText("Charla A")[0].performClick()
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))
        Shots.take("tablet-landscape-editing")

        // Writing with the whole screen folds the list away, and brings it back.
        compose.onNodeWithContentDescription("Escribir a pantalla completa").performClick()
        compose.waitForIdle()
        Shots.take("tablet-focus")
        compose.onNodeWithContentDescription("Mostrar la lista de discursos").performClick()

        // The full menu opens from the rail.
        compose.onNodeWithContentDescription("Abrir el menú").performClick()
        compose.waitForIdle()
        Thread.sleep(400)
        Shots.take("menu-open")
        compose.onNodeWithContentDescription("Cerrar el menú").performClick()
    }

    @Test
    fun portraitTabletTakesTurnsAndScrollingNeverOpensTheMenu() {
        runBlocking { container.speeches.create("Charla vertical", SampleContent.practice) }
        waitForText("Charla vertical")
        assertTrue("the tablet must be in portrait", forcePortrait())
        compose.waitForIdle()
        Shots.take("portrait-library")
        compose.onAllNodesWithText("Charla vertical")[0].performClick()
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))
        Shots.take("portrait-editor")
        // The reported bug: moving through the speech opened the side menu.
        onView(isAssignableFrom(RichEditText::class.java)).perform(swipeUp(), swipeRight(), swipeDown())
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cerrar el menú").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Volver a la lista").performClick()
        waitForText("Charla vertical")
    }

    private fun clipboard(): android.content.ClipboardManager =
        context.getSystemService(android.content.ClipboardManager::class.java)

    private fun clipboardText(): String {
        var text = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            text = clipboard().primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        }
        return text
    }

    @Test
    fun markdownFromAnAssistantComesInFormattedAndGoesBackOutAsMarkdown() {
        assertTrue("the tablet must be in landscape", forceLandscape())
        waitForText("Todos")
        val answer = "```markdown\n# Discurso de la IA\n[Respirar.]\n## Apertura\nBuenos días a **todos**.\n- uno\n- dos\n```"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clipboard().setPrimaryClip(android.content.ClipData.newPlainText("respuesta", answer))
        }

        // Importar → Pegar desde el portapapeles.
        compose.onNodeWithContentDescription("Importar (archivos, portapapeles)").performClick()
        waitForText("Pegar desde el portapapeles")
        Shots.take("import-menu")
        clickVisible("Pegar desde el portapapeles")
        waitForText("Discurso de la IA")
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))
        val saved = eventually(10_000) {
            runBlocking(Dispatchers.IO) {
                container.speeches.observeActive().first().firstOrNull { it.title == "Discurso de la IA" }?.let { container.speeches.load(it.id) }
            }
        }
        Log.i("TALKS_TEST", "imported text='${saved.doc.text}' spans=${saved.doc.spans}")
        for (type in listOf(SpanType.H1, SpanType.H2, SpanType.BOLD, SpanType.BULLET)) {
            assertTrue("imported speech has no $type: ${saved.doc.spans}", saved.doc.spans.any { it.type == type })
        }
        assertTrue(!saved.doc.text.contains("**") && !saved.doc.text.contains("```"))
        // The emulator's screen lags behind the app; give the editor time to show the speech.
        Thread.sleep(1500)
        Shots.take("imported-markdown")

        // ⋮ → Copiar como Markdown gives the same Markdown back (without the code fence).
        compose.onNodeWithContentDescription("Más opciones").performClick()
        waitForText("Copiar como Markdown")
        Shots.take("editor-menu")
        clickVisible("Copiar como Markdown")
        val copied = eventually { clipboardText().takeIf { it.startsWith("# Discurso de la IA") } }
        Log.i("TALKS_TEST", "copied as markdown='${copied.replace("\n", "\\n")}'")
        assertTrue(copied, copied.contains("Buenos días a **todos**.") && copied.contains("- uno\n- dos") && copied.contains("## Apertura"))

        // Importar → Copiar plantilla para tu IA, then open the note from the message.
        compose.onNodeWithContentDescription("Importar (archivos, portapapeles)").performClick()
        waitForText("Copiar plantilla para tu IA")
        clickVisible("Copiar plantilla para tu IA")
        eventually { clipboardText().takeIf { it == SampleContent.AGENT_TEMPLATE } }
        // Earlier messages are shown first, one after another.
        waitForText("Ver nota", 30_000)
        clickVisible("Ver nota")
        waitForText(SampleContent.AGENT_TEMPLATE_TITLE)
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))
        Shots.take("agent-template")
    }

    @Test
    fun settingsOpenFromTheMenu() {
        forceLandscape()
        waitForText("Ajustes")
        clickVisible("Ajustes")
        waitForText("Modo Talks")
        compose.waitForIdle()
        Shots.take("settings")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitForText("Todos")
    }

    @Test
    fun talksModeFollowsASimulatedSpeakerFromStartToFinish() {
        val fake = FakeEngine()
        container.speechEngineFactory = { fake }
        val practice = SampleContent.practice
        runBlocking { container.speeches.create("Discurso de prueba", practice) }
        waitForText("Discurso de prueba")
        compose.onAllNodesWithText("Discurso de prueba")[0].performClick()
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))

        compose.onNode(hasText("Talks") and hasClickAction()).performClick()
        waitForText("COMENZAR")
        Shots.take("talks-prepare")
        compose.onRoot().printToLog("TALKS_TREE_PREPARE")
        compose.onNodeWithText("COMENZAR").performClick()

        // Nothing heard yet: listening, not yet following.
        waitForText("Escuchando")

        // The speaker reads the first paragraph; the recogniser sends growing partial results.
        val words = com.eliadca.talks.core.track.ScriptIndex.build(practice.text).tokens.map { it.norm }
        var spoken = 0
        while (spoken < 40) {
            spoken += 4
            fake.hear(words.take(spoken).joinToString(" "))
            Thread.sleep(120)
        }
        waitForText("Siguiendo")
        Shots.take("talks-following")
        compose.onRoot().printToLog("TALKS_TREE_LIVE")

        // How the text is marked: whole sentences, one word ahead of the voice.
        if (compose.onAllNodes(hasText("Marcado") and hasClickAction()).fetchSemanticsNodes().isEmpty()) {
            onView(isAssignableFrom(ReaderView::class.java)).perform(click()) // a tap shows the controls
        }
        clickVisible("Marcado")
        waitForText("Al ritmo de tu voz")
        clickVisible("Oración")
        clickVisible("Adelantar")
        waitForText("1 palabra por delante")
        Thread.sleep(500)
        Shots.take("talks-marking")
        clickVisible("Listo")

        // Something goes badly wrong: the speaker turns everything automatic off and scrolls by hand.
        if (compose.onAllNodes(hasText("Manual") and hasClickAction()).fetchSemanticsNodes().isEmpty()) {
            onView(isAssignableFrom(ReaderView::class.java)).perform(click()) // a tap shows the controls
        }
        compose.onNode(hasText("Manual") and hasClickAction()).performClick()
        waitForText("Modo manual")
        // Whatever the recogniser still sends is ignored now.
        fake.hear(words.take(70).joinToString(" "), final = true)
        Thread.sleep(300)
        Shots.take("talks-manual")
        compose.onNodeWithText("Seguir con la voz desde aquí").performClick()
        waitForText("Siguiendo")

        // The speaker improvises: the status says so and the place is kept.
        fake.hear("como les decia ayer en la reunion con el equipo de ventas y los clientes del norte y la verdad es que si", final = true)
        waitForText("Improvisando", timeoutMs = 10_000)

        // And reads the rest of the speech to the very end.
        fake.hear(words.drop(36).joinToString(" "), final = true)
        waitForText("Fin del discurso", timeoutMs = 20_000)
        Thread.sleep(1200)
        Log.i("TALKS_TEST", "reader at the end: ${readerState()}")
        Shots.take("talks-finished")

        compose.onNodeWithText("Terminar y ver resumen").performClick()
        waitForText("¿Terminar el modo Talks?")
        compose.onNode(hasText("Terminar") and hasClickAction()).performClick()
        waitForText("Sesión terminada")
        Shots.take("talks-summary")
    }
}
