package com.eliadca.talks.ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliadca.talks.container
import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.firstLine
import com.eliadca.talks.core.doc.leadingTitle
import com.eliadca.talks.core.doc.toMarkdown
import com.eliadca.talks.core.sample.SampleContent
import com.eliadca.talks.data.db.FolderEntity
import com.eliadca.talks.data.db.SpeechListItem
import com.eliadca.talks.data.db.VersionItem
import com.eliadca.talks.export.Clipboard
import com.eliadca.talks.export.Importer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortKey(val label: String) {
    MODIFIED("Última modificación"),
    CREATED("Fecha de creación"),
    TITLE("Título"),
    LENGTH("Duración"),
}

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Pinned : LibraryFilter
    data object Folders : LibraryFilter
    data object NoFolder : LibraryFilter
    data class Folder(val id: Long) : LibraryFilter
    data object Trash : LibraryFilter
}

data class LibraryCounts(
    val all: Int = 0,
    val pinned: Int = 0,
    val noFolder: Int = 0,
    val trash: Int = 0,
    val perFolder: Map<Long, Int> = emptyMap(),
)

/** A short message with an optional action (typically "Deshacer"). */
class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** State of the library: what is listed, filtered and sorted, and what is open. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.container
    private val repo = container.speeches

    private val _filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val _query = MutableStateFlow("")
    private val _sort = MutableStateFlow(SortKey.MODIFIED)
    private val _selectedId = MutableStateFlow<Long?>(null)

    val filter: StateFlow<LibraryFilter> = _filter
    val query: StateFlow<String> = _query
    val sort: StateFlow<SortKey> = _sort
    val selectedId: StateFlow<Long?> = _selectedId

    /** The user's own speaking pace (words per minute) once known from Talks runs. */
    var paceWpm by mutableIntStateOf(0)
        private set

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow: Flow<UiMessage> = messages.receiveAsFlow()

    private val activeSpeeches: Flow<List<SpeechListItem>> = combine(_query, _filter) { query, filter ->
        // Searching folder names should not also scan the bodies of all speeches.
        if (filter == LibraryFilter.Folders) "" else query
    }
        .distinctUntilChanged()
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) repo.observeActive() else repo.search(q) }

    val folders: StateFlow<List<FolderEntity>> = repo.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<SpeechListItem>> = combine(
        activeSpeeches, repo.observeTrashed(), _filter, _sort,
    ) { active, trashed, filter, sort ->
        if (filter == LibraryFilter.Trash) {
            trashed
        } else {
            active
                .filter { matches(it, filter) }
                .sortedWith(compareByDescending<SpeechListItem> { it.pinned }.then(comparatorFor(sort)))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<LibraryCounts> = combine(repo.observeActive(), repo.observeTrashed()) { active, trashed ->
        LibraryCounts(
            all = active.size,
            pinned = active.count { it.pinned },
            noFolder = active.count { it.folderId == null },
            trash = trashed.size,
            perFolder = active.mapNotNull { it.folderId }.groupingBy { it }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCounts())

    val selectedItem: StateFlow<SpeechListItem?> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeItem(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedVersions: StateFlow<List<VersionItem>> = _selectedId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeVersions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshPace()
    }

    private fun matches(item: SpeechListItem, filter: LibraryFilter): Boolean = when (filter) {
        LibraryFilter.All -> true
        LibraryFilter.Pinned -> item.pinned
        LibraryFilter.Folders -> false
        LibraryFilter.NoFolder -> item.folderId == null
        is LibraryFilter.Folder -> item.folderId == filter.id
        LibraryFilter.Trash -> false
    }

    private fun comparatorFor(sort: SortKey): Comparator<SpeechListItem> = when (sort) {
        SortKey.MODIFIED -> compareByDescending { it.updatedAt }
        SortKey.CREATED -> compareByDescending { it.createdAt }
        SortKey.TITLE -> compareBy<SpeechListItem> { it.title.isBlank() }.thenBy { it.title.lowercase() }
        SortKey.LENGTH -> compareByDescending { it.wordCount }
    }

    fun refreshPace(fallback: Int = 130) {
        viewModelScope.launch { paceWpm = repo.typicalWpm(fallback) }
    }

    // --- navigation state ---------------------------------------------------------------------

    fun setFilter(f: LibraryFilter) {
        if (_filter.value != f) _query.value = ""
        _filter.value = f
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(s: SortKey) {
        _sort.value = s
    }

    fun select(id: Long?) {
        _selectedId.value = id
    }

    // --- speeches -----------------------------------------------------------------------------

    fun createSpeech() {
        viewModelScope.launch {
            val folder = (filter.value as? LibraryFilter.Folder)?.id
            if (filter.value == LibraryFilter.Trash) _filter.value = LibraryFilter.All
            if (filter.value == LibraryFilter.Folders) _filter.value = LibraryFilter.NoFolder
            val id = repo.create(folderId = folder)
            _query.value = ""
            _selectedId.value = id
        }
    }

    // --- importing ----------------------------------------------------------------------------

    /** Creates a speech from each file (Markdown, text or Word), keeping its formatting, and opens the last one. */
    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val importer = Importer(getApplication<Application>().contentResolver)
            val folder = (filter.value as? LibraryFilter.Folder)?.id
            var lastId: Long? = null
            var lastTitle = ""
            var imported = 0
            var problem: String? = null
            for (uri in uris) {
                try {
                    val speech = importer.read(uri)
                    lastId = repo.create(speech.title, speech.doc, folderId = folder)
                    lastTitle = speech.title
                    imported++
                } catch (e: Exception) {
                    problem = e.message ?: "No se pudo leer un archivo."
                }
            }
            lastId?.let { showNew(it) }
            messages.trySend(
                UiMessage(
                    when {
                        imported == 0 -> problem ?: "No se pudo importar el archivo."
                        problem != null -> "Importados $imported de ${uris.size}. $problem"
                        imported == 1 -> "Importado «$lastTitle»"
                        else -> "$imported discursos importados"
                    },
                ),
            )
        }
    }

    /**
     * Creates a speech from pasted or shared text. Markdown keeps its formatting, and a `# ` title on
     * the first line names the speech.
     */
    fun importText(text: String, subject: String? = null, message: String = "Discurso creado con el texto copiado") {
        if (text.isBlank()) return
        viewModelScope.launch {
            val doc = Markup.parse(text)
            val title = doc.leadingTitle() ?: subject?.trim()?.takeIf { it.isNotEmpty() } ?: doc.firstLine()
            val id = repo.create(title, doc, folderId = (filter.value as? LibraryFilter.Folder)?.id)
            showNew(id)
            messages.trySend(UiMessage(message))
        }
    }

    /**
     * Copies the prompt for an AI assistant: the user's own template note when it exists (they may
     * have tuned it), or the built-in one.
     */
    fun copyTemplate() {
        viewModelScope.launch {
            val note = templateNoteId()?.let { repo.load(it) }
            val text = note?.doc?.toMarkdown(note.title) ?: SampleContent.AGENT_TEMPLATE
            Clipboard.copy(getApplication<Application>(), "Plantilla para tu IA", text)
            messages.trySend(UiMessage("Plantilla copiada. Pégala en tu agente de IA.", "Ver nota") { openTemplate() })
        }
    }

    /** Opens the template note, creating it again if it was deleted. */
    fun openTemplate() {
        viewModelScope.launch {
            val id = templateNoteId() ?: repo.create(SampleContent.AGENT_TEMPLATE_TITLE, SampleContent.agentTemplate)
            showNew(id)
        }
    }

    private suspend fun templateNoteId(): Long? =
        repo.observeActive().first().firstOrNull { it.title == SampleContent.AGENT_TEMPLATE_TITLE }?.id

    /** Makes sure [id] is listed (not hidden by the trash, the pinned filter or a search) and opens it. */
    private fun showNew(id: Long) {
        if (_filter.value == LibraryFilter.Trash || _filter.value == LibraryFilter.Pinned) _filter.value = LibraryFilter.All
        if (_filter.value == LibraryFilter.Folders) _filter.value = LibraryFilter.NoFolder
        _query.value = ""
        _selectedId.value = id
    }

    fun togglePin(item: SpeechListItem) {
        viewModelScope.launch { repo.setPinned(item.id, !item.pinned) }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            val copy = repo.duplicate(id)
            if (copy != null) {
                _selectedId.value = copy
                messages.trySend(UiMessage("Discurso duplicado"))
            }
        }
    }

    fun moveToTrash(id: Long, title: String) {
        viewModelScope.launch {
            repo.moveToTrash(id)
            if (_selectedId.value == id) _selectedId.value = null
            messages.trySend(
                UiMessage("«${title.ifBlank { "Sin título" }}» movido a la papelera", "Deshacer") {
                    viewModelScope.launch { repo.restoreFromTrash(id) }
                },
            )
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            repo.restoreFromTrash(id)
            messages.trySend(UiMessage("Discurso restaurado"))
        }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repo.deletePermanently(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repo.emptyTrash()
            messages.trySend(UiMessage("Papelera vacía"))
        }
    }

    fun moveToFolder(id: Long, folderId: Long?) {
        viewModelScope.launch {
            val speech = repo.observeItem(id).first() ?: return@launch
            if (speech.folderId == folderId) return@launch
            val destination = repo.observeFolders().first().firstOrNull { it.id == folderId }
            if (folderId != null && destination == null) {
                messages.trySend(UiMessage("La carpeta ya no existe. Elige otra carpeta."))
                return@launch
            }
            repo.setFolder(id, folderId)
            messages.trySend(
                UiMessage("Movido a «${destination?.name ?: "Sin carpeta"}»", "Deshacer") {
                    viewModelScope.launch {
                        // If the old folder was deleted meanwhile, keep the note without a folder.
                        val oldFolder = speech.folderId?.takeIf { previous ->
                            repo.observeFolders().first().any { it.id == previous }
                        }
                        repo.setFolder(id, oldFolder)
                    }
                },
            )
        }
    }

    fun setLabel(id: Long, color: Int) {
        viewModelScope.launch { repo.setColor(id, color) }
    }

    fun setTarget(id: Long, minutes: Int) {
        viewModelScope.launch { repo.setTargetMinutes(id, minutes) }
    }

    fun saveVersion(id: Long, label: String) {
        viewModelScope.launch {
            repo.saveVersion(id, label)
            messages.trySend(UiMessage("Versión guardada"))
        }
    }

    fun restoreVersion(speechId: Long, versionId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.restoreVersion(speechId, versionId)
            messages.trySend(UiMessage("Versión restaurada"))
            onDone()
        }
    }

    fun deleteVersion(id: Long) {
        viewModelScope.launch { repo.deleteVersion(id) }
    }

    // --- folders ------------------------------------------------------------------------------

    fun createFolder(name: String, color: Int) {
        viewModelScope.launch {
            val id = repo.createFolder(name, color)
            setFilter(LibraryFilter.Folder(id))
        }
    }

    fun updateFolder(folder: FolderEntity) {
        viewModelScope.launch { repo.updateFolder(folder) }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            if ((_filter.value as? LibraryFilter.Folder)?.id == id) setFilter(LibraryFilter.Folders)
            repo.deleteFolder(id)
            messages.trySend(UiMessage("Carpeta eliminada. Sus discursos se conservan."))
        }
    }

    fun toast(text: String) {
        messages.trySend(UiMessage(text))
    }
}
