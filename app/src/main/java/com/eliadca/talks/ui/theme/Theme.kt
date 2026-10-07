package com.eliadca.talks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eliadca.talks.data.ThemeMode
import com.eliadca.talks.data.AppAccent
import com.eliadca.talks.data.AppTone
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF3F4FD8)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E4FF),
    onPrimaryContainer = Color(0xFF0E1A66),
    secondary = Color(0xFF5B5E7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E3FB),
    onSecondaryContainer = Color(0xFF171A3A),
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
    secondary = Color(0xFFC3C5E8),
    onSecondary = Color(0xFF2C2F4C),
    secondaryContainer = Color(0xFF3A3E62),
    onSecondaryContainer = Color(0xFFE2E3FB),
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

/** The seed colour of each accent the app can wear. */
fun accentSeed(a: AppAccent): Color = when (a) {
    AppAccent.INDIGO, AppAccent.DYNAMIC -> Indigo
    AppAccent.BLUE -> Color(0xFF1E6FD9)
    AppAccent.TEAL -> Color(0xFF00897B)
    AppAccent.GREEN -> Color(0xFF2E7D32)
    AppAccent.PURPLE -> Color(0xFF7B3FC4)
    AppAccent.PINK -> Color(0xFFC2185B)
    AppAccent.RED -> Color(0xFFC62828)
    AppAccent.ORANGE -> Color(0xFFE0611A)
    AppAccent.AMBER -> Color(0xFFB98500)
    AppAccent.GRAPHITE -> Color(0xFF4A5058)
}

private fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

/** A full colour scheme grown from one seed colour. */
private fun schemeFor(seed: Color, dark: Boolean, tone: AppTone): ColorScheme {
    val tint = when (tone) { AppTone.TINTED -> 1f; else -> 0f }
    val ink = if (dark) Color(0xFFE6E6EE) else Color(0xFF1A1B22)
    return if (!dark) {
        val bg = mix(Color(0xFFF7F7F8), mix(seed, Color.White, 0.94f), tint)
        val sv = mix(Color(0xFFEAEAED), mix(seed, Color.White, 0.86f), tint)
        LightColors.copy(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = mix(seed, Color.White, 0.82f),
            onPrimaryContainer = mix(seed, Color.Black, 0.62f),
            secondary = mix(seed, Color(0xFF5B5E6E), 0.55f),
            secondaryContainer = mix(seed, Color.White, 0.86f),
            onSecondaryContainer = mix(seed, Color.Black, 0.7f),
            background = bg,
            onBackground = ink,
            surface = Color.White,
            onSurface = ink,
            surfaceVariant = sv,
            surfaceContainerLow = mix(bg, Color.White, 0.3f),
            surfaceContainer = mix(bg, sv, 0.4f),
            surfaceContainerHigh = mix(bg, sv, 0.7f),
            surfaceContainerHighest = sv,
            outlineVariant = mix(sv, Color.Black, 0.12f),
        )
    } else {
        val primary = mix(seed, Color.White, 0.45f)
        val base = when (tone) {
            AppTone.BLACK -> Color.Black
            AppTone.NEUTRAL -> Color(0xFF121214)
            AppTone.TINTED -> mix(seed, Color(0xFF0F1014), 0.9f)
        }
        val surface = if (tone == AppTone.BLACK) Color(0xFF0B0B0D) else mix(base, Color.White, 0.03f)
        val sv = if (tone == AppTone.TINTED) mix(seed, Color(0xFF2A2C34), 0.82f) else Color(0xFF2B2B30)
        DarkColors.copy(
            primary = primary,
            onPrimary = mix(seed, Color.Black, 0.55f),
            primaryContainer = mix(seed, Color.Black, 0.45f),
            onPrimaryContainer = mix(seed, Color.White, 0.82f),
            secondary = mix(primary, Color(0xFFC4C4CC), 0.5f),
            secondaryContainer = mix(seed, Color(0xFF2E3038), 0.7f),
            onSecondaryContainer = mix(seed, Color.White, 0.85f),
            background = base,
            onBackground = ink,
            surface = surface,
            onSurface = ink,
            surfaceVariant = sv,
            surfaceContainerLowest = base,
            surfaceContainerLow = mix(surface, sv, 0.2f),
            surfaceContainer = mix(surface, sv, 0.35f),
            surfaceContainerHigh = mix(surface, sv, 0.6f),
            surfaceContainerHighest = sv,
            outlineVariant = mix(sv, Color.White, 0.1f),
        )
    }
}

@Composable
fun TalksTheme(
    darkTheme: Boolean,
    accent: AppAccent = AppAccent.INDIGO,
    tone: AppTone = AppTone.TINTED,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        accent == AppAccent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context).let { if (tone == AppTone.BLACK) it.copy(background = Color.Black, surfaceContainerLowest = Color.Black) else it }
            else dynamicLightColorScheme(context)
        accent == AppAccent.INDIGO && tone == AppTone.TINTED -> if (darkTheme) DarkColors else LightColors
        else -> schemeFor(accentSeed(accent), darkTheme, tone)
    }
    MaterialTheme(colorScheme = colors, shapes = TalksShapes) {
        // Text and icons that set no colour of their own (screen titles, toolbar icons) would
        // otherwise be black, which disappears on a dark background.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
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
