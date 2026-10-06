package com.eliadca.talks.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Carpetas y ajustes") }
            }
            Text(
                titleFor(filter, folderById),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = if (showMenuButton) 0.dp else 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isTrash) {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        for (s in SortKey.entries) {
                            DropdownMenuItem(
                                text = { Text(s.label + if (s == sort) "  ✓" else "") },
                                onClick = { onSort(s); sortMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Nuevo discurso") }
            } else if (items.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) { Text("Vaciar") }
            }
        }

        if (!isTrash) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Buscar en todos los discursos") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
                    }
                },
                shape = RoundedCornerShape(16.dp),
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

    Box {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected) colors.primaryContainer else colors.surface,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Row(Modifier.fillMaxWidth()) {
                if (item.color != 0) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(Color(item.color)))
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
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
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val stamp = if (isTrash && item.trashedAt != null) item.trashedAt else item.updatedAt
                        Text(
                            DateUtils.getRelativeTimeSpanString(stamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            "${item.wordCount} palabras · ${formatDuration(seconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        if (folder != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Folder, null, tint = Color(folder.color), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(folder.name, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
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
