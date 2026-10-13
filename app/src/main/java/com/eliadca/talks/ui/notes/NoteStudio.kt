package com.eliadca.talks.ui.notes

import android.graphics.Path
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliadca.talks.core.ink.*
import com.eliadca.talks.core.notes.*
import kotlin.math.abs

internal enum class StudioTab { PROPERTIES, LAYERS, LIBRARIES }

/** Editor-only theme. The rest of Talks keeps its existing theme. */
@Composable
internal fun NoteStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary=Color(0xff82b5ff), onPrimary=Color(0xff102b4b), primaryContainer=Color(0xff294766),
        surface=Color(0xff303238), onSurface=Color(0xffe8e9ed), onSurfaceVariant=Color(0xffbfc2cb),
        surfaceContainerLow=Color(0xff282a30), surfaceContainer=Color(0xff34373e),
        surfaceContainerHigh=Color(0xff40434c), surfaceContainerHighest=Color(0xff484c56),
        background=Color(0xff202228), outline=Color(0xff737781), outlineVariant=Color(0xff474a53),
        secondaryContainer=Color(0xff3b465b), onSecondaryContainer=Color(0xffdde8ff)
    ), content=content)
}

/** All text surfaces share one routing rule, including selected movable text. */
internal class StudioActions(
    val style: NoteTextStyle,
    val format: ((NoteTextStyle)->NoteTextStyle)->Unit,
    val align: (Int)->Unit,
    val list: (Boolean)->Unit,
    val editable: Boolean,
)

internal fun studioActions(state: NoteEditorState, body: TextFieldValue, ink: InkDocument,
    onChange:(InkDocument)->Unit, textStyle: NoteTextStyle,
    onFormat:((NoteTextStyle)->NoteTextStyle)->Unit, onAlign:(Int)->Unit, onList:(Boolean)->Unit,
    context:android.content.Context?=null): StudioActions {
    val selectedBox=ink.textBoxes.singleOrNull {it.id in state.selected}
    val box=if(state.tool in listOf(InkTool.SELECT,InkTool.LASSO,InkTool.NODES)) selectedBox
        else ink.textBoxes.firstOrNull {it.id==state.activeBox}
    val entire=box!=null && box.id in state.selected && state.tool!=InkTool.TEXT
    val mainSelected=InkDocument.MAIN_TEXT_ID in state.selected
    val scale=box?.let {it.size/46f} ?: if(mainSelected) (ink.bodyFrame?.scale ?: 1f) else 1f
    val style=box?.formatting?.styleAt(if(entire)0 else state.boxSelection.min)
        ?: if(mainSelected)ink.formatting.styleAt(0) else textStyle
    val editable=(box?.id ?: InkDocument.MAIN_TEXT_ID) !in ink.lockedIds &&
        (state.selected.isEmpty() || mainSelected || box!=null || state.tool==InkTool.TEXT)
    return StudioActions(style.copy(size=style.size*scale), {transform->
        if(editable) {
            fun adjusted(s:NoteTextStyle)=transform(s.copy(size=s.size*scale)).let {it.copy(size=it.size/scale)}
            if(box!=null) {
                val start=if(entire || state.boxSelection.collapsed)0 else state.boxSelection.min
                val end=if(entire || state.boxSelection.collapsed)box.text.length else state.boxSelection.max
                val formatting=box.formatting.apply(start,end,::adjusted)
                val height=maxOf(box.height,NoteArtworkExport.layout(box.text,formatting,box.width,box.size,box.color,box.size/46f,context).height+8f)
                onChange(ink.copy(textBoxes=ink.textBoxes.map {if(it.id==box.id)it.copy(formatting=formatting,height=height) else it},height=maxOf(ink.height,box.y+height+100f)))
            } else if(mainSelected) {
                val formatting=ink.formatting.apply(0,body.text.length,::adjusted)
                val frame=ink.bodyFrame ?: NoteTextFrame(width=ink.pageWidth-80f)
                val height=NoteArtworkExport.layout(body.text,formatting,frame.width,46f,0xff232b3c.toInt(),frame.scale,context).height.toFloat()
                onChange(ink.copy(formatting=formatting,bodyFrame=frame.copy(height=height),height=maxOf(ink.height,frame.y+height+100f)))
            }
            else onFormat(transform)
        }
    }, {alignment->if(editable) {
        if(box==null) {if(mainSelected)onChange(ink.copy(formatting=ink.formatting.align(body.text,0,body.text.length,alignment))) else onAlign(alignment)}
        else onChange(ink.copy(textBoxes=ink.textBoxes.map {if(it.id==box.id)it.copy(formatting=it.formatting.align(it.text,if(entire)0 else state.boxSelection.min,if(entire)it.text.length else state.boxSelection.max,alignment)) else it}))
    }}, {checklist->if(editable) {
        if(box==null)onList(checklist) else {
            val start=if(entire)0 else box.text.lastIndexOf('\n',(state.boxSelection.min-1).coerceAtLeast(-1))+1
            val end=if(entire)box.text.length else box.text.indexOf('\n',state.boxSelection.max).takeIf {it>=0} ?: box.text.length
            val prefix=if(checklist)"☐ " else "• ";val rows=box.text.substring(start,end).lines();val remove=rows.all {it.startsWith(prefix)}
            val text=box.text.replaceRange(start,end,rows.joinToString("\n") {if(remove)it.removePrefix(prefix) else prefix+it})
            onChange(ink.copy(textBoxes=ink.textBoxes.map {if(it.id==box.id)it.copy(text=text,formatting=it.formatting.replace(it.text,text)) else it}))
        }
    }}, editable)
}

