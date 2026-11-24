// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanState(val directory: File? = null, val snapshot: Snapshot? = null, val running: Boolean = false, val visited: Long = 0, val error: String? = null, val scanId: Long = 0, val currentPath: String = "", val measuredBytes: Long = 0L, val elapsedMs: Long = 0L, val fresh: Boolean = false)

class StorageModel : ViewModel() {
    private val scanner = StorageScanner()
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private val cache = LinkedHashMap<File, Snapshot>()
    private var index: IndexedScan? = null

    fun open(directory: File, refresh: Boolean = false) {
        job?.cancel()
        val id = ++generation
        if (refresh) { cache.clear(); index=null }
        cache[directory]?.let { mutable.value = ScanState(directory,it,scanId=id); return }
        index?.snapshot(directory)?.let { mutable.value=ScanState(directory,it,scanId=id); return }
        mutable.value = ScanState(directory = directory, running = true, scanId = id)
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    scanner.scanIndexed(directory) { progress ->
                        mutable.update { previous ->
                            if (previous.scanId == id && previous.running) previous.copy(visited = progress.visited, currentPath = progress.path, measuredBytes = progress.bytes, elapsedMs = progress.elapsedMs) else previous
                        }
                    }
                }
                if (generation == id) {
                    if (!result.root.rootUnreadable) {
                        index=result
                        cache[directory] = result.root
                        if (cache.size > 4) cache.remove(cache.keys.first())
                    }
                    mutable.value = ScanState(directory, result.root, scanId = id, fresh=true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == id) mutable.value = ScanState(directory, error = e.message ?: "Scan failed", scanId = id)
            }
        }
    }

    fun cancel() {
        if (!mutable.value.running) return
        ++generation
        job?.cancel()
        mutable.value = mutable.value.copy(running = false, error = "Scan cancelled", scanId = generation)
    }
}
