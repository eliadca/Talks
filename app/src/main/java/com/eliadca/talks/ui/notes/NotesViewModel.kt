package com.eliadca.talks.ui.notes

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.eliadca.talks.core.notes.NoteTextStyle
import com.eliadca.talks.core.notes.NoteFormatting
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliadca.talks.bible.BibleInfo
import com.eliadca.talks.bible.BiblePassage
import com.eliadca.talks.container
import com.eliadca.talks.core.bible.BibleReference
import com.eliadca.talks.core.bible.BibleReferences
import com.eliadca.talks.core.bible.BibleReferenceSuggestion
import com.eliadca.talks.core.ink.InkDocument
import com.eliadca.talks.core.notes.TranscriptRevision
import com.eliadca.talks.core.notes.continuousText
import com.eliadca.talks.core.notes.CorrectionReview
import com.eliadca.talks.speech.NotesVoskSpeechEngine
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
data class TranscriptEntry(val id: Long, val text: String, val added: Boolean = false, val confirmed: Boolean = true,
    val originalText: String? = null, val confidence: Float? = null)

class NotesViewModel(private val app: Application, repository: com.eliadca.talks.data.NotesRepository? = null) : AndroidViewModel(app) {
    private val services = app.container
    private val repo = repository ?: services.notes
    val ai = services.notesAi
    val advancedModel = services.notesVosk
    val smallModel = services.voskModel
    private val voicePrefs = app.getSharedPreferences("notes-voice", android.content.Context.MODE_PRIVATE)
    var voiceEngine by mutableStateOf(EngineKind.VOSK); private set
    var voiceLanguage by mutableStateOf("es-US"); private set
    var advancedVosk by mutableStateOf(voicePrefs.getBoolean("advanced",false)); private set
    var aiBusy by mutableStateOf(false); private set
    var aiProgress by mutableStateOf(""); private set
    data class AiReview(val original: String, val proposed: String, val before: String, val after: String,
        val throughId: Long, val base: String, val concern: String?)
    var aiReview by mutableStateOf<AiReview?>(null); private set
    private var aiJob: Job? = null
    fun configureVoice(engine: EngineKind, language: String, advanced: Boolean) {
        if(listening)return
        voiceEngine=engine; voiceLanguage=language; advancedVosk=advanced
        voicePrefs.edit().putString("engine",engine.name).putString("language",language).putBoolean("advanced",advanced).apply()
    }
    fun cancelAi() { aiJob?.cancel(); aiReview=null }
    fun reviewWithAi(text: String, start: Int, end: Int) {
        if(aiBusy || text != transcriptText || note==null)return
        val a=start.coerceIn(0,text.length); val b=end.coerceIn(a,text.length)
        val source=text.substring(a,b); if(source.isBlank())return
        val owner=note!!.id; val last=entries.maxOfOrNull { it.id } ?: 0
        aiBusy=true; aiReview=null
        aiJob=viewModelScope.launch {
            try {
                val proposed=ai.correct(source) { current,total -> viewModelScope.launch { aiProgress="Revisando $current de $total…" } }
                if(note?.id==owner) aiReview=AiReview(source,proposed,text.take(a),text.drop(b),last,text,CorrectionReview.concern(source,proposed))
            } catch(e: Exception) { report(e) }
            finally { aiBusy=false; aiProgress="" }
        }
    }
    fun applyAiReview() {
        val review=aiReview ?: return
        val prefix=revision.render(entries.filter { it.id<=review.throughId }.map { it.id to it.text })
        if(prefix!=review.base) { aiReview=null; message="El texto cambió durante la revisión. Vuelve a revisarlo."; return }
        if(review.concern!=null) { message=review.concern; return }
        revision=TranscriptRevision(review.before+review.proposed+review.after,review.throughId,true); aiReview=null; persist()
    }
    fun dismissAiReview() { aiReview=null }

    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteListItem>())
    val saveError = repo.error
    var note by mutableStateOf<NoteEntity?>(null); private set
    var body by mutableStateOf(TextFieldValue("")); private set
    var ink by mutableStateOf(InkDocument()); private set
    var canUndoInk by mutableStateOf(false); private set
    var canRedoInk by mutableStateOf(false); private set

    private var recordingInk = InkDocument()
    var navigationRequest by mutableIntStateOf(0); private set
    fun requestOpenFromNotification(id: Long) { open(id); navigationRequest++ }
    fun consumeNavigationRequest() { navigationRequest = 0 }
    var fullScreen by mutableStateOf(false)
    var canUndoText by mutableStateOf(false); private set
    var canRedoText by mutableStateOf(false); private set
    var typingStyle by mutableStateOf<NoteTextStyle?>(null); private set
    private data class TextSnapshot(val value: TextFieldValue, val drawing: InkDocument)
    private val textPast = ArrayDeque<TextSnapshot>()
    private val textFuture = ArrayDeque<TextSnapshot>()
    private var recordingNote by mutableStateOf<NoteEntity?>(null)
    val recordingNoteId get() = recordingNote?.id.takeIf { listening }
    val recordingTitle get() = recordingNote?.title?.ifBlank { "Sin título" }.orEmpty()
    private var recordingEntries = emptyList<TranscriptEntry>()
    var revision by mutableStateOf(TranscriptRevision()); private set
    val transcriptText get() = revision.render(entries.map { it.id to it.text })
    val originalTranscript get() = continuousText(entries.map { it.originalText ?: it.text })
    fun editTranscript(text: String, assisted: Boolean = false, baseText: String = transcriptText) {
        val current=transcriptText
        val replacement=if(current==baseText)text else if(current.startsWith(baseText))text+current.drop(baseText.length)
            else {message="El texto cambió. Revisa la transcripción actual antes de editar.";return}
        revision = TranscriptRevision(replacement, entries.maxOfOrNull { it.id } ?: 0, assisted); scheduleEditSave()
    }
    fun restoreTranscript() { revision = TranscriptRevision(); persist() }
    var entries by mutableStateOf(emptyList<TranscriptEntry>()); private set
    private var capturePartial by mutableStateOf("")
    val partial get() = if (note?.id == recordingNote?.id) capturePartial else ""
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
    var referenceSuggestions by mutableStateOf(emptyList<BibleReferenceSuggestion>()); private set
    private var passageFromVoice = false
    val passageIsFromVoice get() = passageFromVoice
    private var referenceRequest = 0
    private var engine: SpeechEngine? = null
    private var collector: Job? = null
    private var lookup: Job? = null
    private var editSave: Job? = null
    private var generation = 0
    private var lastDetection: BibleReference? = null
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val settings=services.settings.settings.first()
            voiceEngine=runCatching { EngineKind.valueOf(voicePrefs.getString("engine",settings.engine.name)!!) }.getOrDefault(settings.engine)
            voiceLanguage=voicePrefs.getString("language",settings.language) ?: settings.language
        }
        viewModelScope.launch {
            try { bible = services.bible.info() }
            catch (_: Exception) { message = "Vuelve a importar la Biblia para activar las citas." }
        }
    }

    fun create() {
        if (loading) return
        persist(); loading = true
        viewModelScope.launch {
            try { display(repo.create()) }
            catch (e: Exception) { report(e) }
            finally { loading = false }
        }
    }

    fun open(id: Long) {
        if (loading || note?.id == id) return
        persist(); loading = true
        viewModelScope.launch {
            try { repo.load(id)?.let { stored -> display(recordingNote?.takeIf { it.id == id } ?: stored, recordingInk.takeIf { recordingNote?.id == id }) } }
            catch (e: Exception) { report(e) }
            finally { loading = false }
        }
    }

    private fun display(value: NoteEntity, capturedDrawing: InkDocument? = null) {
        val transcript = try { json.decodeFromString<List<TranscriptEntry>>(value.transcript) }
        catch (e: Exception) { message = "No se pudo leer la transcripción guardada."; return }
        val drawing = capturedDrawing ?: try { json.decodeFromString<InkDocument>(value.ink) }
        catch (e: Exception) { message = "No se pudo leer la escritura guardada. Conserva una copia de seguridad."; return }
        cancelAi(); lookup?.cancel(); referenceRequest++; lastDetection = null; passage = null; detected = emptyList(); referenceSuggestions = emptyList()
        note = value
        body = TextFieldValue(value.body, TextRange(value.body.length))
        entries = transcript
        ink = drawing; revision = runCatching { json.decodeFromString<TranscriptRevision>(value.capture) }.getOrDefault(TranscriptRevision())
        textPast.clear(); textFuture.clear(); typingStyle = null; syncTextHistory()
        if (recordingNote?.id == value.id) { recordingNote = value; recordingEntries = transcript; recordingInk = drawing }
    }

    fun backToList() { persist(); cancelAi(); fullScreen = false; lookup?.cancel(); referenceRequest++; note = null; passage = null; detected = emptyList(); referenceSuggestions = emptyList() }
    fun title(value: String) { note = note?.copy(title = value.take(160)); scheduleEditSave() }
    fun edit(value: TextFieldValue) {
        val changed = value.text != body.text
        if (changed) {
            rememberText()
            ink = ink.copy(formatting = ink.formatting.replace(body.text, value.text, typingStyle))
        } else if (value.selection != body.selection) typingStyle = null
        body = value
        if (changed) scheduleEditSave()
    }
    val currentTextStyle get() = typingStyle ?: ink.formatting.styleAt(body.selection.min.coerceAtMost((body.text.length - 1).coerceAtLeast(0)))
    private fun rememberText() {
        textPast.addLast(TextSnapshot(body, ink)); if (textPast.size > 60) textPast.removeFirst()
        textFuture.clear(); syncTextHistory()
    }
    private fun syncTextHistory() { canUndoText = textPast.isNotEmpty(); canRedoText = textFuture.isNotEmpty(); canUndoInk = canUndoText; canRedoInk = canRedoText }
    fun formatText(transform: (NoteTextStyle) -> NoteTextStyle) {
        if (body.selection.collapsed) { typingStyle = transform(currentTextStyle); return }
        rememberText()
        ink = ink.copy(formatting = ink.formatting.apply(body.selection.min, body.selection.max, transform))
        persist()
    }
    fun alignText(alignment: Int) {
        rememberText(); ink = ink.copy(formatting = ink.formatting.align(body.text, body.selection.min, body.selection.max, alignment))
        persist()
    }
    fun undoText() {
        if (textPast.isEmpty()) return
        textFuture.addLast(TextSnapshot(body, ink)); restoreText(textPast.removeLast())
    }
    fun redoText() {
        if (textFuture.isEmpty()) return
        textPast.addLast(TextSnapshot(body, ink)); restoreText(textFuture.removeLast())
    }
    private fun restoreText(snapshot: TextSnapshot) {
        body = snapshot.value; ink = snapshot.drawing; typingStyle = null
        syncTextHistory(); persist()
    }
    fun listText(checklist: Boolean) {
        val start = body.text.lastIndexOf('\n', (body.selection.min - 1).coerceAtLeast(-1)) + 1
        val end = body.text.indexOf('\n', body.selection.max).takeIf { it >= 0 } ?: body.text.length
        val selected = body.text.substring(start, end)
        val prefix = if (checklist) "☐ " else "• "
        val remove = selected.lines().all { it.startsWith(prefix) }
        val replacement = selected.lines().joinToString("\n") { if (remove) it.removePrefix(prefix) else prefix + it }
        edit(TextFieldValue(body.text.replaceRange(start, end, replacement), TextRange(start, start + replacement.length)))
        persist()
    }
    fun moveTextSelection() {
        if(note==null || body.selection.collapsed)return
        val start=body.selection.min; val end=body.selection.max
        val text=body.text.substring(start,end);val format=ink.formatting.slice(start,end)
        rememberText()
        val updated=ink.addTextBox(text, (ink.bodyFrame?.y ?: 40f)+180f)
        val remaining=body.text.removeRange(start,end)
        ink=updated.copy(textBoxes=updated.textBoxes.map {if(it.id==ink.nextId)it.copy(formatting=format) else it}, formatting=ink.formatting.replace(body.text,remaining))
        body=TextFieldValue(remaining,TextRange(start));persist()
    }
    fun flushEdits() = persist()
    fun exportTranscript(): String = buildString {
        append(transcriptText)
        if (partial.isNotBlank()) { if (isNotEmpty()) append(" "); append(partial.trim()) }
    }
    fun exportArtwork(uri:Uri,document:InkDocument,text:String,format:String) {
        viewModelScope.launch {
            try {withContext(Dispatchers.IO) {app.contentResolver.openOutputStream(uri)?.use {out->NoteArtworkExport.write(app,document,text,format,out)} ?: error("No se pudo abrir el archivo.")}}
            catch(e:Exception) {report(e)}
        }
    }
    fun exportTranscript(uri: Uri, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    ?: error("No se pudo abrir el archivo.")
                withContext(Dispatchers.Main) { message = "Transcripción exportada como TXT." }
            } catch (e: Exception) { withContext(Dispatchers.Main) { report(e) } }
        }
    }
    fun automatic(value: Boolean) { note = note?.copy(automatic = value); persist() }
    fun editInk(value: InkDocument) {
        if (note == null || value == ink) return
        rememberText(); ink = value; persist()
    }
    fun undoInk() = undoText()
    fun redoInk() = redoText()
    fun toggleCitations(value: Boolean) {
        citationsEnabled = value
        lastDetection = null
        if (!value) {
            lookup?.cancel(); detected = emptyList(); referenceSuggestions = emptyList()
            if (passageFromVoice) { referenceRequest++; passage = null }
        }
    }
    fun dismissMessage() { message = null }
    fun permissionDenied() {
        message = "Permite el acceso al micrófono para transcribir. Puedes activarlo en Ajustes de Android → Aplicaciones → Talks → Permisos."
    }
    fun expandPassage(value: Boolean) { passageExpanded = value }
    fun dismissPassage() { referenceRequest++; passage = null }

    private fun scheduleEditSave() {
        editSave?.cancel()
        editSave = viewModelScope.launch { delay(350); persist() }
    }

    private fun persist() {
        editSave?.cancel(); editSave = null
        val current = note ?: return
        val snapshot = current.copy(body = body.text, transcript = json.encodeToString(entries), capture = json.encodeToString(revision), updatedAt = System.currentTimeMillis())
        note = snapshot
        if (recordingNote?.id == snapshot.id) { recordingNote = snapshot; recordingEntries = entries; recordingInk = ink }
        repo.save(snapshot, ink)
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
        edit(TextFieldValue(before + insertion + after, TextRange(before.length + insertion.length)))
        persist()
    }

    fun addEntries(ids: Set<Long>) {
        val chosen = entries.filter { it.id in ids && !it.added }
        if (chosen.isEmpty()) return
        entries = entries.map { if (it in chosen) it.copy(added = true) else it }
        insert(continuousText(chosen.map { it.text }))
    }
    fun addPassage() {
        val quote = passage ?: return
        if (!passageFromVoice) {
            // Opening a link may focus its glyph; insert below the paragraph, never inside its name.
            val offset = body.selection.max.coerceIn(0, body.text.length)
            val end = body.text.indexOf('\n', offset).takeIf { it >= 0 } ?: body.text.length
            body = body.copy(selection = TextRange(end))
        }
        insert(quote.forNote)
    }

    fun delete(id: Long) {
        if (recordingNote?.id == id) pause()
        if (note?.id == id) backToList()
        viewModelScope.launch {
            try { repo.delete(id); if (recordingNote?.id == id) { recordingNote = null; recordingEntries = emptyList(); recordingInk = InkDocument() } } catch (e: Exception) { report(e) }
        }
    }

    fun start() {
        if (note == null || listening || loading) return
        persist(); recordingNote = note; recordingEntries = entries; recordingInk = ink
        listening = true; message = null; capturePartial = ""
        // Test engines are deterministic and don't acquire a microphone.
        if (services.speechEngineFactory != null) beginCapture()
        else try { NotesRecordingService.start(app, this) }
        catch (e: Exception) { report(e); pause() }
    }

    internal fun beginCapture() {
        if (!listening || collector != null) return
        val gen = ++generation
        collector = viewModelScope.launch {
            try {
                val settings = services.settings.settings.first()
                if (gen != generation) return@launch
                val recognizer = createEngine(settings)
                engine = recognizer
                launch {
                    recognizer.events.collect { event ->
                        if (gen != generation || !listening) return@collect
                        when (event) {
                            is SpeechEvent.Partial -> {
                                if (capturePartial != event.text) {
                                    capturePartial = event.text
                                    if (note?.id == recordingNote?.id) detect(event.text, debounce = true)
                                }
                            }
                            is SpeechEvent.Final -> {
                                val raw = event.text.trim()
                                val text = withContext(Dispatchers.Default) { BibleReferences.scan(raw).correctBookNames(raw) }
                                if (gen != generation || !listening) return@collect
                                capturePartial = ""
                                if (text.isNotEmpty()) {
                                    captureEntry(TranscriptEntry((recordingEntries.maxOfOrNull { it.id } ?: 0) + 1, text,
                                        originalText = raw.takeIf { it != text }, confidence = event.confidence))
                                    if (note?.id == recordingNote?.id) detect(text, debounce = false)
                                }
                            }
                            is SpeechEvent.Level -> level = event.value
                            is SpeechEvent.State -> engineState = event.state
                            is SpeechEvent.Failure -> { message = event.error.message; if (event.error.fatal) pause() }
                        }
                    }
                }
                recognizer.start()
            } catch (e: Exception) { report(e); pause() }
        }
    }

    private fun captureEntry(entry: TranscriptEntry) {
        val owner = recordingNote ?: return
        val automatic = owner.automatic && entry.confirmed
        recordingEntries = recordingEntries + entry.copy(added = automatic)
        if (note?.id == owner.id) {
            entries = recordingEntries
            if (automatic) {
                val old = body
                val separator = if (old.text.isBlank() || old.text.endsWith("\n")) "" else " "
                val text = old.text + separator + entry.text
                val pending = typingStyle; typingStyle = NoteTextStyle()
                edit(TextFieldValue(text, if (old.selection.collapsed && old.selection.end == old.text.length) TextRange(text.length) else old.selection))
                typingStyle = pending
            }
            persist()
        } else {
            val newBody = if (automatic) owner.body + (if (owner.body.isBlank() || owner.body.endsWith("\n")) "" else " ") + entry.text else owner.body
            if (newBody != owner.body) recordingInk = recordingInk.copy(formatting = recordingInk.formatting.replace(owner.body, newBody, NoteTextStyle()))
            recordingNote = owner.copy(body = newBody, transcript = json.encodeToString(recordingEntries), updatedAt = System.currentTimeMillis())
            repo.save(recordingNote!!, recordingInk)
        }
    }

    fun pause() {
        listening = false; generation++
        collector?.cancel(); collector = null
        engine?.release(); engine = null
        engineState = EngineState.STOPPED; level = 0f
        if (capturePartial.isNotBlank()) captureEntry(TranscriptEntry((recordingEntries.maxOfOrNull { it.id } ?: 0) + 1,
            capturePartial.trim(), confirmed = false))
        capturePartial = ""
        if (note != null) persist()
        // Retain the last immutable capture snapshot: an already-running database read can
        // have completed before the pause saved its final partial. Reopening uses this newer copy.
        NotesRecordingService.stop(app)
    }

    private fun createEngine(s: AppSettings): SpeechEngine = services.speechEngineFactory?.invoke(s) ?: when (voiceEngine) {
        EngineKind.VOSK -> NotesVoskSpeechEngine(app, if (advancedVosk) advancedModel.manager.modelDir else services.voskModel.manager.modelDir)
        EngineKind.ANDROID -> AndroidSpeechEngine(app, AndroidSpeechEngine.Config(voiceLanguage, s.preferOffline, s.forceGoogleService, s.muteSounds, notesMode = true))
    }

    private fun detect(text: String, debounce: Boolean) {
        if (!citationsEnabled) return
        lookup?.cancel()
        val noteId = note?.id ?: return
        // A book/chapter may have ended the preceding recognition segment.
        val previous = entries.takeLast(if (debounce) 1 else 2).firstOrNull()?.text.orEmpty().takeLast(180)
        lookup = viewModelScope.launch {
            if (debounce) delay(450)
            val scan = withContext(Dispatchers.Default) { BibleReferences.scan("$previous $text") }
            if (note?.id != noteId || !citationsEnabled) return@launch
            referenceSuggestions = scan.suggestions.takeLast(3)
            val refs = scan.matches.map { it.reference }
            if (refs.isEmpty()) return@launch
            detected = (detected + refs).distinct().takeLast(12)
            val ref = refs.last()
            if (ref == lastDetection) return@launch
            showReference(ref, fromVoice = true)
        }
    }

    fun showReference(ref: BibleReference, fromVoice: Boolean = false) {
        val id = note?.id ?: return
        if (fromVoice && !citationsEnabled) return
        lastDetection = ref
        val request = ++referenceRequest
        passageFromVoice = fromVoice
        viewModelScope.launch {
            try {
                val found = services.bible.passage(ref)
                if (note?.id != id || request != referenceRequest || (fromVoice && !citationsEnabled)) return@launch
                if (found != null) {
                    passage = found; passageExpanded = true
                    if (bible == null) bible = services.bible.info()
                }
                else if (bible != null) message = "${ref.label} no está disponible en la Biblia importada."
            } catch (e: Exception) { report(e) }
        }
    }

    fun importBible(uri: Uri) {
        if (importing) return
        importing = true; importProgress = 0
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

    internal fun recordingFailure(e: Exception) { report(e); pause() }

    private fun report(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        message = e.message ?: "No se pudo completar la operación."
    }
    override fun onCleared() { pause(); super.onCleared() }
}
