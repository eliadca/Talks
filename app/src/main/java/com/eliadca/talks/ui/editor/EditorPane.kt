package com.eliadca.talks.ui.editor

import androidx.compose.foundation.layout.width
import android.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import com.eliadca.talks.ui.components.TipIconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eliadca.talks.R
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.outline
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.data.db.VersionItem
import com.eliadca.talks.editor.EditorStyle
import com.eliadca.talks.editor.RichEditText
import com.eliadca.talks.ui.home.SpeechActions
import com.eliadca.talks.ui.home.formatDuration
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Code

/** The open speech: title, formatting bar, page and status line. */
@Composable
fun EditorPane(
    vm: EditorViewModel,
    settings: AppSettings,
    item: SpeechListItem?,
    folders: List<FolderEntity>,
    versions: List<VersionItem>,
    paceWpm: Int,
    actions: SpeechActions,
    onBack: (() -> Unit)?,
    /** Whether the list is folded away for writing; null where the list and the editor take turns. */
    focusMode: Boolean?,
    onToggleFocus: () -> Unit,
    onStartTalks: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val speech = vm.current
    if (speech == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Abriendo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val c = vm.controller
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNow() }
    val words = vm.stats.words
    val seconds = if (paceWpm > 0) words * 60 / paceWpm else 0
    val target = item?.targetMinutes ?: 0

    var showMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    val folder = item?.folderId?.let { id -> folders.firstOrNull { it.id == id } }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        // --- top bar ---
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                onBack != null -> TipIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a la lista", onClick = onBack)
                focusMode != null -> TipIconButton(
                    if (focusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    if (focusMode) "Mostrar la lista de discursos" else "Escribir a pantalla completa",
                    onClick = onToggleFocus,
                )
            }
            // Where the speech lives.
            Row(
                Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Folder, null,
                    tint = folder?.let { androidx.compose.ui.graphics.Color(it.color) } ?: MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    folder?.name ?: "Sin carpeta",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item?.pinned == true) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.PushPin, "Fijado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
            TipIconButton(Icons.AutoMirrored.Filled.Undo, "Deshacer (Ctrl+Z)", enabled = c.canUndo) { c.undo() }
            TipIconButton(Icons.AutoMirrored.Filled.Redo, "Rehacer (Ctrl+Y)", enabled = c.canRedo) { c.redo() }
            BarSeparator()
            TipIconButton(
                Icons.Filled.Code,
                if (c.markdown != null) "Ver con formato" else "Ver y editar el Markdown",
                active = c.markdown != null,
            ) { if (c.markdown == null) c.showMarkdown() else c.showFormatted() }
            TipIconButton(Icons.Filled.Search, "Buscar y reemplazar (Ctrl+F)", enabled = c.markdown == null, active = c.findOpen) { c.findOpen = !c.findOpen }
            TipIconButton(Icons.AutoMirrored.Filled.ViewList, "Esquema por títulos") { dialog = EditorDialog.Outline }
            TipIconButton(Icons.Filled.History, "Versiones anteriores") { dialog = EditorDialog.History }
            Box {
                TipIconButton(Icons.Filled.MoreVert, "Más opciones") { showMenu = true }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item?.pinned == true) "Quitar de fijados" else "Fijar arriba") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                        onClick = { showMenu = false; actions.togglePin() },
                    )
                    DropdownMenuItem(
                        text = { Text("Tiempo objetivo…") },
                        leadingIcon = { Icon(Icons.Filled.Timer, null) },
                        onClick = { showMenu = false; dialog = EditorDialog.Target },
                    )
                    DropdownMenuItem(
                        text = { Text("Mover a carpeta…") },
                        leadingIcon = { Icon(Icons.Filled.Folder, null) },
                        onClick = { showMenu = false; dialog = EditorDialog.Move },
                    )
                    DropdownMenuItem(
                        text = { Text("Etiqueta de color…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                        onClick = { showMenu = false; dialog = EditorDialog.Label },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.duplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar como Markdown") },
                        leadingIcon = { Icon(Icons.Filled.CopyAll, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.copyMarkdown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir como Markdown") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.shareMarkdown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir como texto") },
                        leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.shareText() },
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar a PDF") },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.exportPdf() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Mover a la papelera") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { showMenu = false; actions.moveToTrash() },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { vm.flushNow(); onStartTalks() },
                modifier = Modifier.height(46.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Icon(Icons.Filled.RecordVoiceOver, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Talks", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        AnimatedVisibility(c.findOpen) {
            FindBar(c, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            if (c.markdown != null) MarkdownHint(onDone = c::showFormatted) else FormatToolbar(c, Modifier.widthIn(max = 1100.dp))
        }

        // --- the page ---
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 880.dp).fillMaxSize().padding(horizontal = 24.dp)) {
                TitleField(vm.title, vm::onTitleChange, onNext = { c.focus(showKeyboard = true) })
                Spacer(Modifier.height(4.dp))
                if (c.markdown != null) {
                    MarkdownEditor(c, settings, Modifier.weight(1f).fillMaxWidth())
                } else {
                    PageEditor(speech.id, speech.doc, c, settings, dark, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }

        // --- status line ---
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusItem(Icons.Filled.Notes, "$words palabras")
            StatusItem(Icons.Filled.Timer, "≈ ${formatDuration(seconds)} a $paceWpm ppm")
            if (target > 0) {
                val diff = seconds - target * 60
                val label = when {
                    kotlin.math.abs(diff) < 20 -> "en el objetivo de $target min"
                    diff > 0 -> "${formatDuration(diff)} de más sobre $target min"
                    else -> "${formatDuration(-diff)} de menos sobre $target min"
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (diff > 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusItem(if (vm.saving) Icons.Filled.Sync else Icons.Filled.CloudDone, if (vm.saving) "Guardando…" else "Guardado")
        }
    }

    when (val d = dialog) {
        null -> {}
        EditorDialog.Outline -> OutlineDialog(
            doc = c.snapshot() ?: speech.doc,
            onPick = { offset -> c.goTo(offset); dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.History -> HistoryDialog(
            versions = versions,
            onSave = { actions.saveVersion(it) },
            onRestore = { vm.flushNow(); actions.restoreVersion(it); dialog = null },
            onDelete = actions.deleteVersion,
            onDismiss = { dialog = null },
        )
        EditorDialog.Target -> TargetDialog(
            current = item?.targetMinutes ?: 0,
            suggestion = (seconds / 60.0).let { kotlin.math.ceil(it).toInt() },
            onSet = { actions.setTargetMinutes(it); dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Move -> MoveToFolderDialog(
            folders = folders,
            current = item?.folderId,
            onPick = { actions.moveToFolder(it); dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Label -> LabelDialog(
            current = item?.color ?: 0,
            onPick = { actions.setLabel(it); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun StatusItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarSeparator() {
    VerticalDivider(
        Modifier
            .padding(horizontal = 6.dp)
            .height(24.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private sealed interface EditorDialog {
    data object Outline : EditorDialog
    data object History : EditorDialog
    data object Target : EditorDialog
    data object Move : EditorDialog
    data object Label : EditorDialog
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace('\n', ' ')) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                fontSize = 30.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { onNext() }),
            maxLines = 3,
        )
        if (value.isEmpty()) {
            Text(
                "Título del discurso",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Android text editor wrapped for Compose, themed to match the app. */
@Composable
private fun PageEditor(
    speechId: Long,
    doc: RichDoc,
    controller: com.eliadca.talks.editor.EditorController,
    settings: AppSettings,
    dark: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toArgb()
    val hint = colors.onSurface.copy(alpha = 0.4f).toArgb()
    val selection = colors.primary.copy(alpha = 0.3f).toArgb()
    val accent = colors.primary.toArgb()
    val muted = colors.onSurface.copy(alpha = 0.5f).toArgb()

    androidx.compose.runtime.key(speechId) {
        AndroidView(
            // A safety net: nothing the page draws may cover the bars above it.
            modifier = modifier.clipToBounds(),
            factory = { ctx ->
                val themed = ContextThemeWrapper(ctx, if (dark) R.style.EditorTheme_Dark else R.style.EditorTheme_Light)
                val style = EditorStyle(ctx.resources.displayMetrics.density, accent, muted, noteColor = noteTint(dark))
                RichEditText(themed, style).also { view ->
                    view.tag = style
                    view.applyAppearance(textColor, hint, selection, settings.editorFontSp.toFloat(), settings.editorSerif)
                    view.loadDocument(controller.takePendingDoc() ?: doc)
                    controller.attach(view)
                }
            },
            update = { view ->
                (view.tag as? EditorStyle)?.let {
                    it.accent = accent
                    it.muted = muted
                    it.noteColor = noteTint(dark)
                }
                view.applyAppearance(textColor, hint, selection, settings.editorFontSp.toFloat(), settings.editorSerif)
            },
            onRelease = { view -> controller.detach(view) },
        )
        LaunchedEffect(Unit) {
            if (doc.text.isEmpty()) controller.focus(showKeyboard = true)
        }
    }
}

/** What can be written in Markdown, and the way back to the formatted page. */
@Composable
private fun MarkdownHint(onDone: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 1100.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Code, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Markdown: **negrita**  *cursiva*  ==resaltado==  # título  - lista  [nota]",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onDone) { Text("Ver con formato", fontWeight = FontWeight.Bold) }
    }
}

/** The speech as plain Markdown text, to read or edit as written. */
@Composable
private fun MarkdownEditor(controller: com.eliadca.talks.editor.EditorController, settings: AppSettings, modifier: Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            android.widget.EditText(ctx).apply {
                background = null
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                typeface = android.graphics.Typeface.MONOSPACE
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setHorizontallyScrolling(false)
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = false
                setLineSpacing(0f, 1.25f)
                setText(controller.markdown ?: "")
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { controller.editMarkdown(s?.toString() ?: "") }
                })
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.textSize = (settings.editorFontSp - 2).coerceAtLeast(12).toFloat()
        },
    )
}

/** Notes for the speaker look like a soft sticky note. */
private fun noteTint(dark: Boolean): Int = if (dark) 0x40FFC857 else 0x38FFB300
