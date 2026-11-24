// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import androidx.lifecycle.AndroidViewModel
import android.app.Application
import android.os.Environment
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

data class ScanState(val directory: File? = null, val snapshot: Snapshot? = null, val running: Boolean = false, val visited: Long = 0, val error: String? = null, val scanId: Long = 0, val currentPath: String = "", val measuredBytes: Long = 0L, val elapsedMs: Long = 0L, val fresh: Boolean = false, val largestLoading: Boolean = false, val saved: Boolean = false)

class StorageModel(app:Application) : AndroidViewModel(app) {
    private val primary=Environment.getExternalStorageDirectory()
    private val store=IndexStore(File(app.filesDir,"storage-index.gz"),primary)
    private var savedIndex:IndexedScan?=null
    private var diskChecked=false
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
        if (refresh) { cache.clear(); indexes.clear(); savedIndex=null; diskChecked=true; store.clear() }
        cache[directory]?.let { mutable.value = ScanState(directory,it,scanId=id); return }
        val saved=savedIndex
        val indexed=indexes.values.toList().asReversed().firstOrNull{directory==it.root.directory || it.folders.containsKey(directory)} ?: saved?.takeIf{directory==it.root.directory || it.folders.containsKey(directory)}
        if(indexed!=null) {
            val original=if(directory==indexed.root.directory) indexed.root else indexed.folders.getValue(directory)
            val needsLargest=directory!=indexed.root.directory && original.files>0
            mutable.value=ScanState(directory,original,scanId=id,largestLoading=needsLargest,saved=indexed===saved)
            if(needsLargest) largestJob=viewModelScope.launch {
                val complete=withContext(Dispatchers.Default){indexed.snapshot(directory)}
                if(generation==id && complete!=null) {
                    if(indexed!==saved) {cache[directory]=complete;if(cache.size>16)cache.remove(cache.keys.first())}
                    mutable.value=ScanState(directory,complete,scanId=id,saved=indexed===saved)
                }
            }
            return
        }
        mutable.value = ScanState(directory = directory, running = true, scanId = id)
        job = viewModelScope.launch {
            try {
                if(!diskChecked && directory.toPath().startsWith(primary.toPath())) {
                    val loaded=withContext(Dispatchers.IO){store.load()}
                    if(generation!=id)return@launch
                    diskChecked=true
                    savedIndex=loaded
                    if(loaded!=null && (directory==primary || loaded.folders.containsKey(directory))) {mutable.value=ScanState(directory=directory);open(directory);return@launch}
                }
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
                    if(directory==primary && !result.root.rootUnreadable) {
                        savedIndex=null
                        withContext(Dispatchers.IO){try{store.save(result)}catch(e:CancellationException){throw e}catch(_:Exception){}}
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == id) mutable.value = ScanState(directory, error = e.message ?: "Scan failed", scanId = id)
            }
        }
    }

    fun invalidate() {
        ++generation
        job?.cancel()
        largestJob?.cancel()
        cache.clear();indexes.clear();savedIndex=null;diskChecked=true;store.clear()
        mutable.value=ScanState()
    }

    fun cancel() {
        if (!mutable.value.running) return
        ++generation
        job?.cancel()
        mutable.value = mutable.value.copy(running = false, error = "Scan cancelled", scanId = generation)
    }
}
