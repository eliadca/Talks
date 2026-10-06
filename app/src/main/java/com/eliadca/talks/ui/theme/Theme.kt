package com.eliadca.talks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.ThemeMode

private val Indigo = Color(0xFF3F4FD8)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E4FF),
    onPrimaryContainer = Color(0xFF0E1A66),
    secondary = Color(0xFF9A6A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9B3),
    onSecondaryContainer = Color(0xFF3A2800),
    tertiary = Color(0xFF00796B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2F0E6),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE9EAF2),
    onSurfaceVariant = Color(0xFF50525F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F7),
    surfaceContainer = Color(0xFFEBECF3),
    surfaceContainerHigh = Color(0xFFE5E6EE),
    surfaceContainerHighest = Color(0xFFDFE0E9),
    outline = Color(0xFF8A8C9B),
    outlineVariant = Color(0xFFCDCFDB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB7BEFF),
    onPrimary = Color(0xFF131D78),
    primaryContainer = Color(0xFF2B3699),
    onPrimaryContainer = Color(0xFFE1E4FF),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF58400A),
    onSecondaryContainer = Color(0xFFFFE9B3),
    tertiary = Color(0xFF7ED8CB),
    onTertiary = Color(0xFF00201C),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFB2F0E6),
    background = Color(0xFF101116),
    onBackground = Color(0xFFE6E6EE),
    surface = Color(0xFF16171E),
    onSurface = Color(0xFFE6E6EE),
    surfaceVariant = Color(0xFF2A2C38),
    onSurfaceVariant = Color(0xFFB0B2C2),
    surfaceContainerLowest = Color(0xFF0D0E12),
    surfaceContainerLow = Color(0xFF1B1C24),
    surfaceContainer = Color(0xFF20212A),
    surfaceContainerHigh = Color(0xFF292B35),
    surfaceContainerHighest = Color(0xFF333541),
    outline = Color(0xFF7D8090),
    outlineVariant = Color(0xFF464859),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val TalksShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun TalksTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = TalksShapes,
        content = content,
    )
}

/** Label colours for speeches and folders. */
val LabelColors: List<Int> = listOf(
    0xFFE53935.toInt(), // red
    0xFFFB8C00.toInt(), // orange
    0xFFFDD835.toInt(), // yellow
    0xFF43A047.toInt(), // green
    0xFF00ACC1.toInt(), // teal
    0xFF1E88E5.toInt(), // blue
    0xFF8E24AA.toInt(), // purple
    0xFFD81B60.toInt(), // pink
)

/** Text colours that stay readable on both light and dark pages. */
val TextColors: List<Int> = listOf(
    0xFFE53935.toInt(),
    0xFFFB8C00.toInt(),
    0xFF2E9E4F.toInt(),
    0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(),
    0xFF8A8D99.toInt(),
)

/** Translucent so that text stays readable on any background. */
val HighlightColors: List<Int> = listOf(
    0x80FFEB3B.toInt(),
    0x8076FF03.toInt(),
    0x8000E5FF.toInt(),
    0x80FF80AB.toInt(),
    0x80FFAB40.toInt(),
    0x80B388FF.toInt(),
)
