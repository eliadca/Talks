package com.eliadca.talks.ui.notes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliadca.talks.core.ink.*

/** A live thumbnail of the stored paper, vector objects, text and embedded images. */
@Composable
internal fun TemplatePreview(template:NotePageTemplate,modifier:Modifier=Modifier) {
    val document=template.document
    Box(modifier.aspectRatio((document.pageWidth/document.height).coerceIn(.4f,1.8f)).background(Color(document.paperColor))
        .border(1.dp,Color(0xff687080).copy(alpha=.3f))) {
        AndroidView(factory={InkPageView(it).apply {tool=InkTool.MOVE;isEnabled=false}},update={it.document=document;it.ruleColor=0xff8795a7.toInt();it.drawTextBoxes=true},modifier=Modifier.fillMaxSize())
    }
}

@Composable
internal fun TemplateMiniGallery(onOpen:()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        NoteTemplates.builtIn().filter {it.id in listOf("ruled","cornell","dots")}.forEach {template->
            TemplatePreview(template,Modifier.weight(1f).clickable(onClick=onOpen))
        }
    }
}

@Composable
internal fun TemplateGallery(custom:List<NotePageTemplate>,busy:Boolean,error:String?,
    onApply:(NotePageTemplate)->Unit,onSave:(String)->Unit,onImport:()->Unit,
    onExport:(NotePageTemplate)->Unit,onDelete:(NotePageTemplate)->Unit,onClose:()->Unit) {
    var category by rememberSaveable {mutableStateOf("Todas")}
    var selectedId by rememberSaveable {mutableStateOf("plain")}
    var search by rememberSaveable {mutableStateOf("")}
    var creating by remember {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    val all=NoteTemplates.builtIn()+custom
    val filtered=all.filter {template->
        (category=="Todas" || category=="Mis plantillas" && template in custom || category=="Papel" && template.id in listOf("plain","ruled","grid","dots","music") || category=="Estudio y agenda" && template.id in listOf("cornell","planner")) && template.name.contains(search,ignoreCase=true)
    }
    val selected=all.firstOrNull {it.id==selectedId} ?: all.first()
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        NoteStudioTheme {
            Surface(Modifier.fillMaxWidth(.94f).widthIn(max=1120.dp).fillMaxHeight(.9f).testTag("template-gallery"),
                shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=24.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).height(68.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesomeMosaic,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start=12.dp)) {Text("Plantillas de hojas",fontSize=20.sp,fontWeight=FontWeight.SemiBold);Text("Biblioteca / Papel y diseños",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        IconButton(onClose,enabled=!busy) {Icon(Icons.Filled.Close,"Cerrar galería de plantillas")}
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        listOf("Todas","Papel","Estudio y agenda","Mis plantillas").forEach {item->FilterChip(category==item,{category=item},label={Text(item)})}
                        OutlinedTextField(search,{search=it},Modifier.width(210.dp),singleLine=true,placeholder={Text("Buscar plantilla")},leadingIcon={Icon(Icons.Filled.Search,null,Modifier.size(18.dp))})
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val large=maxWidth>=740.dp
                        Row(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(GridCells.Adaptive(if(large)164.dp else 140.dp),Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background).padding(16.dp),
                                horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                items(filtered,key={it.id}) {template->
                                    Surface(shape=RoundedCornerShape(7.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
                                        border=BorderStroke(if(template.id==selectedId)2.dp else 1.dp,if(template.id==selectedId)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier=Modifier.clickable {selectedId=template.id}.semantics {contentDescription="Vista previa de ${template.name}"}) {
                                        Column(Modifier.padding(12.dp)) {
                                            TemplatePreview(template,Modifier.fillMaxWidth())
                                            Row(Modifier.padding(top=10.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                                Text(template.name,Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=2)
                                                if(template.id==selectedId)Icon(Icons.Filled.CheckCircle,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                                            }
                                            Text(if(template in custom)"Personalizada" else templateCategory(template.id),Modifier.padding(top=4.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            if(large) Column(Modifier.width(284.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("VISTA PREVIA",fontSize=10.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                TemplatePreview(selected,Modifier.fillMaxWidth())
                                Text(selected.name,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                                Text(templateDescription(selected),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${number(selected.document.pageWidth)} × ${number(selected.document.height)}",fontSize=12.sp)
                                OutlinedButton({onExport(selected)},Modifier.fillMaxWidth(),enabled=!busy) {Icon(Icons.Filled.FileDownload,null,Modifier.size(18.dp));Text(" Exportar plantilla")}
                                if(selected in custom)TextButton({onDelete(selected);selectedId="plain"},enabled=!busy) {Text("Eliminar plantilla",color=MaterialTheme.colorScheme.error)}
                            }
                        }
                        if(filtered.isEmpty())Text("No hay plantillas en esta vista.",Modifier.align(Alignment.Center),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    error?.let {Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
                    if(creating) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedTextField(name,{name=it},Modifier.weight(1f),label={Text("Nombre de la plantilla")},singleLine=true)
                        Button({onSave(name.trim());name="";creating=false;category="Mis plantillas"},enabled=name.isNotBlank() && !busy) {Text("Guardar plantilla")}
                        IconButton({creating=false}) {Icon(Icons.Filled.Close,"Cancelar creación de plantilla")}
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedButton(onImport,enabled=!busy) {Icon(Icons.Filled.FileUpload,null,Modifier.size(18.dp));Text(" Importar")}
                        TextButton({creating=!creating},enabled=!busy) {Icon(Icons.Filled.Add,null,Modifier.size(18.dp));Text(" Crear con esta hoja")}
                        Spacer(Modifier.weight(1f))
                        Button({onApply(selected)},enabled=!busy,modifier=Modifier.testTag("apply-template")) {Text("Aplicar ${selected.name}")}
                    }
                    Text("Se conserva tu contenido; los elementos de la plantilla se añaden a la hoja. Puedes deshacer.",Modifier.padding(start=20.dp,end=20.dp,bottom=14.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun templateCategory(id:String)=when(id) {"cornell","planner"->"Estudio y organización";"music"->"Música";else->"Papel"}
private fun templateDescription(template:NotePageTemplate)=when(template.id) {
    "plain"->"Un lienzo limpio para texto, dibujo e imágenes."
    "ruled"->"Renglones continuos para escribir con teclado o S Pen."
    "grid"->"Guías para diagramas, esquemas y escritura precisa."
    "dots"->"Puntos discretos para mantener el orden sin líneas."
    "cornell"->"Zona de notas, claves y resumen para estudiar."
    "planner"->"Secciones para objetivos, tareas e ideas del día."
    "music"->"Siete pentagramas para anotación musical."
    else->"Diseño personalizado con papel, objetos, imágenes y cuadros de texto."
}
