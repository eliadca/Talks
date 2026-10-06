package com.eliadca.talks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eliadca.talks.core.track.MarkUnit
import com.eliadca.talks.core.track.Marking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which speech recogniser Talks mode listens with. */
enum class EngineKind { ANDROID, VOSK }

enum class ReaderTheme { NIGHT, DAY, STAGE, SEPIA }

enum class ReaderFont { SANS, SERIF }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    // Speech recognition
    val language: String = "es-US",
    val engine: EngineKind = EngineKind.ANDROID,
    val preferOffline: Boolean = false,
    val forceGoogleService: Boolean = false,
    val muteSounds: Boolean = true,

    // Talks mode
    val wordsPerMinute: Int = 130,
    val readerFontSp: Int = 44,
    val readerTheme: ReaderTheme = ReaderTheme.NIGHT,
    val readerFont: ReaderFont = ReaderFont.SANS,
    val readerLineSpacing: Float = 1.35f,
    /** Vertical position of the line being read, as a fraction of the screen height. */
    val readerAnchor: Float = 0.35f,
    /** Longest stretch (in words) that is underlined as "what to say next" when marking word by word. */
    val highlightWords: Int = 9,
    /** How what comes next is marked: whole phrases (the calmest), sentences, word by word, or not at all. */
    val markUnit: MarkUnit = MarkUnit.PHRASE,
    /** Words the marking runs ahead of the voice (negative: behind it). */
    val markLead: Int = 0,
    val readHeadings: Boolean = false,
    val mirror: Boolean = false,
    val showHeard: Boolean = false,
    val volumeKeys: Boolean = false,
    val dimSpoken: Boolean = true,

    // Editor
    val editorFontSp: Int = 18,
    val editorSerif: Boolean = false,

    val seeded: Boolean = false,
    /** The note with the template for an AI assistant has been added (once, also on older installs). */
    val templateSeeded: Boolean = false,
)

/** The marking of the live reader, as chosen in the settings. */
fun AppSettings.marking(): Marking = Marking(markUnit, markLead, highlightWords)

