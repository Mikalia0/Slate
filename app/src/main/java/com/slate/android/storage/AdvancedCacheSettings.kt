// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import com.slate.android.tr

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.StatFs
import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

private fun freeBytes():Long=StatFs(Environment.getDataDirectory().path).availableBytes
private fun shizukuAvailable():Boolean=runCatching{Shizuku.pingBinder() && Shizuku.getVersion()>=12}.getOrDefault(false)
private fun shizukuGranted():Boolean=shizukuAvailable() && runCatching{Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}.getOrDefault(false)
private suspend fun shizukuTrim(context:Context,target:Long):Int {
    val args=Shizuku.UserServiceArgs(ComponentName(context,CacheUserService::class.java))
        .daemon(false).processNameSuffix("cache_service").version(1)
    val connected=CompletableDeferred<ICacheService>()
    val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder){
            connected.complete(ICacheService.Stub.asInterface(binder))
        }
        override fun onServiceDisconnected(name:ComponentName){
            connected.completeExceptionally(IllegalStateException("Shizuku disconnected"))
        }
    }
    try {
        Shizuku.bindUserService(args,connection)
        val service=withTimeout(15_000){connected.await()}
        return withContext(Dispatchers.IO){service.trim(target)}
    }finally{runCatching{Shizuku.unbindUserService(args,connection,true)}}
}

@Composable
internal fun AdvancedCacheSettings(onChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var available by remember{mutableStateOf(shizukuAvailable())}
    var granted by remember{mutableStateOf(shizukuGranted())}
    var confirm by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var amount by remember{mutableIntStateOf(1)}
    LaunchedEffect(Unit){
        while(true){available=shizukuAvailable();granted=shizukuGranted();delay(1500)}
    }
    Text(tr("Advanced cache cleanup"),style=MaterialTheme.typography.titleMedium)
    Text(tr("Ask Android to free additional space from app caches. App data is not targeted. System-managed cache cleanup is device-wide and does not use folder exclusions."),style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(1,2,5).forEach{n->FilterChip(selected=amount==n,onClick={amount=n},enabled=!busy,label={Text("$n GB")})}
    }
    Text("Shizuku: "+if(granted)"Authorized" else if(available)"Permission required" else "Not running",style=MaterialTheme.typography.labelMedium)
    OutlinedButton(onClick={
        if(granted)confirm="Shizuku"
        else runCatching{Shizuku.requestPermission(301)}.onFailure{message="Could not request Shizuku permission"}
    },enabled=available&&!busy,modifier=Modifier.fillMaxWidth()){Text(tr(if(granted)"Clean with Shizuku" else "Authorize Shizuku"))}
    OutlinedButton(onClick={confirm="Root"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(tr("Clean with root"))}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    confirm?.let{backend->
        AlertDialog(onDismissRequest={confirm=null},title={Text(tr("Trim app caches?"))},text={Text("Request up to $amount GB of additional free space using $backend. Cached downloads may need to be downloaded again. Android decides what can be freed.")},
            dismissButton={TextButton(onClick={confirm=null}){Text(tr("Cancel"))}},
            confirmButton={TextButton(onClick={
                confirm=null;busy=true;message=null
                scope.launch{
                    try{
                        val before=withContext(Dispatchers.IO){freeBytes()}
                        val target=before+amount*1_000_000_000L
                        val result=if(backend=="Root")withContext(Dispatchers.IO){trimCaches(target,true)} else shizukuTrim(context,target)
                        val after=withContext(Dispatchers.IO){freeBytes()}
                        message=if(result==0)"Free space change: "+Formatter.formatFileSize(context,(after-before).coerceAtLeast(0))
                            else "Cleanup did not complete (code $result). Check permissions and free space."
                        onChanged()
                    }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Cleanup unavailable"}
                    finally{busy=false}
                }
            }){Text(tr("Clean"))}})
    }
}
