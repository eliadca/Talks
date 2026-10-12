package com.eliadca.talks.ui.notes

import android.Manifest
import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eliadca.talks.ui.findActivity
import kotlin.math.roundToInt
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.R
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.bible.BibleReferenceSuggestion
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.data.db.NoteListItem
import com.eliadca.talks.export.Clipboard
import com.eliadca.talks.speech.EngineState
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(vm: NotesViewModel, onMenu: () -> Unit, modifier: Modifier = Modifier, allowBack: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notes by vm.items.collectAsState()
    val saveError by vm.saveError.collectAsState()
    var citationAnchor by remember { mutableStateOf<Rect?>(null) }
    var exportText by remember { mutableStateOf("") }
    var bibleOptions by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<NoteListItem?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun startListening() {
        vm.start()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { vm.exportTranscript(it, exportText) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else vm.permissionDenied()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBible) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.flushEdits() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.flushEdits() }
    }
    BackHandler(enabled = vm.note != null && allowBack) { vm.backToList() }
    BackHandler(enabled = vm.fullScreen && allowBack) { vm.fullScreen = false }
    DisposableEffect(vm.fullScreen) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (vm.fullScreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = if (vm.note == null) onMenu else vm::backToList) {
                    Icon(if (vm.note == null) Icons.Filled.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                        if (vm.note == null) "Abrir el menú" else "Volver a notas")
                }
                Column(Modifier.weight(1f)) {
                    if (vm.note == null) Text("Notas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    else BasicTextField(vm.note!!.title, vm::title, singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth().testTag("note-title").semantics { contentDescription = "Título de la nota" },
                        decorationBox = { field -> Box { if (vm.note!!.title.isEmpty()) Text("Sin título", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); field() } })
                    Text(if (vm.note == null) "Escucha, elige y guarda tus ideas" else if (saveError == null) "Guardado automático" else "Pendiente de guardar",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.note != null) IconButton(onClick = { vm.fullScreen = !vm.fullScreen }) {
                    Icon(if (vm.fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (vm.fullScreen) "Salir de pantalla completa" else "Pantalla completa de notas")
                }
                IconButton(onClick = { bibleOptions = true }) { Icon(Icons.Filled.MenuBook, "Biblia de Estudio") }
                if (vm.note == null) {
                    FilledTonalButton(onClick = vm::create, enabled = !vm.loading) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva nota")
                    }
                }
            }
            if (vm.listening && (vm.note == null || vm.note?.id != vm.recordingNoteId)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mic, null, Modifier.size(18.dp))
                        Text("Transcribiendo en ${vm.recordingTitle}", Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        TextButton(onClick = { vm.recordingNoteId?.let(vm::open) }) { Text("Ver") }
                        TextButton(onClick = vm::pause) { Text("Pausar") }
                    }
                }
            }
            if (saveError != null) Text(saveError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            if (vm.note == null) {
                NoteLibrary(notes, vm::open, { delete = it }, Modifier.weight(1f))
            } else {
                key(vm.note!!.id) {
                    NotesWorkspace(entries = vm.entries, partial = vm.partial, body = vm.body,
                        automatic = vm.note!!.automatic, wide = wide, onEdit = vm::edit, onAdd = vm::addEntries, onInsert = vm::insert,
                        ink = vm.ink, onInkChange = vm::editInk, canUndoInk = vm.canUndoInk, canRedoInk = vm.canRedoInk,
                        onUndoInk = vm::undoInk, onRedoInk = vm::redoInk,
                        listening = vm.listening && vm.note?.id == vm.recordingNoteId, engineState = vm.engineState, level = vm.level, onAuto = vm::automatic,
                        onReference = { citationAnchor = null; if (vm.bible == null) bibleOptions = true else vm.showReference(it) },
                        onAnchorChanged = { citationAnchor = it },
                        onReferenceAt = { ref, anchor -> citationAnchor = anchor; if (vm.bible == null) bibleOptions = true else vm.showReference(ref) },
                        onFormat = vm::formatText, textStyle = vm.currentTextStyle, onAlign = vm::alignText, onList = vm::listText,
                        canUndoText = vm.canUndoText, canRedoText = vm.canRedoText, onUndoText = vm::undoText, onRedoText = vm::redoText,
                        onExport = {
                            exportText = vm.exportTranscript()
                            exporter.launch(vm.note!!.title.ifBlank { "Nota" }.replace(Regex("[\\/:*?\"<>|]"), "_") + "_transcripcion.txt")
                        },
                        references = vm.detected, suggestions = vm.referenceSuggestions,
                        allowBack = allowBack,
                        onToggleListen = {
                            if (vm.listening) { if (vm.recordingNoteId == vm.note?.id) vm.pause() else vm.recordingNoteId?.let(vm::open) }
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
                            else permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.weight(1f))
                }
            }
        }
        vm.passage?.let { passage ->
            CitationThought(passage, if (vm.passageIsFromVoice) null else citationAnchor, vm.passageExpanded, vm::expandPassage, vm::dismissPassage, vm::addPassage)
        }
    }

    vm.message?.let { text ->
        AlertDialog(onDismissRequest = vm::dismissMessage, title = { Text("Notas") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("Entendido") } })
    }
    delete?.let { target ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("¿Eliminar esta nota?") },
            text = { Text("Se eliminarán «${target.title.ifBlank { "Sin título" }}» y su transcripción.") },
            confirmButton = { TextButton(onClick = { vm.delete(target.id); delete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } })
    }
    if (bibleOptions) {
        AlertDialog(onDismissRequest = { if (!vm.importing) bibleOptions = false }, title = { Text("Biblia de Estudio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vm.bible?.title ?: "Importa la Biblia de Estudio en español (.jwpub). Después podrás consultar las citas sin conexión.")
                    if (vm.bible != null) Text("${vm.bible!!.verses} versículos disponibles", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Detectar citas al escuchar", Modifier.weight(1f))
                        Switch(vm.citationsEnabled, vm::toggleCitations)
                    }
                    if (vm.importing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Importando versículos… ${vm.importProgress}%")
                    } else {
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text(if (vm.bible == null) "Importar JWPUB" else "Cambiar Biblia")
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.jw.org/es/biblioteca/biblia/biblia-estudio/")))
                        }) { Text("Descargar desde jw.org") }
                    }
                }
            }, confirmButton = { TextButton(onClick = { bibleOptions = false }) { Text("Listo") } })
    }
}