/** Spanish variants offered for recognition: tag to display name. */
val SPANISH_VARIANTS: List<Pair<String, String>> = listOf(
    "es-US" to "Español (Estados Unidos)",
    "es-MX" to "Español (México)",
    "es-ES" to "Español (España)",
    "es-AR" to "Español (Argentina)",
    "es-BO" to "Español (Bolivia)",
    "es-CL" to "Español (Chile)",
    "es-CO" to "Español (Colombia)",
    "es-CR" to "Español (Costa Rica)",
    "es-CU" to "Español (Cuba)",
    "es-DO" to "Español (República Dominicana)",
    "es-EC" to "Español (Ecuador)",
    "es-GT" to "Español (Guatemala)",
    "es-HN" to "Español (Honduras)",
    "es-NI" to "Español (Nicaragua)",
    "es-PA" to "Español (Panamá)",
    "es-PE" to "Español (Perú)",
    "es-PR" to "Español (Puerto Rico)",
    "es-PY" to "Español (Paraguay)",
    "es-SV" to "Español (El Salvador)",
    "es-UY" to "Español (Uruguay)",
    "es-VE" to "Español (Venezuela)",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val engine = stringPreferencesKey("engine")
        val preferOffline = booleanPreferencesKey("preferOffline")
        val forceGoogle = booleanPreferencesKey("forceGoogle")
        val muteSounds = booleanPreferencesKey("muteSounds")
        val wpm = intPreferencesKey("wpm")
        val readerFontSp = intPreferencesKey("readerFontSp")
        val readerTheme = stringPreferencesKey("readerTheme")
        val readerFont = stringPreferencesKey("readerFont")
        val readerLineSpacing = floatPreferencesKey("readerLineSpacing")
        val readerAnchor = floatPreferencesKey("readerAnchor")
        val highlightWords = intPreferencesKey("highlightWords")
        val markUnit = stringPreferencesKey("markUnit")
        val markLead = intPreferencesKey("markLead")
        val readHeadings = booleanPreferencesKey("readHeadings")
        val mirror = booleanPreferencesKey("mirror")
        val showHeard = booleanPreferencesKey("showHeard")
        val volumeKeys = booleanPreferencesKey("volumeKeys")
        val dimSpoken = booleanPreferencesKey("dimSpoken")
        val editorFontSp = intPreferencesKey("editorFontSp")
        val editorSerif = booleanPreferencesKey("editorSerif")
        val seeded = booleanPreferencesKey("seeded")
        val templateSeeded = booleanPreferencesKey("templateSeeded")
    }

    private val defaults = AppSettings(language = defaultLanguage())

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p -> read(p) }

    private fun read(p: Preferences): AppSettings {
        val d = defaults
        return AppSettings(
            themeMode = p[K.theme].toEnum(d.themeMode),
            language = p[K.language] ?: d.language,
            engine = p[K.engine].toEnum(d.engine),
            preferOffline = p[K.preferOffline] ?: d.preferOffline,
            forceGoogleService = p[K.forceGoogle] ?: d.forceGoogleService,
            muteSounds = p[K.muteSounds] ?: d.muteSounds,
            wordsPerMinute = (p[K.wpm] ?: d.wordsPerMinute).coerceIn(60, 260),
            readerFontSp = (p[K.readerFontSp] ?: d.readerFontSp).coerceIn(20, 140),
            readerTheme = p[K.readerTheme].toEnum(d.readerTheme),
            readerFont = p[K.readerFont].toEnum(d.readerFont),
            readerLineSpacing = (p[K.readerLineSpacing] ?: d.readerLineSpacing).coerceIn(1.0f, 2.0f),
            readerAnchor = (p[K.readerAnchor] ?: d.readerAnchor).coerceIn(0.15f, 0.7f),
            highlightWords = (p[K.highlightWords] ?: d.highlightWords).coerceIn(3, 20),
            markUnit = p[K.markUnit].toEnum(d.markUnit),
            markLead = (p[K.markLead] ?: d.markLead).coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD),
            readHeadings = p[K.readHeadings] ?: d.readHeadings,
            mirror = p[K.mirror] ?: d.mirror,
            showHeard = p[K.showHeard] ?: d.showHeard,
            volumeKeys = p[K.volumeKeys] ?: d.volumeKeys,
            dimSpoken = p[K.dimSpoken] ?: d.dimSpoken,
            editorFontSp = (p[K.editorFontSp] ?: d.editorFontSp).coerceIn(12, 36),
            editorSerif = p[K.editorSerif] ?: d.editorSerif,
            seeded = p[K.seeded] ?: d.seeded,
            templateSeeded = p[K.templateSeeded] ?: d.templateSeeded,
        )
    }

    /** Applies [transform] to the current settings and stores the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[K.theme] = s.themeMode.name
            p[K.language] = s.language
            p[K.engine] = s.engine.name
            p[K.preferOffline] = s.preferOffline
            p[K.forceGoogle] = s.forceGoogleService
            p[K.muteSounds] = s.muteSounds
            p[K.wpm] = s.wordsPerMinute
            p[K.readerFontSp] = s.readerFontSp
            p[K.readerTheme] = s.readerTheme.name
            p[K.readerFont] = s.readerFont.name
            p[K.readerLineSpacing] = s.readerLineSpacing
            p[K.readerAnchor] = s.readerAnchor
            p[K.highlightWords] = s.highlightWords
            p[K.markUnit] = s.markUnit.name
            p[K.markLead] = s.markLead.coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)
            p[K.readHeadings] = s.readHeadings
            p[K.mirror] = s.mirror
            p[K.showHeard] = s.showHeard
            p[K.volumeKeys] = s.volumeKeys
            p[K.dimSpoken] = s.dimSpoken
            p[K.editorFontSp] = s.editorFontSp
            p[K.editorSerif] = s.editorSerif
            p[K.seeded] = s.seeded
            p[K.templateSeeded] = s.templateSeeded
        }
    }

    private fun defaultLanguage(): String {
        val locale = Locale.getDefault()
        if (locale.language == "es") {
            val tag = "es-" + locale.country
            if (SPANISH_VARIANTS.any { it.first == tag }) return tag
        }
        return "es-US"
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
