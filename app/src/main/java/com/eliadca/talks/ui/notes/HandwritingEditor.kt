package com.eliadca.talks.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.PenKind
import com.eliadca.talks.export.Clipboard
import kotlin.math.max

@Composable
fun HandwritingNotePanel(
    body: TextFieldValue, onEdit: (TextFieldValue) -> Unit, ink: InkDocument,
    onInkChange: (InkDocument) -> Unit, canUndo: Boolean, canRedo: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var tool by rememberSaveable { mutableStateOf(InkTool.TEXT) }
    var pen by rememberSaveable { mutableStateOf(PenKind.BALLPOINT) }
    var color by rememberSaveable { mutableIntStateOf(0xff232b3c.toInt()) }
    var width by rememberSaveable { mutableFloatStateOf(5f) }
    var pressure by rememberSaveable { mutableStateOf(true) }
    var stylusOnly by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    fun choose(value: InkTool) { tool = value; if (value != InkTool.TEXT) keyboard?.hide() }

    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mi nota", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { Clipboard.copy(context, "Nota", body.text) }, enabled = body.text.isNotBlank()) {
                    Icon(Icons.Filled.ContentCopy, "Copiar nota completa")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(tool == InkTool.TEXT, { choose(InkTool.TEXT) }, label = { Text("Texto") }, leadingIcon = { Icon(Icons.Filled.Keyboard, null, Modifier.size(18.dp)) })
                FilterChip(tool == InkTool.PEN, { choose(InkTool.PEN) }, label = { Text("Lápiz") }, leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) })
                IconToggleButton(tool == InkTool.ERASER, { choose(InkTool.ERASER) }) { Icon(Icons.Filled.Backspace, "Borrador de trazos") }
                IconToggleButton(tool == InkTool.MOVE, { choose(InkTool.MOVE) }) { Icon(Icons.Filled.PanTool, "Mover la página") }
                IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer escritura") }
                IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer escritura") }
                IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Tune, "Tipo, grosor y color del lápiz", tint = Color(color)) }
                IconButton(onClick = { rules = true }) { Icon(Icons.Filled.FormatLineSpacing, "Renglones") }
            }
            if (tool == InkTool.PEN || tool == InkTool.ERASER || tool == InkTool.RULE_AREA) {
                Text(when (tool) {
                    InkTool.RULE_AREA -> "Arrastra un rectángulo en la zona donde quieres renglones."
                    InkTool.ERASER -> "Toca o desliza para borrar trazos completos."
                    else -> "${pen.label} · grosor ${width.toInt()} · ${if (stylusOnly) "solo lápiz digital" else "lápiz o dedo"}"
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            HorizontalDivider()
            NotebookPage(body, onEdit, ink, onInkChange, tool, pen, color, width, pressure, stylusOnly,
                onAreaFinished = { choose(InkTool.PEN) }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
    if (settings) {
        PenOptions(pen, color, width, pressure, stylusOnly,
            onPen = { pen = it }, onColor = { color = it }, onWidth = { width = it },
            onPressure = { pressure = it }, onStylusOnly = { stylusOnly = it }, onClose = { settings = false })
    }
    if (rules) {
        var spacing by remember { mutableFloatStateOf(ink.spacing) }
        AlertDialog(onDismissRequest = { rules = false }, title = { Text("Renglones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Toda la página", Modifier.weight(1f))
                        Switch(ink.wholePageRuled, { onInkChange(ink.copy(wholePageRuled = it)) },
                            Modifier.semantics { contentDescription = "Renglones en toda la página" })
                    }
                    Text("Separación: ${spacing.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(spacing, { spacing = it }, onValueChangeFinished = { onInkChange(ink.copy(spacing = spacing)) }, valueRange = 40f..100f, steps = 5,
                        modifier = Modifier.semantics { contentDescription = "Separación de renglones" })
                    OutlinedButton(onClick = {
                        if (ink.wholePageRuled) onInkChange(ink.copy(wholePageRuled = false))
                        rules = false; choose(InkTool.RULE_AREA)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Elegir una zona de la página") }
                    if (ink.areas.isNotEmpty()) TextButton(onClick = { onInkChange(ink.copy(areas = emptyList())) }) {
                        Text("Quitar renglones de las zonas (${ink.areas.size})")
                    }
                    Text("Los renglones se guardan con la nota. Puedes deshacer los cambios.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = { rules = false }) { Text("Listo") } })
    }
}

private val PenKind.label get() = when (this) {
    PenKind.BALLPOINT -> "Bolígrafo"
    PenKind.PENCIL -> "Lápiz"
    PenKind.FOUNTAIN -> "Pluma"
    PenKind.HIGHLIGHTER -> "Resaltador"
}

@Composable
private fun PenOptions(pen: PenKind, color: Int, width: Float, pressure: Boolean, stylusOnly: Boolean,
    onPen: (PenKind) -> Unit, onColor: (Int) -> Unit, onWidth: (Float) -> Unit,
    onPressure: (Boolean) -> Unit, onStylusOnly: (Boolean) -> Unit, onClose: () -> Unit) {
    var hex by remember { mutableStateOf("#%06X".format(color and 0xffffff)) }
    val parsed = hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (it or 0xff000000).toInt() }
    val palette = listOf(0xff232b3c, 0xff1565c0, 0xff00897b, 0xff2e7d32, 0xff7b1fa2, 0xffe53935,
        0xffef6c00, 0xfffbc02d, 0xffec407a, 0xff795548, 0xff9e9e9e, 0xffffffff).map { it.toInt() }
    AlertDialog(onDismissRequest = onClose, title = { Text("Tu lápiz") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PenKind.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { kind -> FilterChip(pen == kind, { onPen(kind) }, label = { Text(kind.label) }, modifier = Modifier.weight(1f)) }
                }
            }
            Text("Grosor: ${width.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(width, onWidth, valueRange = 1f..24f, modifier = Modifier.semantics { contentDescription = "Grosor del lápiz" })
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.fillMaxWidth().height((width * .55f).coerceAtLeast(1f).dp).background(Color(color), CircleShape))
            }
            Text("Color", style = MaterialTheme.typography.labelLarge)
            palette.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { value ->
                        Box(Modifier.size(38.dp).border(if (color == value) 3.dp else 1.dp,
                            if (color == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(4.dp).background(Color(value), CircleShape).clickable { onColor(value); hex = "#%06X".format(value and 0xffffff) }
                            .semantics { contentDescription = "Color #%06X".format(value and 0xffffff) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(hex, { hex = it.take(7) }, label = { Text("Color personalizado") }, singleLine = true,
                    isError = parsed == null, modifier = Modifier.weight(1f))
                TextButton(onClick = { parsed?.let(onColor) }, enabled = parsed != null) { Text("Usar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar presión del lápiz", Modifier.weight(1f)); Switch(pressure, onPressure)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dibujar solo con lápiz digital", Modifier.weight(1f)); Switch(stylusOnly, onStylusOnly)
            }
            Text("Con un lápiz digital, la palma se ignora mientras escribes. Usa Mover para desplazarte sin dibujar.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Listo") } })
}

@Composable
private fun NotebookPage(body: TextFieldValue, onEdit: (TextFieldValue) -> Unit, ink: InkDocument,
    onInkChange: (InkDocument) -> Unit, tool: InkTool, pen: PenKind, color: Int, width: Float,
    pressure: Boolean, stylusOnly: Boolean, onAreaFinished: () -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    var textHeight by remember { mutableFloatStateOf(0f) }
    val ruleColor = MaterialTheme.colorScheme.primary.toArgb()
    BoxWithConstraints(modifier) {
        val factor = maxWidth.value / InkDocument.PAGE_WIDTH
        val worldHeight = max(ink.height, textHeight + 300)
        val pageHeight = (worldHeight * factor).dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("note-page-scroll")) {
            Box(Modifier.fillMaxWidth().height(pageHeight).background(MaterialTheme.colorScheme.surface)) {
                @Composable fun inkLayer() {
                    AndroidView(factory = { InkPageView(it) }, modifier = Modifier.fillMaxSize().testTag("ink-page"), update = {
                        it.document = ink; it.tool = tool; it.pen = pen; it.penColor = color; it.penWidth = width
                        it.usePressure = pressure; it.stylusOnly = stylusOnly; it.ruleColor = ruleColor
                        it.onChange = onInkChange; it.onAreaFinished = onAreaFinished
                    })
                }
                val writing = tool == InkTool.PEN || tool == InkTool.ERASER || tool == InkTool.RULE_AREA
                if (!writing) inkLayer()
                BasicTextField(body, onEdit, readOnly = tool != InkTool.TEXT,
                    modifier = Modifier.fillMaxWidth().padding((40 * factor).dp).testTag("note-body"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (46 * factor).sp,
                        lineHeight = (64 * factor).sp, color = MaterialTheme.colorScheme.onSurface),
                    onTextLayout = { result -> textHeight = result.multiParagraph.height / density.density / factor + 80 },
                    decorationBox = { field ->
                        Box {
                            if (body.text.isEmpty() && ink.strokes.isEmpty() && tool == InkTool.TEXT) Text(
                                "Escribe tus ideas, añade fragmentos o selecciona Lápiz para escribir a mano…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            field()
                        }
                    })
                if (writing) inkLayer()
            }
            OutlinedButton(onClick = { onInkChange(ink.copy(height = worldHeight + 1000)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Añadir espacio a la página")
            }
        }
    }
}
