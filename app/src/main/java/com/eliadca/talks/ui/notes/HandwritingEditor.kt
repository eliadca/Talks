package com.eliadca.talks.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.eliadca.talks.core.ink.styleSelection
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.PenKind
import com.eliadca.talks.core.ink.NoteTextFrame
import androidx.compose.ui.text.TextRange
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.export.Clipboard
import kotlin.math.max

@Stable
class NoteEditorState {
    var tool by mutableStateOf(InkTool.TEXT)
    var pen by mutableStateOf(PenKind.BALLPOINT)
    internal val presets = mutableStateMapOf<PenKind, Pair<Int, Float>>(
        PenKind.BALLPOINT to (0xff232b3c.toInt() to 5f), PenKind.PENCIL to (0xff546e7a.toInt() to 4f),
        PenKind.FOUNTAIN to (0xff1565c0.toInt() to 7f), PenKind.HIGHLIGHTER to (0xfffbc02d.toInt() to 16f))
    var color: Int get() = presets.getValue(pen).first; set(value) { presets[pen] = value to width }
    var width: Float get() = presets.getValue(pen).second; set(value) { presets[pen] = color to value }
    var pressure by mutableStateOf(true)
    var stylusOnly by mutableStateOf(false)
    var eraserSettings by mutableStateOf(false)
    var eraserMode by mutableStateOf(EraserMode.PARTIAL)
    var eraserRadius by mutableFloatStateOf(20f)
    var preview by mutableStateOf<InkDocument?>(null)
    var settings by mutableStateOf(false)
    var rules by mutableStateOf(false)
    var pageSettings by mutableStateOf(false)
    var textBoxEditor by mutableStateOf(false)
    var textSettings by mutableStateOf(false)
    var zoom by mutableFloatStateOf(1f)
    var toolbarSide by mutableStateOf(false)
    var inspectorOpen by mutableStateOf(true)
    internal var inspectorTab by mutableStateOf(StudioTab.PROPERTIES)
    var inspectPage by mutableStateOf(false)
    var objects by mutableStateOf(false)
    var transform by mutableStateOf(false)
    var shape by mutableStateOf(com.eliadca.talks.core.ink.NoteShape.LINE)
    var shapes by mutableStateOf(false)
    var grid by mutableStateOf(false)
    var templates by mutableStateOf(false)
    var imageOptions by mutableStateOf(false)
    var appearance by mutableStateOf(false)
    var activeBox by mutableStateOf<Long?>(null)
    var boxSelection by mutableStateOf(TextRange(0))
    var selected by mutableStateOf(emptySet<Long>())
    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<NoteEditorState, Any>(
            save = { state -> listOf(state.tool.name, state.pen.name, state.pressure, state.stylusOnly, state.zoom) +
                PenKind.entries.flatMap { listOf(state.presets.getValue(it).first, state.presets.getValue(it).second) } + listOf(state.eraserMode.name,state.eraserRadius,state.toolbarSide,state.shape.name,state.inspectorOpen,state.inspectorTab.name,state.inspectPage) },
            restore = { list -> NoteEditorState().apply {
                tool = InkTool.valueOf(list[0] as String); pen = PenKind.valueOf(list[1] as String)
                pressure = list[2] as Boolean; stylusOnly = list[3] as Boolean; zoom = list[4] as Float
                PenKind.entries.forEachIndexed { i, kind -> presets[kind] = (list[5 + i * 2] as Int) to (list[6 + i * 2] as Float) }
                if(list.size>=17)shape=com.eliadca.talks.core.ink.NoteShape.valueOf(list[16] as String)
                if(list.size>=20) {inspectorOpen=list[17] as Boolean;inspectorTab=StudioTab.valueOf(list[18] as String);inspectPage=list[19] as Boolean}
                if(list.size>=16)toolbarSide=list[15] as Boolean
                if(list.size>=15) {eraserMode=EraserMode.valueOf(list[13] as String);eraserRadius=list[14] as Float}
            } })
    }
}

