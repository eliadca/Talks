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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.export.Exporter
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

    /**
     * Creates speeches from text or files shared with or opened in Talks. Markdown (from an AI
     * assistant, for instance) keeps its formatting.
     */
    private fun handleIncoming(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { home.importFiles(listOf(it)) }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    home.importFiles(listOf(uri))
                } else {
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
                    home.importText(text, intent.getStringExtra(Intent.EXTRA_SUBJECT), "Discurso importado")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) home.importFiles(uris)
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
                    onExportPdf = { title, doc ->
                        scope.launch { context.startActivity(exporter.pdfIntent(title, doc)) }
                    },
                    onShareText = { title, doc -> context.startActivity(exporter.textIntent(title, doc)) },
                    onShareMarkdown = { title, markdown -> context.startActivity(exporter.markdownIntent(title, markdown)) },
                )
            }
            composable("settings") {
                SettingsRoute(
                    settings = settings,
                    onChange = { change -> scope.launch { container.settings.update(change) } },
                    onBack = { nav.popBackStack() },
                    onImportFiles = { uris ->
                        home.setFilter(LibraryFilter.All)
                        home.importFiles(uris)
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
