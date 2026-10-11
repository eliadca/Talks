package com.eliadca.talks.ui.notes

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliadca.talks.bible.BibleInfo
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.container
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.bible.BibleReferences
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.ink.InkHistory
import com.eliadca.talks.data.AppSettings
import com.eliadca.talks.data.EngineKind
import com.eliadca.talks.data.db.NoteEntity
import com.eliadca.talks.data.db.NoteListItem
import com.eliadca.talks.speech.AndroidSpeechEngine
import com.eliadca.talks.speech.EngineState
import com.eliadca.talks.speech.SpeechEngine
import com.eliadca.talks.speech.SpeechEvent
import com.eliadca.talks.speech.VoskSpeechEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TranscriptEntry(val id: Long, val text: String, val added: Boolean = false, val confirmed: Boolean = true)

class NotesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val services = app.container
    private val repo = services.notes
    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteListItem>())
    val saveError = repo.error
    var note by mutableStateOf<NoteEntity?>(null); private set
    var body by mutableStateOf(TextFieldValue("")); private set
    var ink by mutableStateOf(InkDocument()); private set
    var canUndoInk by mutableStateOf(false); private set
    var canRedoInk by mutableStateOf(false); private set
    private val inkHistory = InkHistory()
    private var savedInk = "{}"
    var entries by mutableStateOf(emptyList<TranscriptEntry>()); private set
    var partial by mutableStateOf(""); private set
    var listening by mutableStateOf(false); private set
    var engineState by mutableStateOf(EngineState.IDLE); private set
    var level by mutableFloatStateOf(0f); private set
    var message by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var bible by mutableStateOf<BibleInfo?>(null); private set
    var importing by mutableStateOf(false); private set
    var importProgress by mutableStateOf(0); private set
    var citationsEnabled by mutableStateOf(true); private set
    var passage by mutableStateOf<BiblePassage?>(null); private set
    var passageExpanded by mutableStateOf(true); private set
    var detected by mutableStateOf(emptyList<BibleReference>()); private set
    private var engine: SpeechEngine? = null
    private var collector: Job? = null
    private var lookup: Job? = null
    private var editSave: Job? = null
    private var generation = 0
    private var lastDetection: BibleReference? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            try { bible = services.bible.info() }
            catch (_: Exception) { message = "Vuelve a importar la Biblia para activar las citas." }
        }
    }

    fun create() {
        if (loading) return
        pause(); loading = true
        viewModelScope.launch {
            try { display(repo.create()) }
            catch (e: Exception) { report(e) }
            finally { loading = false }
        }
    }

    fun open(id: Long) {
        if (loading || note?.id == id) return
        pause(); loading = true
        viewModelScope.launch {
            try { repo.load(id)?.let(::display) }
            catch (e: Exception) { report(e) }
            finally { loading = false }
        }
    }

    private fun display(value: NoteEntity) {
        val transcript = try { json.decodeFromString<List<TranscriptEntry>>(value.transcript) }
        catch (e: Exception) { message = "No se pudo leer la transcripción guardada."; return }
        val drawing = try { json.decodeFromString<InkDocument>(value.ink) }
        catch (e: Exception) { message = "No se pudo leer la escritura guardada. Conserva una copia de seguridad."; return }
        lookup?.cancel(); lastDetection = null; passage = null; detected = emptyList()
        note = value
        body = TextFieldValue(value.body, TextRange(value.body.length))
        entries = transcript
        inkHistory.reset(drawing); ink = drawing; canUndoInk = false; canRedoInk = false
        savedInk = value.ink
    }

    fun backToList() { pause(); lookup?.cancel(); note = null; passage = null; detected = emptyList() }
    fun title(value: String) { note = note?.copy(title = value.take(160)); scheduleEditSave() }
    fun edit(value: TextFieldValue) {
        val changed = value.text != body.text
        body = value
        if (changed) scheduleEditSave()
    }
    fun automatic(value: Boolean) { note = note?.copy(automatic = value); persist() }
    fun editInk(value: InkDocument) {
        if (note == null || value == ink) return
        inkHistory.record(value); syncInk()
    }
    fun undoInk() { inkHistory.undo(); syncInk() }
    fun redoInk() { inkHistory.redo(); syncInk() }
    private fun syncInk() {
        ink = inkHistory.document; canUndoInk = inkHistory.canUndo; canRedoInk = inkHistory.canRedo
        savedInk = json.encodeToString(ink)
        persist()
    }
    fun toggleCitations(value: Boolean) {
        citationsEnabled = value
        lastDetection = null
        if (!value) { lookup?.cancel(); passage = null; detected = emptyList() }
    }
    fun dismissMessage() { message = null }
    fun permissionDenied() {
        message = "Permite el acceso al micrófono para transcribir. Puedes activarlo en Ajustes de Android → Aplicaciones → Talks → Permisos."
    }
    fun expandPassage(value: Boolean) { passageExpanded = value }
    fun dismissPassage() { passage = null }

    private fun scheduleEditSave() {
        editSave?.cancel()
        editSave = viewModelScope.launch { delay(350); persist() }
    }

    private fun persist() {
        editSave?.cancel(); editSave = null
        val current = note ?: return
        val snapshot = current.copy(body = body.text, transcript = json.encodeToString(entries), ink = savedInk, updatedAt = System.currentTimeMillis())
        note = snapshot
        repo.save(snapshot)
    }

    /** Inserts a chosen fragment at the editor selection, with paragraph separation. */
    fun insert(text: String) {
        if (note == null || text.isBlank()) return
        val start = body.selection.min.coerceIn(0, body.text.length)
        val end = body.selection.max.coerceIn(start, body.text.length)
        val before = body.text.substring(0, start)
        val after = body.text.substring(end)
        val prefix = if (before.isNotEmpty() && !before.endsWith('\n')) "\n\n" else ""
        val suffix = if (after.isNotEmpty() && !after.startsWith('\n')) "\n\n" else ""
        val insertion = prefix + text.trim() + suffix
        body = TextFieldValue(before + insertion + after, TextRange(before.length + insertion.length))
        persist()
    }

    fun addEntries(ids: Set<Long>) {
        val chosen = entries.filter { it.id in ids && !it.added }
        if (chosen.isEmpty()) return
        entries = entries.map { if (it in chosen) it.copy(added = true) else it }
        insert(chosen.joinToString("\n\n") { it.text })
    }
    fun addPassage() { passage?.let { insert(it.forNote) } }

    fun delete(id: Long) {
        if (note?.id == id) backToList()
        viewModelScope.launch {
            try { repo.delete(id) } catch (e: Exception) { report(e) }
        }
    }

    fun start() {
        if (note == null || listening || loading) return
        listening = true; message = null; partial = ""
        val gen = ++generation
        collector = viewModelScope.launch {
            val settings = services.settings.settings.first()
            if (gen != generation) return@launch
            val recognizer = try { createEngine(settings) }
            catch (e: Exception) { report(e); pause(); return@launch }
            engine = recognizer
            launch {
                recognizer.events.collect { event ->
                    if (gen != generation || !listening) return@collect
                    when (event) {
                        is SpeechEvent.Partial -> {
                            if (partial != event.text) { partial = event.text; detect(event.text, debounce = true) }
                        }
                        is SpeechEvent.Final -> {
                            val text = event.text.trim()
                            partial = ""
                            if (text.isNotEmpty()) {
                                val entry = TranscriptEntry((entries.maxOfOrNull { it.id } ?: 0) + 1, text)
                                entries = entries + entry
                                if (note?.automatic == true) {
                                    entries = entries.map { if (it.id == entry.id) it.copy(added = true) else it }
                                    // Automatic capture appends without replacing the selection being edited.
                                    val old = body
                                    val appended = old.text + (if (old.text.isBlank()) "" else "\n\n") + text
                                    val cursorAtEnd = old.selection.collapsed && old.selection.end == old.text.length
                                    body = old.copy(text = appended, selection = if (cursorAtEnd) TextRange(appended.length) else old.selection)
                                    persist()
                                } else persist()
                                detect(text, debounce = false)
                            }
                        }
                        is SpeechEvent.Level -> level = event.value
                        is SpeechEvent.State -> engineState = event.state
                        is SpeechEvent.Failure -> {
                            message = event.error.message
                            if (event.error.fatal) pause()
                        }
                    }
                }
            }
            try { recognizer.start() }
            catch (e: Exception) { report(e); pause() }
        }
    }

    fun pause() {
        listening = false; generation++
        collector?.cancel(); collector = null
        engine?.release(); engine = null
        engineState = EngineState.STOPPED; level = 0f
        // Cancelling Android recognition can discard its final result. Keep its last partial,
        // labelled as unconfirmed, available for manual copying rather than lose those words.
        if (partial.isNotBlank() && note != null) {
            entries = entries + TranscriptEntry((entries.maxOfOrNull { it.id } ?: 0) + 1, partial.trim(), confirmed = false)
        }
        partial = ""
        if (note != null) persist()
    }

    private fun createEngine(s: AppSettings): SpeechEngine = services.speechEngineFactory?.invoke(s) ?: when (s.engine) {
        EngineKind.VOSK -> VoskSpeechEngine(app, services.voskModel.manager.modelDir)
        EngineKind.ANDROID -> AndroidSpeechEngine(app, AndroidSpeechEngine.Config(s.language, s.preferOffline, s.forceGoogleService, s.muteSounds))
    }

    private fun detect(text: String, debounce: Boolean) {
        if (!citationsEnabled) return
        lookup?.cancel()
        val noteId = note?.id ?: return
        // A book/chapter may have ended the preceding recognition segment.
        val previous = entries.takeLast(if (debounce) 1 else 2).firstOrNull()?.text.orEmpty().takeLast(180)
        lookup = viewModelScope.launch {
            if (debounce) delay(450)
            val refs = withContext(Dispatchers.Default) { BibleReferences.find("$previous $text") }
            if (refs.isEmpty() || note?.id != noteId) return@launch
            detected = (detected + refs).distinct().takeLast(12)
            val ref = refs.last()
            if (ref == lastDetection) return@launch
            showReference(ref)
        }
    }

    fun showReference(ref: BibleReference) {
        val id = note?.id ?: return
        lastDetection = ref
        viewModelScope.launch {
            try {
                val found = services.bible.passage(ref)
                if (note?.id != id || !citationsEnabled || lastDetection != ref) return@launch
                if (found != null) { passage = found; passageExpanded = true }
                else if (bible != null) message = "${ref.label} no está disponible en la Biblia importada."
            } catch (e: Exception) { report(e) }
        }
    }

    fun importBible(uri: Uri) {
        if (importing) return
        pause(); importing = true; importProgress = 0
        viewModelScope.launch {
            try {
                bible = services.bible.import(uri) { percent -> viewModelScope.launch { importProgress = percent } }
                lastDetection = null
                detected.lastOrNull()?.let(::showReference)
                message = "Biblia importada. Las citas están disponibles sin conexión."
            } catch (e: Exception) { report(e) }
            finally { importing = false }
        }
    }

    private fun report(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        message = e.message ?: "No se pudo completar la operación."
    }
    override fun onCleared() { pause(); super.onCleared() }
}
