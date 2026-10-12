package com.eliadca.talks.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eliadca.talks.core.ink.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Composable
internal fun NoteImageButton(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val current by rememberUpdatedState(ink);val change by rememberUpdatedState(onChange)
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) {
        busy=true;scope.launch {
            try {
                val image=withContext(Dispatchers.IO) {NoteImageImport.read(context,uri)}
                require(current.images.size<30 && current.images.sumOf {it.data.length}+image.data.length<=32*1024*1024) {"Esta hoja tiene demasiadas imágenes. Usa otra nota."}
                val width=minOf(500f,current.pageWidth-120f,1500f*500f/image.height)
                val picture=image.copy(id=current.nextId,width=width,height=image.height*width/500f)
                change(current.copy(images=current.images+picture,nextId=current.nextId+1,height=maxOf(current.height,picture.y+picture.height+100)))
                state.tool=InkTool.SELECT;state.selected=setOf(picture.id)
            } catch(e:Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: "No se pudo añadir la imagen."}
            finally {busy=false}
        }
    }}
    IconButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!busy) {if(busy)CircularProgressIndicator(Modifier.size(20.dp)) else Icon(Icons.Filled.AddPhotoAlternate,"Añadir imagen")}
    error?.let {text->AlertDialog(onDismissRequest={error=null},title={Text("Imagen")},text={Text(text)},confirmButton={TextButton(onClick={error=null}) {Text("Entendido")}})}
}

