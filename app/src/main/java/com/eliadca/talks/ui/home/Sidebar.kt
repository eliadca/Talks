package com.eliadca.talks.ui.home

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.ui.components.ColorSwatch
import com.eliadca.talks.ui.theme.LabelColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.FileDownload

/**
 * The menu folded into a slim rail of floating buttons on the screen's own background: new speech,
 * the main lists, folders, trash and settings. "Carpetas" opens the folders right inside the rail;
 * picking one shows what it holds. Nothing here reacts to swipes.
 */
@Composable
fun LibraryRail(
    filter: LibraryFilter,
    counts: LibraryCounts,
    folders: List<FolderEntity>,
    foldersOpen: Boolean,
    onToggleFolders: () -> Unit,
    onNewSpeech: () -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onNewFolder: (String, Int) -> Unit,
    onUpdateFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var folderDialog by remember { mutableStateOf<FolderEntity?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Column(
        modifier
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // New speech: the one strong colour of the rail.
        androidx.compose.material3.Surface(
            onClick = onNewSpeech,
            shape = RoundedCornerShape(20.dp),
            color = colors.primary,
            contentColor = colors.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier.size(60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = "Nuevo discurso", modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        RailButton(Icons.Filled.Description, "Todos", filter == LibraryFilter.All) { onFilter(LibraryFilter.All) }
        RailButton(Icons.Filled.PushPin, "Fijados", filter == LibraryFilter.Pinned) { onFilter(LibraryFilter.Pinned) }
        RailButton(
            if (foldersOpen) Icons.Filled.FolderOpen else Icons.Filled.Folder, "Carpetas",
            foldersOpen || filter is LibraryFilter.Folder || filter == LibraryFilter.NoFolder,
            onClick = onToggleFolders,
        )
        AnimatedVisibility(foldersOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainer)
                    .padding(6.dp),
            ) {
                NavRow(Icons.Filled.FolderOpen, "Sin carpeta", counts.noFolder, filter == LibraryFilter.NoFolder) { onFilter(LibraryFilter.NoFolder) }
                for (f in folders) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        NavRow(
                            Icons.Filled.Folder, f.name, counts.perFolder[f.id] ?: 0,
                            filter == LibraryFilter.Folder(f.id), tint = Color(f.color),
                            onLongClick = { menu = true },
                        ) { onFilter(LibraryFilter.Folder(f.id)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Cambiar nombre y color") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; folderDialog = f },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar carpeta") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; confirmDelete = f },
                            )
                        }
                    }
                }
                NavRow(Icons.Filled.CreateNewFolder, "Nueva carpeta", null, false, tint = colors.primary) { newFolder = true }
            }
        }
        RailButton(Icons.Filled.Delete, "Papelera", filter == LibraryFilter.Trash, badge = counts.trash) { onFilter(LibraryFilter.Trash) }
        RailButton(Icons.Filled.Settings, "Ajustes", false, onClick = onSettings)
    }

    if (newFolder) {
        FolderDialog(null, onSave = { name, color -> onNewFolder(name, color); newFolder = false }, onDismiss = { newFolder = false })
    }
    folderDialog?.let { f ->
        FolderDialog(f, onSave = { name, color -> onUpdateFolder(f.copy(name = name, color = color)); folderDialog = null }, onDismiss = { folderDialog = null })
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar la carpeta «${f.name}»?") },
            text = { Text("Los discursos que contiene no se borran: pasan a «Sin carpeta».") },
            confirmButton = { TextButton(onClick = { onDeleteFolder(f.id); confirmDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** A floating button of the rail: a soft raised tile with its label under it. */
@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (selected) colors.primaryContainer else colors.background,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            shadowElevation = if (selected) 2.dp else 5.dp,
            modifier = Modifier.size(width = 56.dp, height = 46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (badge > 0) {
                    BadgedBox(badge = { Badge { Text(badge.toString()) } }) { Icon(icon, contentDescription = null) }
                } else {
                    Icon(icon, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The full menu: lists, folders, trash and settings. Opens over the screen from the rail. */
@Composable
fun SidebarContent(
    filter: LibraryFilter,
    counts: LibraryCounts,
    folders: List<FolderEntity>,
    onFilter: (LibraryFilter) -> Unit,
    onNewSpeech: () -> Unit,
    onNewFolder: (String, Int) -> Unit,
    onUpdateFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    importActions: ImportActions? = null,
) {
    var folderDialog by remember { mutableStateOf<FolderEntity?>(null) }
    var importMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text("Talks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar el menú") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onNewSpeech, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.NoteAdd, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Nuevo discurso")
        }
        if (importActions != null) {
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(onClick = { importMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileDownload, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Importar")
                }
                ImportMenu(importMenu, { importMenu = false }, importActions)
            }
        }
        Spacer(Modifier.height(14.dp))

        NavRow(Icons.Filled.Description, "Todos los discursos", counts.all, filter == LibraryFilter.All) { onFilter(LibraryFilter.All) }
        NavRow(Icons.Filled.PushPin, "Fijados", counts.pinned, filter == LibraryFilter.Pinned) { onFilter(LibraryFilter.Pinned) }
        NavRow(Icons.Filled.FolderOpen, "Sin carpeta", counts.noFolder, filter == LibraryFilter.NoFolder) { onFilter(LibraryFilter.NoFolder) }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CARPETAS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { newFolder = true }) {
                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Nueva")
            }
        }
        if (folders.isEmpty()) {
            Text(
                "Crea carpetas para agrupar tus discursos por evento o tema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        for (f in folders) {
            var menu by remember { mutableStateOf(false) }
            Box {
                NavRow(
                    Icons.Filled.Folder, f.name, counts.perFolder[f.id] ?: 0,
                    filter == LibraryFilter.Folder(f.id), tint = Color(f.color),
                    onLongClick = { menu = true },
                ) { onFilter(LibraryFilter.Folder(f.id)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Cambiar nombre y color") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; folderDialog = f },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar carpeta") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; confirmDelete = f },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavRow(Icons.Filled.Delete, "Papelera", counts.trash, filter == LibraryFilter.Trash) { onFilter(LibraryFilter.Trash) }
        NavRow(Icons.Filled.Settings, "Ajustes", null, false) { onSettings() }
    }

    if (newFolder) {
        FolderDialog(null, onSave = { name, color -> onNewFolder(name, color); newFolder = false }, onDismiss = { newFolder = false })
    }
    folderDialog?.let { f ->
        FolderDialog(f, onSave = { name, color -> onUpdateFolder(f.copy(name = name, color = color)); folderDialog = null }, onDismiss = { folderDialog = null })
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar la carpeta «${f.name}»?") },
            text = { Text("Los discursos que contiene no se borran: pasan a «Sin carpeta».") },
            confirmButton = { TextButton(onClick = { onDeleteFolder(f.id); confirmDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    count: Int?,
    selected: Boolean,
    tint: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint ?: if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null && count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun FolderDialog(initial: FolderEntity?, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: LabelColors[5]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva carpeta" else "Editar carpeta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text("Nombre") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in LabelColors) ColorSwatch(c, c == color, "Color", size = 28) { color = c }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

