// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.*
import com.slate.android.storage.Snapshot
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

internal data class ScanRecord(val at:Long,val bytes:Long,val files:Long,val skipped:Long,val delta:Long?,val folders:List<FolderInsight>)
internal data class FolderInsight(val name:String,val bytes:Long,val increase:Long?)
internal class StorageHistory(context:Context) {
    private val prefs=context.getSharedPreferences("storage_history",Context.MODE_PRIVATE)
    private val primary=Environment.getExternalStorageDirectory()
    private val used=(primary.totalSpace-primary.usableSpace).coerceAtLeast(0)
    val sessionChange:Long?=if(prefs.contains("session_used") && primary.totalSpace>0) used-prefs.getLong("session_used",used) else null
    private var lastScanId=Long.MIN_VALUE
    var records by mutableStateOf(readRecords())
        private set
    private fun readRecords():List<ScanRecord> = runCatching {
        val array=JSONArray(prefs.getString("records","[]") ?: "[]")
        List(array.length()){i->
            val row=array.getJSONObject(i)
            val folders=row.optJSONArray("folders") ?: JSONArray()
            ScanRecord(row.getLong("at"),row.getLong("bytes"),row.getLong("files"),row.getLong("skipped"),if(row.has("delta"))row.getLong("delta")else null,
                List(folders.length()){j->val item=folders.getJSONObject(j);FolderInsight(item.getString("name"),item.getLong("bytes"),if(item.has("increase"))item.getLong("increase")else null)})
        }
    }.getOrDefault(emptyList())
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

    fun clear(){records=emptyList();insights=emptyList();scannedAt=0;measured=0;prefs.edit().remove("records").remove("folders").remove("scanned_at").remove("measured").apply()}

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
        val delta=if(scannedAt>0 && snapshot.skipped==0L && records.firstOrNull()?.skipped==0L) snapshot.bytes-measured else null
        measured=snapshot.bytes
        scannedAt=System.currentTimeMillis()
        insights=updated
        records=(listOf(ScanRecord(scannedAt,snapshot.bytes,snapshot.files,snapshot.skipped,delta,updated))+records).take(30)
        val array=JSONArray()
        records.forEach{record->
            val folders=JSONArray()
            record.folders.forEach{item->folders.put(JSONObject().put("name",item.name).put("bytes",item.bytes).apply{item.increase?.let{put("increase",it)}})}
            array.put(JSONObject().put("at",record.at).put("bytes",record.bytes).put("files",record.files).put("skipped",record.skipped).put("folders",folders).apply{record.delta?.let{put("delta",it)}})
        }
        prefs.edit().putString("folders",json.toString()).putLong("scanned_at",scannedAt).putLong("measured",measured).putString("records",array.toString()).apply()
    }
}
