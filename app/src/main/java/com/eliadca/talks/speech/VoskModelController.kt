package com.eliadca.talks.speech

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Application-wide state of the offline voice model. It lives outside any screen so that a
 * download keeps going when the user leaves, and the preparation and settings screens agree.
 */
class VoskModelController(val manager: VoskModelManager, private val scope: CoroutineScope) {

    private val mutableInstalled = MutableStateFlow(manager.isInstalled())
    val installed: StateFlow<Boolean> = mutableInstalled

    private val mutableProgress = MutableStateFlow<ModelDownload?>(null)
    val progress: StateFlow<ModelDownload?> = mutableProgress

    private var job: Job? = null

    private val busy: Boolean get() = job?.isActive == true

    fun download() {
        if (busy) return
        job = scope.launch { manager.download().collect(::handle) }
    }

    fun importZip(uri: Uri) {
        if (busy) return
        job = scope.launch { manager.importZip(uri).collect(::handle) }
    }

    fun cancel() {
        job?.cancel()
        mutableProgress.value = null
    }

    fun remove() {
        if (busy) return
        manager.remove()
        mutableInstalled.value = false
    }

    fun clearMessage() {
        if (!busy) mutableProgress.value = null
    }

    private fun handle(e: ModelDownload) {
        mutableProgress.value = if (e is ModelDownload.Done) null else e
        if (e is ModelDownload.Done) mutableInstalled.value = true
    }
}
