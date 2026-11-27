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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
internal fun usageAllowed(context:Context):Boolean {
    val manager=context.getSystemService(AppOpsManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
}
@Suppress("DEPRECATION")
internal suspend fun readAppUsage(context:Context):Pair<List<AppUsage>,Int> = withContext(Dispatchers.IO) {
    val manager=context.getSystemService(StorageStatsManager::class.java) ?: error("Storage statistics unavailable")
    val pm=context.packageManager
    val entries=ArrayList<AppUsage>()
    var skipped=0
    for(app in pm.getInstalledApplications(0)) {
        currentCoroutineContext().ensureActive()
        try {
            val stats=manager.queryStatsForPackage(app.storageUuid ?: StorageManager.UUID_DEFAULT,app.packageName,Process.myUserHandle())
            entries.add(AppUsage(app.packageName,pm.getApplicationLabel(app).toString(),app.flags and ApplicationInfo.FLAG_SYSTEM !=0,
                stats.appBytes.coerceAtLeast(0),stats.dataBytes.coerceAtLeast(0),stats.cacheBytes.coerceAtLeast(0),
                runCatching{pm.getPackageInfo(app.packageName,0).versionName.orEmpty()}.getOrDefault(""),
                runCatching{pm.getPackageInfo(app.packageName,0).firstInstallTime}.getOrDefault(0)))
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){skipped++}
    }
    entries.sortedByDescending{it.total} to skipped
}

internal data class AppsSnapshot(val rows:List<AppUsage> = emptyList(),val skipped:Int=0,val allowed:Boolean=false,val loaded:Boolean=false,val busy:Boolean=false,val error:String?=null)
internal class AppsUsageModel(app:Application):AndroidViewModel(app){
    var snapshot by mutableStateOf(AppsSnapshot())
        private set
    private var job:Job?=null
    private var lastRead=0L
    fun refresh(force:Boolean=false){
        val context=getApplication<Application>()
        if(!usageAllowed(context)){job?.cancel();snapshot=AppsSnapshot();lastRead=0;return}
        snapshot=snapshot.copy(allowed=true)
        if(job?.isActive==true)return
        if(!force && snapshot.loaded && SystemClock.elapsedRealtime()-lastRead<120_000)return
        job=viewModelScope.launch{
            snapshot=snapshot.copy(busy=true,error=null)
            try{
                val result=readAppUsage(context)
                snapshot=AppsSnapshot(result.first,result.second,true,true)
                lastRead=SystemClock.elapsedRealtime()
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){snapshot=snapshot.copy(error=e.message ?: "Could not read app storage")}
            finally{snapshot=snapshot.copy(busy=false)}
        }
    }
    fun invalidate(){lastRead=0}
}
@Composable
internal fun rememberAppUsage():AppsUsageModel{
    val model:AppsUsageModel=viewModel()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){model.refresh()}}
    return model
}
@Composable
internal fun AppsStorageScreen(onBack:()->Unit,reviewFolder:(File)->Unit,initialCacheSort:Boolean=false,onCacheChanged:()->Unit={}) {
    val context=LocalContext.current
    val usage=rememberAppUsage()
    val data=usage.snapshot
    val fmt:(Long)->String={Formatter.formatFileSize(context,it)}
    val allowed=data.allowed
    val rows=data.rows
    val skipped=data.skipped
    val loaded=data.loaded
    val busy=data.busy
    var error by remember{mutableStateOf<String?>(null)}
    var filter by rememberSaveable{mutableIntStateOf(0)}
    var cacheSort by rememberSaveable{mutableStateOf(initialCacheSort)}
    var query by rememberSaveable{mutableStateOf("")}
    var searching by rememberSaveable{mutableStateOf(false)}
    var selectedPackage by rememberSaveable{mutableStateOf<String?>(null)}
    val selected=rows.firstOrNull{it.packageName==selectedPackage}
    LaunchedEffect(loaded,selectedPackage,selected){if(loaded && selectedPackage!=null && selected==null)selectedPackage=null}
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var debloater by rememberSaveable{mutableStateOf(false)}
    if(debloater){SlateDebloater(onBack={debloater=false;usage.invalidate();usage.refresh(true);onCacheChanged()});return}
    BackHandler(selectedPackage!=null){selectedPackage=null}
    val wide=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=800
    if(selected!=null&&!wide){
        AppUsageDetails(selected,fmt,onBack={selectedPackage=null},reviewFolder=reviewFolder,onExternalAction={usage.invalidate()})
        return
    }
    Row(Modifier.fillMaxSize()){
    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Text(tr("App storage"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            TextButton(onClick={debloater=true}){Text(androidx.compose.ui.res.stringResource(R.string.debloat_title))}
            IconButton(onClick={usage.refresh(true)},enabled=allowed&&!busy){Icon(Icons.Default.Refresh,"Refresh")}
        }
        ClearAllAppCaches(enabled=!busy,onChanged={usage.invalidate();usage.refresh(true);onCacheChanged()})
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
            (error ?: data.error)?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            val filtered=remember(rows,filter,query,cacheSort){
                rows.filter{(filter==0 || (filter==1 && !it.system) || (filter==2 && it.system))&&(it.label.contains(query,true)||it.packageName.contains(query,true))}.let{if(cacheSort)it.sortedByDescending{row->row.cacheBytes}else it}
            }
            LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                item {
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text(if(loaded)fmt(rows.sumOf{it.total}) else "—",style=MaterialTheme.typography.headlineSmall)
                            Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.apps_reported,rows.size),style=MaterialTheme.typography.bodySmall)
                            Text(tr("Cache · ")+fmt(rows.sumOf{it.cacheBytes}),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                            Text(tr("App and file totals may overlap."),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if(skipped>0)Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.apps_unavailable,skipped),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                        FilterChip(selected=filter==0,onClick={filter=0},label={Text(tr("All"))})
                        FilterChip(selected=filter==1,onClick={filter=1},label={Text(tr("User"))})
                        FilterChip(selected=filter==2,onClick={filter=2},label={Text(tr("System"))})
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick={searching=!searching;if(!searching)query=""}){Icon(Icons.Default.Search,"Search")}
                    }
                    if(searching)OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text(tr("Search apps"))},modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.apps_count,filtered.size),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        TextButton(onClick={cacheSort=!cacheSort}){Text(tr(if(cacheSort)"Cache ↓" else "Size ↓"))}
                    }
                }
                items(filtered,key={it.packageName}){row->
                    Card(onClick={selectedPackage=row.packageName},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        ListItem(headlineContent={Text(row.label,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(row.packageName,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)},
                            leadingContent={AppIcon(row.packageName)},trailingContent={Text(fmt(row.total),color=MaterialTheme.colorScheme.primary)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                    }
                }
            }
        }
    }
    if(wide&&selected!=null){
        VerticalDivider(Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()){AppUsageDetails(selected,fmt,onBack={selectedPackage=null},reviewFolder=reviewFolder,onExternalAction={usage.invalidate()})}
    }
    }
}