@Composable
internal fun NoteMediaTools(state:NoteEditorState,ink:InkDocument,onChange:(InkDocument)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    if(state.imageOptions) {
        val image=ink.images.firstOrNull {it.id in state.selected}
        if(image==null)LaunchedEffect(Unit) {state.imageOptions=false}
        else {
            var left by remember(image.id) {mutableFloatStateOf(image.cropLeft)};var top by remember(image.id) {mutableFloatStateOf(image.cropTop)}
            var right by remember(image.id) {mutableFloatStateOf(image.cropRight)};var bottom by remember(image.id) {mutableFloatStateOf(image.cropBottom)}
            var rotation by remember(image.id) {mutableFloatStateOf(image.rotation)};var opacity by remember(image.id) {mutableFloatStateOf(image.opacity)}
            var front by remember(image.id) {mutableStateOf(image.foreground)}
            fun preview():InkDocument=ink.copy(images=ink.images.map {if(it.id==image.id)it.copy(cropLeft=left,cropTop=top,cropRight=right,cropBottom=bottom,rotation=rotation,opacity=opacity,foreground=front) else it})
            DisposableEffect(Unit) {onDispose {state.preview=null}}
            LaunchedEffect(left,top,right,bottom,rotation,opacity,front) {state.preview=preview()}
            AlertDialog(onDismissRequest={state.imageOptions=false},title={Text("Editar imagen")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Recorte no destructivo: la imagen original queda guardada.",style=MaterialTheme.typography.bodySmall)
                Text("Izquierda");Slider(left,{left=it.coerceAtMost(right-.05f)},valueRange=0f..95f/100)
                Text("Derecha");Slider(right,{right=it.coerceAtLeast(left+.05f)},valueRange=.05f..1f)
                Text("Arriba");Slider(top,{top=it.coerceAtMost(bottom-.05f)},valueRange=0f..95f/100)
                Text("Abajo");Slider(bottom,{bottom=it.coerceAtLeast(top+.05f)},valueRange=.05f..1f)
                Text("Giro: ${rotation.toInt()}°");Slider(rotation,{rotation=it},valueRange=-180f..180f)
                Text("Opacidad: ${(opacity*100).toInt()}%");Slider(opacity,{opacity=it},valueRange=.05f..1f)
                Row(verticalAlignment=Alignment.CenterVertically) {Text("Encima de los trazos",Modifier.weight(1f));Switch(front,{front=it})}
                TextButton(onClick={left=0f;top=0f;right=1f;bottom=1f;rotation=0f;opacity=1f}) {Text("Restablecer imagen")}
            }},confirmButton={TextButton(onClick={onChange(preview());state.preview=null;state.imageOptions=false}) {Text("Aplicar")}},dismissButton={TextButton(onClick={state.imageOptions=false;state.preview=null}) {Text("Cancelar")}})
        }
    }
    if(state.templates) {
        val file=remember {File(context.filesDir,"notes-templates.json")}
        var custom by remember {mutableStateOf(emptyList<NotePageTemplate>())}
        var name by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
        var exporting by remember {mutableStateOf<NotePageTemplate?>(null)}
        val exportTemplate=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->val template=exporting;exporting=null;if(uri!=null && template!=null)scope.launch {try {withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use {it.write(Json.encodeToString(template).toByteArray(Charsets.UTF_8))} ?: error("No se pudo abrir el archivo.")}}catch(e:Exception){error="No se pudo exportar la plantilla."}}}
        var loaded by remember {mutableStateOf(false)};var saving by remember {mutableStateOf(false)}
        LaunchedEffect(Unit) {try {custom=withContext(Dispatchers.IO) {if(file.exists())Json.decodeFromString<List<NotePageTemplate>>(file.readText()) else emptyList()}}catch(e:Exception){error="No se pudieron leer las plantillas."};loaded=true}
        fun save(templates:List<NotePageTemplate>) {saving=true;scope.launch {try {withContext(Dispatchers.IO) {val temp=File(file.parentFile,"notes-templates.json.part");temp.writeText(Json.encodeToString(templates));check(temp.renameTo(file))};custom=templates}catch(e:Exception){error="No se pudo guardar la plantilla."}finally {saving=false}}}
        val importTemplate=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)scope.launch {
            try {
                val template=withContext(Dispatchers.IO) {
                    val bytes=context.contentResolver.openInputStream(uri)?.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);var count:Int;while(input.read(buffer).also {count=it}>0){require(out.size()+count<=48*1024*1024){"La plantilla supera 48 MB."};out.write(buffer,0,count)};out.toByteArray()} ?: error("No se pudo abrir la plantilla.")
                    val parsed=Json.decodeFromString<NotePageTemplate>(bytes.toString(Charsets.UTF_8))
                    require(parsed.name.isNotBlank() && parsed.document.pageWidth.isFinite() && parsed.document.height.isFinite() && parsed.document.pageWidth in 100f..10000f && parsed.document.height in 100f..50000f){"Plantilla no válida."}
                    val ids=parsed.document.strokes.map {it.id}+parsed.document.textBoxes.map {it.id}+parsed.document.images.map {it.id}+parsed.document.areas.map {it.id}
                    require(ids.size==ids.distinct().size && ids.all {it>0} && ids.all {it<parsed.document.nextId}){"La plantilla tiene identificadores no válidos."}
                    require(parsed.document.strokes.all {s->s.allPoints().all {it.x.isFinite() && it.y.isFinite() && it.x in 0f..10000f && it.y in 0f..50000f}}){"La plantilla tiene coordenadas no válidas."}
                    parsed.document.images.forEach {image->val data=android.util.Base64.decode(image.data,android.util.Base64.DEFAULT);val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(data,0,data.size,bounds);require(bounds.outWidth in 1..3200 && bounds.outHeight in 1..3200){"La plantilla incluye una imagen incompatible."}}
                    parsed.copy(id=java.util.UUID.randomUUID().toString())
                }
                save(custom+template)
            }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: "No se pudo importar la plantilla."}
        }}
        AlertDialog(onDismissRequest={if(!saving)state.templates=false},title={Text("Plantillas de hojas")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Aplicar conserva lo que ya escribiste y añade los elementos de la plantilla. Puedes deshacer la operación.",style=MaterialTheme.typography.bodySmall)
            (NoteTemplates.builtIn()+custom).forEach {template->Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={onChange(ink.applyTemplate(template.document));state.templates=false},modifier=Modifier.weight(1f)) {Text(template.name)}
                TextButton(onClick={exporting=template;exportTemplate.launch(template.name.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"),"_")+".talktemplate.json")}) {Text("Exportar")}
                if(template in custom)TextButton(onClick={save(custom.filterNot {it.id==template.id})},enabled=!saving) {Text("Eliminar")}
            }}
            TextButton(onClick={importTemplate.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=loaded && !saving) {Text("Importar plantilla")}
            HorizontalDivider();Text("Crear plantilla con esta hoja",style=MaterialTheme.typography.titleSmall)
            OutlinedTextField(name,{name=it},label={Text("Nombre de la plantilla")},singleLine=true)
            Text("Guarda papel, formas, imágenes, renglones y cuadros de texto. El texto principal de la nota no se incluye.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={val template=NotePageTemplate(java.util.UUID.randomUUID().toString(),name.trim(),ink.copy(lockedIds=ink.lockedIds-InkDocument.MAIN_TEXT_ID,hiddenIds=ink.hiddenIds-InkDocument.MAIN_TEXT_ID,groups=ink.groups.map {it-InkDocument.MAIN_TEXT_ID}.filter {it.size>1},formatting=com.eliadca.talks.core.notes.NoteFormatting()));save(custom+template);name=""},enabled=loaded && !saving && name.isNotBlank()) {Text(if(saving)"Guardando…" else "Guardar plantilla")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(onClick={state.templates=false},enabled=!saving) {Text("Listo")}})
    }
}
