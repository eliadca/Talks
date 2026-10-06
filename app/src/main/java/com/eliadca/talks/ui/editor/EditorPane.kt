package com.eliadca.talks.ui.editor

import android.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    var showMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        // --- top bar ---
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.findOpen = !c.findOpen }) { Icon(Icons.Filled.Search, contentDescription = "Buscar en el discurso") }
            IconButton(onClick = { dialog = EditorDialog.Outline }) { Icon(Icons.Filled.ViewList, contentDescription = "Esquema") }
            IconButton(onClick = { dialog = EditorDialog.History }) { Icon(Icons.Filled.History, contentDescription = "Versiones anteriores") }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
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
                        leadingIcon = { Icon(Icons.Filled.Label, null) },
                        onClick = { showMenu = false; dialog = EditorDialog.Label },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.duplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir como texto") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.shareText() },
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar a PDF") },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                        onClick = { showMenu = false; vm.flushNow(); actions.exportPdf() },
                    )
                    DropdownMenuItem(
                        text = { Text("Mover a la papelera") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { showMenu = false; actions.moveToTrash() },
                    )
                }
            }
            Button(onClick = { vm.flushNow(); onStartTalks() }) {
                Text("Talks", fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(c.findOpen) {
            FindBar(c, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            FormatToolbar(c, Modifier.widthIn(max = 1100.dp))
        }

        // --- the page ---
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 880.dp).fillMaxSize().padding(horizontal = 24.dp)) {
                TitleField(vm.title, vm::onTitleChange, onNext = { c.focus(showKeyboard = true) })
                Spacer(Modifier.height(4.dp))
                PageEditor(speech.id, speech.doc, c, settings, dark, Modifier.weight(1f).fillMaxWidth())
            }
        }

        // --- status line ---
        val words = vm.stats.words
        val seconds = if (paceWpm > 0) words * 60 / paceWpm else 0
        val target = item?.targetMinutes ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("$words palabras", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "≈ ${formatDuration(seconds)} a $paceWpm ppm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Text(
                if (vm.saving) "Guardando…" else "Guardado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            modifier = modifier,
            factory = { ctx ->
                val themed = ContextThemeWrapper(ctx, if (dark) R.style.EditorTheme_Dark else R.style.EditorTheme_Light)
                val style = EditorStyle(ctx.resources.displayMetrics.density, accent, muted)
                RichEditText(themed, style).also { view ->
                    view.tag = style
                    view.applyAppearance(textColor, hint, selection, settings.editorFontSp.toFloat(), settings.editorSerif)
                    view.loadDocument(doc)
                    controller.attach(view)
                }
            },
            update = { view ->
                (view.tag as? EditorStyle)?.let {
                    it.accent = accent
                    it.muted = muted
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
