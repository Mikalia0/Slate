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

data class ScanState(val directory: File? = null, val snapshot: Snapshot? = null, val running: Boolean = false, val visited: Long = 0, val error: String? = null, val scanId: Long = 0)

class StorageModel : ViewModel() {
    private val scanner = StorageScanner()
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun open(directory: File) {
        job?.cancel()
        val id = ++generation
        mutable.value = ScanState(directory = directory, running = true, scanId = id)
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    scanner.scan(directory) { count ->
                        mutable.update { previous ->
                            if (previous.scanId == id && previous.running) previous.copy(visited = count) else previous
                        }
                    }
                }
                if (generation == id) mutable.value = ScanState(directory, result, scanId = id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == id) mutable.value = ScanState(directory, error = e.message ?: "Scan failed", scanId = id)
            }
        }
    }

    fun cancel() {
        ++generation
        job?.cancel()
        mutable.value = mutable.value.copy(running = false, error = "Scan cancelled", scanId = generation)
    }
}