@Composable
internal fun NoteStudio(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit,
    canUndo:Boolean,canRedo:Boolean,onUndo:()->Unit,onRedo:()->Unit,modifier:Modifier,
    onFormat:((NoteTextStyle)->NoteTextStyle)->Unit,textStyle:NoteTextStyle,onAlign:(Int)->Unit,onList:(Boolean)->Unit,
    canUndoText:Boolean,canRedoText:Boolean,onUndoText:()->Unit,onRedoText:()->Unit,
    onMoveTextSelection:()->Unit={}, crowded:Boolean=false, canvas:@Composable (Modifier)->Unit) {
    NoteStudioTheme {
        val actions=studioActions(state,body,ink,onChange,textStyle,onFormat,onAlign,onList,LocalContext.current)
        Column(modifier.background(MaterialTheme.colorScheme.background).testTag("note-studio")) {
            StudioMenuBar(state,body,ink,onChange)
            NoteEditorToolbar(state,body,ink,onChange,canUndo,canRedo,onUndo,onRedo,onFormat,textStyle,onAlign,onList,
                canUndoText,canRedoText,onUndoText,onRedoText,onMoveTextSelection)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val dockInspector=maxWidth>=960.dp && !crowded
                LaunchedEffect(Unit) {if(maxWidth<960.dp || crowded)state.inspectorOpen=false}
                LaunchedEffect(crowded) {if(crowded)state.inspectorOpen=false}
                val paletteWidth=if(maxWidth<600.dp)56.dp else 112.dp
                Row(Modifier.fillMaxSize()) {
                    if(state.toolbarSide) StudioPalette(state,body,ink,onChange,Modifier.width(paletteWidth).fillMaxHeight(),paletteWidth>60.dp)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if(!state.toolbarSide) StudioPalette(state,body,ink,onChange,Modifier.fillMaxWidth().height(56.dp),false,horizontal=true)
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("studio-canvas")) {
                            canvas(Modifier.fillMaxSize())
                            if(state.selected.isNotEmpty() && state.tool in listOf(InkTool.SELECT,InkTool.NODES,InkTool.LASSO))
                                SelectionTaskbar(state,ink,onChange,Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp))
                        }
                        StudioStatusBar(state,ink)
                    }
                    if(dockInspector && state.inspectorOpen) StudioInspector(state,body,ink,onChange,actions,Modifier.width(296.dp).fillMaxHeight())
                }
                if(!dockInspector && state.inspectorOpen) {
                    // Opening a tool on smaller screens reveals a drawer, without squeezing the page.
                    StudioInspector(state,body,ink,onChange,actions,Modifier.align(Alignment.CenterEnd).width(minOf(296.dp,maxWidth-paletteWidth)).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun StudioMenuBar(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val context=LocalContext.current
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().height(40.dp).horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Filled.EditNote,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Text("NOTAS",Modifier.padding(start=7.dp,end=18.dp),fontWeight=FontWeight.SemiBold,fontSize=12.sp,letterSpacing=1.sp)
            StudioMenu("Hoja") {
                MenuAction("Plantillas de hojas") {state.templates=true}
                MenuAction("Tamaño y papel") {state.inspectPage=true;state.inspectorTab=StudioTab.PROPERTIES;state.inspectorOpen=true}
                MenuAction("Renglones por sección") {state.rules=true}
                MenuAction("Cuadrícula y ajuste magnético") {state.grid=true}
            }
            StudioMenu("Editar") {
                MenuAction("Copiar nota completa",body.text.isNotBlank() || ink.textBoxes.isNotEmpty()) {
                    com.eliadca.talks.export.Clipboard.copy(context,"Nota",(listOf(body.text)+ink.textBoxes.sortedBy {it.y}.map {it.text}).filter {it.isNotBlank()}.joinToString("\n\n"))
                }
                MenuAction("Quitar selección",state.selected.isNotEmpty()) {state.selected=emptySet()}
            }
            StudioMenu("Objeto") {
                MenuAction("Transformación precisa",state.selected.isNotEmpty()) {state.inspectPage=false;state.inspectorOpen=true;state.inspectorTab=StudioTab.PROPERTIES}
                MenuAction("Agrupar",state.selected.size>1) {onChange(ink.groupSelection(state.selected))}
                MenuAction("Desagrupar",state.selected.isNotEmpty()) {onChange(ink.ungroupSelection(state.selected))}
                MenuAction("Traer adelante",state.selected.any {it!=InkDocument.MAIN_TEXT_ID}) {onChange(ink.reorderInk(state.selected,true))}
                MenuAction("Enviar atrás",state.selected.any {it!=InkDocument.MAIN_TEXT_ID}) {onChange(ink.reorderInk(state.selected,false))}
            }
            StudioMenu("Vista") {
                MenuAction("${if(state.toolbarSide)"Herramientas arriba" else "Herramientas a la izquierda"}") {state.toolbarSide=!state.toolbarSide}
                MenuAction("${if(state.inspectorOpen)"Ocultar" else "Mostrar"} inspector") {state.inspectorOpen=!state.inspectorOpen}
                MenuAction("Capas") {state.inspectorTab=StudioTab.LAYERS;state.inspectorOpen=true}
                MenuAction("Zoom 100 %") {state.zoom=1f}
            }
            Text("/  Estudio",Modifier.padding(start=20.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val LocalCloseStudioMenu=staticCompositionLocalOf<()->Unit> {{}}
@Composable private fun StudioMenu(label:String,content:@Composable ()->Unit) {
    var open by remember {mutableStateOf(false)}
    Box {TextButton(onClick={open=true},contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) {Text(label,fontSize=12.sp)}
        DropdownMenu(open,{open=false}) {CompositionLocalProvider(LocalCloseStudioMenu provides {open=false}) {content()}}}
}
@Composable private fun MenuAction(label:String,enabled:Boolean=true,onClick:()->Unit) {
    val close=LocalCloseStudioMenu.current
    DropdownMenuItem(text={Text(label)},onClick={onClick();close()},enabled=enabled)
}

@Composable
internal fun StudioControlBar(state:NoteEditorState,actions:StudioActions,body:TextFieldValue,ink:InkDocument,
    canUndo:Boolean,canRedo:Boolean,onUndo:()->Unit,onRedo:()->Unit,
    canUndoText:Boolean,canRedoText:Boolean,onUndoText:()->Unit,onRedoText:()->Unit,onMoveTextSelection:()->Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    fun choose(tool:InkTool) {state.tool=tool;state.selected=emptySet();state.inspectPage=false;state.inspectorTab=StudioTab.PROPERTIES;if(tool!=InkTool.TEXT)keyboard?.hide()}
    Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).heightIn(min=56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            StudioIcon(Icons.Filled.Keyboard,"Modo teclado",state.tool==InkTool.TEXT) {choose(InkTool.TEXT)}
            StudioIcon(Icons.Filled.Edit,"Modo lápiz",state.tool==InkTool.PEN) {choose(InkTool.PEN);state.inspectorOpen=true}
            StudioSeparator()
            StudioIcon(Icons.AutoMirrored.Filled.Undo,if(state.tool==InkTool.TEXT)"Deshacer texto" else "Deshacer escritura",enabled=if(state.tool==InkTool.TEXT)canUndoText else canUndo) {if(state.tool==InkTool.TEXT)onUndoText() else onUndo()}
            StudioIcon(Icons.AutoMirrored.Filled.Redo,if(state.tool==InkTool.TEXT)"Rehacer texto" else "Rehacer escritura",enabled=if(state.tool==InkTool.TEXT)canRedoText else canRedo) {if(state.tool==InkTool.TEXT)onRedoText() else onRedo()}
            StudioSeparator()
            if(state.tool==InkTool.TEXT || ink.textBoxes.any {it.id in state.selected} || InkDocument.MAIN_TEXT_ID in state.selected) {
                FontPicker(actions.style.font,actions.editable) {font->actions.format {it.copy(font=font)}}
                Spacer(Modifier.width(8.dp))
                StudioNumber("Tamaño del texto",actions.style.size,1f..240f,Modifier.width(66.dp),enabled=actions.editable) {size->actions.format {it.copy(size=size)}}
                StudioIcon(Icons.Filled.Remove,"Reducir tamaño de texto",enabled=actions.editable) {actions.format {it.copy(size=(it.size-2).coerceAtLeast(1f))}}
                StudioIcon(Icons.Filled.Add,"Aumentar tamaño de texto",enabled=actions.editable) {actions.format {it.copy(size=(it.size+2).coerceAtMost(240f))}}
                StudioSeparator()
                StudioIcon(Icons.Filled.FormatBold,"Negrita",actions.style.bold,actions.editable) {actions.format {it.copy(bold=!actions.style.bold)}}
                StudioIcon(Icons.Filled.FormatItalic,"Cursiva",actions.style.italic,actions.editable) {actions.format {it.copy(italic=!actions.style.italic)}}
                StudioIcon(Icons.Filled.FormatUnderlined,"Subrayado",actions.style.underline,actions.editable) {actions.format {it.copy(underline=!actions.style.underline)}}
                if(state.tool==InkTool.TEXT && state.activeBox==null && !body.selection.collapsed)
                    TextButton({onMoveTextSelection();state.tool=InkTool.SELECT;state.selected=setOf(ink.nextId);state.inspectorOpen=true;keyboard?.hide()}) {Text("Mover selección")}
            } else {
                Text(toolTitle(state),Modifier.padding(horizontal=12.dp),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                if(state.tool in listOf(InkTool.PEN,InkTool.SHAPE)) {
                    ColorWell("Color del lápiz",state.color) {state.inspectorOpen=true;state.inspectorTab=StudioTab.PROPERTIES;state.inspectPage=false}
                    Text("${number(state.width)} · grosor",Modifier.padding(horizontal=12.dp).clickable {state.inspectorOpen=true},fontSize=12.sp)
                } else if(state.tool==InkTool.ERASER) Text("${if(state.eraserMode==EraserMode.PARTIAL)"Parcial" else "Trazo completo"} · ${number(state.eraserRadius*2)}",fontSize=12.sp)
            }
            StudioSeparator()
            StudioIcon(Icons.Filled.AutoAwesomeMosaic,"Plantillas de hojas") {state.templates=true}
            StudioIcon(Icons.Filled.ViewSidebar,"Ubicar herramientas arriba o a la izquierda",state.toolbarSide) {state.toolbarSide=!state.toolbarSide}
            StudioIcon(Icons.Filled.Tune,"Mostrar u ocultar inspector",state.inspectorOpen) {state.inspectorOpen=!state.inspectorOpen;state.inspectPage=false}
        }
    }
}

@Composable
internal fun FontPicker(font:NoteFont,enabled:Boolean=true,onFont:(NoteFont)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box {
        Surface(shape=RoundedCornerShape(4.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.width(152.dp).height(44.dp).clickable(enabled=enabled) {open=true}.padding(horizontal=10.dp)
                .semantics {contentDescription="Familia tipográfica"},verticalAlignment=Alignment.CenterVertically) {
                Text(NoteFonts.label(font),Modifier.weight(1f),fontFamily=NoteFonts.family(font),fontSize=15.sp)
                Icon(Icons.Filled.ArrowDropDown,null,Modifier.size(18.dp))
            }
        }
        DropdownMenu(open,{open=false},Modifier.width(272.dp)) {
            Text("TIPOGRAFÍA",Modifier.padding(16.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            NoteFont.entries.forEach {item->DropdownMenuItem(text={Column {Text(NoteFonts.label(item),fontFamily=NoteFonts.family(item),fontSize=19.sp);Text("Aa · Ideas que inspiran 123",fontFamily=NoteFonts.family(item),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
                trailingIcon={if(item==font)Icon(Icons.Filled.Check,null)},onClick={onFont(item);open=false})}
        }
    }
}

internal fun number(value:Float)=if(abs(value-value.toInt())<.01f)value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT,value)

@Composable
internal fun StudioNumber(label:String,value:Float,range:ClosedFloatingPointRange<Float>,modifier:Modifier=Modifier,enabled:Boolean=true,onValue:(Float)->Unit) {
    var edit by remember(value) {mutableStateOf(number(value))}
    val keyboard=LocalSoftwareKeyboardController.current
    var hadFocus by remember {mutableStateOf(false)}
    fun commit(hideKeyboard:Boolean=true) {val n=edit.replace(',','.').toFloatOrNull();if(n!=null && n.isFinite() && n in range && n!=value)onValue(n) else edit=number(value);if(hideKeyboard)keyboard?.hide()}
    Surface(modifier,shape=RoundedCornerShape(4.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        BasicTextField(edit,{edit=it},Modifier.height(44.dp).padding(horizontal=10.dp).onFocusChanged {if(hadFocus && !it.isFocused)commit(false);hadFocus=it.isFocused}.semantics {contentDescription=label},enabled=enabled,singleLine=true,
            textStyle=MaterialTheme.typography.bodyMedium.copy(color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={commit()}),
            decorationBox={field->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterStart) {field()}})
    }
}

@Composable private fun StudioSeparator() {VerticalDivider(Modifier.height(24.dp).padding(horizontal=6.dp))}

@Composable
internal fun StudioIcon(icon:ImageVector,label:String,selected:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    IconButton(onClick,Modifier.size(44.dp).background(if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(5.dp)),enabled=enabled) {
        Icon(icon,label,Modifier.size(22.dp),tint=if(!enabled)MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.35f) else if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StudioPalette(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit,modifier:Modifier,twoColumns:Boolean,horizontal:Boolean=false) {
    val keyboard=LocalSoftwareKeyboardController.current
    fun choose(tool:InkTool) {state.tool=tool;state.selected=emptySet();state.inspectPage=false;state.inspectorOpen=true;state.inspectorTab=StudioTab.PROPERTIES;if(tool!=InkTool.TEXT)keyboard?.hide()}
    @Composable fun group(label:String,content:@Composable ()->Unit) {
        if(horizontal) Row(verticalAlignment=Alignment.CenterVertically) {content();StudioSeparator()}
        else Column(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(label.uppercase(),Modifier.padding(bottom=5.dp),fontSize=9.sp,letterSpacing=.6.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(),maxItemsInEachRow=if(twoColumns)2 else 1,horizontalArrangement=Arrangement.Center) {content()}
            HorizontalDivider(Modifier.padding(horizontal=10.dp,vertical=5.dp))
        }
    }
    @Composable fun content() {
        group("Selección") {
            StudioIcon(Icons.Filled.NearMe,"Seleccionar texto y trazos",state.tool==InkTool.SELECT) {choose(InkTool.SELECT)}
            StudioIcon(Icons.Outlined.NearMe,"Editar puntos del trazado",state.tool==InkTool.NODES) {state.tool=InkTool.NODES;state.inspectorOpen=true;state.inspectPage=false}
            StudioIcon(Icons.Filled.Gesture,"Selección con lazo libre",state.tool==InkTool.LASSO) {choose(InkTool.LASSO)}
            StudioIcon(Icons.Filled.OpenWith,"Mover y ajustar texto") {choose(InkTool.SELECT);state.selected=if(body.text.isNotEmpty())setOf(InkDocument.MAIN_TEXT_ID) else emptySet()}
        }
        group("Dibujo") {
            PenKind.entries.forEach {kind->
                var flyout by remember {mutableStateOf(false)}
                Box {
                    val label=penTitle(kind)
                    Box(Modifier.size(44.dp).background(if(state.tool==InkTool.PEN && state.pen==kind)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(5.dp))
                        .combinedClickable(onClick={state.pen=kind;choose(InkTool.PEN)},onLongClick={state.pen=kind;choose(InkTool.PEN);flyout=true})
                        .semantics {contentDescription=label},contentAlignment=Alignment.Center) {
                        Icon(when(kind){PenKind.BALLPOINT->Icons.Filled.Edit;PenKind.PENCIL->Icons.Filled.Create;PenKind.FOUNTAIN->Icons.Filled.Draw;PenKind.HIGHLIGHTER->Icons.Filled.BorderColor},null,Modifier.size(22.dp),tint=if(state.pen==kind && state.tool==InkTool.PEN)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Filled.ArrowRight,null,Modifier.align(Alignment.BottomEnd).size(10.dp))
                    }
                    DropdownMenu(flyout,{flyout=false}) {PenKind.entries.forEach {p->DropdownMenuItem(text={Text(penTitle(p))},onClick={state.pen=p;choose(InkTool.PEN);flyout=false})}}
                }
            }
            StudioIcon(Icons.Filled.Backspace,"Goma de borrar",state.tool==InkTool.ERASER) {choose(InkTool.ERASER)}
            StudioIcon(Icons.Filled.AutoFixHigh,"Suavizar trazos",enabled=ink.strokes.any {it.id in state.selected}) {onChange(ink.smoothInk(state.selected))}
        }
        group("Formas") {
            var open by remember {mutableStateOf(false)}
            Box {
                StudioIcon(Icons.Filled.CropSquare,"Formas y líneas",state.tool==InkTool.SHAPE) {choose(InkTool.SHAPE);open=true}
                DropdownMenu(open,{open=false}) {NoteShape.entries.forEach {shape->DropdownMenuItem(text={Text(shape.label)},leadingIcon={Icon(shapeIcon(shape),null)},modifier=Modifier.testTag("shape-menu-${shape.name}"),onClick={state.shape=shape;choose(InkTool.SHAPE);open=false})}}
            }
            StudioIcon(Icons.Filled.Circle,"Dibujar elipse",state.tool==InkTool.SHAPE && state.shape==NoteShape.ELLIPSE) {state.shape=NoteShape.ELLIPSE;choose(InkTool.SHAPE)}
            StudioIcon(Icons.Filled.HorizontalRule,"Dibujar línea",state.tool==InkTool.SHAPE && state.shape==NoteShape.LINE) {state.shape=NoteShape.LINE;choose(InkTool.SHAPE)}
            StudioIcon(Icons.Filled.Timeline,"Dibujar curva Bézier",state.tool==InkTool.SHAPE && state.shape==NoteShape.BEZIER) {state.shape=NoteShape.BEZIER;choose(InkTool.SHAPE)}
        }
        group("Texto") {
            StudioIcon(Icons.Filled.Title,"Editar texto",state.tool==InkTool.TEXT) {choose(InkTool.TEXT)}
            StudioIcon(Icons.Filled.TextFields,"Añadir cuadro de texto móvil") {state.textBoxEditor=true}
        }
        group("Página") {
            NoteImageButton(state,ink,onChange)
            StudioIcon(Icons.Filled.FormatLineSpacing,"Renglones") {state.rules=true}
            StudioIcon(Icons.Filled.AspectRatio,"Tamaño de hoja y zoom") {state.inspectPage=true;state.inspectorOpen=true;state.inspectorTab=StudioTab.PROPERTIES}
            StudioIcon(Icons.Filled.PanTool,"Mover la página",state.tool==InkTool.MOVE) {choose(InkTool.MOVE)}
        }
    }
    @Composable fun colorControls() {
            Box(Modifier.fillMaxWidth().height(if(horizontal)44.dp else 60.dp).clickable {state.inspectorOpen=true;state.inspectPage=false;state.inspectorTab=StudioTab.PROPERTIES}.semantics {contentDescription="Color y apariencia del trazo"},contentAlignment=Alignment.Center) {
                Box(Modifier.offset(10.dp,9.dp).size(34.dp).border(3.dp,Color(state.color)).background(MaterialTheme.colorScheme.surface))
                Box(Modifier.offset((-6).dp,(-5).dp).size(34.dp).border(1.dp,MaterialTheme.colorScheme.outline).background(Color(state.color)))
            }
    }
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        if(horizontal) Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {content();Box(Modifier.width(60.dp)) {colorControls()}}
        else Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {content()}
            HorizontalDivider(Modifier.padding(horizontal=10.dp));colorControls()
        }
    }
}

private fun shapeIcon(shape:NoteShape)=when(shape) {NoteShape.ELLIPSE->Icons.Filled.Circle;NoteShape.LINE->Icons.Filled.HorizontalRule;NoteShape.BEZIER->Icons.Filled.Timeline;else->Icons.Filled.CropSquare}
internal fun penTitle(pen:PenKind)=when(pen) {PenKind.BALLPOINT->"Bolígrafo";PenKind.PENCIL->"Lápiz";PenKind.FOUNTAIN->"Pluma";PenKind.HIGHLIGHTER->"Resaltador"}
private fun toolTitle(state:NoteEditorState)=when(state.tool) {InkTool.TEXT->"Texto";InkTool.PEN->penTitle(state.pen);InkTool.ERASER->"Goma de borrar";InkTool.SELECT->"Selección";InkTool.LASSO->"Lazo libre";InkTool.NODES->"Puntos del trazado";InkTool.MOVE->"Desplazar página";InkTool.SHAPE->state.shape.label;InkTool.RULE_AREA->"Zona de renglones"}

@Composable
private fun StudioInspector(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit,actions:StudioActions,modifier:Modifier) {
    Surface(modifier.testTag("studio-inspector"),color=MaterialTheme.colorScheme.surface,shadowElevation=8.dp) {
        Column {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start=8.dp),verticalAlignment=Alignment.CenterVertically) {
                StudioTab.entries.forEach {tab->TextButton(onClick={state.inspectorTab=tab},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.weight(1f)) {
                    Text(when(tab){StudioTab.PROPERTIES->"Propiedades";StudioTab.LAYERS->"Capas";StudioTab.LIBRARIES->"Biblioteca"},fontSize=11.sp,color=if(state.inspectorTab==tab)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }}
                IconButton({state.inspectorOpen=false},Modifier.size(36.dp)) {Icon(Icons.Filled.Close,"Cerrar inspector",Modifier.size(18.dp))}
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(state.inspectorTab) {
                    StudioTab.LAYERS->StudioLayers(state,body,ink,onChange)
                    StudioTab.LIBRARIES->{
                        InspectorSection("Plantillas de hojas") {
                            Text("Diseños de papel, estudio, agenda y tus propias hojas.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            TemplateMiniGallery {state.templates=true}
                            Button({state.templates=true},Modifier.fillMaxWidth()) {Text("Explorar plantillas")}
                        }
                        InspectorSection("Recursos") {NoteImageButton(state,ink,onChange);Text("Las imágenes se guardan dentro de la nota y también pueden formar parte de tus plantillas.",fontSize=12.sp)}
                    }
                    StudioTab.PROPERTIES->{
                        Text(if(state.inspectPage)"Hoja" else if(state.selected.size==1)"1 elemento seleccionado" else if(state.selected.isNotEmpty())"${state.selected.size} elementos seleccionados" else toolTitle(state),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        if(state.inspectPage) PageInspector(state,ink,onChange)
                        else {
                            if(state.selected.isNotEmpty()) TransformInspector(state,body,ink,onChange)
                            if(state.tool==InkTool.TEXT || ink.textBoxes.any {it.id in state.selected} || InkDocument.MAIN_TEXT_ID in state.selected) TextInspector(actions)
                            if(state.tool==InkTool.ERASER) EraserInspector(state)
                            if(state.tool in listOf(InkTool.PEN,InkTool.SHAPE,InkTool.RULE_AREA)) PenInspector(state)
                            if(ink.strokes.any {it.id in state.selected}) AppearanceInspector(state,ink,onChange)
                            if(ink.images.any {it.id in state.selected}) ImageInspector(state,ink,onChange)
                            if(state.tool==InkTool.SHAPE) InspectorSection("Forma") {
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {NoteShape.entries.forEach {shape->FilterChip(state.shape==shape,{state.shape=shape},label={Text(shape.label,fontSize=11.sp)})}}
                                Text("Arrastra sobre la hoja para dibujar. Para ajustar la forma, cambia a Selección o a Puntos.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(state.tool in listOf(InkTool.SELECT,InkTool.NODES,InkTool.LASSO) && state.selected.isEmpty()) Text("Selecciona texto, una imagen o un trazo. Arrastra para mover y usa los puntos del marco para ajustar el tamaño.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(state.tool==InkTool.NODES) Text("Arrastra los puntos del trazado. En una curva Bézier, los puntos intermedios controlan la curvatura.",fontSize=12.sp)
                            if(state.tool==InkTool.MOVE) {
                                InspectorSection("Navegación") {Text("Arrastra para desplazarte. Usa dos dedos para ampliar o reducir.",fontSize=12.sp);ZoomControls(state)}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InspectorSection(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(),fontSize=10.sp,fontWeight=FontWeight.SemiBold,letterSpacing=.7.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        content();HorizontalDivider(Modifier.padding(top=4.dp))
    }
}

@Composable
private fun TextInspector(actions:StudioActions) {
    InspectorSection("Carácter") {
        Text("Familia tipográfica",fontSize=11.sp)
        // The main bar provides the only font picker; this entry brings it into view on small screens.
        var fontMenu by remember {mutableStateOf(false)}
        Box {OutlinedButton({fontMenu=true},Modifier.fillMaxWidth(),enabled=actions.editable) {Text(NoteFonts.label(actions.style.font),Modifier.weight(1f),fontFamily=NoteFonts.family(actions.style.font));Icon(Icons.Filled.ArrowDropDown,null)}
            DropdownMenu(fontMenu,{fontMenu=false}) {NoteFont.entries.forEach {font->DropdownMenuItem(text={Text(NoteFonts.label(font),fontFamily=NoteFonts.family(font),fontSize=19.sp)},onClick={actions.format {it.copy(font=font)};fontMenu=false})}}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Tamaño",Modifier.weight(1f),fontSize=12.sp)
            StudioNumber("Tamaño en propiedades",actions.style.size,1f..240f,Modifier.width(100.dp),actions.editable) {size->actions.format {it.copy(size=size)}}
        }
        Row {StudioIcon(Icons.Filled.FormatBold,"Negrita en propiedades",actions.style.bold,actions.editable) {actions.format {it.copy(bold=!actions.style.bold)}}
            StudioIcon(Icons.Filled.FormatItalic,"Cursiva en propiedades",actions.style.italic,actions.editable) {actions.format {it.copy(italic=!actions.style.italic)}}
            StudioIcon(Icons.Filled.FormatUnderlined,"Subrayado en propiedades",actions.style.underline,actions.editable) {actions.format {it.copy(underline=!actions.style.underline)}}
            StudioIcon(Icons.Filled.FormatStrikethrough,"Tachado",actions.style.strike,actions.editable) {actions.format {it.copy(strike=!actions.style.strike)}}}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Espacio entre letras",Modifier.weight(1f),fontSize=12.sp);StudioNumber("Espaciado de letras",actions.style.tracking,-4f..20f,Modifier.width(72.dp),actions.editable) {v->actions.format {it.copy(tracking=v)}}}
        Text("Color del texto",fontSize=11.sp);StudioColors(actions.style.color ?: 0xff232b3c.toInt(),"Texto",actions.editable) {color->actions.format {it.copy(color=color)}}
        Text("Resaltado",fontSize=11.sp);StudioColors(actions.style.highlight ?: 0x00000000,"Resaltar",actions.editable) {color->actions.format {it.copy(highlight=(color and 0xffffff) or 0x44000000)}}
        TextButton({actions.format {it.copy(highlight=null)}},enabled=actions.editable) {Text("Quitar resaltado",fontSize=12.sp)}
        TextButton({actions.format {NoteTextStyle()}},enabled=actions.editable) {Text("Quitar formato",fontSize=12.sp)}
    }
    InspectorSection("Párrafo") {
        Row {StudioIcon(Icons.Filled.FormatAlignLeft,"Alinear a la izquierda",enabled=actions.editable) {actions.align(0)}
            StudioIcon(Icons.Filled.FormatAlignCenter,"Centrar texto",enabled=actions.editable) {actions.align(1)}
            StudioIcon(Icons.Filled.FormatAlignRight,"Alinear a la derecha",enabled=actions.editable) {actions.align(2)}}
        Row {StudioIcon(Icons.Filled.FormatListBulleted,"Lista con viñetas",enabled=actions.editable) {actions.list(false)}
            StudioIcon(Icons.Filled.Checklist,"Lista de tareas",enabled=actions.editable) {actions.list(true)}}
        Text("Selecciona palabras para cambiar su estilo. Sin selección, el estilo se aplica a lo que escribas.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PenInspector(state:NoteEditorState) {
    InspectorSection("Pincel y trazo") {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {PenKind.entries.forEach {pen->FilterChip(state.pen==pen,{state.pen=pen},label={Text(penTitle(pen),fontSize=11.sp)})}}
        PenPreview(state)
        Text("Grosor: ${number(state.width)}",fontSize=12.sp)
        Slider(state.width,{state.width=it},valueRange=1f..36f,modifier=Modifier.semantics {contentDescription="Grosor del lápiz"})
        StudioColors(state.color,"Color") {state.color=it}
        HexColorField(state.color) {state.color=it}
        InspectorSwitch("Presión del S Pen",state.pressure) {state.pressure=it}
        InspectorSwitch("Dibujar solo con lápiz",state.stylusOnly) {state.stylusOnly=it}
    }
}

@Composable private fun PenPreview(state:NoteEditorState) {
    Canvas(Modifier.fillMaxWidth().height(62.dp).background(Color(0xfff7f7f5),RoundedCornerShape(5.dp))) {
        val path=androidx.compose.ui.graphics.Path().apply {moveTo(18f,size.height*.65f);cubicTo(size.width*.3f,-size.height*.05f,size.width*.55f,size.height*1.2f,size.width-18f,size.height*.35f)}
        drawPath(path,Color(state.color).copy(alpha=if(state.pen==PenKind.HIGHLIGHTER).35f else 1f),style=androidx.compose.ui.graphics.drawscope.Stroke(state.width*.7f,cap=androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable private fun EraserInspector(state:NoteEditorState) {
    InspectorSection("Goma de borrar") {
        Text("Diámetro: ${number(state.eraserRadius*2)}",fontSize=12.sp)
        Slider(state.eraserRadius,{state.eraserRadius=it},valueRange=6f..65f,modifier=Modifier.semantics {contentDescription="Tamaño de goma"})
        EraserMode.entries.forEach {mode->Row(Modifier.fillMaxWidth().clickable {state.eraserMode=mode},verticalAlignment=Alignment.CenterVertically) {RadioButton(state.eraserMode==mode,{state.eraserMode=mode});Text(if(mode==EraserMode.PARTIAL)"Borrar solo lo que toco" else "Borrar el trazo completo",fontSize=12.sp)}}
        Text("El botón del S Pen también activa la goma. Cada gesto se puede deshacer.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val studioColors=listOf(0xff232b3c,0xff1565c0,0xff00897b,0xff7b1fa2,0xffe53935,0xfffbc02d,0xffff9800,0xffec407a,0xff90a4ae,0xffffffff).map {it.toInt()}
@Composable internal fun StudioColors(value:Int,label:String,enabled:Boolean=true,onColor:(Int)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)) {studioColors.forEach {color->
        Box(Modifier.size(44.dp).clickable(enabled=enabled) {onColor(color)}.semantics {contentDescription="$label #%06X".format(color and 0xffffff)},contentAlignment=Alignment.Center) {
            Box(Modifier.size(27.dp).border(if(value==color)2.dp else 1.dp,if(value==color)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,CircleShape).padding(3.dp).background(Color(color),CircleShape))
        }
    }}
}
@Composable private fun ColorWell(label:String,color:Int,onClick:()->Unit) {Box(Modifier.size(44.dp).clickable(onClick=onClick).semantics {contentDescription=label},contentAlignment=Alignment.Center) {Box(Modifier.size(26.dp).background(Color(color),RoundedCornerShape(4.dp)).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(4.dp)))}}

@Composable private fun HexColorField(color:Int,onColor:(Int)->Unit) {
    var value by remember(color) {mutableStateOf("%06X".format(color and 0xffffff))}
    OutlinedTextField(value,{value=it.filter {c->c.isDigit() || c.lowercaseChar() in 'a'..'f'}.take(6)},label={Text("Color HEX",fontSize=11.sp)},singleLine=true,modifier=Modifier.fillMaxWidth(),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={value.toLongOrNull(16)?.takeIf {value.length==6}?.let {onColor((it or 0xff000000).toInt())}}))
}
@Composable internal fun InspectorSwitch(label:String,value:Boolean,onValue:(Boolean)->Unit) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f),fontSize=12.sp);Switch(value,onValue)}}

@Composable
private fun TransformInspector(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val context=LocalContext.current
    val document=ink.copy(bodyFrame=ink.bodyFrame ?: if(body.text.isNotEmpty())NoteTextFrame(width=ink.pageWidth-80f) else null)
    val bounds=document.selectionBounds(state.selected) ?: return
    val width=(bounds.right-bounds.left).coerceAtLeast(1f);val height=(bounds.bottom-bounds.top).coerceAtLeast(1f)
    val box=ink.textBoxes.singleOrNull {it.id in state.selected && state.selected.size==1}
    val main=state.selected==setOf(InkDocument.MAIN_TEXT_ID)
    var proportional by remember(state.selected) {mutableStateOf(box==null && !main)}
    fun resize(w:Float,h:Float,byWidth:Boolean) {
        if(proportional || box==null && !main) {onChange(document.transformSelection(state.selected,0f,0f,if(byWidth)w/width else h/height));return}
        if(box!=null) {
            val measured=NoteArtworkExport.layout(box.text,box.formatting,w,box.size,box.color,box.size/46f,context).height+8f
            val resized=box.copy(width=w,height=maxOf(h,measured))
            onChange(ink.copy(textBoxes=ink.textBoxes.map {if(it.id==box.id)resized else it},pageWidth=maxOf(ink.pageWidth,box.x+w+40),height=maxOf(ink.height,box.y+resized.height+100)))
        } else {
            val frame=document.bodyFrame ?: return
            val measured=NoteArtworkExport.layout(body.text,ink.formatting,w,46f,0xff232b3c.toInt(),frame.scale,context).height+8f
            val resized=frame.copy(width=w,height=maxOf(h,measured))
            onChange(ink.copy(bodyFrame=resized,pageWidth=maxOf(ink.pageWidth,frame.x+w+40),height=maxOf(ink.height,frame.y+resized.height+100)))
        }
    }
    InspectorSection("Transformar") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            LabeledNumber("X",bounds.left,0f..10000f,Modifier.weight(1f)) {x->onChange(document.transformSelection(state.selected,dx=x-bounds.left))}
            LabeledNumber("Y",bounds.top,0f..50000f,Modifier.weight(1f)) {y->onChange(document.transformSelection(state.selected,dy=y-bounds.top))}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            LabeledNumber("Ancho",width,10f..10000f,Modifier.weight(1f)) {w->resize(w,height,true)}
            LabeledNumber("Alto",height,10f..50000f,Modifier.weight(1f)) {h->resize(width,h,false)}
        }
        if(box!=null || main) {InspectorSwitch("Conservar proporciones",proportional) {proportional=it};Text(if(proportional)"El texto y su marco cambian de tamaño juntos." else "El ancho redistribuye el texto sin cambiar el tamaño de letra.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else Text("El tamaño conserva las proporciones.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val onlyInk=state.selected.all {id->ink.strokes.any {it.id==id}}
        if(onlyInk) {Row {StudioIcon(Icons.Filled.RotateLeft,"Girar −15°") {onChange(ink.rotateInk(state.selected,-15f))};StudioIcon(Icons.Filled.RotateRight,"Girar +15°") {onChange(ink.rotateInk(state.selected,15f))};StudioIcon(Icons.Filled.Flip,"Espejo horizontal") {onChange(ink.flipInk(state.selected,true))}}
            TextButton({onChange(ink.smoothInk(state.selected))}) {Text("Suavizar trazo")}}
        if(state.selected.size>1) {
            Row {StudioIcon(Icons.Filled.AlignHorizontalLeft,"Alinear objetos a la izquierda") {onChange(document.alignObjects(state.selected,0))};StudioIcon(Icons.Filled.AlignHorizontalCenter,"Centrar objetos horizontalmente") {onChange(document.alignObjects(state.selected,1))};StudioIcon(Icons.Filled.AlignHorizontalRight,"Alinear objetos a la derecha") {onChange(document.alignObjects(state.selected,2))}}
            Row {StudioIcon(Icons.Filled.AlignVerticalTop,"Alinear objetos arriba") {onChange(document.alignObjects(state.selected,3))};StudioIcon(Icons.Filled.AlignVerticalCenter,"Centrar objetos verticalmente") {onChange(document.alignObjects(state.selected,4))};StudioIcon(Icons.Filled.AlignVerticalBottom,"Alinear objetos abajo") {onChange(document.alignObjects(state.selected,5))}}
            Row {TextButton({onChange(document.groupSelection(state.selected))}) {Text("Agrupar")};TextButton({onChange(document.ungroupSelection(state.selected))}) {Text("Desagrupar")}}
            if(state.selected.size>=3)Row {TextButton({onChange(document.distributeObjects(state.selected,true))}) {Text("Distribuir X")};TextButton({onChange(document.distributeObjects(state.selected,false))}) {Text("Distribuir Y")}}
        }
    }
}
@Composable private fun LabeledNumber(label:String,value:Float,range:ClosedFloatingPointRange<Float>,modifier:Modifier,onValue:(Float)->Unit) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);StudioNumber("Transformar $label",value,range,Modifier.fillMaxWidth(),onValue=onValue)}
}

@Composable
private fun AppearanceInspector(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val selected=ink.strokes.filter {it.id in state.selected && it.id !in ink.lockedIds};val first=selected.firstOrNull() ?: return
    DisposableEffect(first.id) {onDispose {state.preview=null}}
    fun style(transform:(InkStroke)->InkStroke) {onChange(ink.copy(strokes=ink.strokes.map {if(it.id in state.selected && it.id !in ink.lockedIds)transform(it) else it}))}
    InspectorSection("Apariencia") {
        Text("Contorno",fontSize=11.sp);StudioColors(first.color,"Contorno") {color->style {it.copy(color=color)}}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Grosor",Modifier.weight(1f),fontSize=12.sp);StudioNumber("Grosor del contorno",first.width,1f..100f,Modifier.width(90.dp)) {v->style {it.copy(width=v)}}}
        InspectorSwitch("Relleno",first.fillColor!=null) {v->style {it.copy(fillColor=if(v)state.color else null,gradientColor=null)}}
        if(first.fillColor!=null) {StudioColors(first.fillColor!!,"Relleno") {color->style {it.copy(fillColor=color)}}
            InspectorSwitch("Degradado",first.gradientColor!=null) {v->style {it.copy(gradientColor=if(v)0xffffffff.toInt() else null)}}
            if(first.gradientColor!=null)StudioColors(first.gradientColor!!,"Fin de degradado") {color->style {it.copy(gradientColor=color)}}}
        InspectorSwitch("Contorno discontinuo",first.dashed) {v->style {it.copy(dashed=v)}}
        var opacity by remember(first.id,first.opacity) {mutableFloatStateOf(first.opacity)}
        Text("Opacidad: ${(opacity*100).toInt()}%",fontSize=12.sp)
        Slider(opacity,{opacity=it;state.preview=ink.copy(strokes=ink.strokes.map {s->if(s.id in state.selected)s.copy(opacity=it) else s})},onValueChangeFinished={state.preview=null;style {it.copy(opacity=opacity)}},valueRange=.05f..1f)
        if(selected.size>=2 && selected.size==state.selected.size && selected.all {it.geometric}) {
            var error by remember {mutableStateOf<String?>(null)}
            Text("Combinar formas",fontSize=12.sp)
            FlowRow {listOf("Unir" to Path.Op.UNION,"Restar" to Path.Op.DIFFERENCE,"Intersección" to Path.Op.INTERSECT,"Excluir" to Path.Op.XOR).forEach {(label,op)->TextButton({try {val doc=combineShapes(ink,state.selected,op);onChange(doc);state.selected=if(doc.nextId>ink.nextId)setOf(doc.nextId-1) else emptySet()}catch(e:Exception){error=e.message}}) {Text(label,fontSize=12.sp)}}}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        }
    }
}

@Composable
private fun ImageInspector(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val image=ink.images.singleOrNull {it.id in state.selected} ?: return
    fun change(transform:(NoteImage)->NoteImage) {onChange(ink.copy(images=ink.images.map {if(it.id==image.id)transform(it) else it}))}
    InspectorSection("Imagen") {
        TextButton({state.imageOptions=true}) {Icon(Icons.Filled.Crop,null,Modifier.size(18.dp));Text(" Recorte no destructivo")}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Giro",Modifier.weight(1f),fontSize=12.sp);StudioNumber("Giro de imagen",image.rotation,-360f..360f,Modifier.width(92.dp)) {angle->change {it.copy(rotation=angle)}}}
        InspectorSwitch("Encima de los trazos",image.foreground) {v->change {it.copy(foreground=v)}}
    }
}

@Composable private fun PageInspector(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    InspectorSection("Tamaño del papel") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            LabeledNumber("Ancho de hoja",ink.pageWidth,600f..2400f,Modifier.weight(1f)) {w->onChange(ink.resizePage(w,ink.height))}
            LabeledNumber("Alto de hoja",ink.height,1000f..20000f,Modifier.weight(1f)) {h->onChange(ink.resizePage(ink.pageWidth,h))}
        }
        Text("Color de papel",fontSize=11.sp);StudioColors(ink.paperColor,"Papel") {color->onChange(ink.copy(paperColor=color))}
        ZoomControls(state)
    }
    InspectorSection("Guías de escritura") {
        InspectorSwitch("Renglones en toda la página",ink.wholePageRuled) {v->onChange(ink.copy(wholePageRuled=v))}
        InspectorSwitch("Cuadrícula",ink.gridVisible) {v->onChange(ink.copy(gridVisible=v))}
        InspectorSwitch("Puntos",ink.dotted) {v->onChange(ink.copy(dotted=v))}
        InspectorSwitch("Ajuste magnético",ink.snapToGrid) {v->onChange(ink.copy(snapToGrid=v))}
        TextButton({state.rules=true}) {Text("Renglones en una zona")}
        TextButton({state.templates=true}) {Text("Elegir plantilla de hoja")}
    }
}
@Composable private fun ZoomControls(state:NoteEditorState) {Text("Zoom: ${(state.zoom*100).toInt()}%",fontSize=12.sp);Slider(state.zoom,{state.zoom=it},valueRange=.5f..3f,modifier=Modifier.semantics {contentDescription="Zoom de la hoja"});TextButton({state.zoom=1f}) {Text("Restablecer zoom")}}

@Composable
private fun StudioLayers(state:NoteEditorState,body:TextFieldValue,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val objects=buildList<Pair<Long,String>> {
        if(body.text.isNotEmpty())add(InkDocument.MAIN_TEXT_ID to "Texto principal")
        ink.textBoxes.asReversed().forEach {add(it.id to it.text.take(26).ifBlank {"Cuadro de texto"})}
        ink.images.asReversed().forEachIndexed {i,image->add(image.id to "Imagen ${ink.images.size-i}")}
        ink.strokes.asReversed().forEachIndexed {i,stroke->add(stroke.id to if(stroke.geometric)"Forma ${ink.strokes.size-i}" else "Trazo ${ink.strokes.size-i}")}
    }
    Text("${objects.size} objetos",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    objects.forEach {(id,label)->
        Surface(color=if(id in state.selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f).clickable(enabled=id !in ink.lockedIds && id !in ink.hiddenIds) {state.selected=ink.expandSelection(setOf(id));state.tool=InkTool.SELECT;state.inspectPage=false;keyboard?.hide()}.padding(8.dp),fontSize=12.sp,maxLines=2)
                IconButton({onChange(ink.copy(hiddenIds=if(id in ink.hiddenIds)ink.hiddenIds-id else ink.hiddenIds+id))},Modifier.size(40.dp)) {Icon(if(id in ink.hiddenIds)Icons.Filled.VisibilityOff else Icons.Filled.Visibility,"Visibilidad de $label",Modifier.size(18.dp))}
                IconButton({onChange(ink.copy(lockedIds=if(id in ink.lockedIds)ink.lockedIds-id else ink.lockedIds+id))},Modifier.size(40.dp)) {Icon(if(id in ink.lockedIds)Icons.Filled.Lock else Icons.Filled.LockOpen,"Bloqueo de $label",Modifier.size(18.dp))}
            }
        }
    }
    if(objects.isEmpty())Text("Los objetos aparecerán aquí al escribir, dibujar o añadir imágenes.",fontSize=12.sp)
}

@Composable
private fun SelectionTaskbar(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit,modifier:Modifier) {
    Surface(modifier,shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,shadowElevation=8.dp) {
        Row(Modifier.padding(4.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            Text("${state.selected.size}",Modifier.padding(horizontal=10.dp),fontSize=12.sp)
            StudioIcon(Icons.Filled.Tune,"Propiedades de la selección") {state.inspectPage=false;state.inspectorOpen=true;state.inspectorTab=StudioTab.PROPERTIES}
            if(ink.textBoxes.any {it.id in state.selected})StudioIcon(Icons.Filled.EditNote,"Editar texto seleccionado") {state.activeBox=ink.textBoxes.first {it.id in state.selected}.id;state.tool=InkTool.TEXT;state.inspectorOpen=true}
            StudioIcon(Icons.Filled.ContentCopy,"Duplicar trazos",enabled=InkDocument.MAIN_TEXT_ID !in state.selected) {onChange(ink.duplicateSelection(state.selected))}
            StudioIcon(Icons.Filled.DeleteOutline,"Eliminar trazos seleccionados",enabled=InkDocument.MAIN_TEXT_ID !in state.selected) {onChange(ink.removeSelection(state.selected));state.selected=emptySet()}
            StudioIcon(Icons.Filled.Deselect,"Quitar selección") {state.selected=emptySet()}
        }
    }
}
@Composable private fun StudioStatusBar(state:NoteEditorState,ink:InkDocument) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${number(ink.pageWidth)} × ${number(ink.height)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton({state.zoom=(state.zoom-.1f).coerceAtLeast(.5f)},Modifier.size(36.dp)) {Icon(Icons.Filled.Remove,"Reducir zoom",Modifier.size(18.dp))}
            Text("${(state.zoom*100).toInt()}%",Modifier.clickable {state.inspectPage=true;state.inspectorOpen=true}.padding(horizontal=8.dp),fontSize=12.sp)
            IconButton({state.zoom=(state.zoom+.1f).coerceAtMost(3f)},Modifier.size(36.dp)) {Icon(Icons.Filled.Add,"Ampliar zoom",Modifier.size(18.dp))}
        }
    }
}
