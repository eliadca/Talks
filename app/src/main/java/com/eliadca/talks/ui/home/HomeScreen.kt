package com.eliadca.talks.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.toMarkdown
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.export.Clipboard
import com.eliadca.talks.ui.components.EmptyState
import com.eliadca.talks.ui.editor.EditorPane
import com.eliadca.talks.ui.editor.EditorViewModel
import com.eliadca.talks.ui.editor.MoveToFolderDialog
import kotlinx.coroutines.launch

/**
 * The main screen. The menu is folded into a slim rail (its full version opens on demand, never
 * by swiping); the list of speeches sits next to the editor when there is room for both, and can
 * be folded away to write with the whole screen. Narrow windows show the list or the editor.
 */
@Composable
fun HomeScreen(
    home: HomeViewModel,
    editor: EditorViewModel,
    settings: AppSettings,
    dark: Boolean,
    onStartTalks: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onExportPdf: (String, RichDoc) -> Unit,
    onShareText: (String, RichDoc) -> Unit,
    onShareMarkdown: (String, String) -> Unit,
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
    var movingSpeech by remember { mutableStateOf<SpeechListItem?>(null) }
    /** Writing with the whole screen: the list of speeches is folded away. */
    var focusMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedId) { editor.open(selectedId) }
    LaunchedEffect(Unit) {
        home.messageFlow.collect { m ->
            val result = snackbar.showSnackbar(m.text, m.actionLabel, duration = androidx.compose.material3.SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val pace = if (home.paceWpm > 0) home.paceWpm else settings.wordsPerMinute
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val closeMenu: () -> Unit = { scope.launch { drawerState.close() } }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        home.importFiles(uris)
    }
    val importActions = ImportActions(
        files = { importLauncher.launch(IMPORT_TYPES) },
        paste = {
            val text = Clipboard.readText(context)
            if (text.isNullOrBlank()) home.toast("No hay texto en el portapapeles. Copia el discurso y vuelve a intentarlo.")
            else home.importText(text)
        },
        copyTemplate = home::copyTemplate,
    )
    val drawerImportActions = ImportActions(
        files = { closeMenu(); importActions.files() },
        paste = { closeMenu(); importActions.paste() },
        copyTemplate = { closeMenu(); importActions.copyTemplate() },
    )

    /** The open speech as it is on screen right now (the stored copy may lag a moment behind). */
    fun openSpeech(): Pair<String, RichDoc>? {
        val doc = editor.controller.snapshot() ?: editor.current?.doc ?: return null
        return editor.title to doc
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val showRail = maxWidth >= RAIL_MIN_WIDTH
        val listWidth = if (maxWidth >= 1200.dp) 360.dp else 330.dp
        val railWidth = if (showRail) RAIL_WIDTH else 0.dp
        val twoPane = maxWidth - railWidth - listWidth >= MIN_EDITOR_WIDTH
        val navigate: (LibraryFilter) -> Unit = { destination ->
            home.setFilter(destination)
            focusMode = false
            // In a narrow window, navigation must actually reveal the library.
            if (!twoPane) home.select(null)
        }

        BackHandler(
            enabled = filter.isFolderLocation && filter != LibraryFilter.Folders &&
                (selectedId == null || (twoPane && !focusMode)) && drawerState.isClosed,
        ) { navigate(LibraryFilter.Folders) }

        val list: @Composable (Modifier) -> Unit = { mod ->
            AnimatedContent(filter == LibraryFilter.Folders, modifier = mod, label = "library-pane") { browsingFolders ->
                if (browsingFolders) {
                    FolderListPane(
                        folders = folders,
                        counts = counts,
                        query = query,
                        showMenuButton = !showRail,
                        onMenu = openMenu,
                        onQuery = home::setQuery,
                        onOpen = navigate,
                        onNewFolder = home::createFolder,
                        onUpdateFolder = home::updateFolder,
                        onDeleteFolder = home::deleteFolder,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SpeechListPane(
                        items = items,
                        folders = folders,
                        selectedId = selectedId,
                        filter = filter,
                        query = query,
                        sort = sort,
                        paceWpm = pace,
                        showMenuButton = !showRail,
                        onMenu = openMenu,
                        onQuery = home::setQuery,
                        onSort = home::setSort,
                        // A speech opens with the whole screen to write; the list comes back with one tap.
                        onSelect = { id -> home.select(id); focusMode = true },
                        onNew = home::createSpeech,
                        onTogglePin = home::togglePin,
                        onDuplicate = home::duplicate,
                        onTrash = { home.moveToTrash(it.id, it.title) },
                        onMove = { movingSpeech = it },
                        onBackToFolders = { navigate(LibraryFilter.Folders) },
                        onRestore = home::restore,
                        onDeleteForever = { confirmDelete = it },
                        onEmptyTrash = { confirmDelete = EMPTY_TRASH_MARKER },
                        importActions = importActions,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        val actions = SpeechActions(
            togglePin = { selectedItem?.let(home::togglePin) },
            duplicate = { selectedId?.let(home::duplicate) },
            moveToTrash = { selectedItem?.let { home.moveToTrash(it.id, it.title) } },
            moveToFolder = { f -> selectedId?.let { home.moveToFolder(it, f) } },
            setLabel = { c -> selectedId?.let { home.setLabel(it, c) } },
            setTargetMinutes = { m -> selectedId?.let { home.setTarget(it, m) } },
            exportPdf = { openSpeech()?.let { (title, doc) -> onExportPdf(title, doc) } },
            shareText = { openSpeech()?.let { (title, doc) -> onShareText(title, doc) } },
            copyMarkdown = {
                openSpeech()?.let { (title, doc) ->
                    Clipboard.copy(context, title.ifBlank { "Discurso" }, doc.toMarkdown(title))
                    home.toast("Copiado como Markdown")
                }
            },
            shareMarkdown = { openSpeech()?.let { (title, doc) -> onShareMarkdown(title, doc.toMarkdown(title)) } },
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
                    action = {
                        FilledTonalButton(onClick = home::createSpeech) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Nuevo discurso")
                        }
                    },
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
                    focusMode = if (compact) null else focusMode,
                    onToggleFocus = { focusMode = !focusMode },
                    onStartTalks = { selectedId?.let(onStartTalks) },
                    dark = dark,
                    modifier = mod,
                )
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            // Only a swipe that closes the open menu is honoured: scrolling never opens it.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(Modifier.width(320.dp)) {
                    SidebarContent(
                        filter = filter,
                        counts = counts,
                        onFilter = {
                            navigate(it)
                            closeMenu()
                        },
                        onNewSpeech = {
                            home.createSpeech()
                            closeMenu()
                        },
                        onSettings = {
                            closeMenu()
                            onOpenSettings()
                        },
                        onClose = closeMenu,
                        importActions = drawerImportActions,
                    )
                }
            },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (showRail) {
                    LibraryRail(
                        filter = filter,
                        counts = counts,
                        onNewSpeech = home::createSpeech,
                        onFilter = navigate,
                        onSettings = onOpenSettings,
                        modifier = Modifier.width(RAIL_WIDTH).fillMaxHeight(),
                    )
                }
                if (twoPane) {
                    AnimatedVisibility(
                        visible = !(focusMode && selectedId != null),
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally(),
                    ) {
                        Row(Modifier.fillMaxHeight()) {
                            list(Modifier.width(listWidth).fillMaxHeight())
                            VerticalDivider()
                        }
                    }
                    editorPane(Modifier.weight(1f).fillMaxHeight(), false)
                } else {
                    BackHandler(enabled = selectedId != null && drawerState.isClosed) { home.select(null) }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (selectedId == null) list(Modifier.fillMaxSize()) else editorPane(Modifier.fillMaxSize(), true)
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    movingSpeech?.let { speech ->
        MoveToFolderDialog(
            folders = folders,
            current = speech.folderId,
            onPick = { folder -> home.moveToFolder(speech.id, folder); movingSpeech = null },
            onDismiss = { movingSpeech = null },
        )
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

/** From this width on, the menu is a slim rail at the side. */
private val RAIL_MIN_WIDTH: Dp = 600.dp
private val RAIL_WIDTH: Dp = 88.dp

/** The editor next to the list must be at least this wide; otherwise they take turns. */
private val MIN_EDITOR_WIDTH: Dp = 520.dp

/** What the file picker offers; anything is allowed, since many apps label .md files vaguely. */
private val IMPORT_TYPES = arrayOf(
    "text/markdown", "text/x-markdown", "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/octet-stream", "*/*",
)

/** Stands in for "all trashed speeches" in the delete confirmation. */
private val EMPTY_TRASH_MARKER = SpeechListItem(-1, "", "", 0, null, false, 0, 0, 0, 0, null, null)
