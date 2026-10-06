package com.eliadca.talks.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The ways to bring a speech in. */
class ImportActions(
    /** Pick Markdown, text or Word files (several at once). */
    val files: () -> Unit,
    /** Create a speech from what is on the clipboard; Markdown keeps its formatting. */
    val paste: () -> Unit,
    /** Copy the prompt that teaches an AI assistant to write speeches in Talks' Markdown. */
    val copyTemplate: () -> Unit,
)

/** Files, the clipboard and the template for an AI assistant, in one menu. */
@Composable
fun ImportMenu(expanded: Boolean, onDismiss: () -> Unit, actions: ImportActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ImportItem(Icons.Filled.FileOpen, "Importar archivos", "Markdown (.md), texto o Word. Varios a la vez.") {
            onDismiss(); actions.files()
        }
        ImportItem(Icons.Filled.ContentPaste, "Pegar desde el portapapeles", "Un discurso nuevo; el Markdown conserva su formato.") {
            onDismiss(); actions.paste()
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ImportItem(Icons.Filled.AutoAwesome, "Copiar plantilla para tu IA", "El prompt con todo el formato que entiende Talks.") {
            onDismiss(); actions.copyTemplate()
        }
    }
}

@Composable
private fun ImportItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
