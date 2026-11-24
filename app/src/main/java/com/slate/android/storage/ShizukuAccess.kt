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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import android.provider.Settings
import androidx.activity.compose.BackHandler
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


internal fun cacheCandidates(rows:List<AppUsage>):List<AppUsage> =
    rows.filter{it.cacheBytes>0 && validCachePackage(it.packageName)}.distinctBy{it.packageName}.sortedByDescending{it.cacheBytes}

@Composable
internal fun ClearAllAppCaches(enabled:Boolean,onChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val state=rememberAccess(context)
    var review by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    var scanning by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var candidates by remember{mutableStateOf<List<AppUsage>>(emptyList())}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())}
    var skipped by remember{mutableIntStateOf(0)}
    var done by remember{mutableIntStateOf(0)}
    var total by remember{mutableIntStateOf(0)}
    var message by remember{mutableStateOf<String?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    val chosen=candidates.filter{it.packageName in selected}
    val fmt:(Long)->String={Formatter.formatFileSize(context,it)}
    BackHandler(busy||scanning){job?.cancel()}
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        OutlinedButton(onClick={
            message=null
            when{
                state!=Access.READY -> runCatching{activate(context,state)}.onFailure{message=context.getString(R.string.shizuku_action_failed)}
                !usageAllowed(context) -> runCatching{context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}.onFailure{message=context.getString(R.string.cache_scan_failed)}
                else -> {
                    scanning=true
                    job=scope.launch{
                        try{
                            val supported=withCacheService(context){it.supportsCacheOnly()}
                            if(!supported){message=context.getString(R.string.cache_only_unsupported);return@launch}
                            val (rows,unavailable)=readAppUsage(context)
                            candidates=cacheCandidates(rows)
                            skipped=unavailable+rows.count{it.cacheBytes>0&&!validCachePackage(it.packageName)}
                            selected=candidates.map{it.packageName}.toSet()
                            if(candidates.isEmpty())message=context.getString(R.string.cache_none,skipped)
                            else review=true
                        }catch(e:CancellationException){throw e}
                        catch(_:Exception){message=context.getString(R.string.cache_scan_failed)}
                        finally{scanning=false}
                    }
                }
            }
        },enabled=enabled&&!busy&&!scanning,modifier=Modifier.fillMaxWidth()){
            Text(stringResource(R.string.cache_analyze))
        }
        if(state!=Access.READY)Text(stringResource(R.string.cache_requires_shizuku),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(scanning){LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.cache_scanning))}
        if(busy){
            LinearProgressIndicator(progress={if(total>0)done.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.cache_progress,done,total),style=MaterialTheme.typography.labelSmall)
        }
        if(scanning||busy)TextButton(onClick={job?.cancel()}){Text(stringResource(android.R.string.cancel))}
        message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
    }
    if(review&&!confirm)AlertDialog(onDismissRequest={review=false},title={Text(stringResource(R.string.cache_review))},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(R.string.cache_selected,chosen.size,fmt(chosen.sumOf{it.cacheBytes})),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cache_estimate),style=MaterialTheme.typography.bodySmall)
                if(skipped>0)Text(stringResource(R.string.cache_skipped,skipped),style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    TextButton(onClick={selected=candidates.map{it.packageName}.toSet()}){Text(stringResource(R.string.cache_select_all))}
                    TextButton(onClick={selected=emptySet()}){Text(stringResource(R.string.cache_select_none))}
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=300.dp)){
                    items(candidates,key={it.packageName}){row->
                        val checked=row.packageName in selected
                        Row(Modifier.fillMaxWidth().toggleable(value=checked,role=Role.Checkbox,onValueChange={value->selected=if(value)selected+row.packageName else selected-row.packageName}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=checked,onCheckedChange=null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)){
                                Text(row.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(row.packageName,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(row.system)Text(stringResource(R.string.cache_system_app),style=MaterialTheme.typography.labelSmall)
                            }
                            Text(fmt(row.cacheBytes),style=MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },dismissButton={TextButton(onClick={review=false}){Text(stringResource(android.R.string.cancel))}},
        confirmButton={TextButton(enabled=chosen.isNotEmpty(),onClick={confirm=true}){Text(stringResource(R.string.cache_continue))}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.cache_confirm_title))},
        text={Text(stringResource(R.string.cache_confirm_selected,chosen.size,fmt(chosen.sumOf{it.cacheBytes})))},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.cache_back))}},
        confirmButton={TextButton(onClick={
            val packages=chosen.map{it.packageName}
            confirm=false;review=false;busy=true;done=0;total=packages.size;message=null
            job=scope.launch{
                var successful=0
                try{
                    check(shizukuGranted())
                    val before=withContext(Dispatchers.IO){StatFs(Environment.getDataDirectory().path).availableBytes}
                    val user=Process.myUid()/100000
                    withCacheService(context){service->
                        check(service.supportsCacheOnly())
                        for(name in packages){
                            currentCoroutineContext().ensureActive()
                            val ok=service.clearCache(user,name)
                            withContext(Dispatchers.Main){if(ok)successful++;done++}
                        }
                    }
                    val after=withContext(Dispatchers.IO){StatFs(Environment.getDataDirectory().path).availableBytes}
                    message=context.getString(R.string.cache_all_result,successful,total-successful,fmt((after-before).coerceAtLeast(0)))
                }catch(e:CancellationException){message=context.getString(R.string.cache_all_interrupted,done,total);throw e}
                catch(_:Exception){message=context.getString(R.string.cache_all_interrupted,done,total)}
                finally{busy=false;onChanged()}
            }
        }){Text(stringResource(R.string.cache_confirm_title))}})
}
