package com.eliadca.talks.data

import com.eliadca.talks.core.ink.InkDocument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.eliadca.talks.data.db.NoteDao
import com.eliadca.talks.data.db.NoteEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One ordered writer: switching notes or leaving the screen never discards queued edits. */
class NotesRepository(private val dao: NoteDao, scope: CoroutineScope) {
    private data class Write(val note: NoteEntity? = null, val drawing: InkDocument? = null, val barrier: CompletableDeferred<Unit>? = null)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val items = dao.observeAll()

    init {
        scope.launch {
            for (write in writes) {
                if (write.note != null) {
                    try {
                        val saved = write.drawing?.let { write.note.copy(ink = Json.encodeToString(it)) } ?: write.note
                        dao.update(saved); _error.value = null
                    }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _error.value = "No se pudo guardar la nota. ${e.message.orEmpty()}"
                    }
                }
                write.barrier?.complete(Unit)
            }
        }
    }

    fun save(note: NoteEntity, drawing: InkDocument? = null) { writes.trySend(Write(note = note, drawing = drawing)) }
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writes.send(Write(barrier = done))
        done.await()
    }
    suspend fun create(): NoteEntity {
        val now = System.currentTimeMillis()
        val note = NoteEntity(title = "", createdAt = now, updatedAt = now)
        return note.copy(id = dao.insert(note))
    }
    suspend fun load(id: Long): NoteEntity? { flush(); return dao.get(id) }
    suspend fun delete(id: Long) { flush(); dao.delete(id) }
}
