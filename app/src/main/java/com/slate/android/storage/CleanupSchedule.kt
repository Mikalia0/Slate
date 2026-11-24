// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import com.slate.android.tr

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Environment
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

private const val JOB=31472
private const val PREF="scheduled_cleanup"
private fun hasAccess(context:Context)=if(Build.VERSION.SDK_INT>=30)Environment.isExternalStorageManager()
    else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED

internal fun scheduleCleanup(context:Context,days:Int):Boolean {
    val scheduler=context.getSystemService(JobScheduler::class.java) ?: return false
    if(days==0){scheduler.cancel(JOB);context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt("days",0).apply();return true}
    if(days !in listOf(1,7))return false
    val info=JobInfo.Builder(JOB,ComponentName(context,CleanupJob::class.java))
        .setPeriodic(days*24L*60*60*1000)
        .setPersisted(true).setRequiresCharging(true).setRequiresBatteryNotLow(true)
        .build()
    val ok=runCatching{scheduler.schedule(info)==JobScheduler.RESULT_SUCCESS}.getOrDefault(false)
    if(ok)context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt("days",days).apply()
    return ok
}

class CleanupJob:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var running:Job?=null
    override fun onStartJob(params:JobParameters):Boolean {
        if(!hasAccess(this))return false
        running?.cancel()
        running=scope.launch {
            try{
                val root=Environment.getExternalStorageDirectory()
                val excluded=getSharedPreferences("storage_exclusions",MODE_PRIVATE).getStringSet("paths",emptySet()).orEmpty().toSet()
                val result=withContext(Dispatchers.IO){
                    val temp=findCleanup(root,CleanupKind.TEMPORARY,excluded){}
                    val empty=findCleanup(root,CleanupKind.EMPTY,excluded){}
                    Triple(temp.items.sumOf{it.bytes},temp.items.size+empty.items.size,temp.limited || empty.limited)
                }
                getSharedPreferences(PREF,MODE_PRIVATE).edit().putLong("at",System.currentTimeMillis()).putLong("bytes",result.first)
                    .putInt("count",result.second).putBoolean("limited",result.third).apply()
                jobFinished(params,false)
            }catch(e:CancellationException){throw e}catch(_:Exception){jobFinished(params,true)}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {running?.cancel();running=null;return true}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

@Composable
internal fun ScheduledCleanupSettings(){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences(PREF,Context.MODE_PRIVATE)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var days by remember{mutableIntStateOf(prefs.getInt("days",0))}
    var at by remember{mutableLongStateOf(prefs.getLong("at",0))}
    var count by remember{mutableIntStateOf(prefs.getInt("count",0))}
    var bytes by remember{mutableLongStateOf(prefs.getLong("bytes",0))}
    var error by remember{mutableStateOf(false)}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
        days=prefs.getInt("days",0);at=prefs.getLong("at",0);count=prefs.getInt("count",0);bytes=prefs.getLong("bytes",0)
    }}
    Text(tr("Scheduled cleanup review"),style=MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf(0 to "Off",1 to "Daily",7 to "Weekly").forEach{(value,label)->
            FilterChip(selected=days==value,onClick={error=!scheduleCleanup(context,value);if(!error)days=value},label={Text(tr(label))})
        }
    }
    Text(tr("Scans temporary files and empty folders while charging. Review before deletion."),style=MaterialTheme.typography.bodySmall)
    if(at>0)Text(DateFormat.getDateTimeInstance().format(Date(at))+" · $count candidates · "+Formatter.formatFileSize(context,bytes),style=MaterialTheme.typography.bodySmall)
    if(error)Text(tr("Could not schedule scan"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
}
