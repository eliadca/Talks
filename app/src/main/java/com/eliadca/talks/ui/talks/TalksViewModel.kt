package com.eliadca.talks.ui.talks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliadca.talks.container
import com.eliadca.talks.core.doc.silentRanges
import com.eliadca.talks.core.track.ScriptIndex
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.data.LoadedSpeech
import com.eliadca.talks.data.db.TalkSessionEntity
import com.eliadca.talks.speech.AndroidSpeechEngine
import com.eliadca.talks.speech.EngineError
import com.eliadca.talks.speech.SessionSummary
import com.eliadca.talks.speech.SpeechEngine
import com.eliadca.talks.speech.SpeechEvent
import com.eliadca.talks.speech.TalksSession
import com.eliadca.talks.speech.VoskSpeechEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TalksPhase { LOADING, PREPARE, LIVE, ENDED }

/** Prepares, runs and closes one Talks session for a speech. */
class TalksViewModel(private val app: Application) : AndroidViewModel(app) {

    private val container = app.container

    var phase by mutableStateOf(TalksPhase.LOADING)
        private set
    var speech by mutableStateOf<LoadedSpeech?>(null)
        private set
    var index by mutableStateOf<ScriptIndex?>(null)
        private set
    var session by mutableStateOf<TalksSession?>(null)
        private set
    var summary by mutableStateOf<SessionSummary?>(null)
        private set

    /** Script word the run will start from; chosen by long-pressing the text while preparing. */
    var startToken by mutableIntStateOf(0)
        private set

    // --- microphone test ----------------------------------------------------------------------
    var testing by mutableStateOf(false)
        private set
    var testHeard by mutableStateOf("")
        private set
    var testLevel by mutableFloatStateOf(0f)
        private set
    var testError by mutableStateOf<EngineError?>(null)
        private set
    private var testEngine: SpeechEngine? = null
    private var testJob: Job? = null

    private var loadedFor: Long? = null

    fun load(speechId: Long, settings: AppSettings) {
        if (loadedFor == speechId) return
        loadedFor = speechId
        phase = TalksPhase.LOADING
        viewModelScope.launch {
            val s = container.speeches.load(speechId)
            if (s == null) {
                phase = TalksPhase.ENDED
                return@launch
            }
            speech = s
            buildIndex(s, settings.readHeadings)
            startToken = 0
            phase = TalksPhase.PREPARE
        }
    }

    private fun buildIndex(s: LoadedSpeech, readHeadings: Boolean) {
        index = ScriptIndex.build(s.doc.text, s.doc.silentRanges(readHeadings))
    }

    /** Rebuilds the index when the "read headings" setting changed while preparing. */
    fun refreshIndex(readHeadings: Boolean) {
        val s = speech ?: return
        if (phase != TalksPhase.PREPARE) return
        buildIndex(s, readHeadings)
        startToken = startToken.coerceIn(0, index?.size ?: 0)
    }

    fun setStartFromOffset(offset: Int) {
        val ix = index ?: return
        startToken = ix.tokenAtChar(offset).coerceIn(0, ix.size)
    }

    fun resetStart() {
        startToken = 0
    }

    /** Text offsets (spoken end, next start, next end) to show for a run that starts at [token]. */
    fun previewRanges(token: Int, highlightWords: Int): Triple<Int, Int, Int> {
        val ix = index ?: return Triple(0, 0, 0)
        val start = ix.startChar(token)
        val end = if (token >= ix.size) ix.text.length else ix.endChar(ix.chunkEnd(token, 3, highlightWords) - 1)
        return Triple(start, start, end)
    }

    // --- engines --------------------------------------------------------------------------------

    private fun createEngine(s: AppSettings): SpeechEngine = when (s.engine) {
        EngineKind.VOSK -> VoskSpeechEngine(app, container.voskModel.manager.modelDir)
        EngineKind.ANDROID -> AndroidSpeechEngine(
            app,
            AndroidSpeechEngine.Config(s.language, s.preferOffline, s.forceGoogleService, s.muteSounds),
        )
    }

    fun startTest() {
        if (testing) return
        testing = true
        testHeard = ""
        testLevel = 0f
        testError = null
        viewModelScope.launch {
            val s = container.settings.settings.first()
            val engine = createEngine(s)
            testEngine = engine
            testJob = launch {
                engine.events.collect { e ->
                    when (e) {
                        is SpeechEvent.Partial -> testHeard = e.text
                        is SpeechEvent.Final -> testHeard = e.text
                        is SpeechEvent.Level -> testLevel = e.value
                        is SpeechEvent.Failure -> {
                            testError = e.error
                            if (e.error.fatal) testing = false
                        }
                        is SpeechEvent.State -> {}
                    }
                }
            }
            engine.start()
        }
    }

    fun stopTest() {
        testJob?.cancel()
        testEngine?.release()
        testEngine = null
        testing = false
        testLevel = 0f
    }

    // --- running ---------------------------------------------------------------------------------

    fun begin() {
        val ix = index ?: return
        val sp = speech ?: return
        stopTest()
        viewModelScope.launch {
            val s = container.settings.settings.first()
            val engine = createEngine(s)
            val run = TalksSession(viewModelScope, ix, engine, s.highlightWords)
            session = run
            summary = null
            phase = TalksPhase.LIVE
            run.start(startToken)
            container.speeches.setLastTalkNow(sp.id)
        }
    }

    /** Ends the run, keeps its statistics and shows the summary. */
    fun finish() {
        val run = session ?: return
        val sp = speech ?: return
        val result = run.stop()
        run.release()
        session = null
        summary = result
        phase = TalksPhase.ENDED
        if (result.activeMs >= 30_000 && result.wordsCovered >= 15) {
            viewModelScope.launch {
                container.speeches.recordSession(
                    TalkSessionEntity(
                        speechId = sp.id,
                        startedAt = result.startedAtMs,
                        durationMs = result.activeMs,
                        wordsSpoken = result.wordsCovered,
                        wpm = result.wpm,
                        completion = result.completion,
                        engine = result.engineName,
                    ),
                )
            }
        }
    }

    /** Back to the preparation screen to run the speech again. */
    fun again() {
        summary = null
        startToken = 0
        phase = TalksPhase.PREPARE
    }

    override fun onCleared() {
        stopTest()
        session?.let {
            it.stop()
            it.release()
        }
        super.onCleared()
    }
}
