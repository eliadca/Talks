package com.eliadca.talks.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.ui.notes.BibleLinkedTextField
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BibleLinkedTextFieldTest {
    @get:Rule val compose = createComposeRule()
    private val text = "Una idea y Juan 3:16 para recordar."
    private var layout: TextLayoutResult? = null
    private var opened: BibleReference? = null
    private fun content(enabled: Boolean = true) {
        compose.setContent {
            var body by remember { mutableStateOf(TextFieldValue(text)) }
            MaterialTheme {
                BibleLinkedTextField(body, { body = it }, false, enabled, { opened = it },
                    MaterialTheme.typography.bodyLarge, { layout = it }, Modifier.width(400.dp).testTag("linked-note"))
            }
        }
        if (enabled) compose.waitUntil(10000) {
            compose.onNodeWithTag("linked-note").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)?.isNotEmpty() == true
        } else compose.waitForIdle()
    }

    @Test fun tappingAWrittenReferenceOpensItsPassageWithoutReplacingTheNote() {
        content()
        val point = layout!!.getBoundingBox(text.indexOf("Juan") + 2).center
        compose.onNodeWithTag("linked-note").performTouchInput { click(point) }
        assertEquals(BibleReference(43, 3, 16), opened)
        compose.onNodeWithTag("linked-note").assertTextContains(text)
    }

    @Test fun draggingAcrossACitationDoesNotOpenAPopup() {
        content()
        val point = layout!!.getBoundingBox(text.indexOf("Juan") + 2).center
        compose.onNodeWithTag("linked-note").performTouchInput { swipe(point, point + androidx.compose.ui.geometry.Offset(90f, 0f), 200) }
        assertNull(opened)
    }

    @Test fun inkModeDisablesLinkTapsAndReplacingTextDoesNotLeaveOldLinkActions() {
        content(enabled = false)
        val point = layout!!.getBoundingBox(text.indexOf("Juan") + 2).center
        compose.onNodeWithTag("linked-note").performTouchInput { click(point) }
        assertNull(opened)
        compose.onNodeWithTag("linked-note").performTextReplacement("Una nota sin referencias")
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("linked-note").fetchSemanticsNode().config[SemanticsActions.CustomActions].isEmpty())
    }
}
