package com.eliadca.talks.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.ui.components.EmptyState
import com.eliadca.talks.ui.editor.EditorPane
import com.eliadca.talks.ui.editor.EditorViewModel
import kotlinx.coroutines.launch

/**
 * The main screen. Large tablets show folders, the list and the editor side by side; medium
 * windows show the list and the editor with folders in a drawer; phones show one at a time.
 */
@Composable
fun HomeScreen(
    home: HomeViewModel,
    editor: EditorViewModel,
    settings: AppSettings,
    dark: Boolean,
    onStartTalks: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onExportPdf: (Long) -> Unit,
    onShareText: (Long) -> Unit,
) {
    val filter by home.filter.collectAsState()
    val query by home.query.collectAsState()
    val sort by home.sort.collectAsState()
    val items by home.items.collectAsState()
    val counts by home.counts.collectAsState()
    val folders by home.folders.collectAsState()
    val selectedId by home.selectedId.collectAsState()
    val selectedItem by home.selectedItem.collectAsState()
    val versions by home.selectedVersions.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var confirmDelete by remember { mutableStateOf<SpeechListItem?>(null) }

    LaunchedEffect(selectedId) { editor.open(selectedId) }
    LaunchedEffect(Unit) {
        home.messageFlow.collect { m ->
            val result = snackbar.showSnackbar(m.text, m.actionLabel, duration = androidx.compose.material3.SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val pace = if (home.paceWpm > 0) home.paceWpm else settings.wordsPerMinute

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val large = maxWidth >= LARGE_WIDTH
        val twoPane = maxWidth >= MEDIUM_WIDTH
        val sidebar: @Composable (Modifier) -> Unit = { mod ->
            SidebarContent(
                filter = filter,
                counts = counts,
                folders = folders,
                onFilter = {
                    home.setFilter(it)
                    scope.launch { drawerState.close() }
                },
                onNewSpeech = {
                    home.createSpeech()
                    scope.launch { drawerState.close() }
                },
                onNewFolder = home::createFolder,
                onUpdateFolder = home::updateFolder,
                onDeleteFolder = home::deleteFolder,
                onSettings = onOpenSettings,
                modifier = mod,
            )
        }
        val list: @Composable (Modifier) -> Unit = { mod ->
            SpeechListPane(
                items = items,
                folders = folders,
                selectedId = selectedId,
                filter = filter,
                query = query,
                sort = sort,
                paceWpm = pace,
                showMenuButton = !large,
                onMenu = { scope.launch { drawerState.open() } },
                onQuery = home::setQuery,
                onSort = home::setSort,
                onSelect = home::select,
                onNew = home::createSpeech,
                onTogglePin = home::togglePin,
                onDuplicate = home::duplicate,
                onTrash = { home.moveToTrash(it.id, it.title) },
                onRestore = home::restore,
                onDeleteForever = { confirmDelete = it },
                onEmptyTrash = { confirmDelete = EMPTY_TRASH_MARKER },
                modifier = mod,
            )
        }
        val actions = SpeechActions(
            togglePin = { selectedItem?.let(home::togglePin) },
            duplicate = { selectedId?.let(home::duplicate) },
            moveToTrash = { selectedItem?.let { home.moveToTrash(it.id, it.title) } },
            moveToFolder = { f -> selectedId?.let { home.moveToFolder(it, f) } },
            setLabel = { c -> selectedId?.let { home.setLabel(it, c) } },
            setTargetMinutes = { m -> selectedId?.let { home.setTarget(it, m) } },
            exportPdf = { selectedId?.let(onExportPdf) },
            shareText = { selectedId?.let(onShareText) },
            saveVersion = { label -> selectedId?.let { home.saveVersion(it, label) } },
            restoreVersion = { v -> selectedId?.let { id -> home.restoreVersion(id, v) { editor.reload() } } },
            deleteVersion = home::deleteVersion,
        )
        val editorPane: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
            if (selectedId == null) {
                EmptyState(
                    Icons.Filled.EditNote, "Elige un discurso",
                    "Selecciona uno de la lista o crea uno nuevo para empezar a escribir.",
                    modifier = mod.fillMaxSize(),
                    action = { TextButton(onClick = home::createSpeech) { Text("Nuevo discurso") } },
                )
            } else {
                EditorPane(
                    vm = editor,
                    settings = settings,
                    item = selectedItem,
                    folders = folders,
                    versions = versions,
                    paceWpm = pace,
                    actions = actions,
                    onBack = if (compact) ({ home.select(null) }) else null,
                    onStartTalks = { selectedId?.let(onStartTalks) },
                    dark = dark,
                    modifier = mod,
                )
            }
        }

        when {
            large -> Row(Modifier.fillMaxSize()) {
                sidebar(Modifier.width(264.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer))
                list(Modifier.width(372.dp).fillMaxHeight())
                VerticalDivider()
                editorPane(Modifier.weight(1f).fillMaxHeight(), false)
            }
            twoPane -> ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { ModalDrawerSheet { sidebar(Modifier) } },
            ) {
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(340.dp).fillMaxHeight())
                    VerticalDivider()
                    editorPane(Modifier.weight(1f).fillMaxHeight(), false)
                }
            }
            else -> ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { ModalDrawerSheet { sidebar(Modifier) } },
            ) {
                BackHandler(enabled = selectedId != null) { home.select(null) }
                if (selectedId == null) list(Modifier.fillMaxSize()) else editorPane(Modifier.fillMaxSize(), true)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    confirmDelete?.let { target ->
        val all = target === EMPTY_TRASH_MARKER
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (all) "¿Vaciar la papelera?" else "¿Eliminar definitivamente?") },
            text = {
                Text(
                    if (all) "Se eliminarán para siempre todos los discursos de la papelera. Esta acción no se puede deshacer."
                    else "«${target.title.ifBlank { "Sin título" }}» se eliminará para siempre. Esta acción no se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (all) home.emptyTrash() else home.deleteForever(target.id)
                    confirmDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

private val LARGE_WIDTH: Dp = 1060.dp
private val MEDIUM_WIDTH: Dp = 700.dp

/** Stands in for "all trashed speeches" in the delete confirmation. */
private val EMPTY_TRASH_MARKER = SpeechListItem(-1, "", "", 0, null, false, 0, 0, 0, 0, null, null)
