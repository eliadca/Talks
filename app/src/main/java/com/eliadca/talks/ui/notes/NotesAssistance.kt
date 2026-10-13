package com.eliadca.talks.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.notesai.AiDownload
import com.eliadca.talks.speech.ModelDownload

@Composable
internal fun NotesVoiceOptions(vm: NotesViewModel, onDismiss: ()->Unit) {
    val advancedInstalled by vm.advancedModel.installed.collectAsState()
    val advancedProgress by vm.advancedModel.progress.collectAsState()
    val smallInstalled by vm.smallModel.installed.collectAsState()
    val smallProgress by vm.smallModel.progress.collectAsState()
    val selectedController=if(vm.advancedVosk)vm.advancedModel else vm.smallModel
    val progress=if(vm.advancedVosk)advancedProgress else smallProgress
    val installed=if(vm.advancedVosk)advancedInstalled else smallInstalled
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(selectedController::importZip) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Voz para Notas")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("El motor de Discursos conserva su configuración.",style=MaterialTheme.typography.bodySmall)
            if(vm.listening) Text("Pausa el micrófono para cambiar el motor.",color=MaterialTheme.colorScheme.primary)
            EngineKind.entries.forEach { engine -> Row(Modifier.fillMaxWidth().clickable(enabled=!vm.listening) {vm.configureVoice(engine,vm.voiceLanguage,vm.advancedVosk)},verticalAlignment=Alignment.CenterVertically) {
                RadioButton(vm.voiceEngine==engine,{vm.configureVoice(engine,vm.voiceLanguage,vm.advancedVosk)},enabled=!vm.listening)
                Text(if(engine==EngineKind.VOSK)"Vosk · sin conexión" else "Android · servicio del dispositivo")
            } }
            if(vm.voiceEngine==EngineKind.VOSK) {
                HorizontalDivider(); Text("Modelo español",Modifier.padding(top=12.dp),style=MaterialTheme.typography.titleSmall)
                listOf(false,true).forEach { precise -> Row(Modifier.fillMaxWidth().clickable(enabled=!vm.listening) {vm.configureVoice(vm.voiceEngine,vm.voiceLanguage,precise)},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(vm.advancedVosk==precise,{vm.configureVoice(vm.voiceEngine,vm.voiceLanguage,precise)},enabled=!vm.listening)
                    Column { Text(if(precise)"Preciso · 1,4 GB" else "Compacto · 40 MB"); Text(if(precise)"Para tablet; requiere más memoria." else "Rápido, con menor precisión.",style=MaterialTheme.typography.bodySmall) }
                } }
                Text(if(installed)"Modelo instalado" else "Descarga el modelo seleccionado para usarlo.",style=MaterialTheme.typography.labelMedium)
                when(val status=progress) {
                    is ModelDownload.Progress -> { LinearProgressIndicator(progress={status.fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth()); Text("Descargando ${(status.fraction.coerceAtLeast(0f)*100).toInt()}%") }
                    is ModelDownload.Installing -> { LinearProgressIndicator(Modifier.fillMaxWidth());Text("Preparando el modelo…") }
                    is ModelDownload.Failed -> Text(status.message,color=MaterialTheme.colorScheme.error)
                    else -> {}
                }
                if(progress is ModelDownload.Progress || progress is ModelDownload.Installing) TextButton(onClick=selectedController::cancel) {Text("Cancelar descarga")}
                else if(!installed) Row {
                    TextButton(onClick=selectedController::download) {Text(if(progress is ModelDownload.Failed)"Reintentar" else "Descargar modelo")}
                    TextButton(onClick={import.launch(arrayOf("application/zip","application/octet-stream"))}) {Text("Importar ZIP")}
                }
            } else {
                Text("El idioma y el servicio de voz de Android influyen en la precisión. El servicio puede usar Internet.",style=MaterialTheme.typography.bodySmall)
                listOf("es-US" to "Español · Latinoamérica","es-MX" to "Español · México","es-ES" to "Español · España").forEach { (language,label) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(vm.voiceLanguage==language,{vm.configureVoice(vm.voiceEngine,language,vm.advancedVosk)},enabled=!vm.listening);Text(label)
                } }
            }
            Spacer(Modifier.height(12.dp));Text("Acerca la tablet a quien habla. El indicador de micrófono permite comprobar que la voz llega con suficiente volumen.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Listo")}})
}

@Composable
internal fun NotesAiSetup(vm: NotesViewModel, onDismiss: ()->Unit) {
    val installed by vm.ai.installed.collectAsState(); val progress by vm.ai.download.collectAsState()
    AlertDialog(onDismissRequest=onDismiss,title={Text("IA local para transcripción")},text={
        Column {
            Text("Corrige acentos, puntuación y pequeños errores de palabras. Verás el original y la propuesta antes de aplicarla.")
            Spacer(Modifier.height(12.dp));Text("Qwen 1,5B · descarga inicial de 1,12 GB. Después funciona sin conexión; el texto permanece en tu tablet.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            when(val state=progress) {
                is AiDownload.Progress -> {LinearProgressIndicator(progress={state.fraction},modifier=Modifier.fillMaxWidth());Text("Descargando ${(state.fraction*100).toInt()}%")}
                is AiDownload.Verifying -> {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Verificando el modelo…")}
                is AiDownload.Failed -> Text(state.message,color=MaterialTheme.colorScheme.error)
                null -> if(installed)Text("IA instalada. Selecciona texto y toca Revisar con IA.")
            }
        }
    },confirmButton={
        if(installed)TextButton(onClick=onDismiss){Text("Listo")}
        else if(progress is AiDownload.Progress || progress is AiDownload.Verifying)TextButton(onClick=vm.ai::cancelDownload){Text("Cancelar descarga")}
        else TextButton(onClick=vm.ai::install){Text(if(progress is AiDownload.Failed)"Reintentar" else "Descargar IA")}
    },dismissButton={if(!installed)TextButton(onClick=onDismiss){Text("Cerrar")}})
}

@Composable
internal fun NotesAiReview(vm: NotesViewModel) {
    val review=vm.aiReview ?: return
    AlertDialog(onDismissRequest=vm::dismissAiReview,title={Text("Revisar propuesta de IA")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Original",style=MaterialTheme.typography.labelLarge);SelectionContainer{Text(review.original)}
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Text("Propuesta",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);SelectionContainer{Text(review.proposed)}
            Spacer(Modifier.height(12.dp));Text(review.concern ?: "Comprueba nombres, citas y sentido antes de aplicar. El original se conserva.",style=MaterialTheme.typography.bodySmall,color=if(review.concern==null)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=vm::applyAiReview,enabled=review.concern==null && review.original!=review.proposed){Text("Aplicar")}},dismissButton={TextButton(onClick=vm::dismissAiReview){Text("Conservar original")}})
}