@Composable
fun rememberNoteEditorState(): NoteEditorState {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("notes-pencil", android.content.Context.MODE_PRIVATE) }
    val state = rememberSaveable(saver = NoteEditorState.Saver) { NoteEditorState().apply {
        PenKind.entries.forEach { kind -> presets[kind] = prefs.getInt("${kind.name}-color", presets.getValue(kind).first) to prefs.getFloat("${kind.name}-width", presets.getValue(kind).second) }
        pen = runCatching { PenKind.valueOf(prefs.getString("kind", PenKind.BALLPOINT.name)!!) }.getOrDefault(PenKind.BALLPOINT)
        tool=runCatching {InkTool.valueOf(prefs.getString("tool","TEXT")!!)}.getOrDefault(InkTool.TEXT)
        shape=runCatching {com.eliadca.talks.core.ink.NoteShape.valueOf(prefs.getString("shape","LINE")!!)}.getOrDefault(com.eliadca.talks.core.ink.NoteShape.LINE)
        zoom = prefs.getFloat("zoom",1f).coerceIn(.5f,3f)
        toolbarSide=prefs.getBoolean("studio-palette-side",true)
        inspectorOpen=prefs.getBoolean("studio-inspector",true)
        inspectorTab=runCatching {StudioTab.valueOf(prefs.getString("studio-tab","PROPERTIES")!!)}.getOrDefault(StudioTab.PROPERTIES)
        inspectPage=prefs.getBoolean("studio-page",false)
        pressure = prefs.getBoolean("pressure", true); stylusOnly = prefs.getBoolean("stylus", false)
        eraserRadius=prefs.getFloat("eraser-radius",20f).coerceIn(6f,65f);eraserMode=runCatching {EraserMode.valueOf(prefs.getString("eraser-mode",EraserMode.PARTIAL.name)!!)}.getOrDefault(EraserMode.PARTIAL)
    } }
    val presets = state.presets.toMap()
    LaunchedEffect(presets, state.pen, state.pressure, state.stylusOnly, state.eraserRadius, state.eraserMode, state.zoom, state.toolbarSide,state.tool,state.shape,state.inspectorOpen,state.inspectorTab,state.inspectPage) {
        prefs.edit().apply {
            putString("tool",state.tool.name);putString("shape",state.shape.name);putFloat("zoom",state.zoom);putBoolean("toolbar-side",state.toolbarSide)
            putBoolean("studio-palette-side",state.toolbarSide);putBoolean("studio-inspector",state.inspectorOpen)
            putString("studio-tab",state.inspectorTab.name);putBoolean("studio-page",state.inspectPage)
            presets.forEach { (kind, value) -> putInt("${kind.name}-color", value.first); putFloat("${kind.name}-width", value.second) }
            putFloat("eraser-radius",state.eraserRadius);putString("eraser-mode",state.eraserMode.name);putString("kind", state.pen.name); putBoolean("pressure", state.pressure); putBoolean("stylus", state.stylusOnly)
        }.apply()
    }
    return state
}

