// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.*
import com.slate.android.storage.Snapshot
import java.io.File
import org.json.JSONObject

internal data class FolderInsight(val name:String,val bytes:Long,val increase:Long?)
internal class StorageHistory(context:Context) {
    private val prefs=context.getSharedPreferences("storage_history",Context.MODE_PRIVATE)
    private val primary=Environment.getExternalStorageDirectory()
    private val used=(primary.totalSpace-primary.usableSpace).coerceAtLeast(0)
    val sessionChange:Long?=if(prefs.contains("session_used") && primary.totalSpace>0) used-prefs.getLong("session_used",used) else null
    private var lastScanId=Long.MIN_VALUE
    var scannedAt by mutableLongStateOf(prefs.getLong("scanned_at",0))
        private set
    var measured by mutableLongStateOf(prefs.getLong("measured",0))
        private set
    var insights by mutableStateOf(readInsights())
        private set
    init { if(primary.totalSpace>0) prefs.edit().putLong("session_used",used).apply() }

    private fun readInsights():List<FolderInsight> = runCatching {
        val json=JSONObject(prefs.getString("folders","{}") ?: "{}")
        json.keys().asSequence().map{key ->
            val row=json.getJSONObject(key)
            FolderInsight(key,row.getLong("bytes"),if(row.has("increase"))row.getLong("increase") else null)
        }.toList().sortedByDescending{it.bytes}
    }.getOrDefault(emptyList())

    fun record(snapshot:Snapshot,scanId:Long) {
        if(snapshot.directory.absolutePath!=primary.absolutePath || snapshot.rootUnreadable || scanId==lastScanId) return
        lastScanId=scanId
        val previous=insights.associateBy{it.name}
        val updated=snapshot.entries.filter{it.directory && it.skipped==0L}.take(100).map {
            FolderInsight(it.file.name,it.bytes,previous[it.file.name]?.let{old->it.bytes-old.bytes})
        }
        val json=JSONObject()
        updated.forEach{item ->
            json.put(item.name,JSONObject().put("bytes",item.bytes).apply{item.increase?.let{put("increase",it)}})
        }
        measured=snapshot.bytes
        scannedAt=System.currentTimeMillis()
        insights=updated
        prefs.edit().putString("folders",json.toString()).putLong("scanned_at",scannedAt).putLong("measured",measured).apply()
    }
}
