package com.eliadca.talks.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eliadca.talks.BuildConfig
import com.eliadca.talks.container
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.BackupManager
import com.eliadca.talks.export.Importer
import com.eliadca.talks.ui.talks.ModelControls
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings plus everything that needs files: backup, restore, importing speeches, the offline model. */
@Composable
fun SettingsRoute(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val backup = remember { BackupManager(container.database, context.contentResolver) }
    val importer = remember { Importer(context.contentResolver) }
    val model = container.voskModel
    val voskInstalled by model.installed.collectAsState()

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val r = backup.export(uri)
                toast("Copia guardada: ${r.speeches} discursos.")
            } catch (e: Exception) {
                toast("No se pudo guardar la copia: ${e.message}")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val r = backup.import(uri)
                toast("Restaurados ${r.speeches} discursos" + if (r.skipped > 0) " (${r.skipped} ya estaban)." else ".")
            } catch (e: Exception) {
                toast(e.message ?: "No se pudo restaurar la copia.")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val imported = importer.read(uri)
                val id = container.speeches.create(imported.title, imported.doc)
                toast("Importado «${imported.title}».")
                onImported(id)
            } catch (e: Exception) {
                toast(e.message ?: "No se pudo importar el archivo.")
            }
        }
    }
    val modelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importZip(uri)
    }

    SettingsScreen(
        settings = settings,
        onChange = onChange,
        onBack = onBack,
        extra = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Modelo de voz sin conexión", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (voskInstalled) "Instalado (${remember(voskInstalled) { model.manager.sizeOnDiskMb() }} MB)."
                    else "No instalado. Es un archivo de unos 40 MB que se descarga una sola vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelControls(model) { modelImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                if (voskInstalled) TextButton(onClick = model::remove) { Text("Eliminar modelo") }
            }
        },
        data = {
            Section("Copia de seguridad e importación") {
                ActionRow(
                    "Crear copia de seguridad",
                    "Guarda todos tus discursos, carpetas y versiones en un archivo que puedes llevarte a otro dispositivo.",
                ) { exportLauncher.launch("talks-copia-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ActionRow("Restaurar copia de seguridad", "Añade los discursos de una copia. No duplica los que ya tienes.") {
                    restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ActionRow("Importar un archivo", "Crea un discurso a partir de un documento de Word (.docx), de Markdown (.md) o de texto (.txt).") {
                    importLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain", "text/markdown", "application/octet-stream", "*/*",
                        ),
                    )
                }
            }
            Section("Acerca de Talks") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Talks ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Tus discursos se guardan solo en este dispositivo. El reconocimiento de voz del servicio de Android puede enviar audio a Google; el modo sin conexión (Vosk) no envía nada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
