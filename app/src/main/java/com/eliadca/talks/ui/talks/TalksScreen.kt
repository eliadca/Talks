package com.eliadca.talks.ui.talks

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Flag
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliadca.talks.MainActivity
import com.eliadca.talks.core.doc.estimatedSeconds
import com.eliadca.talks.core.track.TrackStatus
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.data.ReaderTheme
import com.eliadca.talks.speech.EngineState
import com.eliadca.talks.speech.ModelDownload
import com.eliadca.talks.speech.TalksSession
import com.eliadca.talks.speech.VoskModelController
import com.eliadca.talks.container
import com.eliadca.talks.ui.findActivity
import com.eliadca.talks.ui.formatClock
import com.eliadca.talks.ui.home.formatDuration
import com.eliadca.talks.ui.theme.TalksTheme
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatUnderlined
import com.eliadca.talks.core.track.CharSpan
import com.eliadca.talks.core.track.MarkUnit
import com.eliadca.talks.core.track.Marking
import com.eliadca.talks.data.marking
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Green = Color(0xFF2ECC71)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFFF5252)
private val Gray = Color(0xFF8E8E93)
private val Blue = Color(0xFF4FA3FF)

@Composable
fun TalksScreen(
    speechId: Long,
    settings: AppSettings,
    onChangeSettings: ((AppSettings) -> AppSettings) -> Unit,
    onExit: () -> Unit,
) {
    val vm: TalksViewModel = viewModel()
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity

    LaunchedEffect(speechId) { vm.load(speechId, settings) }
    LaunchedEffect(settings.readHeadings) { vm.refreshIndex(settings.readHeadings) }

    val phase = vm.phase
    val index = vm.index
    val session = vm.session
    val doc = vm.speech?.doc
    val palette = remember(settings.readerTheme) { ReaderPalette.of(settings.readerTheme) }

    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    // Never keep listening in the background (and never leave the media volume muted).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.stopTest()
        vm.session?.pause()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasMic = granted }
    val model = context.container.voskModel
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importZip(uri)
    }

    ImmersiveAndAwake()

    var confirmExit by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var following by remember { mutableStateOf(true) }
    var touchLocked by remember { mutableStateOf(false) }
    /** The bar to choose how what comes next is marked, and how far ahead of the voice. */
    var markingOpen by remember { mutableStateOf(false) }
    val readerRef = remember { mutableStateOf<ReaderView?>(null) }

    fun touch() {
        lastInteraction = System.currentTimeMillis()
        controlsVisible = true
    }

    /** Back to following the voice from script word [token], with the text already in place. */
    fun followVoiceFrom(token: Int) {
        val run = vm.session ?: return
        val ix = vm.index ?: return
        val reader = readerRef.value
        val t = token.coerceIn(0, ix.size)
        reader?.setManualMode(false)
        reader?.let {
            val start = ix.startChar(t)
            it.setProgress(start, start, start, jump = true)
            it.resumeFollow(animated = false)
        }
        if (run.state.value.manual) run.leaveManual(t) else run.setPosition(t)
        following = true
    }

    /** Continues from the line the speaker is looking at (the reading line). */
    fun followFromReadingLine() {
        val reader = readerRef.value ?: return
        val ix = vm.index ?: return
        followVoiceFrom(ix.tokenAtChar(reader.readingLineOffset()))
        touch()
    }

    fun enterManual() {
        val run = vm.session ?: return
        readerRef.value?.setManualMode(true)
        run.enterManual()
        following = true
        touch()
    }

    BackHandler {
        when {
            markingOpen -> markingOpen = false
            phase == TalksPhase.LIVE -> confirmExit = true
            else -> onExit()
        }
    }

    // Controls (and the marking bar) fade out a few seconds after the last touch, once the talk is under way.
    LaunchedEffect(phase, controlsVisible, lastInteraction) {
        if (phase == TalksPhase.LIVE && controlsVisible) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
            markingOpen = false
        }
    }

    // The marking can change during the run, from the live bar or the settings.
    LaunchedEffect(session, settings.markUnit, settings.markLead, settings.highlightWords) {
        session?.setMarking(settings.marking())
    }

    // Steer the run with a presentation remote or the volume keys.
    val currentSession by rememberUpdatedState(session)
    val keyHandler = rememberUpdatedState<(KeyEvent) -> Boolean>(
        { ev ->
            handleKey(
                ev, vm.phase, currentSession, settings.volumeKeys,
                onManualScroll = { lines -> readerRef.value?.scrollLines(lines) },
                onToggleManual = { if (currentSession?.state?.value?.manual == true) followFromReadingLine() else enterManual() },
            ) { touch() }
        },
    )
    DisposableEffect(activity) {
        activity?.talksKeyHandler = { ev -> keyHandler.value(ev) }
        onDispose { activity?.talksKeyHandler = null }
    }

    val longPressHandler = rememberUpdatedState<(Int) -> Unit>(
        { offset ->
            val ix = vm.index
            if (ix != null && !(touchLocked && vm.phase == TalksPhase.LIVE)) {
                when (vm.phase) {
                    TalksPhase.LIVE -> followVoiceFrom(ix.tokenAtChar(offset))
                    TalksPhase.PREPARE -> vm.setStartFromOffset(offset)
                    else -> {}
                }
            }
            touch()
        },
    )
    val tapHandler = rememberUpdatedState<() -> Unit>(
        {
            if (vm.phase == TalksPhase.LIVE && !touchLocked) {
                controlsVisible = !controlsVisible
                lastInteraction = System.currentTimeMillis()
            }
        },
    )

    // Panels and dialogs follow the stage: dark on a dark stage, light on a light one.
    val darkStage = Color(palette.background).luminance() < 0.5f
    TalksTheme(darkTheme = darkStage) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(palette.background)),
    ) {
        if (doc != null) {
            AndroidView(
                // The text keeps clear of the camera cutout; the background still fills the screen.
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
                factory = { ctx ->
                    ReaderView(ctx).apply {
                        setDocument(doc)
                        listener = object : ReaderView.Listener {
                            override fun onLongPress(offset: Int) = longPressHandler.value(offset)
                            override fun onTap() = tapHandler.value()
                            override fun onUserScroll() { following = false }
                            override fun onFollowResumed() { following = true }
                        }
                        readerRef.value = this
                    }
                },
                update = { reader ->
                    reader.configure(
                        ReaderConfig(
                            fontSp = settings.readerFontSp.toFloat(),
                            lineSpacing = settings.readerLineSpacing,
                            serif = settings.readerFont == com.eliadca.talks.data.ReaderFont.SERIF,
                            palette = palette,
                            anchor = settings.readerAnchor,
                            dimSpoken = settings.dimSpoken,
                            guide = settings.markUnit == MarkUnit.NONE,
                        ),
                    )
                    reader.scaleX = if (settings.mirror) -1f else 1f
                },
                onRelease = { readerRef.value = null },
            )
        }

        // Feed the reader: the chosen start while preparing, the live position while running.
        val reader = readerRef.value
        LaunchedEffect(reader, phase, vm.startToken, settings.markUnit, settings.markLead, settings.highlightWords, index) {
            if (reader != null && index != null && (phase == TalksPhase.PREPARE || phase == TalksPhase.LOADING)) {
                val (focus, marks) = vm.preview(vm.startToken, settings.marking())
                reader.setProgress(0, focus, marks, jump = true)
            }
        }
        LaunchedEffect(reader, session) {
            if (reader != null && session != null) {
                reader.resumeFollow(animated = false)
                // Only what the reader shows; the clock and the microphone level change far more often.
                session.state
                    .map { ReaderFeed(it.manual, it.spokenEnd, it.focus, it.marks) }
                    .distinctUntilChanged()
                    .collect { f ->
                        reader.setManualMode(f.manual)
                        reader.setProgress(f.spokenEnd, f.focus, f.marks)
                    }
            }
        }

        when (phase) {
            TalksPhase.LOADING -> Text(
                "Cargando…",
                color = Color(palette.text),
                modifier = Modifier.align(Alignment.Center),
            )
            TalksPhase.PREPARE -> PreparePanel(
                vm = vm,
                model = model,
                settings = settings,
                hasMic = hasMic,
                onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onImportModel = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onChangeSettings = onChangeSettings,
                onStart = {
                    if (hasMic) vm.begin() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onCancel = onExit,
                // Beside the text on a landscape tablet, so the speech stays in view; below it otherwise.
                modifier = if (androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ) {
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(460.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp)
                },
            )
            TalksPhase.LIVE -> if (session != null) {
                LiveOverlay(
                    session = session,
                    targetMinutes = vm.speech?.targetMinutes ?: 0,
                    totalSeconds = remember(doc, settings.wordsPerMinute, settings.readHeadings) {
                        doc?.estimatedSeconds(settings.wordsPerMinute, settings.readHeadings) ?: 0
                    },
                    palette = palette,
                    controlsVisible = controlsVisible,
                    following = following,
                    locked = touchLocked,
                    showHeard = settings.showHeard,
                    marking = settings.marking(),
                    markingOpen = markingOpen,
                    actions = LiveActions(
                        onToggleLock = { touchLocked = !touchLocked; controlsVisible = true; lastInteraction = System.currentTimeMillis() },
                        onFollow = { readerRef.value?.resumeFollow(); following = true; touch() },
                        onFollowFromHere = { followFromReadingLine() },
                        onManual = { on -> if (on) enterManual() else followFromReadingLine() },
                        onPauseResume = { st ->
                            if (st.listening || st.auto) session.pause() else session.resume()
                            touch()
                        },
                        onAuto = { st ->
                            if (st.auto) session.stopAuto() else session.startAuto(settings.wordsPerMinute)
                            touch()
                        },
                        onAutoSpeed = { delta ->
                            session.setAutoSpeed(session.state.value.autoWpm + delta)
                            touch()
                        },
                        onFont = { delta ->
                            onChangeSettings { it.copy(readerFontSp = (it.readerFontSp + delta).coerceIn(24, 140)) }
                            touch()
                        },
                        onTheme = {
                            onChangeSettings { it.copy(readerTheme = nextTheme(it.readerTheme)) }
                            touch()
                        },
                        onMarking = { markingOpen = !markingOpen; touch() },
                        onMarkUnit = { unit ->
                            onChangeSettings { it.copy(markUnit = unit) }
                            touch()
                        },
                        onMarkLead = { delta ->
                            onChangeSettings { it.copy(markLead = (it.markLead + delta).coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)) }
                            touch()
                        },
                        onFinish = { confirmExit = true },
                        onRetry = { session.retry(); touch() },
                    ),
                )
            }
            TalksPhase.ENDED -> EndedPanel(
                vm = vm,
                onAgain = vm::again,
                onExit = onExit,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Terminar el modo Talks?") },
            text = { Text("Se detiene la escucha y se guarda tu ritmo de lectura para estimar mejor la duración de tus discursos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.finish()
                }) { Text("Terminar") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir hablando") } },
        )
    }
    }
}

