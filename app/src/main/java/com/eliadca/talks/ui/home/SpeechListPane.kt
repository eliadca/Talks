package com.eliadca.talks.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import com.eliadca.talks.ui.components.TipIconButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.ui.components.EmptyState

/** The list of speeches with search, sorting and per-speech actions. */
@Composable
fun SpeechListPane(
    items: List<SpeechListItem>,
    folders: List<FolderEntity>,
    selectedId: Long?,
    filter: LibraryFilter,
    query: String,
    sort: SortKey,
    paceWpm: Int,
    showMenuButton: Boolean,
    onMenu: () -> Unit,
    onQuery: (String) -> Unit,
    onSort: (SortKey) -> Unit,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onTogglePin: (SpeechListItem) -> Unit,
    onDuplicate: (Long) -> Unit,
    onTrash: (SpeechListItem) -> Unit,
    onRestore: (Long) -> Unit,
    onDeleteForever: (SpeechListItem) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTrash = filter == LibraryFilter.Trash
    val folderById = remember(folders) { folders.associateBy { it.id } }
    var sortMenu by remember { mutableStateOf(false) }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenuButton) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Abrir el menú") }
            }
            Column(Modifier.weight(1f).padding(start = if (showMenuButton) 0.dp else 10.dp)) {
                Text(
                    titleFor(filter, folderById),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (items.size) {
                        0 -> "Ninguno"
                        1 -> "1 discurso"
                        else -> "${items.size} discursos"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isTrash) {
                Box {
                    TipIconButton(Icons.AutoMirrored.Filled.Sort, "Ordenar") { sortMenu = true }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        for (s in SortKey.entries) {
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                trailingIcon = { if (s == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                                onClick = { onSort(s); sortMenu = false },
                            )
                        }
                    }
                }
                FilledTonalIconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Nuevo discurso") }
            } else if (items.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) { Text("Vaciar") }
            }
        }

        if (!isTrash) {
            TextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                singleLine = true,
                placeholder = { Text("Buscar en todos los discursos") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        } else {
            Text(
                "Los discursos se eliminan definitivamente a los 30 días.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    isTrash -> EmptyState(Icons.Filled.Delete, "La papelera está vacía", "Los discursos que elimines aparecerán aquí durante 30 días.")
                    query.isNotBlank() -> EmptyState(Icons.Filled.Search, "Sin resultados", "Ningún discurso contiene «$query». La búsqueda ignora mayúsculas y acentos.")
                    else -> EmptyState(
                        Icons.Filled.Description, "Aún no hay discursos aquí", "Crea tu primer discurso y practícalo con el modo Talks.",
                        action = { TextButton(onClick = onNew) { Text("Nuevo discurso") } },
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    SpeechCard(
                        item = item,
                        folder = item.folderId?.let { folderById[it] },
                        selected = item.id == selectedId,
                        paceWpm = paceWpm,
                        isTrash = isTrash,
                        onClick = { if (!isTrash) onSelect(item.id) },
                        onTogglePin = { onTogglePin(item) },
                        onDuplicate = { onDuplicate(item.id) },
                        onTrash = { onTrash(item) },
                        onRestore = { onRestore(item.id) },
                        onDeleteForever = { onDeleteForever(item) },
                    )
                }
            }
        }
    }
}

/** "Ahora mismo" for the last minute, then the system's own relative time ("hace 5 minutos"). */
private fun relativeTime(stamp: Long): String {
    val now = System.currentTimeMillis()
    return if (now - stamp < DateUtils.MINUTE_IN_MILLIS) "Ahora mismo"
    else DateUtils.getRelativeTimeSpanString(stamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** A small fact under a speech: its length in time or in words. */
@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun titleFor(filter: LibraryFilter, folders: Map<Long, FolderEntity>): String = when (filter) {
    LibraryFilter.All -> "Discursos"
    LibraryFilter.Pinned -> "Fijados"
    LibraryFilter.NoFolder -> "Sin carpeta"
    is LibraryFilter.Folder -> folders[filter.id]?.name ?: "Carpeta"
    LibraryFilter.Trash -> "Papelera"
}

@Composable
private fun SpeechCard(
    item: SpeechListItem,
    folder: FolderEntity?,
    selected: Boolean,
    paceWpm: Int,
    isTrash: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val seconds = if (paceWpm > 0) item.wordCount * 60 / paceWpm else 0
    val shape = RoundedCornerShape(18.dp)

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) colors.secondaryContainer else colors.surface)
                .border(1.dp, if (selected) colors.secondary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.6f), shape)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .height(IntrinsicSize.Min),
        ) {
            // The colour label runs down the left edge.
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(if (item.color != 0) Color(item.color) else Color.Transparent),
            )
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "Sin título" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.title.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.PushPin, contentDescription = "Fijado", tint = colors.primary, modifier = Modifier.size(16.dp))
                    }
                }
                if (item.preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip(Icons.Filled.Timer, formatDuration(seconds))
                    MetaChip(null, "${item.wordCount} palabras")
                    if (folder != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(folder.color)))
                            Spacer(Modifier.width(5.dp))
                            Text(folder.name, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val stamp = if (isTrash && item.trashedAt != null) item.trashedAt else item.updatedAt
                    Text(
                        relativeTime(stamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (isTrash) {
                DropdownMenuItem(
                    text = { Text("Restaurar") },
                    leadingIcon = { Icon(Icons.Filled.Restore, null) },
                    onClick = { menu = false; onRestore() },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar definitivamente") },
                    leadingIcon = { Icon(Icons.Filled.DeleteForever, null) },
                    onClick = { menu = false; onDeleteForever() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(if (item.pinned) "Quitar de fijados" else "Fijar arriba") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                    onClick = { menu = false; onTogglePin() },
                )
                DropdownMenuItem(
                    text = { Text("Duplicar") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text("Mover a la papelera") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menu = false; onTrash() },
                )
            }
        }
    }
}
