package com.eliadca.talks.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.ink.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolRow(side:Boolean,content:@Composable ()->Unit) {
    if(side) FlowRow(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.Center,maxItemsInEachRow=2) {content()}
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {content()}
}

@Composable
internal fun AdvancedNoteTools(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    if(state.grid) {
      var spacing by remember {mutableFloatStateOf(ink.gridSpacing)}
      AlertDialog(onDismissRequest={state.grid=false},title={Text("Cuadrícula y ajuste magnético")},text={Column {
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Mostrar cuadrícula",Modifier.weight(1f));Switch(ink.gridVisible,{onChange(ink.copy(gridVisible=it))})}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Ajustar formas y puntos",Modifier.weight(1f));Switch(ink.snapToGrid,{onChange(ink.copy(snapToGrid=it))})}
        Text("Separación: ${spacing.toInt()}");Slider(spacing,{spacing=it},valueRange=16f..160f,onValueChangeFinished={onChange(ink.copy(gridSpacing=spacing))})
        Text("El ajuste magnético se aplica a formas y a la edición de puntos. La escritura a mano sigue siendo libre.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={state.grid=false}) {Text("Listo")}})
    }
    if(state.shapes) AlertDialog(onDismissRequest={state.shapes=false},title={Text("Formas y líneas")},text={
        Column {NoteShape.entries.forEach {shape->TextButton(onClick={state.shape=shape;state.tool=InkTool.SHAPE;state.shapes=false;state.selected=emptySet()}) {Text(shape.label)}}}
    },confirmButton={TextButton(onClick={state.shapes=false}) {Text("Cerrar")}})
    if(state.objects) AlertDialog(onDismissRequest={state.objects=false},title={Text("Objetos de la nota")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text("Selecciona un objeto para moverlo, ajustar su tamaño o modificarlo.",style=MaterialTheme.typography.bodySmall)
            if(ink.bodyFrame!=null)ObjectRow("Texto principal",InkDocument.MAIN_TEXT_ID,state,ink,onChange)
            ink.images.asReversed().forEachIndexed {index,image->ObjectRow("Imagen ${ink.images.size-index}",image.id,state,ink,onChange)}
            ink.textBoxes.asReversed().forEach {box->ObjectRow("Texto: ${box.text.take(36)}",box.id,state,ink,onChange)}
            ink.strokes.asReversed().forEachIndexed {index,stroke->ObjectRow("Trazo ${ink.strokes.size-index} · ${stroke.points.size} puntos",stroke.id,state,ink,onChange)}
            if(ink.strokes.isEmpty() && ink.textBoxes.isEmpty() && ink.images.isEmpty() && ink.bodyFrame==null)Text("Todavía no hay objetos.")
        }
    },confirmButton={TextButton(onClick={state.objects=false}) {Text("Cerrar")}})
    if(state.transform) {
        var x by remember {mutableStateOf("0")};var y by remember {mutableStateOf("0")};var scale by remember {mutableStateOf("100")}
        val ids=state.selected
        val onlyInk=ids.isNotEmpty() && ids.all {id->ink.strokes.any {it.id==id}}
        val valid=x.toFloatOrNull()?.isFinite()==true && y.toFloatOrNull()?.isFinite()==true && scale.toFloatOrNull()?.let {it.isFinite() && it>0}==true
        AlertDialog(onDismissRequest={state.transform=false},title={Text("Transformar selección")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Desplazamiento en unidades de la hoja; tamaño en porcentaje.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(x,{x=it},label={Text("Mover horizontalmente")},singleLine=true)
                OutlinedTextField(y,{y=it},label={Text("Mover verticalmente")},singleLine=true)
                OutlinedTextField(scale,{scale=it},label={Text("Tamaño %")},singleLine=true)
                TextButton(onClick={onChange(ink.transformSelection(ids,x.toFloat(),y.toFloat(),scale.toFloat()/100));x="0";y="0";scale="100"},enabled=valid && ids.isNotEmpty()) {Text("Aplicar posición y tamaño")}
                Row {TextButton(onClick={val b=ink.selectionBounds(ids);if(b!=null)onChange(ink.transformSelection(ids,dx=(ink.pageWidth-(b.right-b.left))/2-b.left))},enabled=ids.isNotEmpty()) {Text("Centrar")}
                    TextButton(onClick={onChange(ink.styleSelection(ids,state.color,state.width))},enabled=ids.isNotEmpty()) {Text("Color y grosor del lápiz")}}
                Text("Usa el color y grosor actuales del lápiz para cambiar la selección.",style=MaterialTheme.typography.bodySmall)
                if(ids.size>1) {
                    Text("Alinear objetos",style=MaterialTheme.typography.titleSmall)
                    Row {TextButton(onClick={onChange(ink.alignObjects(ids,0))}) {Text("Izquierda")};TextButton(onClick={onChange(ink.alignObjects(ids,1))}) {Text("Centro")};TextButton(onClick={onChange(ink.alignObjects(ids,2))}) {Text("Derecha")}}
                    Row {TextButton(onClick={onChange(ink.alignObjects(ids,3))}) {Text("Arriba")};TextButton(onClick={onChange(ink.alignObjects(ids,4))}) {Text("Medio")};TextButton(onClick={onChange(ink.alignObjects(ids,5))}) {Text("Abajo")}}
                    Row {TextButton(onClick={onChange(ink.distributeObjects(ids,true))},enabled=ids.size>=3) {Text("Distribuir horizontal")};TextButton(onClick={onChange(ink.distributeObjects(ids,false))},enabled=ids.size>=3) {Text("Vertical")}}
                }
                if(onlyInk) {
                    Row {TextButton(onClick={onChange(ink.rotateInk(ids,-15f))}) {Text("Girar −15°")};TextButton(onClick={onChange(ink.rotateInk(ids,15f))}) {Text("Girar +15°")}}
                    Row {TextButton(onClick={onChange(ink.flipInk(ids,true))}) {Text("Espejo horizontal")};TextButton(onClick={onChange(ink.flipInk(ids,false))}) {Text("Vertical")}}
                    TextButton(onClick={onChange(ink.smoothInk(ids))}) {Text("Suavizar trazo")}
                }
                Row {TextButton(onClick={onChange(ink.groupSelection(ids))},enabled=ids.size>1) {Text("Agrupar")};TextButton(onClick={onChange(ink.ungroupSelection(ids))}) {Text("Desagrupar")}}
                Row {TextButton(onClick={onChange(ink.reorderInk(ids,true))}) {Text("Traer adelante")};TextButton(onClick={onChange(ink.reorderInk(ids,false))}) {Text("Enviar atrás")}}
                Text("El orden se aplica entre trazos o entre cuadros de texto. El texto permanece encima de la tinta.",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={state.transform=false}) {Text("Listo")}})
    }
}

@Composable
private fun ObjectRow(label:String,id:Long,state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick={state.selected=ink.expandSelection(setOf(id));state.tool=InkTool.SELECT;state.objects=false},enabled=id !in ink.lockedIds && id !in ink.hiddenIds) {Text(label)}
        Row {
            TextButton(onClick={onChange(ink.copy(lockedIds=if(id in ink.lockedIds)ink.lockedIds-id else ink.lockedIds+id))}) {Text(if(id in ink.lockedIds)"Desbloquear" else "Bloquear")}
            TextButton(onClick={onChange(ink.copy(hiddenIds=if(id in ink.hiddenIds)ink.hiddenIds-id else ink.hiddenIds+id))}) {Text(if(id in ink.hiddenIds)"Mostrar" else "Ocultar")}
        }
        HorizontalDivider()
    }
}