private fun nextTheme(t: ReaderTheme): ReaderTheme = when (t) {
    ReaderTheme.NIGHT -> ReaderTheme.STAGE
    ReaderTheme.STAGE -> ReaderTheme.DAY
    ReaderTheme.DAY -> ReaderTheme.SEPIA
    ReaderTheme.SEPIA -> ReaderTheme.NIGHT
}

private const val CONTROLS_HIDE_MS = 5_000L

/**
 * The status (with the time) at the top left and the controls at the top right; when they do not fit
 * side by side (a narrower screen, a bigger font) the controls go just below the status.
 */
@Composable
private fun StatusAndControls(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val status = measurables[0].measure(loose)
        val controls = measurables.getOrNull(1)?.measure(loose)
        val gap = 12.dp.roundToPx()
        val beside = controls == null || status.width + gap + controls.width <= constraints.maxWidth
        val height = when {
            controls == null -> status.height
            beside -> maxOf(status.height, controls.height)
            else -> status.height + gap / 2 + controls.height
        }
        layout(constraints.maxWidth, height) {
            status.place(0, 0)
            controls?.place(constraints.maxWidth - controls.width, if (beside) 0 else status.height + gap / 2)
        }
    }
}

/** Width that fits the status (time) and the whole controls bar side by side. */
private val CONTROLS_BESIDE_STATUS = 1220.dp

