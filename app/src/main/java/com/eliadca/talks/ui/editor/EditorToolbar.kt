package com.eliadca.talks.ui.editor

import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.doc.Align
import com.eliadca.talks.core.doc.BlockType
import com.eliadca.talks.editor.EditorController
import com.eliadca.talks.editor.InlineKind
import com.eliadca.talks.ui.components.ColorSwatch
import com.eliadca.talks.ui.theme.HighlightColors
import com.eliadca.talks.ui.theme.TextColors

/**
 * The formatting bar above the page, in groups: paragraph style, character style, colour and
 * size, lists, alignment, and notes. It scrolls sideways when the screen is narrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatToolbar(c: EditorController, modifier: Modifier = Modifier) {
    val f = c.format
    Surface(
        modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // On narrow screens the groups wrap onto a second line instead of hiding off the edge.
        FlowRow(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextStyleMenu(f.block, onClear = { c.clearFormatting(); c.focus() }) { c.toggleBlock(it); c.focus() }
            ToolGroup {
                ToolButton(Icons.Filled.FormatBold, "Negrita (Ctrl+B)", active = f.bold) { c.toggle(InlineKind.BOLD); c.focus() }
                ToolButton(Icons.Filled.FormatItalic, "Cursiva (Ctrl+I)", active = f.italic) { c.toggle(InlineKind.ITALIC); c.focus() }
                ToolButton(Icons.Filled.FormatUnderlined, "Subrayado (Ctrl+U)", active = f.underline) { c.toggle(InlineKind.UNDERLINE); c.focus() }
                ToolButton(Icons.Filled.StrikethroughS, "Tachado", active = f.strike) { c.toggle(InlineKind.STRIKE); c.focus() }
            }
            ToolGroup {
                PaletteButton(Icons.Filled.FormatColorText, "Color del texto", TextColors, f.color, "Color predeterminado") {
                    c.setColor(it); c.focus()
                }
                PaletteButton(Icons.Filled.FormatColorFill, "Resaltado", HighlightColors, f.highlight, "Quitar resaltado") {
                    c.setHighlight(it); c.focus()
                }
                SizeButton(f.sizePercent) { c.setSizePercent(it); c.focus() }
            }
            ToolGroup {
                ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Lista con viñetas", active = f.block == BlockType.BULLET) {
                    c.toggleBlock(BlockType.BULLET); c.focus()
                }
                ToolButton(Icons.Filled.FormatListNumbered, "Lista numerada", active = f.block == BlockType.NUMBER) {
                    c.toggleBlock(BlockType.NUMBER); c.focus()
                }
                ToolButton(Icons.Filled.CheckBox, "Lista de tareas", active = f.block == BlockType.CHECK) {
                    c.toggleBlock(BlockType.CHECK); c.focus()
                }
                ToolButton(Icons.Filled.FormatQuote, "Cita", active = f.block == BlockType.QUOTE) {
                    c.toggleBlock(BlockType.QUOTE); c.focus()
                }
            }
            ToolGroup {
                AlignMenu(f.align) { c.setAlign(it); c.focus() }
                ToolButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Reducir sangría") { c.indent(-1); c.focus() }
                ToolButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Aumentar sangría") { c.indent(1); c.focus() }
            }
            NoteButton(active = f.stage) { c.insertNote(); c.focus() }
        }
    }
}

/** A rounded group of related buttons on the toolbar. */
@Composable
private fun ToolGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Alignment in one button that shows the current one; the choices open from it. */
@Composable
private fun AlignMenu(current: Align, onPick: (Align) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        Triple(Align.START, Icons.Filled.FormatAlignLeft, "Alinear a la izquierda"),
        Triple(Align.CENTER, Icons.Filled.FormatAlignCenter, "Centrar"),
        Triple(Align.END, Icons.Filled.FormatAlignRight, "Alinear a la derecha"),
    )
    val icon = options.firstOrNull { it.first == current }?.second ?: Icons.Filled.FormatAlignLeft
    Box {
        ToolButton(icon, "Alineación", active = current != Align.START) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((align, optionIcon, label) in options) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(optionIcon, contentDescription = null) },
                    trailingIcon = { if (align == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onPick(align) },
                )
            }
        }
    }
}

/** The paragraph style, shown by name: Texto, Título grande, Título, Subtítulo. */
@Composable
private fun TextStyleMenu(current: BlockType, onClear: () -> Unit, onPick: (BlockType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val label = when (current) {
        BlockType.H1 -> "Título grande"
        BlockType.H2 -> "Título"
        BlockType.H3 -> "Subtítulo"
        else -> "Texto"
    }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .clickable(role = Role.Button, onClickLabel = "Estilo del párrafo") { open = true }
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Title, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 64.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val entries = listOf(
                Triple("Texto", BlockType.NORMAL, 16),
                Triple("Título grande", BlockType.H1, 26),
                Triple("Título", BlockType.H2, 22),
                Triple("Subtítulo", BlockType.H3, 18),
            )
            for ((name, block, size) in entries) {
                val selected = if (block == BlockType.NORMAL) !current.isHeading else block == current
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            fontSize = size.sp,
                            fontWeight = if (block == BlockType.NORMAL) FontWeight.Normal else FontWeight.Bold,
                        )
                    },
                    trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        when {
                            block == BlockType.NORMAL -> if (current.isHeading) onPick(current)
                            block != current -> onPick(block)
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Quitar formato") },
                leadingIcon = { Icon(Icons.Filled.FormatClear, contentDescription = null) },
                onClick = { open = false; onClear() },
            )
        }
    }
}

/** Notes for the speaker: the selection becomes one, or "[ ]" starts a new one at the cursor. */
@Composable
private fun NoteButton(active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.primaryContainer else colors.surface)
            .semantics { contentDescription = "Nota para ti: se ve, pero no se espera que la digas" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.EditNote, contentDescription = null, tint = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Nota", style = MaterialTheme.typography.labelLarge, color = if (active) colors.onPrimaryContainer else colors.onSurface)
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.32f)
        active -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.primaryContainer else Color.Transparent)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PaletteButton(
    icon: ImageVector,
    description: String,
    colors: List<Int>,
    current: Int,
    resetLabel: String,
    onPick: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(icon, description, active = current != 0) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(description, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (color in colors) {
                        ColorSwatch(color, selected = color == current, description = description) {
                            onPick(color); open = false
                        }
                    }
                }
                TextButton(onClick = { onPick(null); open = false }) { Text(resetLabel) }
            }
        }
    }
}

@Composable
private fun SizeButton(current: Int, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(Icons.Filled.FormatSize, "Tamaño del texto", active = current != 100) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, pct) in listOf("Pequeño" to 80, "Normal" to 100, "Grande" to 130, "Muy grande" to 170, "Enorme" to 220)) {
                DropdownMenuItem(
                    text = { Text(label + if (pct == current) "  ✓" else "") },
                    onClick = { onPick(pct); open = false },
                )
            }
        }
    }
}

/** A slim spacer used between toolbar groups on wide screens. */
@Composable
fun ToolbarGap() = Spacer(Modifier.width(8.dp))
