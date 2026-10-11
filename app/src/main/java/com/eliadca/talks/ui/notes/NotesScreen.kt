package com.eliadca.talks.ui.notes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.data.db.NoteListItem
import com.eliadca.talks.export.Clipboard
import com.eliadca.talks.speech.EngineState

@Composable
fun NotesScreen(vm: NotesViewModel, onMenu: () -> Unit, modifier: Modifier = Modifier, allowBack: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notes by vm.items.collectAsState()
    val saveError by vm.saveError.collectAsState()
    var bibleOptions by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<NoteListItem?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start()
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
                if (vm.detected.isNotEmpty() && vm.citationsEnabled) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 48.dp)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.detected.forEach { ref -> AssistChip(onClick = { if (vm.bible == null) bibleOptions = true else vm.showReference(ref) },
                            label = { Text(ref.label) }, leadingIcon = { Icon(Icons.Filled.MenuBook, null, Modifier.size(16.dp)) }) }
                    }
                }
                NotesWorkspace(entries = vm.entries, partial = vm.partial, body = vm.body,
                    automatic = vm.note!!.automatic, wide = wide, onEdit = vm::edit, onAdd = vm::addEntries, onInsert = vm::insert,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                MicrophoneBar(vm.listening, vm.engineState, vm.note!!.automatic, vm.level, vm::automatic,
                    onToggle = {
                        if (vm.listening) vm.pause()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.start()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    })
            }
        }
        vm.passage?.let { passage ->
            FloatingPassage(passage, vm.passageExpanded, vm::expandPassage, vm::dismissPassage, vm::addPassage,
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, bottom = 98.dp)
                    .widthIn(max = 420.dp).fillMaxWidth(if (wide) .5f else 1f))
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

/** Responsive two-panel workspace. On a phone, switching panels keeps the microphone running. */
@Composable
fun NotesWorkspace(
    entries: List<TranscriptEntry>, partial: String, body: TextFieldValue, automatic: Boolean, wide: Boolean,
    onEdit: (TextFieldValue) -> Unit, onAdd: (Set<Long>) -> Unit, onInsert: (String) -> Unit, modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (wide) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TranscriptPanel(entries, partial, automatic, onAdd, onInsert, Modifier.weight(1f).fillMaxHeight())
            NotePanel(body, onEdit, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            TabRow(tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Transcripción") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mi nota") })
            }
            if (tab == 0) TranscriptPanel(entries, partial, automatic, onAdd, onInsert, Modifier.weight(1f).fillMaxWidth())
            else NotePanel(body, onEdit, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun TranscriptPanel(entries: List<TranscriptEntry>, partial: String, automatic: Boolean, onAdd: (Set<Long>) -> Unit,
    onInsert: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val list = rememberLazyListState()
    val following = remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(entries.size, partial) {
        if (following.value && (entries.isNotEmpty() || partial.isNotEmpty())) list.animateScrollToItem(entries.size.coerceAtMost(list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
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
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAdd(if (selected.isNotEmpty()) selected else entries.filterNot { it.added }.map { it.id }.toSet()); selected = emptySet() },
                    enabled = entries.any { !it.added }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(if (selected.isEmpty()) "Añadir todo" else "Añadir selección (${selected.size})")
                }
                if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet() }) { Text("Cancelar") }
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
    val fragment = if (value.selection.collapsed) entry.text else entry.text.substring(value.selection.min, value.selection.max)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(selected, onSelected, enabled = !entry.added, modifier = Modifier.size(38.dp))
                BasicTextField(value, { value = it }, readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).padding(8.dp).testTag("fragment-${entry.id}"))
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
private fun NotePanel(body: TextFieldValue, onEdit: (TextFieldValue) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mi nota", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { Clipboard.copy(context, "Nota", body.text) }, enabled = body.text.isNotBlank()) { Icon(Icons.Filled.ContentCopy, "Copiar nota completa") }
            }
            HorizontalDivider()
            BasicTextField(body, onEdit, modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                .verticalScroll(rememberScrollState()).testTag("note-body"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { field ->
                    Box { if (body.text.isEmpty()) Text("Escribe tus ideas o añade fragmentos de la transcripción…", color = MaterialTheme.colorScheme.onSurfaceVariant); field() }
                })
        }
    }
}

@Composable
private fun MicrophoneBar(listening: Boolean, state: EngineState, automatic: Boolean, level: Float, onAuto: (Boolean) -> Unit, onToggle: () -> Unit) {
    Surface(shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggle) {
                Icon(if (listening) Icons.Filled.Pause else Icons.Filled.Mic, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp)); Text(if (listening) "Pausar" else "Escuchar")
            }
            Column(Modifier.weight(1f)) {
                Text(if (!listening) "Micrófono pausado" else when (state) {
                    EngineState.STARTING -> "Iniciando…"
                    EngineState.RESTARTING -> "Reconectando…"
                    else -> "Escuchando"
                }, style = MaterialTheme.typography.labelMedium)
                if (listening) LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(automatic, onAuto, modifier = Modifier.height(32.dp).semantics { contentDescription = "Añadir automáticamente" })
                Text("Automático", style = MaterialTheme.typography.labelSmall)
            }
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