/**
 * Presenter remotes send page/arrow/media keys; the volume keys can be used when enabled. In
 * manual mode next/previous scroll the text. The "black screen" key of most remotes (B or .)
 * switches manual mode on and off.
 */
private fun handleKey(
    ev: KeyEvent,
    phase: TalksPhase,
    session: TalksSession?,
    volumeKeys: Boolean,
    onManualScroll: (Int) -> Unit,
    onToggleManual: () -> Unit,
    onUsed: () -> Unit,
): Boolean {
    if (phase != TalksPhase.LIVE || session == null) return false
    val code = ev.keyCode
    val next = code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
        code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_MEDIA_NEXT ||
        (volumeKeys && code == KeyEvent.KEYCODE_VOLUME_DOWN)
    val previous = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_DPAD_LEFT ||
        code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
        (volumeKeys && code == KeyEvent.KEYCODE_VOLUME_UP)
    val toggle = code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK
    val manualKey = code == KeyEvent.KEYCODE_B || code == KeyEvent.KEYCODE_PERIOD
    if (!next && !previous && !toggle && !manualKey) return false
    if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
        val manual = session.state.value.manual
        when {
            manualKey -> onToggleManual()
            manual && next -> onManualScroll(3)
            manual && previous -> onManualScroll(-3)
            manual -> onToggleManual()
            next -> session.nudge(1)
            previous -> session.nudge(-1)
            else -> if (session.state.value.listening) session.pause() else session.resume()
        }
        onUsed()
    }
    return true
}

