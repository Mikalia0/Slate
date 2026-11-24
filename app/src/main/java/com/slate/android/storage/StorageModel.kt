// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import androidx.lifecycle.AndroidViewModel
import com.slate.android.StorageHistory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class ScanState(val directory: File? = null, val snapshot: Snapshot? = null, val running: Boolean = false, val visited: Long = 0, val error: String? = null, val scanId: Long = 0, val currentPath: String = "", val measuredBytes: Long = 0L, val elapsedMs: Long = 0L, val fresh: Boolean = false, val largestLoading: Boolean = false, val saved: Boolean = false, val partialRefresh:Boolean = false)

class StorageModel(app:Application) : AndroidViewModel(app) {
    private val primary=Environment.getExternalStorageDirectory()
    private val store=IndexStore(File(app.filesDir,"storage-index.gz"),primary)
    private var savedIndex:IndexedScan?=null
    private var diskChecked=false
    internal val exclusions=ExclusionStore(app)
    internal val history=StorageHistory(app)
    private val persistence=Mutex()
    private var saveJob:Job?=null
    private var backgroundJob:Job?=null
    private var checkedSession=false
    private val scanner = StorageScanner()
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private val cache = LinkedHashMap<File, Snapshot>()
    private val indexes=LinkedHashMap<File,IndexedScan>()
    private var largestJob: Job? = null
    private val partialRoots=HashSet<File>()

    internal suspend fun personalCategories():List<CategoryStats>? {
        val indexed=indexes[primary] ?: savedIndex ?: return null
        return withContext(Dispatchers.Default){indexed.personalCategories()}
    }

    fun open(directory: File, refresh: Boolean = false) {
        if(!refresh && mutable.value.directory==directory && mutable.value.running) return
        job?.cancel()
        largestJob?.cancel()
        backgroundJob?.cancel()
        val id = ++generation
        val previous=if(refresh && directory==primary) indexes[primary] ?: savedIndex else null
        val parentIndex=if(refresh && directory!=primary) indexes.values.toList().asReversed().firstOrNull{it.folders.containsKey(directory)} else null
        if (refresh) { clearIndexes() }
        cache[directory]?.let { mutable.value = ScanState(directory,it,scanId=id,partialRefresh=partialRoots.any{root->directory.toPath().startsWith(root.toPath())}); return }
        val saved=savedIndex
        val indexed=indexes.values.toList().asReversed().firstOrNull{directory==it.root.directory || it.folders.containsKey(directory)} ?: saved?.takeIf{directory==it.root.directory || it.folders.containsKey(directory)}
        if(indexed!=null) {
            val original=if(directory==indexed.root.directory) indexed.root else indexed.folders.getValue(directory)
            val needsLargest=directory!=indexed.root.directory && original.files>0
            mutable.value=ScanState(directory,original,scanId=id,largestLoading=needsLargest,saved=indexed===saved,partialRefresh=indexed.root.directory in partialRoots)
            if(needsLargest) largestJob=viewModelScope.launch {
                val complete=withContext(Dispatchers.Default){indexed.snapshot(directory)}
                if(generation==id && complete!=null) {
                    if(indexed!==saved) {cache[directory]=complete;if(cache.size>16)cache.remove(cache.keys.first())}
                    mutable.value=ScanState(directory,complete,scanId=id,saved=indexed===saved,partialRefresh=indexed.root.directory in partialRoots)
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
                    scanner.scanIndexed(directory,exclusions.absolute(),previous=previous) { progress ->
                        mutable.update { previous ->
                            if (previous.scanId == id && previous.running) previous.copy(visited = progress.visited, currentPath = progress.path, measuredBytes = progress.bytes, elapsedMs = progress.elapsedMs) else previous
                        }
                    }
                }
                val merged=if(parentIndex!=null)withContext(Dispatchers.Default){parentIndex.replaceSubtree(result)}else null
                if (generation == id) {
                    if(merged!=null){indexes[merged.root.directory]=merged;partialRoots.add(merged.root.directory)}
                    if (!result.root.rootUnreadable) {
                        indexes[directory]=result
                        if(indexes.size>2)indexes.remove(indexes.keys.first())
                        cache[directory] = result.root
                        if (cache.size > 16) cache.remove(cache.keys.first())
                    }
                    mutable.value = ScanState(directory, result.root, scanId = id, fresh=true)
                    if(directory==primary && !result.root.rootUnreadable) {
                        savedIndex=null
                        history.record(result.root,id)
                        persist(result)

                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == id) mutable.value = ScanState(directory, error = e.message ?: "Scan failed", scanId = id)
            }
        }
    }

    private fun persist(result:IndexedScan){
        saveJob?.cancel()
        saveJob=viewModelScope.launch(Dispatchers.IO){
            persistence.withLock { try{store.save(result)}catch(e:CancellationException){throw e}catch(_:Exception){} }
        }
    }
    private fun clearIndexes(){
        saveJob?.cancel();backgroundJob?.cancel()
        cache.clear();indexes.clear();partialRoots.clear();savedIndex=null;diskChecked=true
        // A queued clear follows any in-flight write, so stale data is not restored.
        saveJob=viewModelScope.launch(Dispatchers.IO){persistence.withLock{store.clear()}}
    }
    fun checkSession() {
        if(checkedSession || history.scannedAt==0L || mutable.value.running)return
        checkedSession=true
        val excluded=exclusions.absolute()
        val baseline=generation
        backgroundJob=viewModelScope.launch{
            try{
                val prior=withContext(Dispatchers.IO){persistence.withLock{store.load()}}
                if(generation!=baseline)return@launch
                // Publish the restored index before reconciliation. It is explicitly
                // marked saved, and destructive actions remain unavailable on it.
                if(prior!=null){savedIndex=prior;diskChecked=true}
                val result=withContext(Dispatchers.IO){scanner.scanIndexed(primary,excluded,previous=prior)}
                if(generation==baseline && !result.root.rootUnreadable){
                    history.record(result.root,++generation)
                    indexes[primary]=result
                    cache[primary]=result.root
                    savedIndex=null;diskChecked=true
                    persist(result)
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){}
        }
    }

    fun invalidate() {
        ++generation
        job?.cancel()
        largestJob?.cancel()
        clearIndexes()
        mutable.value=ScanState()
    }

    fun cancel() {
        if (!mutable.value.running) return
        ++generation
        job?.cancel()
        mutable.value = mutable.value.copy(running = false, error = "Scan cancelled", scanId = generation)
    }
}
