package com.eliadca.talks.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.eliadca.talks.editor.EditorController

/** Find and replace for the open speech. */
@Composable
fun FindBar(c: EditorController, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) { c.find(query) }

    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text("Buscar en el discurso") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { c.findNext() }),
                    shape = RoundedCornerShape(12.dp),
                )
                Text(
                    if (query.isBlank()) "" else "${c.findCurrent}/${c.findTotal}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                IconButton(onClick = { c.findPrevious() }, enabled = c.findTotal > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Anterior")
                }
                IconButton(onClick = { c.findNext() }, enabled = c.findTotal > 0) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Siguiente")
                }
                IconButton(onClick = { showReplace = !showReplace }) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Reemplazar")
                }
                IconButton(onClick = { c.closeFind() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar búsqueda", modifier = Modifier.size(22.dp))
                }
            }
            if (showReplace) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Reemplazar con") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    TextButton(onClick = { c.replaceCurrent(replacement) }, enabled = c.findTotal > 0) { Text("Reemplazar") }
                    TextButton(onClick = { c.replaceAll(replacement) }, enabled = c.findTotal > 0) { Text("Todo") }
                }
            }
        }
    }
}
