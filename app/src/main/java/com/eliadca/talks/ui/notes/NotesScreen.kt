package com.eliadca.talks.ui.notes

import android.Manifest
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
    var bibleOptions by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<NoteListItem?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.permissionDenied()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBible) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.pause() }
    }
    BackHandler(enabled = vm.note != null && allowBack) { vm.backToList() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = if (vm.note == null) onMenu else vm::backToList) {
                    Icon(if (vm.note == null) Icons.Filled.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                        if (vm.note == null) "Abrir el menú" else "Volver a notas")
                }
                Column(Modifier.weight(1f)) {
                    Text("Notas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (vm.note == null) "Escucha, elige y guarda tus ideas" else if (saveError == null) "Guardado automático" else "Pendiente de guardar",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { bibleOptions = true }) { Icon(Icons.Filled.MenuBook, "Biblia de Estudio") }
                if (vm.note == null) {
                    FilledTonalButton(onClick = vm::create, enabled = !vm.loading) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva nota")
                    }
                }
            }
            if (saveError != null) Text(saveError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            if (vm.note == null) {
                NoteLibrary(notes, vm::open, { delete = it }, Modifier.weight(1f))
            } else {
                OutlinedTextField(value = vm.note!!.title, onValueChange = vm::title, label = { Text("Título de la nota") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                key(vm.note!!.id) {
                    NotesWorkspace(entries = vm.entries, partial = vm.partial, body = vm.body,
                        automatic = vm.note!!.automatic, wide = wide, onEdit = vm::edit, onAdd = vm::addEntries, onInsert = vm::insert,
                        ink = vm.ink, onInkChange = vm::editInk, canUndoInk = vm.canUndoInk, canRedoInk = vm.canRedoInk,
                        onUndoInk = vm::undoInk, onRedoInk = vm::redoInk,
                        listening = vm.listening, engineState = vm.engineState, level = vm.level, onAuto = vm::automatic,
                        onReference = { if (vm.bible == null) bibleOptions = true else vm.showReference(it) },
                        references = vm.detected, suggestions = vm.referenceSuggestions,
                        allowBack = allowBack,
                        onToggleListen = {
                            if (vm.listening) vm.pause()
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.start()
                            else permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                }
            }
        }
        vm.passage?.let { passage ->
            FloatingPassage(passage, vm.passageExpanded, vm::expandPassage, vm::dismissPassage, vm::addPassage,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                    .width(minOf(420.dp, maxWidth - 32.dp)))
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

/** The page stays central; voice lives in a right-hand panel opened explicitly by its microphone. */
@Composable
fun NotesWorkspace(
    entries: List<TranscriptEntry>, partial: String, body: TextFieldValue, automatic: Boolean, wide: Boolean,
    onEdit: (TextFieldValue) -> Unit, onAdd: (Set<Long>) -> Unit, onInsert: (String) -> Unit, modifier: Modifier = Modifier,
    ink: InkDocument = InkDocument(), onInkChange: (InkDocument) -> Unit = {},
    canUndoInk: Boolean = false, canRedoInk: Boolean = false, onUndoInk: () -> Unit = {}, onRedoInk: () -> Unit = {},
    listening: Boolean = false, engineState: EngineState = EngineState.IDLE, level: Float = 0f,
    onAuto: (Boolean) -> Unit = {}, onToggleListen: () -> Unit = {}, onReference: (BibleReference) -> Unit = {},
    references: List<BibleReference> = emptyList(), suggestions: List<BibleReferenceSuggestion> = emptyList(),
    allowBack: Boolean = true,
) {
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(panelOpen && allowBack) { panelOpen = false }
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val panelWidth = if (wide) minOf(420.dp, maxWidth * .4f) else minOf(420.dp, maxWidth - 20.dp)
        @Composable fun voicePanel(panelModifier: Modifier) {
            Surface(panelModifier.testTag("voice-panel"), shape = RoundedCornerShape(22.dp), shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    TranscriptPanel(entries, partial, automatic, onAdd, onInsert, Modifier.weight(1f).fillMaxWidth(),
                        onClose = { panelOpen = false }, references = references, suggestions = suggestions, onReference = onReference)
                    MicrophoneBar(listening, engineState, automatic, level, onAuto, onToggleListen)
                }
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HandwritingNotePanel(body, onEdit, ink, onInkChange, canUndoInk, canRedoInk, onUndoInk, onRedoInk,
                Modifier.weight(1f).fillMaxHeight().padding(bottom = if (panelOpen) 0.dp else 76.dp), onReference = onReference)
            if (wide && panelOpen) voicePanel(Modifier.width(panelWidth).fillMaxHeight())
        }
        if (!wide) {
            AnimatedVisibility(panelOpen, modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
                voicePanel(Modifier.width(panelWidth).fillMaxHeight())
            }
        }
        if (!panelOpen) {
            FloatingActionButton(onClick = { keyboard?.hide(); panelOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .semantics { stateDescription = if (listening) "Escuchando" else "Micrófono pausado" },
                shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.primary) {
                Box {
                    Icon(painterResource(R.drawable.ic_note_microphone), "Abrir transcripción", Modifier.size(36.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
                    if (listening) Box(Modifier.align(Alignment.TopEnd).size(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
    }
}

@Composable
private fun TranscriptPanel(entries: List<TranscriptEntry>, partial: String, automatic: Boolean, onAdd: (Set<Long>) -> Unit,
    onInsert: (String) -> Unit, modifier: Modifier, onClose: () -> Unit, references: List<BibleReference>,
    suggestions: List<BibleReferenceSuggestion>, onReference: (BibleReference) -> Unit) {
    val context = LocalContext.current
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
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Transcripción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (automatic) "Las frases confirmadas pasan a tu nota" else "Selecciona palabras o varios fragmentos",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Clipboard.copy(context, "Transcripción", entries.joinToString("\n\n") { it.text }) }, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Filled.ContentCopy, "Copiar transcripción completa")
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cerrar transcripción") }
            }
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
                    enabled = entries.any { !it.added }) {
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
private fun MicrophoneBar(listening: Boolean, state: EngineState, automatic: Boolean, level: Float, onAuto: (Boolean) -> Unit, onToggle: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
        Text(if (!listening) "Micrófono pausado" else when (state) {
            EngineState.STARTING -> "Iniciando…"
            EngineState.RESTARTING -> "Reconectando…"
            else -> "Escuchando"
        }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
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
