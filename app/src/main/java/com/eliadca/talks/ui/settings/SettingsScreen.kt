package com.eliadca.talks.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.data.ReaderFont
import com.eliadca.talks.data.ReaderTheme
import com.eliadca.talks.data.SPANISH_VARIANTS
import com.eliadca.talks.data.ThemeMode
import kotlin.math.roundToInt
import com.eliadca.talks.core.track.Marking
import com.eliadca.talks.core.track.MarkUnit

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {},
    data: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Spacer(Modifier.width(4.dp))
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Section("Apariencia", Icons.Filled.Palette) {
                    ChoiceRow(
                        "Tema", themeLabel(settings.themeMode),
                        listOf(ThemeMode.SYSTEM to "Según el sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
                        settings.themeMode,
                    ) { v -> onChange { it.copy(themeMode = v) } }
                    SliderRow("Tamaño de letra del editor", settings.editorFontSp.toFloat(), 14f..30f, "${settings.editorFontSp} sp") { v ->
                        onChange { it.copy(editorFontSp = v.roundToInt()) }
                    }
                    SwitchRow("Letra con serifa en el editor", null, settings.editorSerif) { v -> onChange { it.copy(editorSerif = v) } }
                }

                Section("Reconocimiento de voz", Icons.Filled.Mic) {
                    ChoiceRow(
                        "Idioma y variante", SPANISH_VARIANTS.firstOrNull { it.first == settings.language }?.second ?: settings.language,
                        SPANISH_VARIANTS, settings.language,
                    ) { v -> onChange { it.copy(language = v) } }
                    ChoiceRow(
                        "Motor de voz", engineLabel(settings.engine),
                        listOf(
                            EngineKind.ANDROID to "Servicio de voz de Android (Google)",
                            EngineKind.VOSK to "Sin conexión (Vosk) — descarga un modelo",
                        ),
                        settings.engine,
                    ) { v -> onChange { it.copy(engine = v) } }
                    if (settings.engine == EngineKind.ANDROID) {
                        SwitchRow(
                            "Preferir reconocimiento sin conexión",
                            "Usa el idioma descargado en el dispositivo si está disponible. Recomendado en un escenario con mala señal.",
                            settings.preferOffline,
                        ) { v -> onChange { it.copy(preferOffline = v) } }
                        SwitchRow(
                            "Forzar el servicio de Google",
                            "Actívalo si el dispositivo usa otro servicio de voz (por ejemplo el de Samsung) y el seguimiento falla.",
                            settings.forceGoogleService,
                        ) { v -> onChange { it.copy(forceGoogleService = v) } }
                        SwitchRow(
                            "Silenciar los pitidos del reconocedor",
                            "Silencia el volumen multimedia mientras escucha, para que no suenen avisos entre frases.",
                            settings.muteSounds,
                        ) { v -> onChange { it.copy(muteSounds = v) } }
                    }
                    extra()
                }

                Section("Modo Talks", Icons.Filled.RecordVoiceOver) {
                    SliderRow("Tamaño de letra", settings.readerFontSp.toFloat(), 24f..110f, "${settings.readerFontSp} sp") { v ->
                        onChange { it.copy(readerFontSp = v.roundToInt()) }
                    }
                    SliderRow("Espacio entre líneas", settings.readerLineSpacing, 1.0f..1.9f, "×%.2f".format(settings.readerLineSpacing)) { v ->
                        onChange { it.copy(readerLineSpacing = (v * 20).roundToInt() / 20f) }
                    }
                    SliderRow("Posición de la línea actual", settings.readerAnchor, 0.15f..0.65f, "${(settings.readerAnchor * 100).roundToInt()} % desde arriba") { v ->
                        onChange { it.copy(readerAnchor = (v * 20).roundToInt() / 20f) }
                    }
                    ChoiceRow(
                        "Cómo marcar lo que viene", markUnitLabel(settings.markUnit),
                        listOf(
                            MarkUnit.PHRASE to "Frase entera: queda quieta hasta que la terminas",
                            MarkUnit.SENTENCE to "Oración entera: bloques más largos",
                            MarkUnit.WORD to "Palabra a palabra",
                            MarkUnit.NONE to "Sin marcar: solo la línea de lectura",
                        ),
                        settings.markUnit,
                    ) { v -> onChange { it.copy(markUnit = v) } }
                    SliderRow(
                        "Adelanto del marcado", settings.markLead.toFloat(), Marking.MIN_LEAD.toFloat()..Marking.MAX_LEAD.toFloat(),
                        when {
                            settings.markLead == 0 -> "Al ritmo de tu voz"
                            settings.markLead > 0 -> "${settings.markLead} ${if (settings.markLead == 1) "palabra" else "palabras"} por delante"
                            else -> "${-settings.markLead} ${if (settings.markLead == -1) "palabra" else "palabras"} por detrás"
                        },
                    ) { v -> onChange { it.copy(markLead = v.roundToInt().coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)) } }
                    if (settings.markUnit == MarkUnit.WORD) {
                        SliderRow("Palabras marcadas a la vez", settings.highlightWords.toFloat(), 3f..20f, "${settings.highlightWords}") { v ->
                            onChange { it.copy(highlightWords = v.roundToInt()) }
                        }
                    }
                    ChoiceRow(
                        "Colores", readerThemeLabel(settings.readerTheme),
                        listOf(
                            ReaderTheme.NIGHT to "Noche (fondo negro)",
                            ReaderTheme.STAGE to "Escenario (amarillo sobre negro)",
                            ReaderTheme.DAY to "Día (fondo blanco)",
                            ReaderTheme.SEPIA to "Sepia",
                        ),
                        settings.readerTheme,
                    ) { v -> onChange { it.copy(readerTheme = v) } }
                    ChoiceRow(
                        "Tipo de letra", if (settings.readerFont == ReaderFont.SANS) "Sin serifa" else "Con serifa",
                        listOf(ReaderFont.SANS to "Sin serifa", ReaderFont.SERIF to "Con serifa"), settings.readerFont,
                    ) { v -> onChange { it.copy(readerFont = v) } }
                    SliderRow("Ritmo de habla para estimar la duración", settings.wordsPerMinute.toFloat(), 90f..200f, "${settings.wordsPerMinute} palabras/min") { v ->
                        onChange { it.copy(wordsPerMinute = v.roundToInt()) }
                    }
                    SwitchRow("Atenuar lo ya dicho", "El texto que ya leíste se ve más tenue.", settings.dimSpoken) { v -> onChange { it.copy(dimSpoken = v) } }
                    SwitchRow("Leer también los títulos en voz alta", "Si lo desactivas, los títulos son solo separadores y no se esperan en tu discurso.", settings.readHeadings) { v ->
                        onChange { it.copy(readHeadings = v) }
                    }
                    SwitchRow("Mostrar lo que escucha la app", "Una línea discreta con las últimas palabras reconocidas.", settings.showHeard) { v ->
                        onChange { it.copy(showHeard = v) }
                    }
                    SwitchRow("Imagen en espejo", "Para leer a través de un teleprompter de cristal.", settings.mirror) { v -> onChange { it.copy(mirror = v) } }
                    SwitchRow(
                        "Teclas de volumen y control remoto",
                        "Con la tecla de volumen o un control remoto de presentaciones avanzas o retrocedes frase a frase.",
                        settings.volumeKeys,
                    ) { v -> onChange { it.copy(volumeKeys = v) } }
                }
                data()
                Column(Modifier.padding(bottom = 24.dp)) {}
            }
        }
    }
}

private fun themeLabel(m: ThemeMode) = when (m) {
    ThemeMode.SYSTEM -> "Según el sistema"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.DARK -> "Oscuro"
}

private fun engineLabel(e: EngineKind) = when (e) {
    EngineKind.ANDROID -> "Servicio de voz de Android (Google)"
    EngineKind.VOSK -> "Sin conexión (Vosk)"
}

private fun markUnitLabel(u: MarkUnit): String = when (u) {
    MarkUnit.PHRASE -> "Frase entera (recomendado)"
    MarkUnit.SENTENCE -> "Oración entera"
    MarkUnit.WORD -> "Palabra a palabra"
    MarkUnit.NONE -> "Sin marcar"
}

private fun readerThemeLabel(t: ReaderTheme) = when (t) {
    ReaderTheme.NIGHT -> "Noche"
    ReaderTheme.STAGE -> "Escenario"
    ReaderTheme.DAY -> "Día"
    ReaderTheme.SEPIA -> "Sepia"
}

@Composable
fun Section(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun <T> ChoiceRow(title: String, current: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((value, label) in options) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(value); open = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = { onPick(value); open = false })
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } },
        )
    }
}
