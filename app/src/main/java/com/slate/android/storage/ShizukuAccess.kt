// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slate.android.R
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

private enum class Access { MISSING, STOPPED, UNAUTHORIZED, READY }
private const val managerPackage="moe.shizuku.privileged.api"
private fun access(context:Context):Access {
    if(shizukuGranted())return Access.READY
    if(shizukuAvailable())return Access.UNAUTHORIZED
    return if(runCatching{context.packageManager.getPackageInfo(managerPackage,0)}.isSuccess)Access.STOPPED else Access.MISSING
}
@Composable
private fun rememberAccess(context:Context):Access {
    val state by produceState(access(context),context){
        while(true){value=access(context);delay(1000)}
    }
    return state
}
private fun activate(context:Context,state:Access){
    when(state){
        Access.UNAUTHORIZED -> Shizuku.requestPermission(302)
        Access.STOPPED -> {
            val intent=context.packageManager.getLaunchIntentForPackage(managerPackage)
                ?: Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/"))
            context.startActivity(intent)
        }
        Access.MISSING -> context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")))
        Access.READY -> context.packageManager.getLaunchIntentForPackage(managerPackage)?.let{context.startActivity(it)}
    }
}
@Composable
private fun accessAction(state:Access)=stringResource(when(state){
    Access.MISSING->R.string.shizuku_install
    Access.STOPPED->R.string.shizuku_start
    Access.UNAUTHORIZED->R.string.shizuku_authorize
    Access.READY->R.string.shizuku_manage
})
@Composable
internal fun ShizukuAccessRow(){
    val context=LocalContext.current
    val state=rememberAccess(context)
    var error by remember{mutableStateOf(false)}
    ListItem(
        headlineContent={Text("Shizuku")},
        supportingContent={
            Column {
                Text(stringResource(when(state){
                    Access.MISSING->R.string.shizuku_optional
                    Access.STOPPED->R.string.shizuku_stopped
                    Access.UNAUTHORIZED->R.string.shizuku_permission
                    Access.READY->R.string.shizuku_ready
                }))
                if(error)Text(stringResource(R.string.shizuku_action_failed),color=MaterialTheme.colorScheme.error)
            }
        },
        trailingContent={TextButton(onClick={error=runCatching{activate(context,state)}.isFailure}){Text(accessAction(state))}}
    )
}

@Composable
internal fun ClearAllAppCaches(enabled:Boolean,onChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val state=rememberAccess(context)
    var confirm by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var done by remember{mutableIntStateOf(0)}
    var total by remember{mutableIntStateOf(0)}
    var message by remember{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        OutlinedButton(onClick={
            message=null
            if(state==Access.READY)confirm=true
            else runCatching{activate(context,state)}.onFailure{message=context.getString(R.string.shizuku_action_failed)}
        },enabled=enabled&&!busy,modifier=Modifier.fillMaxWidth()){
            Text(stringResource(R.string.clear_all_caches))
        }
        if(state!=Access.READY)Text(stringResource(R.string.cache_requires_shizuku),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy){
            LinearProgressIndicator(progress={if(total>0)done.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.cache_progress,done,total),style=MaterialTheme.typography.labelSmall)
        }
        message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.clear_all_caches))},
        text={Text(stringResource(R.string.cache_all_confirmation))},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(android.R.string.cancel))}},
        confirmButton={TextButton(onClick={
            confirm=false;busy=true;done=0;total=0;message=null
            scope.launch{
                try{
                    check(shizukuGranted())
                    val before=withContext(Dispatchers.IO){StatFs(Environment.getDataDirectory().path).availableBytes}
                    val packages=withContext(Dispatchers.IO){context.packageManager.getInstalledApplications(0).map{it.packageName}.distinct().sorted()}
                    total=packages.size
                    val user=Process.myUid()/100000
                    val successful=withCacheService(context){service->
                        if(!service.supportsCacheOnly())return@withCacheService -1
                        var success=0
                        for(name in packages){
                            currentCoroutineContext().ensureActive()
                            if(service.clearCache(user,name))success++
                            withContext(Dispatchers.Main){done++}
                        }
                        success
                    }
                    if(successful<0)message=context.getString(R.string.cache_only_unsupported)
                    else{
                        val after=withContext(Dispatchers.IO){StatFs(Environment.getDataDirectory().path).availableBytes}
                        message=context.getString(R.string.cache_all_result,successful,total-successful,Formatter.formatFileSize(context,(after-before).coerceAtLeast(0)))
                    }
                }catch(e:CancellationException){throw e}
                catch(_:Exception){message=context.getString(R.string.cache_all_interrupted,done,total)}
                finally{busy=false;onChanged()}
            }
        }){Text(stringResource(R.string.clear_all_caches))}})
}
