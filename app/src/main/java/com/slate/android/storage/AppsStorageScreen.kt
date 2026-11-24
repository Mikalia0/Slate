// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import com.slate.android.tr

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.slate.android.R
import com.slate.android.device.DeviceGlyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class AppUsage(val packageName:String,val label:String,val system:Boolean,val appBytes:Long,val dataBytes:Long,val cacheBytes:Long,val version:String="",val installedAt:Long=0) {
    val total:Long get()=appBytes+dataBytes
}
private fun usageAllowed(context:Context):Boolean {
    val manager=context.getSystemService(AppOpsManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
}
@Suppress("DEPRECATION")
private suspend fun readAppUsage(context:Context):Pair<List<AppUsage>,Int> = withContext(Dispatchers.IO) {
    val manager=context.getSystemService(StorageStatsManager::class.java) ?: error("Storage statistics unavailable")
    val pm=context.packageManager
    val entries=ArrayList<AppUsage>()
    var skipped=0
    for(app in pm.getInstalledApplications(0)) {
        currentCoroutineContext().ensureActive()
        if(app.storageUuid!=StorageManager.UUID_DEFAULT)continue
        try {
            val stats=manager.queryStatsForPackage(StorageManager.UUID_DEFAULT,app.packageName,Process.myUserHandle())
            entries.add(AppUsage(app.packageName,pm.getApplicationLabel(app).toString(),app.flags and ApplicationInfo.FLAG_SYSTEM !=0,
                stats.appBytes.coerceAtLeast(0),stats.dataBytes.coerceAtLeast(0),stats.cacheBytes.coerceAtLeast(0),
                runCatching{pm.getPackageInfo(app.packageName,0).versionName.orEmpty()}.getOrDefault(""),
                runCatching{pm.getPackageInfo(app.packageName,0).firstInstallTime}.getOrDefault(0)))
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){skipped++}
    }
    entries.sortedByDescending{it.total} to skipped
}

@Composable
internal fun AppsStorageScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val fmt:(Long)->String={Formatter.formatFileSize(context,it)}
    var allowed by remember{mutableStateOf(usageAllowed(context))}
    var loaded by remember{mutableStateOf(false)}
    var rows by remember{mutableStateOf<List<AppUsage>>(emptyList())}
    var skipped by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var refresh by remember{mutableIntStateOf(0)}
    var system by remember{mutableStateOf(false)}
    var cacheSort by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    var searching by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<AppUsage?>(null)}
    LaunchedEffect(lifecycle,refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            allowed=usageAllowed(context)
            if(allowed) {
                busy=true;error=null
                try {
                    val result=readAppUsage(context)
                    rows=result.first;skipped=result.second;loaded=true
                } catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){error=e.message ?: "Could not read app storage"}
                finally{busy=false}
            } else {rows=emptyList();loaded=false}
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Text(tr("App storage"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            IconButton(onClick={refresh++},enabled=allowed&&!busy){Icon(Icons.Default.Refresh,"Refresh")}
        }
        if(!allowed) {
            Card(Modifier.fillMaxWidth().padding(top=12.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(30.dp))
                    Text(tr("Usage access"),style=MaterialTheme.typography.titleLarge)
                    Text(tr("Allow Slate to read installed app sizes and cache."),style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={try{context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}catch(_:Exception){error="Usage access settings unavailable"}}){Text(tr("Grant access"))}
                    error?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                }
            }
        } else {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            val filtered=remember(rows,system,query,cacheSort){
                rows.filter{it.system==system&&(it.label.contains(query,true)||it.packageName.contains(query,true))}.let{if(cacheSort)it.sortedByDescending{row->row.cacheBytes}else it}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                item {
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text(if(loaded)fmt(rows.sumOf{it.total}) else "—",style=MaterialTheme.typography.headlineMedium)
                            Text("Reported app storage · ${rows.size} apps",style=MaterialTheme.typography.bodySmall)
                            Text(tr("Cache · ")+fmt(rows.sumOf{it.cacheBytes}),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                            Text(tr("Shared files may overlap with file analysis."),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(skipped>0)Text("$skipped apps unavailable",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                        FilterChip(selected=!system,onClick={system=false},label={Text(tr("User apps"))})
                        FilterChip(selected=system,onClick={system=true},label={Text(tr("System apps"))})
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick={searching=!searching;if(!searching)query=""}){Icon(Icons.Default.Search,"Search")}
                    }
                    if(searching)OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text(tr("Search apps"))},modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("${filtered.size} apps",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        TextButton(onClick={cacheSort=!cacheSort}){Text(tr(if(cacheSort)"Cache ↓" else "Size ↓"))}
                    }
                }
                items(filtered,key={it.packageName}){row->
                    Card(onClick={selected=row},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        ListItem(headlineContent={Text(row.label,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(tr("Cache · ")+fmt(row.cacheBytes),style=MaterialTheme.typography.bodySmall)},
                            leadingContent={AppIcon(row.packageName)},trailingContent={Text(fmt(row.total),color=MaterialTheme.colorScheme.primary)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                    }
                }
            }
        }
    }
    selected?.let{row->
        AlertDialog(onDismissRequest={selected=null},title={Text(row.label)},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(row.packageName,style=MaterialTheme.typography.bodySmall)
                if(row.version.isNotBlank())Text(tr("Version ")+row.version,style=MaterialTheme.typography.bodySmall)
                if(row.installedAt>0)Text(tr("Installed ")+java.text.DateFormat.getDateInstance().format(java.util.Date(row.installedAt)),style=MaterialTheme.typography.bodySmall)
                listOf("App" to row.appBytes,"Data (incl. cache)" to row.dataBytes,"Cache" to row.cacheBytes).forEach{(label,value)->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text(fmt(value))}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TextButton(onClick={selected=null;context.packageManager.getLaunchIntentForPackage(row.packageName)?.let{runCatching{context.startActivity(it)}}},enabled=context.packageManager.getLaunchIntentForPackage(row.packageName)!=null){Text(tr("Open"))}
                    if(!row.system && row.packageName!=context.packageName)TextButton(onClick={selected=null;runCatching{context.startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:"+row.packageName)))}}){Text(tr("Uninstall"),color=MaterialTheme.colorScheme.error)}
                }
            }
        },confirmButton={TextButton(onClick={
            selected=null
            runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+row.packageName)))}
        }){Text(tr("App settings"))}},dismissButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})
    }
}

@Composable
private fun AppIcon(packageName:String) {
    val context=LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,packageName){
        value=withContext(Dispatchers.IO){runCatching{context.packageManager.getApplicationIcon(packageName).toBitmap(96,96).asImageBitmap()}.getOrNull()}
    }
    val bitmap=image
    if(bitmap!=null)Image(bitmap,null,Modifier.size(40.dp))
    else DeviceGlyph(R.drawable.ic_device_overview,modifier=Modifier.size(40.dp))
}
