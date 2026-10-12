package com.eliadca.talks.ui.notes

import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.ink.*

@Composable
internal fun NoteVectorAppearance(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    if(!state.appearance)return
    val chosen=ink.strokes.filter {it.id in state.selected && it.id !in ink.lockedIds}
    val first=chosen.firstOrNull()
    if(first==null) {LaunchedEffect(Unit){state.appearance=false};return}
    var fill by remember {mutableStateOf(first.fillColor!=null)}
    var gradient by remember {mutableStateOf(first.gradientColor!=null)}
    var color by remember {mutableIntStateOf(first.fillColor ?: state.color)}
    var end by remember {mutableIntStateOf(first.gradientColor ?: 0xffffffff.toInt())}
    var opacity by remember {mutableFloatStateOf(first.opacity)}
    var dashed by remember {mutableStateOf(first.dashed)}
    var error by remember {mutableStateOf<String?>(null)}
    val colors=listOf(0xff232b3c,0xff1565c0,0xff26a69a,0xffe53935,0xfffbc02d,0xffab47bc,0xffffffff)
    fun changed()=ink.copy(strokes=ink.strokes.map {if(it.id in state.selected && it.id !in ink.lockedIds)it.copy(fillColor=if(fill)color else null,gradientColor=if(fill && gradient)end else null,opacity=opacity,dashed=dashed) else it})
    LaunchedEffect(fill,gradient,color,end,opacity,dashed) {state.preview=changed()}
    DisposableEffect(Unit){onDispose {state.preview=null}}
    AlertDialog(onDismissRequest={state.appearance=false;state.preview=null},title={Text("Apariencia vectorial")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("${chosen.size} objetos · vista previa en la hoja",style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically){Text("Relleno",Modifier.weight(1f));Switch(fill,{fill=it})}
        Row {colors.forEach {value->Box(Modifier.padding(4.dp).size(28.dp).background(Color(value),CircleShape).clickable {color=value.toInt()})}}
        Row(verticalAlignment=Alignment.CenterVertically){Text("Degradado lineal",Modifier.weight(1f));Switch(gradient,{gradient=it},enabled=fill)}
        if(gradient)Row {colors.forEach {value->Box(Modifier.padding(4.dp).size(28.dp).background(Color(value),CircleShape).clickable {end=value.toInt()})}}
        Text("Opacidad: ${(opacity*100).toInt()}%");Slider(opacity,{opacity=it},valueRange=.05f..1f)
        Row(verticalAlignment=Alignment.CenterVertically){Text("Borde discontinuo",Modifier.weight(1f));Switch(dashed,{dashed=it})}
        if(chosen.size>=2 && chosen.size==state.selected.size && chosen.all {it.geometric}) {
            HorizontalDivider();Text("Combinar formas",style=MaterialTheme.typography.titleSmall)
            Text("Restar usa como base la forma situada más atrás. El resultado es un objeto editable y se puede deshacer.",style=MaterialTheme.typography.bodySmall)
            listOf("Unir" to Path.Op.UNION,"Restar" to Path.Op.DIFFERENCE,"Intersección" to Path.Op.INTERSECT,"Excluir" to Path.Op.XOR).forEach {(label,op)->
                TextButton(onClick={try {val doc=combineShapes(changed(),state.selected,op);onChange(doc);state.selected=if(doc.nextId>ink.nextId)setOf(doc.nextId-1) else emptySet();state.appearance=false;state.preview=null}catch(e:Exception){error=e.message}}) {Text(label)}
            }
        }
        Text("Las formas rellenas y los trazados compuestos se borran como un objeto completo.",style=MaterialTheme.typography.bodySmall)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={onChange(changed());state.appearance=false;state.preview=null}) {Text("Aplicar")}},dismissButton={TextButton(onClick={state.appearance=false;state.preview=null}) {Text("Cancelar")}})
}