@Composable
private fun AppUsageDetails(row:AppUsage,fmt:(Long)->String,onBack:()->Unit,reviewFolder:(File)->Unit,onExternalAction:()->Unit){
    val context=LocalContext.current
    val launchIntent=remember(row.packageName){context.packageManager.getLaunchIntentForPackage(row.packageName)}
    val info=remember(row.packageName){runCatching{context.packageManager.getApplicationInfo(row.packageName,0)}.getOrNull()}
    val privatePath=info?.dataDir
    val external=remember(row.packageName){listOf("data","obb","media").map{File(android.os.Environment.getExternalStorageDirectory(),"Android/$it/${row.packageName}")}}
    val readable by produceState<Set<File>>(emptySet(),row.packageName){
        value=withContext(Dispatchers.IO){external.filter{file->runCatching{file.isDirectory && file.list()!=null}.getOrDefault(false)}.toSet()}
    }
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Text(row.label,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)){
            item{
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            AppIcon(row.packageName)
                            Column(Modifier.weight(1f)){
                                Text(row.label,style=MaterialTheme.typography.titleMedium)
                                Text(row.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(row.version.isNotBlank())Text(tr("Version ")+row.version,style=MaterialTheme.typography.bodySmall)
                            }
                            Text(fmt(row.total),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                        val dataWithoutCache=(row.dataBytes-row.cacheBytes).coerceAtLeast(0)
                        listOf("App" to row.appBytes,"Data" to dataWithoutCache,"Cache" to row.cacheBytes).forEach{(label,value)->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(tr(label));Text(fmt(value))}
                        }
                        if(row.installedAt>0)Text(tr("Installed ")+java.text.DateFormat.getDateInstance().format(java.util.Date(row.installedAt)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={launchIntent?.let{runCatching{context.startActivity(it)}}},enabled=launchIntent!=null,modifier=Modifier.weight(1f)){Text(tr("Open"))}
                    if(!row.system && row.packageName!=context.packageName)OutlinedButton(onClick={onExternalAction();runCatching{context.startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:"+row.packageName))) }},modifier=Modifier.weight(1f)){Text(tr("Uninstall"),color=MaterialTheme.colorScheme.error)}
                }
                Button(onClick={onExternalAction();runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+row.packageName))) }},modifier=Modifier.fillMaxWidth()){Text(tr("App settings"))}
            }
            item{Text(tr("App folders"),style=MaterialTheme.typography.titleMedium)}
            item{
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column{
                        privatePath?.let{path->
                            ListItem(headlineContent={Text(tr("Private data"))},supportingContent={Text(path+"\n"+tr("No direct access"),style=MaterialTheme.typography.bodySmall)},leadingContent={DeviceGlyph(R.drawable.ic_storage_folder)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                        }
                        external.forEach{folder->
                            val access=folder in readable
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            ListItem(modifier=if(access)Modifier.clickable{reviewFolder(folder)}else Modifier,headlineContent={Text(folder.parentFile?.name?.uppercase().orEmpty())},supportingContent={Text(folder.path+if(access)"" else "\n"+tr("Not accessible or absent"),style=MaterialTheme.typography.bodySmall,maxLines=3)},leadingContent={DeviceGlyph(R.drawable.ic_storage_folder)},trailingContent={if(access)TextButton(onClick={reviewFolder(folder)}){Text(tr("Open"))}},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(packageName:String) {
    val context=LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,packageName){
        value=withContext(Dispatchers.IO){runCatching{context.packageManager.getApplicationIcon(packageName).toBitmap(96,96).asImageBitmap()}.getOrNull()}
    }
    val bitmap=image
    if(bitmap!=null)Image(bitmap,null,Modifier.size(40.dp))
    else DeviceGlyph(R.drawable.ic_device_overview,modifier=Modifier.size(40.dp))
}
