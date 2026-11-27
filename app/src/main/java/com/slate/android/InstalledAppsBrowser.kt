// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.slate.android.storage.AppIcon
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*

private data class InstalledApk(val id:String,val label:String,val version:String,val system:Boolean,val files:List<String>,val updated:Long){
    val fileName:String get()=(label+"_"+version).replace(Regex("[^\\p{L}\\p{N} ._()-]"),"_").take(100)+"_"+id.takeLast(80)+if(files.size>1)".apks" else ".apk"
}
private object InstalledCatalog {
    @Volatile var snapshot:List<InstalledApk>?=null
    suspend fun restore(context:Context):List<InstalledApk>?=withContext(Dispatchers.IO){runCatching{
        val raw=android.util.AtomicFile(File(context.cacheDir,"installed-catalog-v1")).openRead().bufferedReader().use{it.readText()}
        val array=org.json.JSONArray(raw);require(array.length()<=20000)
        List(array.length()){i->val row=array.getJSONObject(i);val paths=row.getJSONArray("paths");InstalledApk(row.getString("id"),row.getString("label"),row.getString("version"),row.getBoolean("system"),List(paths.length()){paths.getString(it)},row.getLong("updated"))}
    }.getOrNull()}
    private fun save(context:Context,rows:List<InstalledApk>){
        val file=android.util.AtomicFile(File(context.cacheDir,"installed-catalog-v1"))
        val array=org.json.JSONArray();rows.forEach{row->array.put(org.json.JSONObject().put("id",row.id).put("label",row.label).put("version",row.version).put("system",row.system).put("paths",org.json.JSONArray(row.files)).put("updated",row.updated))}
        var stream:java.io.FileOutputStream?=null
        try{val raw=file.startWrite();stream=raw;raw.write(array.toString().toByteArray(Charsets.UTF_8));file.finishWrite(raw);stream=null}catch(_:Exception){stream?.let{file.failWrite(it)}}
    }
    @Suppress("DEPRECATION")
    suspend fun read(context:Context):List<InstalledApk> = withContext(Dispatchers.IO){
        val pm=context.packageManager
        pm.getInstalledPackages(0).mapNotNull{pkg->
            coroutineContext.ensureActive()
            val app=pkg.applicationInfo ?: return@mapNotNull null
            runCatching{InstalledApk(pkg.packageName,pm.getApplicationLabel(app).toString(),pkg.versionName.orEmpty(),app.flags and ApplicationInfo.FLAG_SYSTEM!=0,listOf(app.sourceDir)+app.splitSourceDirs.orEmpty(),pkg.lastUpdateTime)}.getOrNull()
        }.sortedBy{it.label.lowercase()}.also{snapshot=it;save(context,it)}
    }
}
private suspend fun writeInstalled(context:Context,app:InstalledApk,target:java.io.OutputStream)=withContext(Dispatchers.IO){
    @Suppress("DEPRECATION") val current=context.packageManager.getPackageInfo(app.id,0)
    check(current.lastUpdateTime==app.updated)
    val info=checkNotNull(current.applicationInfo)
    check(listOf(info.sourceDir)+info.splitSourceDirs.orEmpty()==app.files)
    val buffer=ByteArray(256*1024)
    suspend fun copy(path:String,out:java.io.OutputStream){
        File(path).inputStream().use{input->while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}
    }
    if(app.files.size==1)copy(app.files.first(),target)
    else ZipOutputStream(target.buffered()).use{zip->
        zip.setLevel(0)
        app.files.forEachIndexed{i,path->zip.putNextEntry(ZipEntry(if(i==0)"base.apk" else File(path).name));copy(path,zip);zip.closeEntry()}
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun InstalledAppsBrowser(onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var rows by remember{mutableStateOf(InstalledCatalog.snapshot.orEmpty())}
    var loaded by remember{mutableStateOf(InstalledCatalog.snapshot!=null)}
    var revision by remember{mutableIntStateOf(0)}
    var query by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableIntStateOf(0)}
    var grid by rememberSaveable{mutableStateOf(true)}
    var chosen by remember{mutableStateOf(setOf<String>())}
    var active by remember{mutableStateOf<InstalledApk?>(null)}
    var pending by remember{mutableStateOf<InstalledApk?>(null)}
    var batch by remember{mutableStateOf<List<InstalledApk>>(emptyList())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    DisposableEffect(context){
        val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){InstalledCatalog.snapshot=null;revision++}}
        val intent=IntentFilter().apply{addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REMOVED);addAction(Intent.ACTION_PACKAGE_REPLACED);addDataScheme("package")}
        ContextCompat.registerReceiver(context,receiver,intent,ContextCompat.RECEIVER_EXPORTED)
        onDispose{context.unregisterReceiver(receiver)}
    }
    LaunchedEffect(revision){
        try{if(!loaded)InstalledCatalog.restore(context)?.let{rows=it;loaded=true};rows=InstalledCatalog.read(context);chosen=chosen.intersect(rows.map{it.id}.toSet());loaded=true}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not read installed apps"}
    }
    fun runTask(action:suspend ()->Unit){scope.launch{busy=true;try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Operation failed"}finally{busy=false}}}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val app=pending;pending=null
        if(uri!=null&&app!=null)runTask{withContext(Dispatchers.IO){checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).use{writeInstalled(context,app,it)}}}
    }
    val batchExporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        val list=batch;batch=emptyList()
        if(uri!=null&&list.isNotEmpty())runTask{withContext(Dispatchers.IO){val dir=checkNotNull(androidx.documentfile.provider.DocumentFile.fromTreeUri(context,uri));for(app in list){coroutineContext.ensureActive();val dest=checkNotNull(dir.createFile(if(app.files.size==1)"application/vnd.android.package-archive" else "application/octet-stream",app.fileName));try{checkNotNull(context.contentResolver.openOutputStream(dest.uri,"wt")).use{writeInstalled(context,app,it)}}catch(e:Throwable){dest.delete();throw e}}}}
    }
    fun share(apps:List<InstalledApk>){runTask{
        val uris=withContext(Dispatchers.IO){
            val root=File(context.cacheDir,"installed-apps").apply{mkdirs()}
            root.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86400000L}?.forEach{it.deleteRecursively()}
            val dir=java.nio.file.Files.createTempDirectory(root.toPath(),"export-").toFile()
            try{ArrayList(apps.map{app->val file=File(dir,app.fileName);file.outputStream().use{writeInstalled(context,app,it)};FileProvider.getUriForFile(context,context.packageName+".files",file)})}catch(e:Throwable){dir.deleteRecursively();throw e}
        }
        val intent=Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply{
            type=if(apps.all{it.files.size==1})"application/vnd.android.package-archive" else "application/octet-stream"
            if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris.first())else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData=ClipData.newRawUri("APK",uris.first()).apply{uris.drop(1).forEach{addItem(ClipData.Item(it))}}
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,"Share"))
    }}
    BackHandler{if(!busy){if(chosen.isNotEmpty())chosen=emptySet()else onBack()}}
    val visible=remember(rows,filter,query){rows.filter{(filter==0||filter==1&&!it.system||filter==2&&it.system)&&(it.label.contains(query,true)||it.id.contains(query,true))}}
    fun tap(app:InstalledApk){if(chosen.isEmpty())active=app else chosen=if(app.id in chosen)chosen-app.id else chosen+app.id}
    Column(Modifier.fillMaxSize()){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(chosen.isNotEmpty())chosen=emptySet()else onBack()},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Text(if(chosen.isEmpty())tr("Installed apps") else chosen.size.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={grid=!grid}){Icon(Icons.Default.List,tr("View"))}
            IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Default.Refresh,tr("Refresh"))}
        }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=12.dp),singleLine=true,placeholder={Text(tr("Search"))})
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("All","User","System").forEachIndexed{i,label->FilterChip(selected=filter==i,onClick={filter=i},label={Text(tr(label))})}
        }
        if(chosen.isNotEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
            TextButton(onClick={chosen=visible.map{it.id}.toSet()},enabled=!busy){Text(tr("Select all"))}
            TextButton(onClick={share(rows.filter{it.id in chosen})},enabled=!busy){Text(tr("Share"))}
            TextButton(onClick={batch=rows.filter{it.id in chosen};batchExporter.launch(null)},enabled=!busy){Text(tr("Export"))}
        }
        DelayedBrowserProgress(!loaded||busy)
        Text(visible.size.toString()+" "+tr("Apps"),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
        if(grid)LazyVerticalGrid(GridCells.Adaptive(92.dp),contentPadding=PaddingValues(8.dp)){
            items(visible,key={it.id}){app->
                Surface(color=if(app.id in chosen)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,modifier=Modifier.padding(2.dp).combinedClickable(enabled=!busy,onClick={tap(app)},onLongClick={chosen=chosen+app.id})){
                    Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){AppIcon(app.id);Text(app.label,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall);Text(if(app.files.size>1)".apks" else ".apk",style=MaterialTheme.typography.labelSmall)}
                }
            }
        }else LazyColumn{items(visible,key={it.id}){app->ListItem(headlineContent={Text(app.label)},supportingContent={Text(app.version+" · "+app.id,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingContent={AppIcon(app.id)},colors=ListItemDefaults.colors(containerColor=if(app.id in chosen)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),modifier=Modifier.combinedClickable(enabled=!busy,onClick={tap(app)},onLongClick={chosen=chosen+app.id}))}}
    }
    active?.let{app->AlertDialog(onDismissRequest={active=null},title={Text(app.label)},text={Column{
        Text(app.id+" · "+app.version)
        Text(app.files.size.toString()+" APK")
        TextButton(onClick={active=null;share(listOf(app))}){Text(tr("Share"))}
        TextButton(onClick={active=null;pending=app;exporter.launch(app.fileName)}){Text(tr("Export APK"))}
        TextButton(onClick={active=null;context.packageManager.getLaunchIntentForPackage(app.id)?.let{context.startActivity(it)}}){Text(tr("Open"))}
        TextButton(onClick={active=null;context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.id)))}){Text(tr("App info"))}
        if(!app.system)TextButton(onClick={active=null;context.startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.id)))}){Text(tr("Uninstall"))}
    }},confirmButton={TextButton(onClick={active=null}){Text(tr("Close"))}})}
    error?.let{msg->AlertDialog(onDismissRequest={error=null},title={Text(tr("Error"))},text={Text(msg)},confirmButton={TextButton(onClick={error=null}){Text(tr("Close"))}})}
}
