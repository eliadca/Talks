package com.eliadca.talks.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.outline
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.VersionItem
import com.eliadca.talks.ui.components.ColorSwatch
import com.eliadca.talks.ui.theme.LabelColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OutlineDialog(doc: RichDoc, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val entries = remember(doc) { doc.outline() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Esquema del discurso") },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "Todavía no hay títulos. Usa el botón de títulos de la barra de formato para marcar las partes de tu discurso y aparecerán aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(entries) { e ->
                        Text(
                            e.title,
                            style = if (e.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(e.offset) }
                                .padding(start = ((e.level - 1) * 18).dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun HistoryDialog(
    versions: List<VersionItem>,
    onSave: (String) -> Unit,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf<VersionItem?>(null) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Versiones anteriores") },
        text = {
            Column {
                Text(
                    "Talks guarda copias automáticas mientras escribes. Puedes guardar una versión con nombre antes de hacer cambios grandes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Nombre de la versión") },
                    )
                    TextButton(onClick = { onSave(label); label = "" }) { Text("Guardar") }
                }
                HorizontalDivider()
                if (versions.isEmpty()) {
                    Text("Aún no hay versiones.", modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(versions, key = { it.id }) { v ->
                            ListItem(
                                headlineContent = { Text(v.label ?: format.format(Date(v.createdAt))) },
                                supportingContent = {
                                    Text(
                                        (if (v.label != null) format.format(Date(v.createdAt)) + " · " else "") + "${v.wordCount} palabras",
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { confirmRestore = v }) { Icon(Icons.Filled.Restore, contentDescription = "Restaurar") }
                                        IconButton(onClick = { onDelete(v.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar versión") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
    confirmRestore?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("¿Restaurar esta versión?") },
            text = { Text("El texto actual se guardará antes como «Antes de restaurar», así que no perderás nada.") },
            confirmButton = { TextButton(onClick = { onRestore(v.id); confirmRestore = null }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun TargetDialog(current: Int, suggestion: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiempo objetivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cuánto tiempo tienes para este discurso. Talks te avisa si tu texto se pasa o se queda corto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text("Minutos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(5, 10, 15, 20, 30)) {
                        AssistChip(onClick = { text = m.toString() }, label = { Text("$m") })
                    }
                }
                if (suggestion > 0) {
                    TextButton(onClick = { text = suggestion.toString() }) { Text("Usar lo que dura ahora: $suggestion min") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(text.toIntOrNull() ?: 0) }) { Text("Guardar") } },
        dismissButton = {
            TextButton(onClick = { onSet(0) }) { Text("Quitar objetivo") }
        },
    )
}

@Composable
fun MoveToFolderDialog(folders: List<FolderEntity>, current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    var destination by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a carpeta") },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text("Crea una carpeta en «Carpetas» para organizar tus discursos.", modifier = Modifier.padding(bottom = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        FolderChoice("Sin carpeta", null, destination == null) { destination = null }
                    }
                    items(folders, key = { it.id }) { f ->
                        FolderChoice(f.name, f.color, destination == f.id) { destination = f.id }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(destination) },
                enabled = destination != current && (destination == null || folders.any { it.id == destination }),
            ) { Text("Mover") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FolderChoice(name: String, color: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (color != null) {
            ColorSwatch(color, selected = false, description = name, size = 18, onClick = onClick)
            Spacer(Modifier.size(10.dp))
        }
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LabelDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiqueta de color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in LabelColors.take(4)) ColorSwatch(c, c == current, "Color", size = 40) { onPick(c) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in LabelColors.drop(4)) ColorSwatch(c, c == current, "Color", size = 40) { onPick(c) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(0) }) { Text("Sin etiqueta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
