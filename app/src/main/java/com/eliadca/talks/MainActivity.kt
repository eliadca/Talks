package com.eliadca.talks

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
import com.eliadca.talks.ui.settings.SettingsScreen
import com.eliadca.talks.ui.theme.TalksTheme
import com.eliadca.talks.ui.theme.isDarkFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set by Talks mode so that remote controls and volume keys can steer it. */
    var talksKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TalksRoot() }
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
                SettingsScreen(
                    settings = settings,
                    onChange = { change -> scope.launch { container.settings.update(change) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "talks/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                androidx.compose.material3.Text("Talks $id")
            }
        }
    }
}