@Composable
private fun NoteLibrary(notes: List<NoteListItem>, onOpen: (Long) -> Unit, onDelete: (NoteListItem) -> Unit, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = notes.filter { SpanishText.foldForSearch(it.title + " " + it.preview).contains(SpanishText.foldForSearch(query)) }
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar notas") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (notes.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.EditNote, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp)); Text("Tus ideas, en un solo lugar", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp)); Text("Crea una nota para escuchar, seleccionar fragmentos y guardar las citas que se mencionan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { note ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(note.id) }) {
                        Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "Sin título" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (note.preview.isNotBlank()) Text(note.preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(note) }) { Icon(Icons.Filled.DeleteOutline, "Eliminar nota: ${note.title.ifBlank { "Sin título" }}") }
                        }
                    }
                }
                if (visible.isEmpty()) item { Text("No se encontraron notas.") }
            }
        }
    }
}

private enum class VoiceDock(val label: String) { RIGHT("Derecha"), BOTTOM("Debajo de la nota"), TOP("Encima de la nota"), FLOATING("Flotante sobre la nota") }

/** Editing tools span the top. The audio panel docks, resizes or floats independently of capture. */
@Composable
fun NotesWorkspace(
    entries: List<TranscriptEntry>, partial: String, body: TextFieldValue, automatic: Boolean, wide: Boolean,
    onEdit: (TextFieldValue) -> Unit, onAdd: (Set<Long>) -> Unit, onInsert: (String) -> Unit, modifier: Modifier = Modifier,
    ink: InkDocument = InkDocument(), onInkChange: (InkDocument) -> Unit = {},
    canUndoInk: Boolean = false, canRedoInk: Boolean = false, onUndoInk: () -> Unit = {}, onRedoInk: () -> Unit = {},
    listening: Boolean = false, engineState: EngineState = EngineState.IDLE, level: Float = 0f,
    onAuto: (Boolean) -> Unit = {}, onToggleListen: () -> Unit = {}, onReference: (BibleReference) -> Unit = {},
    references: List<BibleReference> = emptyList(), suggestions: List<BibleReferenceSuggestion> = emptyList(), allowBack: Boolean = true,
    onReferenceAt: ((BibleReference, Rect) -> Unit)? = null, onAnchorChanged: (Rect) -> Unit = {},
    onFormat: ((com.eliadca.talks.core.notes.NoteTextStyle) -> com.eliadca.talks.core.notes.NoteTextStyle) -> Unit = {},
    textStyle: com.eliadca.talks.core.notes.NoteTextStyle = com.eliadca.talks.core.notes.NoteTextStyle(),
    onAlign: (Int) -> Unit = {}, onList: (Boolean) -> Unit = {}, canUndoText: Boolean = false, canRedoText: Boolean = false,
    onUndoText: () -> Unit = {}, onRedoText: () -> Unit = {}, onExport: () -> Unit = {},
) {
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var dock by rememberSaveable { mutableStateOf(VoiceDock.RIGHT) }
    var requestedWidth by rememberSaveable { mutableFloatStateOf(420f) }
    var requestedHeight by rememberSaveable { mutableFloatStateOf(380f) }
    var floatingX by rememberSaveable { mutableFloatStateOf(.7f) }
    var floatingY by rememberSaveable { mutableFloatStateOf(.15f) }
    var sizeDialog by remember { mutableStateOf(false) }
    val editorState = rememberNoteEditorState()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    BackHandler(panelOpen && allowBack) { panelOpen = false }
    Column(modifier.fillMaxSize().imePadding()) {
        NoteEditorToolbar(editorState, body, ink, onInkChange, canUndoInk, canRedoInk, onUndoInk, onRedoInk,
            onFormat, textStyle, onAlign, onList, canUndoText, canRedoText, onUndoText, onRedoText)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableWidth = maxWidth.value; val availableHeight = maxHeight.value
            val panelWidth = requestedWidth.coerceIn(minOf(280f, availableWidth), if (wide && dock == VoiceDock.RIGHT) maxOf(280f, availableWidth - 280f) else availableWidth)
            val panelHeight = requestedHeight.coerceIn(minOf(260f, availableHeight),
                if (dock == VoiceDock.TOP || dock == VoiceDock.BOTTOM) maxOf(minOf(260f, availableHeight), availableHeight - 120f) else availableHeight)
            @Composable fun page(pageModifier: Modifier) {
                HandwritingNotePanel(body, onEdit, ink, onInkChange, canUndoInk, canRedoInk, onUndoInk, onRedoInk,
                    pageModifier, onReference, editorState, showToolbar = false, onReferenceAt = onReferenceAt, onAnchorChanged = onAnchorChanged)
            }
            @Composable fun voicePanel(panelModifier: Modifier) {
                Surface(panelModifier.testTag("voice-panel"), shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    BoxWithConstraints {
                        val compact = maxHeight < 380.dp
                        Column {
                            TranscriptPanel(entries, partial, automatic, onAdd, onInsert, Modifier.weight(1f).fillMaxWidth(),
                                onClose = { panelOpen = false }, references = references, suggestions = suggestions, onReference = onReference,
                                onExport = onExport, compact = compact, dock = dock, onDock = { dock = it }, onResize = { sizeDialog = true },
                                dragHandle = Modifier.pointerInput(dock, availableWidth, availableHeight, panelWidth, panelHeight) {
                                    if (dock == VoiceDock.FLOATING) detectDragGestures { change, delta ->
                                        change.consume()
                                        floatingX = (floatingX + delta.x / density.density / maxOf(1f, availableWidth - panelWidth)).coerceIn(0f, 1f)
                                        floatingY = (floatingY + delta.y / density.density / maxOf(1f, availableHeight - panelHeight)).coerceIn(0f, 1f)
                                    }
                                })
                            MicrophoneBar(listening, engineState, automatic, level, onAuto, onToggleListen, compact)
                        }
                        Box(Modifier.align(Alignment.BottomEnd).size(24.dp).pointerInput(availableWidth, availableHeight, dock) {
                            detectDragGestures { change, delta ->
                                change.consume(); requestedWidth = (requestedWidth + delta.x / density.density).coerceIn(minOf(280f, availableWidth), availableWidth)
                                requestedHeight = (requestedHeight + delta.y / density.density).coerceIn(minOf(260f, availableHeight), availableHeight)
                            }
                        }.semantics { contentDescription = "Arrastrar para cambiar tamaño del panel" }) {
                            Icon(Icons.Filled.DragHandle, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            val dockedHorizontal = panelOpen && dock in listOf(VoiceDock.TOP, VoiceDock.BOTTOM)
            if (dockedHorizontal) {
                Column(Modifier.fillMaxSize()) {
                    if (dock == VoiceDock.TOP) voicePanel(Modifier.fillMaxWidth().height(panelHeight.dp))
                    if (dock == VoiceDock.TOP) ResizeHandle(false) { delta -> requestedHeight = (panelHeight + delta).coerceIn(minOf(260f, availableHeight), maxOf(260f, availableHeight - 120)) }
                    page(Modifier.weight(1f).fillMaxWidth())
                    if (dock == VoiceDock.BOTTOM) ResizeHandle(false) { delta -> requestedHeight = (panelHeight - delta).coerceIn(minOf(260f, availableHeight), maxOf(260f, availableHeight - 120)) }
                    if (dock == VoiceDock.BOTTOM) voicePanel(Modifier.fillMaxWidth().height(panelHeight.dp))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    page(Modifier.weight(1f).fillMaxHeight())
                    if (panelOpen && wide && dock == VoiceDock.RIGHT) {
                        ResizeHandle(true) { delta -> requestedWidth = (panelWidth - delta).coerceIn(280f, maxOf(280f, availableWidth - 280)) }
                        voicePanel(Modifier.width(panelWidth.dp).fillMaxHeight())
                    }
                }
                if (panelOpen && (!wide && dock == VoiceDock.RIGHT || dock == VoiceDock.FLOATING)) {
                    val floating = dock == VoiceDock.FLOATING
                    voicePanel(if (floating) Modifier.offset(x = ((availableWidth - panelWidth) * floatingX).dp, y = ((availableHeight - panelHeight) * floatingY).dp)
                        .width(panelWidth.dp).height(panelHeight.dp)
                        else Modifier.align(Alignment.CenterEnd).width(panelWidth.dp).fillMaxHeight())
                }
            }
            if (!panelOpen) FloatingActionButton(onClick = { keyboard?.hide(); panelOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .semantics { stateDescription = if (listening) "Escuchando" else "Micrófono pausado" },
                shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.primary) {
                Box {
                    Icon(painterResource(R.drawable.ic_note_microphone), "Abrir transcripción", Modifier.size(36.dp), tint = Color.Unspecified)
                    if (listening) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(MaterialTheme.colorScheme.tertiary, androidx.compose.foundation.shape.CircleShape))
                }
            }
            if (sizeDialog) AlertDialog(onDismissRequest = { sizeDialog = false }, title = { Text("Tamaño del panel de voz") }, text = {
                Column {
                    if (dock == VoiceDock.RIGHT || dock == VoiceDock.FLOATING) {
                        Text("Ancho: ${panelWidth.toInt()}")
                        Slider(panelWidth, { requestedWidth = it }, valueRange = minOf(280f, availableWidth)..maxOf(minOf(280f, availableWidth), availableWidth - if (wide && dock == VoiceDock.RIGHT) 280f else 0f),
                            modifier = Modifier.semantics { contentDescription = "Ancho del panel de voz" })
                    }
                    if (dock != VoiceDock.RIGHT) {
                        Text("Altura: ${panelHeight.toInt()}")
                        Slider(panelHeight, { requestedHeight = it }, valueRange = minOf(260f, availableHeight)..availableHeight,
                            modifier = Modifier.semantics { contentDescription = "Altura del panel de voz" })
                    }
                    Text("También puedes arrastrar el borde. En modo flotante, mueve el panel desde su título.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = { sizeDialog = false }) { Text("Listo") } })
        }
    }
}

@Composable
private fun ResizeHandle(vertical: Boolean, onResize: (Float) -> Unit) {
    val density = LocalDensity.current
    val resize by rememberUpdatedState(onResize)
    Box((if (vertical) Modifier.width(10.dp).fillMaxHeight() else Modifier.height(10.dp).fillMaxWidth())
        .background(MaterialTheme.colorScheme.surfaceContainerHigh).pointerInput(vertical) {
            detectDragGestures { change, delta -> change.consume(); resize((if (vertical) delta.x else delta.y) / density.density) }
        }.semantics { contentDescription = "Redimensionar panel de voz" }, contentAlignment = Alignment.Center) {
        Box((if (vertical) Modifier.width(3.dp).height(36.dp) else Modifier.height(3.dp).width(36.dp))
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun TranscriptPanel(entries: List<TranscriptEntry>, partial: String, automatic: Boolean, onAdd: (Set<Long>) -> Unit,
    onInsert: (String) -> Unit, modifier: Modifier, onClose: () -> Unit, references: List<BibleReference>,
    suggestions: List<BibleReferenceSuggestion>, onReference: (BibleReference) -> Unit,
    onExport: () -> Unit, compact: Boolean, dock: VoiceDock, onDock: (VoiceDock) -> Unit, onResize: () -> Unit, dragHandle: Modifier) {
    val context = LocalContext.current
    var placementMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val list = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) {
        if (dragging) following = false
        else if (!list.canScrollForward) following = true
    }
    LaunchedEffect(entries.size, partial) {
        val last = entries.size - 1 + if (partial.isNotBlank()) 1 else 0
        if (following && last >= 0) list.animateScrollToItem(last)
    }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).then(dragHandle)) {
                    Text("Transcripción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { Clipboard.copy(context, "Transcripción", entries.joinToString("\n\n") { it.text }) }, enabled = entries.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Copiar transcripción completa", Modifier.size(20.dp)) }
                IconButton(onClick = onExport, enabled = entries.isNotEmpty() || partial.isNotBlank(), modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Download, "Exportar transcripción a TXT", Modifier.size(20.dp)) }
                Box {
                    IconButton(onClick = { placementMenu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.OpenWith, "Ubicación y tamaño del panel", Modifier.size(20.dp)) }
                    DropdownMenu(placementMenu, { placementMenu = false }) {
                        VoiceDock.entries.forEach { position -> DropdownMenuItem(text = { Text(position.label) },
                            onClick = { onDock(position); placementMenu = false }, leadingIcon = { if (dock == position) Icon(Icons.Filled.Check, null) }) }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Cambiar tamaño") }, onClick = { onResize(); placementMenu = false })
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ExpandMore, "Cerrar transcripción", Modifier.size(22.dp)) }
            }
            if (!compact) Text(if (automatic) "Las frases confirmadas pasan a tu nota" else "Selecciona palabras o varios fragmentos",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (references.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                references.takeLast(4).forEach { ref -> AssistChip(onClick = { onReference(ref) }, label = { Text(ref.label) }) }
            }
            if (suggestions.isNotEmpty()) {
                Text("¿Te referías a…?", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.takeLast(3).forEach { suggestion -> AssistChip(onClick = { onReference(suggestion.reference) }, label = { Text(suggestion.reference.label) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAdd(if (selected.isNotEmpty()) selected else entries.filterNot { it.added }.map { it.id }.toSet()); selected = emptySet() },
                    enabled = entries.any { !it.added }, modifier = if (compact) Modifier.height(32.dp) else Modifier) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(if (selected.isEmpty()) "Añadir todo" else "Añadir selección (${selected.size})")
                }
                if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet() }) { Text("Cancelar") }
                if (!following) {
                    val scope = rememberCoroutineScope()
                    IconButton(onClick = {
                        following = true
                        scope.launch { list.animateScrollToItem((entries.size - 1 + if (partial.isNotBlank()) 1 else 0).coerceAtLeast(0)) }
                    }) { Icon(Icons.Filled.VerticalAlignBottom, "Ver lo último") }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f).testTag("transcript"), state = list, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty() && partial.isEmpty()) item {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Filled.MicNone, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp)); Text("Lo que se diga aparecerá aquí.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp)); Text("Toca Escuchar para empezar. Podrás copiar frases completas o seleccionar solo las palabras que te interesan.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    TranscriptCard(entry, entry.id in selected, { selected = if (it) selected + entry.id else selected - entry.id },
                        onAdd = { onAdd(setOf(entry.id)) }, onInsert = onInsert)
                }
                if (partial.isNotBlank()) item {
                    Text(partial, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Reconociendo…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(entry: TranscriptEntry, selected: Boolean, onSelected: (Boolean) -> Unit, onAdd: () -> Unit, onInsert: (String) -> Unit) {
    val context = LocalContext.current
    var value by remember(entry.id, entry.text) { mutableStateOf(TextFieldValue(entry.text)) }
    var originalVisible by remember(entry.id) { mutableStateOf(false) }
    val fragment = if (value.selection.collapsed) entry.text else entry.text.substring(value.selection.min, value.selection.max)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(selected, onSelected, enabled = !entry.added, modifier = Modifier.size(38.dp))
                BasicTextField(value, { value = it }, readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).padding(8.dp).testTag("fragment-${entry.id}"))
            }
            if (entry.originalText != null) {
                TextButton(onClick = { originalVisible = !originalVisible }) { Text(if (originalVisible) "Ocultar original" else "Nombre bíblico corregido · ver original") }
                if (originalVisible) SelectionContainer { Text(entry.originalText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (!entry.confirmed) Text("Sin confirmar", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { Clipboard.copy(context, "Transcripción", fragment) }) { Text(if (value.selection.collapsed) "Copiar" else "Copiar selección") }
                TextButton(onClick = { if (value.selection.collapsed) onAdd() else onInsert(fragment) }, enabled = !entry.added || !value.selection.collapsed) {
                    Text(if (!value.selection.collapsed) "Añadir selección" else if (entry.added) "Añadido" else "Añadir")
                }
            }
        }
    }
}

@Composable
private fun MicrophoneBar(listening: Boolean, state: EngineState, automatic: Boolean, level: Float, onAuto: (Boolean) -> Unit, onToggle: () -> Unit, compact: Boolean = false) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggle) {
                Icon(if (listening) Icons.Filled.Pause else Icons.Filled.Mic, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp)); Text(if (listening) "Pausar" else "Escuchar")
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(automatic, onAuto, modifier = Modifier.height(32.dp).semantics { contentDescription = "Añadir automáticamente" })
                Text("Automático", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!compact) Text(if (!listening) "Micrófono pausado" else when (state) {
            EngineState.STARTING -> "Iniciando…"
            EngineState.RESTARTING -> "Reconectando…"
            else -> "Escuchando"
        }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        if (listening && !compact) Text("Sigue escuchando al minimizar. Pausar detiene el micrófono.", style = MaterialTheme.typography.labelSmall)
        if (listening) LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

@Composable
fun FloatingPassage(passage: BiblePassage, expanded: Boolean, onExpand: (Boolean) -> Unit, onDismiss: () -> Unit, onAdd: () -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(modifier.testTag("floating-citation"), shape = RoundedCornerShape(22.dp), shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MenuBook, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(passage.reference.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onExpand(!expanded) }, Modifier.size(40.dp)) { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    if (expanded) "Contraer cita" else "Expandir cita") }
                IconButton(onClick = onDismiss, Modifier.size(40.dp)) { Icon(Icons.Filled.Close, "Cerrar cita") }
            }
            AnimatedVisibility(expanded) {
                Column {
                    SelectionContainer { Text(passage.text, Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge) }
                    Text(passage.edition, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { Clipboard.copy(context, passage.reference.label, passage.forNote) }) { Text("Copiar cita") }
                        FilledTonalButton(onClick = onAdd) { Text("Añadir a nota") }
                    }
                }
            }
        }
    }
}