@Composable
fun HandwritingNotePanel(
    body: TextFieldValue, onEdit: (TextFieldValue) -> Unit, ink: InkDocument,
    onInkChange: (InkDocument) -> Unit, canUndo: Boolean, canRedo: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, modifier: Modifier = Modifier,
    onReference: (BibleReference) -> Unit = {}, state: NoteEditorState = rememberNoteEditorState(),
    showToolbar: Boolean = true, onReferenceAt: ((BibleReference, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onFormat: ((com.eliadca.talks.core.notes.NoteTextStyle) -> com.eliadca.talks.core.notes.NoteTextStyle) -> Unit = {},
    textStyle: com.eliadca.talks.core.notes.NoteTextStyle = com.eliadca.talks.core.notes.NoteTextStyle(),
    onAlign: (Int) -> Unit = {}, onList: (Boolean) -> Unit = {},
    canUndoText: Boolean = false, canRedoText: Boolean = false, onUndoText: () -> Unit = {}, onRedoText: () -> Unit = {},
) {
    if(showToolbar) NoteStudio(state,body,ink,onInkChange,canUndo,canRedo,onUndo,onRedo,modifier,
        onFormat,textStyle,onAlign,onList,canUndoText,canRedoText,onUndoText,onRedoText) {pageModifier->
        NotebookPage(body,onEdit,ink,onInkChange,state,onReference,onReferenceAt,onAnchorChanged,pageModifier)
    } else Surface(modifier,color=MaterialTheme.colorScheme.background) {
        NotebookPage(body,onEdit,ink,onInkChange,state,onReference,onReferenceAt,onAnchorChanged,Modifier.fillMaxSize())
    }
}

@Composable
fun NoteEditorToolbar(state: NoteEditorState, body: TextFieldValue, ink: InkDocument, onInkChange: (InkDocument) -> Unit,
    canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit,
    onFormat: ((com.eliadca.talks.core.notes.NoteTextStyle) -> com.eliadca.talks.core.notes.NoteTextStyle) -> Unit = {},
    textStyle: com.eliadca.talks.core.notes.NoteTextStyle = com.eliadca.talks.core.notes.NoteTextStyle(),
    onAlign: (Int) -> Unit = {}, onList: (Boolean) -> Unit = {},
    canUndoText: Boolean = false, canRedoText: Boolean = false, onUndoText: () -> Unit = {}, onRedoText: () -> Unit = {},
    onMoveTextSelection: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    LaunchedEffect(ink.strokes, ink.textBoxes,ink.images,ink.lockedIds,ink.hiddenIds, body.text) { state.selected = (state.selected-ink.lockedIds-ink.hiddenIds).intersect((ink.strokes.map { it.id } + ink.textBoxes.map { it.id } + ink.images.map {it.id} + if (body.text.isNotEmpty()) listOf(InkDocument.MAIN_TEXT_ID) else emptyList()).toSet()) }
    val actions=studioActions(state,body,ink,onInkChange,textStyle,onFormat,onAlign,onList,context)
    val effectiveStyle=actions.style
    fun format(transform:(com.eliadca.talks.core.notes.NoteTextStyle)->com.eliadca.talks.core.notes.NoteTextStyle) = actions.format(transform)
    fun choose(tool:InkTool) {state.tool=tool;state.selected=emptySet();if(tool!=InkTool.TEXT)keyboard?.hide()}
    StudioControlBar(state,actions,body,ink,canUndo,canRedo,onUndo,onRedo,canUndoText,canRedoText,onUndoText,onRedoText,onMoveTextSelection)
    NoteMediaTools(state,ink,onInkChange)
    NoteVectorAppearance(state,ink,onInkChange)
    AdvancedNoteTools(state,ink.copy(bodyFrame=ink.bodyFrame ?: if(body.text.isNotEmpty())NoteTextFrame(width=ink.pageWidth-80f) else null),onInkChange)
    if (state.eraserSettings) AlertDialog(onDismissRequest = { state.eraserSettings = false }, title = { Text("Goma de borrar") }, text = {
        Column {
            Text("Tamaño: ${(state.eraserRadius * 2).toInt()}")
            Slider(state.eraserRadius, { state.eraserRadius = it }, valueRange = 6f..65f)
            EraserMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth().clickable { state.eraserMode = mode }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.eraserMode == mode, { state.eraserMode = mode }); Text(if (mode == EraserMode.PARTIAL) "Borrar solo lo que toco" else "Borrar el trazo completo")
            } }
            Text("También puedes borrar manteniendo el botón del S Pen. Deshacer recupera el gesto completo.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { state.eraserSettings = false }) { Text("Listo") } })
    if (state.settings) PenOptions(state.pen, state.color, state.width, state.pressure, state.stylusOnly,
        { state.pen = it }, { state.color = it }, { state.width = it }, { state.pressure = it }, { state.stylusOnly = it }, { state.settings = false })
    if (state.textBoxEditor) {
        val existing = ink.textBoxes.singleOrNull { it.id in state.selected }
        var text by remember { mutableStateOf(existing?.text ?: if (!body.selection.collapsed) body.text.substring(body.selection.min, body.selection.max) else "") }
        AlertDialog(onDismissRequest = { state.textBoxEditor = false }, title = { Text(if (existing == null) "Cuadro de texto móvil" else "Editar cuadro de texto") }, text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Texto del cuadro") }, minLines = 3, maxLines = 8)
                Text("Puedes mover y ajustar el cuadro con Seleccionar trazos.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = {
            val updated = if (existing == null) ink.addTextBox(text) else ink.copy(textBoxes = ink.textBoxes.map { if (it.id == existing.id) it.copy(text = text, formatting = it.formatting.replace(it.text, text),
                height = maxOf(it.height, text.lines().sumOf { row -> maxOf(1, (row.length * it.size * .6f / it.width).toInt() + 1) } * it.size * 1.4f)) else it })
            onInkChange(updated); state.tool = InkTool.SELECT; state.selected = setOf(existing?.id ?: ink.nextId); state.textBoxEditor = false; keyboard?.hide()
        }, enabled = text.isNotBlank()) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { state.textBoxEditor = false }) { Text("Cancelar") } })
    }
    if (state.textSettings) TextOptions(effectiveStyle, ::format) { state.textSettings = false }
    if (state.pageSettings) {
        var width by remember { mutableFloatStateOf(ink.pageWidth) }
        var height by remember { mutableFloatStateOf(ink.height.coerceAtMost(20000f)) }
        var paper by remember {mutableIntStateOf(ink.paperColor)}
        AlertDialog(onDismissRequest = { state.pageSettings = false }, title = { Text("Hoja y zoom") }, text = {
            Column {
                Text("Ancho de la hoja: ${width.toInt()}")
                Slider(width, { width = it }, valueRange = 600f..2400f, modifier = Modifier.semantics { contentDescription = "Ancho de hoja" })
                Text("Altura de la hoja: ${height.toInt()}")
                Slider(height, { height = it }, valueRange = 1000f..20000f, modifier = Modifier.semantics { contentDescription = "Altura de hoja" })
                Text("Color de papel")
                Row {listOf(0xffffffff,0xfffff8e1,0xffe8f5e9,0xffe3f2fd,0xfff3e5f5).forEach {value->Box(Modifier.padding(4.dp).size(30.dp).border(if(paper==value.toInt())3.dp else 1.dp,MaterialTheme.colorScheme.outline,CircleShape).background(Color(value),CircleShape).clickable {paper=value.toInt()})}}
                Text("Zoom: ${(state.zoom * 100).toInt()}%")
                Slider(state.zoom, { state.zoom = it }, valueRange = .5f..3f, modifier = Modifier.semantics { contentDescription = "Zoom de la hoja" })
                TextButton(onClick = { state.zoom = (InkDocument.PAGE_WIDTH / ink.pageWidth).coerceIn(.5f, 3f) }) { Text("Ajustar al ancho") }
                Text("La hoja conserva todos los trazos. Usa Mover para desplazarte por las zonas ampliadas.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { onInkChange(ink.resizePage(width, height).copy(paperColor=paper)); state.pageSettings = false }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { state.pageSettings = false }) { Text("Cerrar") } })
    }
    if (state.rules) {
        var spacing by remember { mutableFloatStateOf(ink.spacing) }
        AlertDialog(onDismissRequest = { state.rules = false }, title = { Text("Renglones") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Toda la página", Modifier.weight(1f)); Switch(ink.wholePageRuled, { onInkChange(ink.copy(wholePageRuled = it)) },
                        Modifier.semantics { contentDescription = "Renglones en toda la página" }) }
                Text("Separación: ${spacing.toInt()}")
                Slider(spacing, { spacing = it }, onValueChangeFinished = { onInkChange(ink.copy(spacing = spacing)) }, valueRange = 40f..100f, steps = 5,
                    modifier = Modifier.semantics { contentDescription = "Separación de renglones" })
                OutlinedButton(onClick = { if (ink.wholePageRuled) onInkChange(ink.copy(wholePageRuled = false)); state.rules = false; choose(InkTool.RULE_AREA) }) {
                    Text("Elegir una zona de la página") }
                if (ink.areas.isNotEmpty()) TextButton(onClick = { onInkChange(ink.copy(areas = emptyList())) }) { Text("Quitar renglones de las zonas (${ink.areas.size})") }
            }
        }, confirmButton = { TextButton(onClick = { state.rules = false }) { Text("Listo") } })
    }
}

@Composable
private fun TextOptions(style: com.eliadca.talks.core.notes.NoteTextStyle,
    onFormat: ((com.eliadca.talks.core.notes.NoteTextStyle) -> com.eliadca.talks.core.notes.NoteTextStyle) -> Unit, onClose: () -> Unit) {
    val palette = listOf(0xff232b3c, 0xff1565c0, 0xff00897b, 0xff7b1fa2, 0xffe53935, 0xfffbc02d).map { it.toInt() }
    AlertDialog(onDismissRequest = onClose, title = { Text("Formato de texto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tamaño: ${style.size.toInt()}")
            Slider(style.size, { size -> onFormat { it.copy(size = size) } }, valueRange = 28f..96f,
                modifier = Modifier.semantics { contentDescription = "Tamaño del texto" })
            Text("Color del texto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { palette.forEach { color ->
                Box(Modifier.size(32.dp).background(Color(color), CircleShape).clickable { onFormat { it.copy(color = color) } }
                    .semantics { contentDescription = "Texto #%06X".format(color and 0xffffff) }) } }
            Text("Resaltado")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { palette.drop(1).forEach { color ->
                Box(Modifier.size(32.dp).background(Color(color).copy(alpha = .28f), CircleShape).clickable {
                    onFormat { it.copy(highlight = (color and 0xffffff) or 0x44000000) }
                }.semantics { contentDescription = "Resaltar #%06X".format(color and 0xffffff) }) } }
            TextButton(onClick = { onFormat { it.copy(highlight = null) } }) { Text("Quitar resaltado") }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Tachado", Modifier.weight(1f)); Switch(style.strike, { value -> onFormat { it.copy(strike = value) } }) }
            TextButton(onClick = { onFormat { com.eliadca.talks.core.notes.NoteTextStyle() } }) { Text("Quitar formato") }
            Text("Selecciona texto para darle formato, o elige un estilo para lo que vas a escribir.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Listo") } })
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
    onInkChange: (InkDocument) -> Unit, state: NoteEditorState, onReference: (BibleReference) -> Unit,
    onReferenceAt: ((BibleReference, androidx.compose.ui.geometry.Rect) -> Unit)?, onAnchorChanged: (androidx.compose.ui.geometry.Rect) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    var textHeight by remember { mutableFloatStateOf(0f) }
    val ruleColor = MaterialTheme.colorScheme.primary.toArgb()
    BoxWithConstraints(modifier.pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val fingers = event.changes.filter { it.pressed && it.type == PointerType.Touch }
                if (fingers.size >= 2 && event.changes.none { it.type == PointerType.Stylus || it.type == PointerType.Eraser }) {
                    state.zoom = (state.zoom * event.calculateZoom()).coerceIn(.5f, 3f)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }) {
        val fitWidth = (maxWidth - 24.dp).coerceAtLeast(100.dp)
        // Increasing paper width adds writing space without shrinking existing writing.
        val factor = minOf(fitWidth.value, 700f) / InkDocument.PAGE_WIDTH * state.zoom
        val paperWidth = (ink.pageWidth * factor).dp
        val frame = ink.bodyFrame ?: NoteTextFrame(width = ink.pageWidth - 80f)
        val displayed = state.preview ?: ink
        val shownFrame = displayed.bodyFrame ?: frame
        val measured = displayed.copy(bodyFrame = if(body.text.isEmpty())null else frame.copy(height = maxOf(100f, textHeight)))
        val worldHeight = max(ink.height, shownFrame.y + textHeight + 300)
        val pageHeight = (worldHeight * factor).dp
        val scrollPrefs=rememberNoteWorkspacePreferences()
        val vertical = rememberScrollState(); val horizontal = rememberScrollState()
        LaunchedEffect(vertical,horizontal) {
            kotlinx.coroutines.delay(150)
            vertical.scrollTo(scrollPrefs.getInt("scroll-y",0));horizontal.scrollTo(scrollPrefs.getInt("scroll-x",0))
            snapshotFlow {vertical.value to horizontal.value}.collect {(y,x)->scrollPrefs.edit().putInt("scroll-y",y).putInt("scroll-x",x).apply()}
        }
        Column(Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal).widthIn(min = maxWidth).testTag("note-page-scroll"), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(12.dp).width(paperWidth).height(pageHeight).background(Color(ink.paperColor))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                val writing = state.tool !in listOf(InkTool.TEXT,InkTool.MOVE)
                @Composable fun inkLayer(input:Boolean) {
                    AndroidView(factory = { InkPageView(it) }, modifier = Modifier.fillMaxSize().testTag(if(input || !writing)"ink-page" else "ink-artwork"), update = {
                        it.document = if(input)ink.copy(bodyFrame=measured.bodyFrame) else measured; it.drawContent = !input; it.drawTextBoxes = false; it.onPreview = { preview -> state.preview = preview }; it.tool = if(input)state.tool else InkTool.MOVE; it.shape = state.shape; it.pen = state.pen; it.penColor = state.color; it.penWidth = state.width
                        it.eraserMode = state.eraserMode; it.eraserRadius = state.eraserRadius; it.usePressure = state.pressure; it.stylusOnly = state.stylusOnly; it.ruleColor = ruleColor
                        it.selection = state.selected; it.onSelectionChange = { selected -> state.selected = selected }
                        it.onChange = onInkChange; it.onAreaFinished = { state.tool = InkTool.PEN }
                    })
                }
                inkLayer(false)
                if(InkDocument.MAIN_TEXT_ID !in ink.hiddenIds) BibleLinkedTextField(body, onEdit, readOnly = state.tool != InkTool.TEXT || InkDocument.MAIN_TEXT_ID in ink.lockedIds, linksEnabled = !writing, onReference = onReference,
                    onReferenceAt = onReferenceAt, onAnchorChanged = onAnchorChanged, formatting = ink.formatting, textScale = factor * shownFrame.scale,
                    modifier = Modifier.offset((shownFrame.x * factor).dp, (shownFrame.y * factor).dp).width((shownFrame.width * factor).dp).onFocusChanged {if(it.isFocused)state.activeBox=null}.testTag("note-body"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (46 * factor * shownFrame.scale).sp,
                        lineHeight = (64 * factor * shownFrame.scale).sp, color = Color(0xff232b3c)),
                    onTextLayout = { result -> textHeight = result.multiParagraph.height / density.density / factor },
                    decorationBox = { field -> Box {
                        if (body.text.isEmpty() && ink.strokes.isEmpty() && state.tool == InkTool.TEXT) Text(
                            "Escribe tus ideas o toca el lápiz para escribir a mano…",
                            color = Color(0xff737b88), style = MaterialTheme.typography.bodyMedium)
                        field()
                    } })
                displayed.textBoxes.filterNot {it.id in ink.hiddenIds}.forEach { box ->
                    key(box.id) {
                        var value by remember { mutableStateOf(TextFieldValue(box.text)) }
                        LaunchedEffect(box.text) { if (value.text != box.text) value = TextFieldValue(box.text) }
                        BibleLinkedTextField(value, { next ->
                            value = next;state.activeBox=box.id;state.boxSelection=next.selection
                            if (next.text != box.text) onInkChange(ink.copy(textBoxes = ink.textBoxes.map {
                                if (it.id == box.id) it.copy(text = next.text, formatting = it.formatting.replace(it.text, next.text),height=maxOf(it.height,next.text.lines().sumOf {row->maxOf(1,(row.length*it.size*.6f/it.width).toInt()+1)}*it.size*1.4f)) else it
                            }))
                        }, readOnly = state.tool != InkTool.TEXT || box.id in ink.lockedIds, linksEnabled = !writing, onReference = onReference,
                            onReferenceAt = onReferenceAt, onAnchorChanged = onAnchorChanged, formatting = box.formatting,
                            textScale = factor * box.size / 46f,
                            modifier = Modifier.offset((box.x * factor).dp, (box.y * factor).dp).width((box.width * factor).dp).onFocusChanged {if(it.isFocused) {state.activeBox=box.id;state.boxSelection=value.selection}}.testTag("text-box-${box.id}"),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (box.size * factor).sp,
                                lineHeight = (box.size * factor * 1.4f).sp, color = Color(box.color)), onTextLayout = {})
                    }
                }
                if (writing) inkLayer(true)
            }
            OutlinedButton(onClick = { onInkChange(ink.copy(height = worldHeight + 1000)) }, modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Añadir espacio a la página")
            }
        }
    }
}
