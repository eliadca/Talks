package com.eliadca.talks.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.ui.editor.MoveToFolderDialog
import com.eliadca.talks.ui.home.FolderListPane
import com.eliadca.talks.ui.home.ImportActions
import com.eliadca.talks.ui.home.LibraryCounts
import com.eliadca.talks.ui.home.LibraryFilter
import com.eliadca.talks.ui.home.SortKey
import com.eliadca.talks.ui.home.SpeechListPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual folder UI without requiring an emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1000dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderLibraryTest {
    @get:Rule val compose = createComposeRule()

    private val source = FolderEntity(1, "Reuniones", 0xFF1E88E5.toInt(), 0)
    private val destination = FolderEntity(2, "Ensayos", 0xFF43A047.toInt(), 0)

    @Test fun folderSearchIgnoresAccentsAndOpeningUsesTheLibraryNavigation() {
        var opened: LibraryFilter? = null
        compose.setContent {
            var query by remember { mutableStateOf("") }
            MaterialTheme {
                FolderListPane(
                    folders = listOf(source, destination), counts = LibraryCounts(noFolder = 2, perFolder = mapOf(1L to 1)),
                    query = query, showMenuButton = false, onMenu = {}, onQuery = { query = it },
                    onOpen = { opened = it }, onNewFolder = { _, _ -> }, onUpdateFolder = {}, onDeleteFolder = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("Sin carpeta").assertIsDisplayed()
        compose.onNodeWithText("Buscar carpetas").performTextInput("REUNIÓN")
        compose.onNodeWithText("Ensayos").assertDoesNotExist()
        compose.onNodeWithText("Reuniones").performClick()
        assertEquals(LibraryFilter.Folder(1), opened)
        compose.onNodeWithContentDescription("Borrar búsqueda").performClick()
        compose.onNodeWithText("Sin carpeta").performClick()
        assertEquals(LibraryFilter.NoFolder, opened)
    }

    @Test fun folderOptionsRenameAndRequireConfirmationBeforeDeleting() {
        var changed: FolderEntity? = null
        var deleted: Long? = null
        compose.setContent {
            MaterialTheme {
                FolderListPane(
                    folders = listOf(source), counts = LibraryCounts(), query = "", showMenuButton = false,
                    onMenu = {}, onQuery = {}, onOpen = {}, onNewFolder = { _, _ -> },
                    onUpdateFolder = { changed = it }, onDeleteFolder = { deleted = it }, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithContentDescription("Opciones de carpeta: Reuniones").performClick()
        compose.onNodeWithText("Cambiar nombre y color").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Eventos")
        compose.onNodeWithText("Guardar").performClick()
        assertEquals(source.copy(name = "Eventos"), changed)
        compose.onNodeWithContentDescription("Opciones de carpeta: Reuniones").performClick()
        compose.onNodeWithText("Eliminar carpeta").performClick()
        assertNull(deleted)
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(deleted)
        compose.onNodeWithContentDescription("Opciones de carpeta: Reuniones").performClick()
        compose.onNodeWithText("Eliminar carpeta").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        assertEquals(1L, deleted)
    }

    @Test fun aNoteCanMoveFromItsVisibleListMenuAndOnlyMovesAfterConfirmation() {
        val note = SpeechListItem(1, "Mi discurso", "Hola mundo", 2, 1, false, 0, 0, 0, 0, null, null)
        val picked = mutableListOf<Long?>()
        compose.setContent {
            var moving by remember { mutableStateOf(false) }
            MaterialTheme {
                SpeechListPane(
                    items = listOf(note), folders = listOf(source, destination), selectedId = null,
                    filter = LibraryFilter.Folder(1), query = "", sort = SortKey.MODIFIED, paceWpm = 130,
                    showMenuButton = false, onMenu = {}, onQuery = {}, onSort = {}, onSelect = {}, onNew = {},
                    onTogglePin = {}, onDuplicate = {}, onTrash = {}, onMove = { moving = true }, onBackToFolders = {},
                    onRestore = {}, onDeleteForever = {}, onEmptyTrash = {},
                    importActions = ImportActions({}, {}, {}), modifier = Modifier.fillMaxSize(),
                )
                if (moving) {
                    MoveToFolderDialog(listOf(source, destination), note.folderId, { picked += it; moving = false }, { moving = false })
                }
            }
        }
        compose.onNodeWithContentDescription("Volver a carpetas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Opciones de discurso: Mi discurso").performClick()
        compose.onNodeWithText("Mover a carpeta").performClick()
        compose.onNodeWithText("Mover").assertIsNotEnabled()
        compose.onNodeWithText("Ensayos").performClick()
        assertEquals(emptyList<Long?>(), picked)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(emptyList<Long?>(), picked)
        compose.onNodeWithContentDescription("Opciones de discurso: Mi discurso").performClick()
        compose.onNodeWithText("Mover a carpeta").performClick()
        compose.onNodeWithText("Sin carpeta").performClick()
        compose.onNodeWithText("Mover").performClick()
        assertEquals(listOf<Long?>(null), picked)
    }
}
