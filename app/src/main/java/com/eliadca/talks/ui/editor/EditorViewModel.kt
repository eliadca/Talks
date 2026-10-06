package com.eliadca.talks.ui.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliadca.talks.container
import com.eliadca.talks.core.doc.RichDoc
import com.eliadca.talks.core.doc.spokenWordCount
import com.eliadca.talks.data.LoadedSpeech
import com.eliadca.talks.editor.EditorController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DocStats(val words: Int = 0, val characters: Int = 0)

/**
 * Owns the speech open in the editor: loads it, and saves it a moment after each change and
 * whenever the user leaves, so that nothing typed is ever lost.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.container
    private val repo = container.speeches

    val controller = EditorController()

    var current by mutableStateOf<LoadedSpeech?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var stats by mutableStateOf(DocStats())
        private set
    var saving by mutableStateOf(false)
        private set

    private var dirty = false
    private var saveJob: Job? = null
    private var openRequest: Long? = null

    init {
        controller.onChanged = { onContentChanged() }
    }

    /** Opens speech [id] (or closes the editor for null), saving the one that was open. */
    fun open(id: Long?) {
        if (id == openRequest) return
        flushNow()
        openRequest = id
        if (id == null) {
            current = null
            title = ""
            return
        }
        viewModelScope.launch {
            val loaded = repo.load(id)
            if (openRequest != id) return@launch
            if (loaded == null) {
                current = null
                return@launch
            }
            title = loaded.title
            stats = statsOf(loaded.doc)
            dirty = false
            saving = false
            current = loaded
        }
    }

    /** Reloads the open speech from storage (after restoring an older version). */
    fun reload() {
        val id = openRequest ?: return
        flushNow()
        openRequest = null
        current = null
        open(id)
    }

    fun onTitleChange(value: String) {
        title = value
        onContentChanged()
    }

    private fun onContentChanged() {
        if (current == null) return
        dirty = true
        saving = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    /** Saves right now if there are unsaved changes. */
    fun flushNow() {
        saveJob?.cancel()
        if (dirty) persist()
    }

    private fun persist() {
        val id = current?.id ?: return
        val doc = controller.snapshot() ?: return
        val t = title
        dirty = false
        stats = statsOf(doc)
        // The application scope outlives this ViewModel, so a save started while closing still finishes.
        container.scope.launch(Dispatchers.IO) {
            repo.saveContent(id, t, doc)
            saving = false
        }
    }

    private fun statsOf(doc: RichDoc) = DocStats(doc.spokenWordCount(), doc.text.length)

    override fun onCleared() {
        flushNow()
        super.onCleared()
    }

    private companion object {
        const val SAVE_DELAY_MS = 700L
    }
}
