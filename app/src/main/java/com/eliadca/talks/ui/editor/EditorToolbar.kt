package com.eliadca.talks.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.material.icons.filled.VisibilityOff
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

/** The formatting bar above the page. It scrolls sideways when the screen is narrow. */
@Composable
fun FormatToolbar(c: EditorController, modifier: Modifier = Modifier) {
    val f = c.format
    Surface(
        modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(Icons.AutoMirrored.Filled.Undo, "Deshacer", enabled = c.canUndo) { c.undo() }
            ToolButton(Icons.AutoMirrored.Filled.Redo, "Rehacer", enabled = c.canRedo) { c.redo() }
            Sep()
            ToolButton(Icons.Filled.FormatBold, "Negrita (Ctrl+B)", active = f.bold) { c.toggle(InlineKind.BOLD); c.focus() }
            ToolButton(Icons.Filled.FormatItalic, "Cursiva (Ctrl+I)", active = f.italic) { c.toggle(InlineKind.ITALIC); c.focus() }
            ToolButton(Icons.Filled.FormatUnderlined, "Subrayado (Ctrl+U)", active = f.underline) { c.toggle(InlineKind.UNDERLINE); c.focus() }
            ToolButton(Icons.Filled.StrikethroughS, "Tachado", active = f.strike) { c.toggle(InlineKind.STRIKE); c.focus() }
            Sep()
            PaletteButton(Icons.Filled.FormatColorText, "Color del texto", TextColors, f.color, "Color predeterminado") {
                c.setColor(it); c.focus()
            }
            PaletteButton(Icons.Filled.FormatColorFill, "Resaltado", HighlightColors, f.highlight, "Quitar resaltado") {
                c.setHighlight(it); c.focus()
            }
            SizeButton(f.sizePercent) { c.setSizePercent(it); c.focus() }
            Sep()
            HeadingButton(f.block) { c.toggleBlock(it); c.focus() }
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
            Sep()
            ToolButton(Icons.Filled.FormatAlignLeft, "Alinear a la izquierda", active = f.align == Align.START) { c.setAlign(Align.START); c.focus() }
            ToolButton(Icons.Filled.FormatAlignCenter, "Centrar", active = f.align == Align.CENTER) { c.setAlign(Align.CENTER); c.focus() }
            ToolButton(Icons.Filled.FormatAlignRight, "Alinear a la derecha", active = f.align == Align.END) { c.setAlign(Align.END); c.focus() }
            ToolButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Reducir sangría") { c.indent(-1); c.focus() }
            ToolButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Aumentar sangría") { c.indent(1); c.focus() }
            Sep()
            ToolButton(Icons.Filled.VisibilityOff, "Nota para mí: se ve pero no se lee en voz alta", active = f.stage) {
                c.toggle(InlineKind.STAGE); c.focus()
            }
            ToolButton(Icons.Filled.FormatClear, "Quitar formato") { c.clearFormatting(); c.focus() }
        }
    }
}

@Composable
private fun Sep() {
    VerticalDivider(
        Modifier
            .padding(horizontal = 4.dp)
            .height(26.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.primaryContainer else Color.Transparent)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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

@Composable
private fun HeadingButton(current: BlockType, onPick: (BlockType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(Icons.Filled.Title, "Títulos", active = current.isHeading) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val entries = listOf(
                "Título grande" to BlockType.H1,
                "Título" to BlockType.H2,
                "Subtítulo" to BlockType.H3,
            )
            for ((label, block) in entries) {
                DropdownMenuItem(
                    text = { Text(label + if (block == current) "  ✓" else "") },
                    onClick = { onPick(block); open = false },
                )
            }
            if (current.isHeading) {
                DropdownMenuItem(text = { Text("Texto normal") }, onClick = { onPick(current); open = false })
            }
        }
    }
}

/** A slim spacer used between toolbar groups on wide screens. */
@Composable
fun ToolbarGap() = Spacer(Modifier.width(8.dp))
