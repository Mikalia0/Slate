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

data class ScanState(val directory: File? = null, val snapshot: Snapshot? = null, val running: Boolean = false, val visited: Long = 0, val error: String? = null, val scanId: Long = 0, val currentPath: String = "", val measuredBytes: Long = 0L, val elapsedMs: Long = 0L, val fresh: Boolean = false, val largestLoading: Boolean = false)

class StorageModel : ViewModel() {
    private val scanner = StorageScanner()
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private val cache = LinkedHashMap<File, Snapshot>()
    private val indexes=LinkedHashMap<File,IndexedScan>()
    private var largestJob: Job? = null

    fun open(directory: File, refresh: Boolean = false) {
        if(!refresh && mutable.value.directory==directory && mutable.value.running) return
        job?.cancel()
        largestJob?.cancel()
        val id = ++generation
        if (refresh) { cache.clear(); indexes.clear() }
        cache[directory]?.let { mutable.value = ScanState(directory,it,scanId=id); return }
        val indexed=indexes.values.toList().asReversed().firstOrNull{directory==it.root.directory || it.folders.containsKey(directory)}
        if(indexed!=null) {
            val original=if(directory==indexed.root.directory) indexed.root else indexed.folders.getValue(directory)
            val needsLargest=directory!=indexed.root.directory && original.files>0
            mutable.value=ScanState(directory,original,scanId=id,largestLoading=needsLargest)
            if(needsLargest) largestJob=viewModelScope.launch {
                val complete=withContext(Dispatchers.Default){indexed.snapshot(directory)}
                if(generation==id && complete!=null) {
                    cache[directory]=complete
                    if(cache.size>16)cache.remove(cache.keys.first())
                    mutable.value=ScanState(directory,complete,scanId=id)
                }
            }
            return
        }
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
                        indexes[directory]=result
                        if(indexes.size>2)indexes.remove(indexes.keys.first())
                        cache[directory] = result.root
                        if (cache.size > 16) cache.remove(cache.keys.first())
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
