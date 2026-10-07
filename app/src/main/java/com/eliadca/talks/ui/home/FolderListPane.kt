package com.eliadca.talks.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.eliadca.talks.core.text.SpanishText
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.ui.components.EmptyState

/** Folder browser, in the same pane and at the same width as the speech list. */
@Composable
fun FolderListPane(
    folders: List<FolderEntity>,
    counts: LibraryCounts,
    query: String,
    showMenuButton: Boolean,
    onMenu: () -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (LibraryFilter) -> Unit,
    onNewFolder: (String, Int) -> Unit,
    onUpdateFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newFolder by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FolderEntity?>(null) }
    var deleting by remember { mutableStateOf<FolderEntity?>(null) }
    val search = SpanishText.foldForSearch(query).trim()
    val visibleFolders = remember(folders, search) {
        folders.filter { SpanishText.foldForSearch(it.name).contains(search) }
    }
    val showNoFolder = SpanishText.foldForSearch("Sin carpeta").contains(search)

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenuButton) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Abrir el menú") }
            }
            Column(Modifier.weight(1f).padding(start = if (showMenuButton) 0.dp else 10.dp)) {
                Text("Carpetas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (folders.size == 1) "1 carpeta" else "${folders.size} carpetas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { newFolder = true }) {
                Icon(Icons.Filled.CreateNewFolder, "Nueva carpeta")
            }
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("Buscar carpetas") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Borrar búsqueda") }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (visibleFolders.isEmpty() && !showNoFolder && search.isNotEmpty()) {
            EmptyState(Icons.Filled.Search, "Sin resultados", "No hay carpetas con ese nombre.", modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showNoFolder) {
                    item(key = "no-folder") {
                        FolderRow(null, counts.noFolder, { onOpen(LibraryFilter.NoFolder) })
                    }
                }
                items(visibleFolders, key = { it.id }) { folder ->
                    FolderRow(
                        folder, counts.perFolder[folder.id] ?: 0,
                        onClick = { onOpen(LibraryFilter.Folder(folder.id)) },
                        onEdit = { editing = folder },
                        onDelete = { deleting = folder },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (folders.isEmpty() && search.isEmpty()) {
                    item(key = "empty-folders") {
                        EmptyState(
                            Icons.Filled.Folder, "Organiza tus discursos",
                            "Crea carpetas por tema o evento. Usa «Mover a carpeta» en el menú de cada discurso.",
                            action = { TextButton(onClick = { newFolder = true }) { Text("Nueva carpeta") } },
                        )
                    }
                }
            }
        }
    }

    if (newFolder) {
        FolderDialog(null, { name, color -> onNewFolder(name, color); newFolder = false }, { newFolder = false })
    }
    editing?.let { folder ->
        FolderDialog(folder, { name, color ->
            onUpdateFolder(folder.copy(name = name, color = color))
            editing = null
        }, { editing = null })
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar la carpeta «${folder.name}»?") },
            text = { Text("Los discursos que contiene se conservan en «Sin carpeta».") },
            confirmButton = { TextButton(onClick = { onDeleteFolder(folder.id); deleting = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FolderRow(
    folder: FolderEntity?,
    count: Int,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = folder?.let { Color(it.color) } ?: colors.onSurfaceVariant
    val name = folder?.name ?: "Sin carpeta"
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = if (folder != null) ({ menu = true }) else null)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (folder == null) Icons.Filled.FolderOpen else Icons.Filled.Folder, null, tint = tint)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 discurso" else "$count discursos",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
        }
        if (folder != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opciones de carpeta: $name") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Cambiar nombre y color") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit?.invoke() },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar carpeta") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete?.invoke() },
                    )
                }
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.padding(12.dp), tint = colors.onSurfaceVariant)
        }
    }
}
