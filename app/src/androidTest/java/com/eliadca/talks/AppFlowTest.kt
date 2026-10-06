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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
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
            container.settings.update { AppSettings(seeded = true, engine = EngineKind.ANDROID) }
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
    fun tabletLandscapeShowsFoldersListAndEditorSideBySide() {
        runBlocking { container.speeches.create("Charla A", Markup.parse("Texto de A")) }
        waitForText("Charla A")
        val landscape = forceLandscape()
        val metrics = compose.activity.resources.displayMetrics
        compose.waitForIdle()
        Log.i("TALKS_TEST", "landscape=$landscape size=${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.density}")
        assertTrue("the tablet must be in landscape", landscape)
        compose.onRoot().printToLog("TALKS_TREE_TABLET")
        // Sidebar, list and (empty) editor are all visible at once.
        assertShown("Papelera")
        assertShown("Nuevo discurso")
        assertShown("Elige un discurso")
        compose.onAllNodesWithText("Charla A")[0].performClick()
        onView(isAssignableFrom(RichEditText::class.java)).check(matches(isDisplayed()))
        Shots.take("tablet-landscape-editing")
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
        Shots.take("talks-finished")

        compose.onNodeWithText("Terminar y ver resumen").performClick()
        waitForText("¿Terminar el modo Talks?")
        compose.onNode(hasText("Terminar") and hasClickAction()).performClick()
        waitForText("Sesión terminada")
        Shots.take("talks-summary")
    }
}