@Composable
private fun ImmersiveAndAwake() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// ==========================================================================================
// Live
// ==========================================================================================

/** What the reader is given from the session; everything else in the state is for the overlay. */
private data class ReaderFeed(val manual: Boolean, val spokenEnd: Int, val focus: Int, val marks: List<CharSpan>)

/** What the live controls can do. */
private class LiveActions(
    val onToggleLock: () -> Unit,
    val onFollow: () -> Unit,
    val onFollowFromHere: () -> Unit,
    val onManual: (Boolean) -> Unit,
    val onPauseResume: (TalksSession.State) -> Unit,
    val onAuto: (TalksSession.State) -> Unit,
    val onAutoSpeed: (Int) -> Unit,
    val onFont: (Int) -> Unit,
    val onTheme: () -> Unit,
    val onMarking: () -> Unit,
    val onMarkUnit: (MarkUnit) -> Unit,
    val onMarkLead: (Int) -> Unit,
    val onFinish: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
private fun LiveOverlay(
    session: TalksSession,
    targetMinutes: Int,
    totalSeconds: Int,
    palette: ReaderPalette,
    controlsVisible: Boolean,
    following: Boolean,
    locked: Boolean,
    showHeard: Boolean,
    marking: Marking,
    markingOpen: Boolean,
    actions: LiveActions,
) {
    val st by session.state.collectAsState()
    val ink = Color(palette.text)

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        // Status: always visible, so the speaker can trust at a glance that the app is listening. The
        // controls sit beside it when both fit, otherwise just below it: the time is never covered.
        val besideStatus = maxWidth >= CONTROLS_BESIDE_STATUS
        StatusAndControls(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp)) {
            StatusPill(st, targetMinutes, totalSeconds, palette, Modifier.padding(2.dp))
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                ControlsBar(st, palette, locked, markingOpen, actions)
            }
        }

        // Problems with the recogniser.
        st.error?.takeIf { !st.manual }?.let { err ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = if (besideStatus) 104.dp else 160.dp, start = 16.dp, end = 16.dp).widthIn(max = 680.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (err.fatal) Color(0xFFB3261E) else Color(0xFF8A5A00),
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(err.message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        if (err.fatal) {
                            Text(
                                "Sigue en modo manual: desliza el texto o usa el control remoto.",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (err.fatal) {
                        TextButton(onClick = { actions.onManual(true) }) { Text("Manual", color = Color.White, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = actions.onRetry) { Text("Reintentar", color = Color.White) }
                    }
                }
            }
        }

        // The end of the speech.
        if (st.finished && !st.manual) {
            Surface(
                Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(28.dp),
                color = Color(palette.accent),
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Flag, null, tint = contentOn(Color(palette.accent)), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Fin del discurso", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = contentOn(Color(palette.accent)))
                    TextButton(onClick = actions.onFinish) {
                        Text("Terminar y ver resumen", color = contentOn(Color(palette.accent)), fontSize = 16.sp)
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                st.manual -> ManualBar(palette, actions.onFollowFromHere)
                markingOpen -> MarkingBar(marking, palette, actions)
                !following -> RecoverBar(palette, actions.onFollow, actions.onFollowFromHere)
                st.auto -> AutoSpeedBar(st, palette, actions)
            }
            if (showHeard && st.heard.isNotBlank() && !st.manual) {
                Text(
                    st.heard,
                    color = ink.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            LinearProgressIndicator(
                progress = { st.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(palette.accent),
                trackColor = ink.copy(alpha = 0.12f),
            )
        }
    }
}

/** Black or white, whichever reads better on [background]. */
private fun contentOn(background: Color): Color = if (background.luminance() > 0.45f) Color.Black else Color.White

@Composable
private fun ControlsBar(st: TalksSession.State, palette: ReaderPalette, locked: Boolean, markingOpen: Boolean, a: LiveActions) {
    val ink = Color(palette.text)
    val accent = Color(palette.accent)
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(palette.background).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ink.copy(alpha = 0.16f)),
        shadowElevation = 10.dp,
    ) {
        Row(
            Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModeSwitch(st.manual, ink, accent, onVoice = { a.onManual(false) }, onManual = { a.onManual(true) })
            BarDivider(ink)
            if (!st.manual) {
                val running = st.listening || st.auto
                BarButton(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (running) "Pausa" else "Seguir", ink, accent, active = !running,
                ) { a.onPauseResume(st) }
                BarButton(Icons.Filled.Speed, "Auto", ink, accent, active = st.auto) { a.onAuto(st) }
                BarButton(Icons.Filled.FormatUnderlined, "Marcado", ink, accent, active = markingOpen) { a.onMarking() }
                BarDivider(ink)
            }
            BarGlyphButton(15, "Letra −", ink) { a.onFont(-4) }
            BarGlyphButton(24, "Letra +", ink) { a.onFont(4) }
            BarButton(Icons.Filled.Palette, "Colores", ink, accent) { a.onTheme() }
            BarButton(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (locked) "Bloqueado" else "Bloquear", ink, accent, active = locked) { a.onToggleLock() }
            BarDivider(ink)
            BarButton(Icons.Filled.Close, "Salir", ink, accent) { a.onFinish() }
        }
    }
}

/** Voice following or everything by hand: the one switch to reach for when something goes wrong. */
@Composable
private fun ModeSwitch(manual: Boolean, ink: Color, accent: Color, onVoice: () -> Unit, onManual: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeSegment(Icons.Filled.RecordVoiceOver, "Voz", !manual, ink, accent, onVoice)
        ModeSegment(Icons.Filled.PanTool, "Manual", manual, ink, accent, onManual)
    }
}

@Composable
private fun ModeSegment(icon: ImageVector, label: String, selected: Boolean, ink: Color, accent: Color, onClick: () -> Unit) {
    val fg = if (selected) contentOn(accent) else ink
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, ink: Color, accent: Color, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .widthIn(min = 64.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (active) accent else ink.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) contentOn(accent) else ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(3.dp))
        Text(label, color = ink.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

/** A text-size button: a small or a large "A". */
@Composable
private fun BarGlyphButton(glyphSp: Int, label: String, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .widthIn(min = 64.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = ink, fontSize = glyphSp.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(3.dp))
        Text(label, color = ink.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun BarDivider(ink: Color) {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(40.dp)
            .background(ink.copy(alpha = 0.14f)),
    )
}

/** Shown in manual mode: how it works, and the way back to following the voice. */
@Composable
private fun ManualBar(palette: ReaderPalette, onFollowFromHere: () -> Unit) {
    val ink = Color(palette.text)
    val accent = Color(palette.accent)
    Surface(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).widthIn(max = 860.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(palette.background),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PanTool, null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text("Modo manual", color = ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Desliza el texto con el dedo y lee en la línea marcada.",
                    color = ink.copy(alpha = 0.75f), fontSize = 14.sp,
                )
            }
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = onFollowFromHere,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contentOn(accent)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Filled.RecordVoiceOver, null)
                Spacer(Modifier.size(8.dp))
                Text("Seguir con la voz desde aquí", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** After scrolling by hand while following: back to the marker, or carry on from where you looked. */
@Composable
private fun RecoverBar(palette: ReaderPalette, onBack: () -> Unit, onHere: () -> Unit) {
    val ink = Color(palette.text)
    val accent = Color(palette.accent)
    Row(
        Modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.5.dp, ink.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(palette.background).copy(alpha = 0.9f), contentColor = ink),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Filled.MyLocation, null)
            Spacer(Modifier.size(8.dp))
            Text("Volver a donde voy", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Button(
            onClick = onHere,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contentOn(accent)),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Filled.VerticalAlignCenter, null)
            Spacer(Modifier.size(8.dp))
            Text("Seguir desde aquí", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** How what comes next is marked, and how far ahead of (or behind) the voice. */
@Composable
private fun MarkingBar(marking: Marking, palette: ReaderPalette, a: LiveActions) {
    val ink = Color(palette.text)
    val accent = Color(palette.accent)
    Surface(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).widthIn(max = 940.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(palette.background).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.FormatUnderlined, null, tint = accent, modifier = Modifier.size(24.dp))
                for ((unit, label) in MARK_UNITS) {
                    val selected = marking.unit == unit
                    Text(
                        label,
                        color = if (selected) contentOn(accent) else ink,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) accent else ink.copy(alpha = 0.08f))
                            .clickable(onClickLabel = label) { a.onMarkUnit(unit) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { a.onMarkLead(-1) },
                    enabled = marking.lead > Marking.MIN_LEAD,
                    border = BorderStroke(1.dp, ink.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
                ) {
                    Icon(Icons.Filled.ChevronLeft, null)
                    Text("Atrasar", fontSize = 16.sp)
                }
                Text(
                    leadLabel(marking.lead),
                    color = ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                OutlinedButton(
                    onClick = { a.onMarkLead(1) },
                    enabled = marking.lead < Marking.MAX_LEAD,
                    border = BorderStroke(1.dp, ink.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
                ) {
                    Text("Adelantar", fontSize = 16.sp)
                    Icon(Icons.Filled.ChevronRight, null)
                }
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = a.onMarking,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = contentOn(accent)),
                ) { Text("Listo", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

private val MARK_UNITS = listOf(
    MarkUnit.PHRASE to "Frase",
    MarkUnit.SENTENCE to "Oración",
    MarkUnit.PARAGRAPH to "Párrafo",
    MarkUnit.WORD to "Palabra a palabra",
    MarkUnit.NONE to "Sin marcar",
)

/** "Al ritmo de tu voz", "1 palabra por delante", "2 palabras por detrás"... */
private fun leadLabel(lead: Int): String = when {
    lead == 0 -> "Al ritmo de tu voz"
    lead == 1 -> "1 palabra por delante"
    lead > 1 -> "$lead palabras por delante"
    lead == -1 -> "1 palabra por detrás"
    else -> "${-lead} palabras por detrás"
}

@Composable
private fun AutoSpeedBar(st: TalksSession.State, palette: ReaderPalette, a: LiveActions) {
    val ink = Color(palette.text)
    Surface(
        Modifier.padding(bottom = 16.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(palette.background).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Blue.copy(alpha = 0.7f)),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Speed, null, tint = Blue, modifier = Modifier.padding(start = 8.dp).size(24.dp))
            TextButton(onClick = { a.onAutoSpeed(-10) }) { Text("−10", color = ink, fontSize = 20.sp) }
            Text("${st.autoWpm} palabras/min", color = ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { a.onAutoSpeed(10) }) { Text("+10", color = ink, fontSize = 20.sp) }
            TextButton(onClick = { a.onAuto(st) }) { Text("Volver a escuchar", color = Blue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StatusPill(st: TalksSession.State, targetMinutes: Int, totalSeconds: Int, palette: ReaderPalette, modifier: Modifier) {
    val ink = Color(palette.text)
    val (color, label) = when {
        st.manual -> Blue to "Manual"
        st.auto -> Blue to "Avance automático"
        st.error?.fatal == true -> Red to "Sin escucha"
        !st.listening -> Gray to "En pausa"
        st.error != null -> Amber to "Reconectando"
        st.status == TrackStatus.OFF_SCRIPT -> Amber to "Improvisando"
        st.status == TrackStatus.SEARCHING -> Amber to "Buscando"
        st.engineState == EngineState.IDLE || st.engineState == EngineState.STARTING -> Gray to "Iniciando"
        st.status == TrackStatus.WAITING -> Green to "Escuchando"
        else -> Green to "Siguiendo"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(palette.background).copy(alpha = 0.9f))
            .border(1.dp, ink.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        if (!st.manual) {
            // Microphone level: proof that the room is being heard.
            Box(Modifier.width(28.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(st.level.coerceIn(0.02f, 1f)).background(color))
            }
        }
        Text(label, color = ink.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(formatClock(st.elapsedMs), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (totalSeconds > 0 && st.progress > 0.02f && !st.finished) {
            // What is left at the speaker's usual pace.
            val left = (totalSeconds * (1f - st.progress)).toLong().coerceAtLeast(0)
            Text("quedan ${formatClock(left * 1000)}", color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        if (targetMinutes > 0 && st.progress > 0.03f) {
            // Seconds ahead of (+) or behind (-) the time planned for the part already read.
            val planned = st.progress * targetMinutes * 60
            val ahead = (planned - st.elapsedMs / 1000f).toInt()
            if (kotlin.math.abs(ahead) >= 10) {
                Text(
                    (if (ahead > 0) "+" else "−") + formatClock(kotlin.math.abs(ahead) * 1000L),
                    color = if (ahead > 0) Green else Amber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ==========================================================================================
// Preparing
// ==========================================================================================

@Composable
private fun PreparePanel(
    vm: TalksViewModel,
    model: VoskModelController,
    settings: AppSettings,
    hasMic: Boolean,
    onRequestMic: () -> Unit,
    onImportModel: () -> Unit,
    onChangeSettings: ((AppSettings) -> AppSettings) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val serviceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val doc = vm.speech?.doc
    val seconds = doc?.estimatedSeconds(settings.wordsPerMinute, settings.readHeadings) ?: 0
    val voskInstalled by model.installed.collectAsState()
    val engineReady = context.container.speechEngineFactory != null || when (settings.engine) {
        EngineKind.ANDROID -> serviceAvailable
        EngineKind.VOSK -> voskInstalled
    }
    val canStart = engineReady

    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        color = colors.surface,
        tonalElevation = 4.dp,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            // --- the speech ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.speech?.title?.ifBlank { "Sin título" } ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        InfoChip("≈ ${formatDuration(seconds)}")
                        (vm.speech?.targetMinutes ?: 0).takeIf { it > 0 }?.let { InfoChip("Objetivo $it min") }
                        InfoChip("${settings.wordsPerMinute} pal/min")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --- ready to listen ---
                PrepareCard("Listo para escucharte") {
                    CheckRow(
                        ok = hasMic, title = "Micrófono",
                        detail = if (hasMic) "Permiso concedido" else "Talks necesita el micrófono para escucharte.",
                    ) {
                        if (!hasMic) Button(onClick = onRequestMic) { Text("Conceder permiso") }
                    }
                    val engineDetail = when (settings.engine) {
                        EngineKind.ANDROID ->
                            if (serviceAvailable) "Servicio de voz de Android · ${settings.language}" + if (settings.preferOffline) " · sin conexión preferido" else ""
                            else "Este dispositivo no tiene servicio de reconocimiento. Usa el modo sin conexión."
                        EngineKind.VOSK ->
                            if (voskInstalled) "Sin conexión (Vosk) · modelo instalado, ${remember(voskInstalled) { model.manager.sizeOnDiskMb() }} MB"
                            else "Falta el modelo de voz sin conexión (unos 40 MB)."
                    }
                    CheckRow(ok = engineReady, title = "Reconocimiento de voz", detail = engineDetail) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.engine == EngineKind.ANDROID,
                                onClick = { onChangeSettings { it.copy(engine = EngineKind.ANDROID) } },
                                label = { Text("Android") },
                            )
                            FilterChip(
                                selected = settings.engine == EngineKind.VOSK,
                                onClick = { onChangeSettings { it.copy(engine = EngineKind.VOSK) } },
                                label = { Text("Sin conexión (Vosk)") },
                            )
                        }
                        if (settings.engine == EngineKind.VOSK) ModelControls(model, onImportModel)
                    }
                    // Microphone test
                    CheckRow(
                        ok = null, title = "Prueba de reconocimiento",
                        detail = when {
                            vm.testError != null -> vm.testError?.message ?: ""
                            vm.testing && vm.testHeard.isBlank() -> "Habla en voz alta, como en el escenario…"
                            vm.testing -> "«${vm.testHeard}»"
                            else -> "Comprueba que la app te entiende antes de empezar."
                        },
                    ) {
                        if (vm.testing) {
                            LinearProgressIndicator(progress = { vm.testLevel }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        }
                        OutlinedButton(
                            onClick = { if (vm.testing) vm.stopTest() else if (hasMic) vm.startTest() else onRequestMic() },
                            enabled = engineReady,
                        ) {
                            Icon(Icons.Filled.Mic, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (vm.testing) "Detener prueba" else "Probar micrófono")
                        }
                    }
                }

                // --- how the text is marked ---
                PrepareCard("Cómo se marca lo que viene") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((unit, label) in MARK_UNITS) {
                            FilterChip(
                                selected = settings.markUnit == unit,
                                onClick = { onChangeSettings { it.copy(markUnit = unit) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (settings.markUnit != MarkUnit.NONE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onChangeSettings { it.copy(markLead = (it.markLead - 1).coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)) } },
                                enabled = settings.markLead > Marking.MIN_LEAD,
                            ) { Text("− Atrasar") }
                            Text(
                                leadLabel(settings.markLead),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            TextButton(
                                onClick = { onChangeSettings { it.copy(markLead = (it.markLead + 1).coerceIn(Marking.MIN_LEAD, Marking.MAX_LEAD)) } },
                                enabled = settings.markLead < Marking.MAX_LEAD,
                            ) { Text("Adelantar +") }
                        }
                    }
                }

                // --- where to start ---
                val startsAtBeginning = vm.startToken == 0
                PrepareCard("Dónde empezar") {
                    Text(
                        if (startsAtBeginning) {
                            "Desde el principio. Mantén pulsada una palabra del texto para empezar desde ahí."
                        } else {
                            val ix = vm.index
                            val snippet = ix?.text?.let { t ->
                                val from = ix.startChar(vm.startToken)
                                t.substring(from, minOf(t.length, from + 60)).replace('\n', ' ')
                            } ?: ""
                            "Desde «$snippet…»"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    if (!startsAtBeginning) TextButton(onClick = vm::resetStart) { Text("Volver al principio") }
                }
            }

            // --- start ---
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(28.dp))
                Spacer(Modifier.size(10.dp))
                Text("COMENZAR", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            TextButton(
                onClick = {
                    vm.stopTest()
                    onCancel()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Cancelar") }
        }
    }
}

/** A small piece of information about the speech, in a pill. */
@Composable
private fun InfoChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** One group of the preparation panel, on a soft card. */
@Composable
private fun PrepareCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun ModelControls(model: VoskModelController, onImport: () -> Unit) {
    val p by model.progress.collectAsState()
    val installed by model.installed.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val state = p) {
            is ModelDownload.Progress -> {
                if (state.fraction >= 0) LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.fraction >= 0) "Descargando… ${(state.fraction * 100).toInt()} %" else "Descargando…",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = model::cancel) { Text("Cancelar") }
                }
            }
            ModelDownload.Installing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Instalando el modelo…", style = MaterialTheme.typography.bodySmall)
            }
            is ModelDownload.Failed -> {
                Text(
                    "No se pudo instalar: ${state.message}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {}
        }
        if (p !is ModelDownload.Progress && p != ModelDownload.Installing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!installed) {
                    Button(onClick = model::download) {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Descargar modelo")
                    }
                }
                OutlinedButton(onClick = onImport) { Text("Importar .zip") }
            }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean?, title: String, detail: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (icon, tint) = when (ok) {
            true -> Icons.Filled.CheckCircle to Green
            false -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
            null -> Icons.Filled.Mic to MaterialTheme.colorScheme.primary
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}

// ==========================================================================================
// Ended
// ==========================================================================================

@Composable
private fun EndedPanel(vm: TalksViewModel, onAgain: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    val s = vm.summary
    Surface(
        modifier.padding(24.dp).widthIn(max = 640.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Sesión terminada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    vm.speech?.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (s != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(formatClock(s.activeMs), "Tiempo hablando", Modifier.weight(1f))
                    StatTile("${(s.completion * 100).toInt()} %", "${s.wordsCovered} de ${s.totalWords} palabras", Modifier.weight(1f))
                    StatTile(if (s.wpm > 0) "${s.wpm}" else "—", "Palabras por minuto", Modifier.weight(1f))
                }
                Text(
                    if (s.wpm > 0) "Talks usará este ritmo para calcular cuánto dura cada discurso."
                    else "Fue demasiado corta para medir tu ritmo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.Replay, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Practicar de nuevo")
                }
                Button(onClick = onExit, modifier = Modifier.weight(1f).height(52.dp)) { Text("Salir", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
