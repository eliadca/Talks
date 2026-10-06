package com.eliadca.talks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.firstLine
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.export.Exporter
import com.eliadca.talks.export.Importer
import com.eliadca.talks.ui.editor.EditorViewModel
import com.eliadca.talks.ui.home.HomeScreen
import com.eliadca.talks.ui.home.HomeViewModel
import com.eliadca.talks.ui.home.LibraryFilter
import com.eliadca.talks.ui.settings.SettingsRoute
import com.eliadca.talks.ui.talks.TalksScreen
import com.eliadca.talks.ui.theme.TalksTheme
import com.eliadca.talks.ui.theme.isDarkFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by Talks mode so that remote controls and volume keys can steer it. */
    var talksKeyHandler: ((KeyEvent) -> Boolean)? = null

    /** Shared with the Compose screens (same owner), so incoming files can open the new speech. */
    private val home: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TalksRoot() }
        if (savedInstanceState == null) handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Creates a speech from text or a file shared/opened from another app. */
    private fun handleIncoming(intent: Intent?) {
        val action = intent?.action
        if (intent == null || (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW)) return
        lifecycleScope.launch {
            try {
                val repo = application.container.speeches
                val uri: Uri? = if (action == Intent.ACTION_VIEW) intent.data
                else IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val id = if (uri != null) {
                    val imported = Importer(contentResolver).read(uri)
                    repo.create(imported.title, imported.doc)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return@launch
                    val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
                        ?: RichDoc(text).firstLine()
                    repo.create(title, RichDoc(text.replace("\r\n", "\n")))
                }
                home.setFilter(LibraryFilter.All)
                home.select(id)
                home.toast("Discurso importado")
            } catch (e: Exception) {
                home.toast(e.message ?: "No se pudo importar el archivo.")
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (talksKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun TalksRoot() {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val dark = isDarkFor(settings.themeMode)
    val scope = rememberCoroutineScope()

    val home: HomeViewModel = viewModel()
    val editor: EditorViewModel = viewModel()
    val exporter = androidx.compose.runtime.remember { Exporter(context) }
    val nav = rememberNavController()

    TalksTheme(darkTheme = dark) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    home = home,
                    editor = editor,
                    settings = settings,
                    dark = dark,
                    onStartTalks = { id -> nav.navigate("talks/$id") },
                    onOpenSettings = { nav.navigate("settings") },
                    onExportPdf = { id ->
                        scope.launch {
                            val speech = container.speeches.load(id) ?: return@launch
                            val intent = exporter.pdfIntent(speech.title, speech.doc)
                            context.startActivity(intent)
                        }
                    },
                    onShareText = { id ->
                        scope.launch {
                            val speech = container.speeches.load(id) ?: return@launch
                            context.startActivity(exporter.textIntent(speech.title, speech.doc))
                        }
                    },
                )
            }
            composable("settings") {
                SettingsRoute(
                    settings = settings,
                    onChange = { change -> scope.launch { container.settings.update(change) } },
                    onBack = { nav.popBackStack() },
                    onImported = { id ->
                        home.setFilter(LibraryFilter.All)
                        home.select(id)
                        nav.popBackStack()
                    },
                )
            }
            composable(
                "talks/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                TalksScreen(
                    speechId = id,
                    settings = settings,
                    onChangeSettings = { change -> scope.launch { container.settings.update(change) } },
                    onExit = {
                        nav.popBackStack()
                        home.refreshPace(settings.wordsPerMinute)
                    },
                )
            }
        }
    }
}
