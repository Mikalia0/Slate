// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.media.MediaScannerConnection
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slate.android.storage.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.*

internal data class CatalogFile(val root:File,val entry:Entry)
internal data class FileCatalog(val files:List<CatalogFile>,val incomplete:Boolean)
internal suspend fun buildFileCatalog(volumes:List<Volume>,index:IndexedScan?):FileCatalog{
    val ctx=currentCoroutineContext()
    val output=ArrayList<CatalogFile>()
    var incomplete=false
    fun allowed(root:File,file:File)=!protectedAppPath(file.toPath()) &&
        !(file.parentFile==File(root,"Android") && file.name in setOf("data","obb"))
    for(volume in volumes.distinctBy{it.root.path}){
        ctx.ensureActive()
        val root=volume.root
        val indexed=index?.takeIf{it.root.directory==root && !it.root.rootUnreadable}
        if(indexed!=null){
            val pending=ArrayDeque<Iterator<Entry>>()
            pending.add(indexed.root.entries.iterator())
            val start=output.size
            var complete=true
            while(pending.isNotEmpty()){
                ctx.ensureActive()
                val iter=pending.last()
                if(!iter.hasNext()){pending.removeLast();continue}
                val e=iter.next()
                if(!allowed(root,e.file))continue
                if(e.skipped>0L){complete=false;break}
                if(e.directory){
                    val children=indexed.folders[e.file]
                    if(children==null && e.files>0L){complete=false;break}
                    if(children!=null)pending.add(children.entries.iterator())
                }else if(e.files==1L)output.add(CatalogFile(root,e))
            }
            if(complete)continue
            output.subList(start,output.size).clear()
        }
        val pending=ArrayDeque<File>()
        pending.add(root)
        while(pending.isNotEmpty()){
            ctx.ensureActive()
            val folder=pending.removeLast()
            val children=runCatching{folder.listFiles()}.getOrNull()
            if(children==null){incomplete=true;continue}
            for(file in children){
                ctx.ensureActive()
                if(!allowed(root,file))continue
                val attrs=runCatching{Files.readAttributes(file.toPath(),BasicFileAttributes::class.java,NOFOLLOW_LINKS)}.getOrNull()
                if(attrs==null){incomplete=true;continue}
                if(attrs.isDirectory)pending.add(file)
                else if(attrs.isRegularFile)output.add(CatalogFile(root,Entry(file,false,attrs.size(),1,0,attrs.lastModifiedTime().toMillis())))
            }
        }
    }
    return FileCatalog(output,incomplete)
}

@Composable
internal fun categoryTitle(category:FileCategory)=if(category==FileCategory.INSTALLERS)stringResource(R.string.files_apks)else tr(category.label)
internal fun categoryIcon(category:FileCategory)=when(category){
    FileCategory.INSTALLERS->R.drawable.ic_file_apk
    FileCategory.IMAGES->R.drawable.ic_device_camera
    FileCategory.VIDEOS->R.drawable.ic_file_video
    FileCategory.AUDIO->R.drawable.ic_file_audio
    FileCategory.ARCHIVES->R.drawable.ic_file_archive
    else->R.drawable.ic_storage_file
}

@Composable
internal fun FilesOverview(granted:Boolean,volumes:List<Volume>,catalog:FileCatalog?,request:()->Unit,onRefresh:()->Unit,onOpenFolder:(File,File)->Unit,onChanged:()->Unit,initialPage:String="",onMenu:()->Unit={},bookmarks:List<String> = emptyList(),onOpenBookmark:(String)->Unit={},sources:List<String> = emptyList(),onSource:(String)->Unit={},onAddSource:()->Unit={}){
    val context=LocalContext.current
    val compact=useTopNavigation()
    val scope=rememberCoroutineScope()
    var page by rememberSaveable{mutableStateOf(initialPage)}
    if(page=="installed_apps"){InstalledAppsBrowser(onBack={page=""});return}
    var query by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<CatalogFile?>(null)}
    var delete by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var viewer by remember{mutableStateOf<CatalogFile?>(null)}
    var apkChoice by remember{mutableStateOf<CatalogFile?>(null)}
    var viewerTab by remember{mutableIntStateOf(0)}
    fun open(item:CatalogFile){if(canViewSlateFile(item.root,item.entry.file)){if(item.entry.file.extension.equals("apk",true))apkChoice=item else{viewerTab=0;viewer=item}}else if(!openSharedFile(context,item.root,item.entry.file,false))failed=true}
    apkChoice?.let{item->SlateApkActions(item.root,item.entry.file,onClose={apkChoice=null},onInspect={viewerTab=it;apkChoice=null;viewer=item})}
    val grouped by produceState<Map<FileCategory,List<CatalogFile>>>(emptyMap(),catalog){
        value=withContext(Dispatchers.Default){catalog?.files.orEmpty().groupBy{fileCategory(it.entry.file.extension)}}
    }
    val recent by produceState<List<CatalogFile>>(emptyList(),catalog){
        value=withContext(Dispatchers.Default){catalog?.files.orEmpty().sortedByDescending{it.entry.modified}.take(8)}
    }
    val chosen=FileCategory.entries.firstOrNull{it.name==page}
    val visible by produceState<List<CatalogFile>>(emptyList(),catalog,grouped,page,query,sort){
        value=withContext(Dispatchers.Default){
            val source=if(chosen!=null)grouped[chosen].orEmpty()else catalog?.files.orEmpty()
            val filtered=source.filter{it.entry.file.name.contains(query,true)||it.entry.file.parent.orEmpty().contains(query,true)}
            when(sort){1->filtered.sortedByDescending{it.entry.bytes};2->filtered.sortedBy{it.entry.file.name.lowercase()};else->filtered.sortedByDescending{it.entry.modified}}
        }
    }
    val homeScroll=rememberLazyListState()
    val storageScroll=rememberLazyListState()
    val categoryScroll=rememberLazyListState()
    val resultScroll=key(page){rememberLazyListState()}
    viewer?.let{item->val before=remember(item){Triple(item.entry.file.length(),item.entry.file.lastModified(),item.entry.file.parentFile?.lastModified())};SlateFileViewer(item.root,item.entry.file,onClose={viewer=null;scope.launch{val f=item.entry.file;if(withContext(Dispatchers.IO){before!=Triple(f.length(),f.lastModified(),f.parentFile?.lastModified())})onChanged()}},onExternal={openSharedFile(context,item.root,item.entry.file,false)},initialApkTab=viewerTab);return}
    fun leave(){page="";query="";selected=null;failed=false}
    BackHandler(page.isNotEmpty()||busy){if(!busy)leave()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            if(page.isNotEmpty())IconButton(onClick={leave()},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            else IconButton(onClick=onMenu,enabled=!busy){Icon(Icons.Default.Menu,stringResource(R.string.file_volumes))}
            Text(if(chosen!=null)categoryTitle(chosen)else if(page=="search")stringResource(R.string.file_search)else if(page=="recent")stringResource(R.string.files_recent)else if(page=="all")stringResource(R.string.browser_all_files)else stringResource(R.string.browser_home),Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(page.isEmpty())IconButton(onClick={page="installed_apps"}){Icon(painterResource(R.drawable.ic_file_apk),tr("Installed apps"))}
            if(granted&&page.isEmpty())IconButton(onClick={page="search"}){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
            IconButton(onClick=onRefresh,enabled=granted&&!busy){Icon(Icons.Default.Refresh,tr("Refresh"))}
        }
        if(!granted){
            Text(tr("Storage access"),Modifier.padding(16.dp))
            Button(onClick=request,modifier=Modifier.padding(horizontal=16.dp)){Text(tr("Grant access"))}
            if(shizukuGranted())volumes.firstOrNull()?.let{volume->
                listOf("Android/data","Android/obb").forEach{relative->TextButton(onClick={onOpenFolder(volume.root,File(volume.root,relative))}){Text(relative)}}
            }
            Text(stringResource(R.string.browser_connections),Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)){items(sources,key={"source:"+it}){source->StorageConnectionRow(source){onSource(source)}};item{TextButton(onClick=onAddSource){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.browser_add_storage))}}}
            return@Column
        }
        DelayedBrowserProgress(catalog==null||busy)
        if(failed)Text(stringResource(R.string.file_failed),Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)
        if(page.isEmpty()&&compact){
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                LazyColumn(Modifier.weight(1f),state=storageScroll,verticalArrangement=Arrangement.spacedBy(2.dp)){
                    item{Text(tr("Storage"),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.titleSmall)}
                    items(volumes,key={it.root.path}){volume->
                        val space by produceState<Pair<Long,Long>?>(null,volume.root){value=withContext(Dispatchers.IO){runCatching{val stat=android.os.StatFs(volume.root.path);(stat.totalBytes-stat.availableBytes).coerceAtLeast(0L) to stat.totalBytes}.getOrNull()}}
                        Row(Modifier.fillMaxWidth().clickable{onOpenFolder(volume.root,volume.root)}.heightIn(min=44.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Icon(painterResource(R.drawable.ic_device_storage),null,Modifier.size(22.dp));Column(Modifier.weight(1f)){Text(tr(volume.label),style=MaterialTheme.typography.bodyMedium);space?.let{Text(Formatter.formatFileSize(context,it.first)+" / "+Formatter.formatFileSize(context,it.second),style=MaterialTheme.typography.bodySmall)}};Icon(Icons.Default.KeyboardArrowRight,null)
                        }
                    }
                    item{Text(stringResource(R.string.browser_connections),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.titleSmall)}
                    items(sources,key={"source:"+it}){source->StorageConnectionRow(source){onSource(source)}}
                    item{TextButton(onClick=onAddSource){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.browser_add_storage))}}
                    if(bookmarks.isNotEmpty()){item{Text(stringResource(R.string.browser_favorites),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.titleSmall)};items(bookmarks){key->Text(File(key.substringAfter('\n')).name,Modifier.fillMaxWidth().clickable{onOpenBookmark(key)}.padding(vertical=8.dp))}}
                }
                LazyColumn(Modifier.weight(1.5f),state=categoryScroll,verticalArrangement=Arrangement.spacedBy(3.dp)){
                    item{Text(stringResource(R.string.files_categories),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.titleSmall)}
                    items(listOf(FileCategory.INSTALLERS,FileCategory.DOCUMENTS,FileCategory.IMAGES,FileCategory.VIDEOS,FileCategory.AUDIO,FileCategory.ARCHIVES,FileCategory.OTHER).chunked(2)){pair->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        pair.forEach{category->Surface(Modifier.weight(1f).clickable{page=category.name;query=""},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Icon(painterResource(categoryIcon(category)),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f)){Text(categoryTitle(category),style=MaterialTheme.typography.labelLarge,maxLines=2);Text(if(catalog==null)"—"else stringResource(R.string.file_items,grouped[category].orEmpty().size),style=MaterialTheme.typography.labelSmall)}}}}
                        if(pair.size==1)Spacer(Modifier.weight(1f))
                    }}
                    item{TextButton(onClick={page="recent";sort=0}){Text(stringResource(R.string.files_recent))}}
                    items(recent,key={it.entry.file.path}){file->CatalogRow(file,{open(file)},{selected=file})}
                }
            }
        }else if(page.isEmpty()){
            LazyColumn(Modifier.weight(1f),state=homeScroll,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item{Text(tr("Storage"),style=MaterialTheme.typography.titleMedium)}
                items(volumes,key={it.root.path}){volume->
                    val space by produceState<Pair<Long,Long>?>(null,volume.root){value=withContext(Dispatchers.IO){runCatching{val stat=android.os.StatFs(volume.root.path);(stat.totalBytes-stat.availableBytes).coerceAtLeast(0L) to stat.totalBytes}.getOrNull()}}
                    ListItem(headlineContent={Text(tr(volume.label))},leadingContent={Icon(painterResource(R.drawable.ic_device_storage),null)},supportingContent={
                        space?.let{(used,total)->Text(Formatter.formatFileSize(context,used)+" / "+Formatter.formatFileSize(context,total))}
                    },trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},modifier=Modifier.clickable{onOpenFolder(volume.root,volume.root)})
                }
                item{Text(stringResource(R.string.browser_connections),style=MaterialTheme.typography.titleMedium)}
                items(sources,key={"source:"+it}){source->StorageConnectionRow(source){onSource(source)}}
                item{TextButton(onClick=onAddSource){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.browser_add_storage))}}
                item{Text(stringResource(R.string.files_categories),style=MaterialTheme.typography.titleMedium)}
                val categories=listOf(FileCategory.INSTALLERS,FileCategory.DOCUMENTS,FileCategory.IMAGES,FileCategory.VIDEOS,FileCategory.AUDIO,FileCategory.ARCHIVES,FileCategory.OTHER)
                items(categories.chunked(2)){pair->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        for(category in pair){
                            val files=grouped[category].orEmpty()
                            val size=remember(files){files.sumOf{it.entry.bytes}}
                            Surface(Modifier.weight(1f).clickable{page=category.name;query=""},shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow){
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Icon(painterResource(categoryIcon(category)),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.weight(1f))
                                        Text(if(catalog==null)"—" else Formatter.formatFileSize(context,size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(categoryTitle(category),style=MaterialTheme.typography.titleSmall)
                                    Text(if(catalog==null)"—" else stringResource(R.string.file_items,files.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if(pair.size==1)Spacer(Modifier.weight(1f))
                    }
                }
                if(catalog?.incomplete==true)item{Text(stringResource(R.string.files_partial),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(bookmarks.isNotEmpty()){
                    item{Text(stringResource(R.string.browser_favorites),style=MaterialTheme.typography.titleMedium)}
                    items(bookmarks){key->ListItem(headlineContent={Text(File(key.substringAfter('\n')).name)},leadingContent={Icon(painterResource(R.drawable.ic_storage_folder),null)},modifier=Modifier.clickable{onOpenBookmark(key)})}
                }
                item{
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(stringResource(R.string.files_recent),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        if(recent.isNotEmpty())TextButton(onClick={page="recent";sort=0}){Text(stringResource(R.string.files_view_all))}
                    }
                }
                items(recent,key={it.entry.file.path}){file->CatalogRow(file,{open(file)},{selected=file})}
            }
        }else{
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text(stringResource(R.string.file_search))})
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(R.string.file_items,visible.size),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={sort=(sort+1)%3}){Text(stringResource(listOf(R.string.file_sort_date,R.string.file_sort_size,R.string.file_sort_name)[sort]))}
            }
            if(visible.isEmpty()&&catalog!=null)Text(stringResource(R.string.file_empty),Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f),state=resultScroll){
                items(visible,key={it.entry.file.path}){file->
                    CatalogRow(file,{open(file)},{selected=file})
                }
            }
        }
    }
    selected?.let{file->
        val entry=file.entry
        if(delete)AlertDialog(onDismissRequest={if(!busy)delete=false},title={Text(tr("Delete"))},text={Text(stringResource(R.string.file_recursive_delete,1)+"\n\n"+entry.file.name)},dismissButton={TextButton(onClick={delete=false}){Text(tr("Cancel"))}},confirmButton={
            TextButton(enabled=!busy,onClick={
                busy=true;delete=false;selected=null
                scope.launch{
                    try{withContext(Dispatchers.IO){withFileOperations(context,false){ops->check(safeFile(file.root,entry.file)&&entry.file.isFile&&entry.file.length()==entry.bytes&&entry.file.lastModified()==entry.modified);ops.delete(file.root,entry.file,false)};MediaScannerConnection.scanFile(context,arrayOf(entry.file.path),null,null)};onChanged()}
                    catch(e:CancellationException){throw e}catch(_:Exception){failed=true}finally{busy=false}
                }
            }){Text(tr("Delete"))}
        })
        else{
            val info by produceState<ApkDetails?>(null,entry.file.path,entry.modified){if(entry.file.extension.equals("apk",true))value=readApkDetails(context,entry.file)}
            AlertDialog(onDismissRequest={selected=null},title={Text(info?.label?:entry.file.name,maxLines=2,overflow=TextOverflow.Ellipsis)},text={
                Column{
                    Text(entry.file.path,style=MaterialTheme.typography.bodySmall)
                    Text(Formatter.formatFileSize(context,entry.bytes))
                    info?.let{apk->
                        Text(apk.packageName,style=MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.files_version,apk.version,apk.code))
                        Text(stringResource(when{apk.installedCode==null->R.string.files_not_installed;apk.code>apk.installedCode->R.string.files_newer;apk.code<apk.installedCode->R.string.files_older;else->R.string.files_installed}))
                    }
                    TextButton(onClick={if(!openSharedFile(context,file.root,entry.file,false))failed=true;selected=null}){Text(stringResource(if(entry.file.extension.equals("apk",true))R.string.files_install else R.string.files_open))}
                    TextButton(onClick={entry.file.parentFile?.let{onOpenFolder(file.root,it)};selected=null}){Text(stringResource(R.string.files_location))}
                    TextButton(onClick={if(!openSharedFile(context,file.root,entry.file,true))failed=true;selected=null}){Text(tr("Share"))}
                    TextButton(onClick={delete=true}){Text(tr("Delete"),color=MaterialTheme.colorScheme.error)}
                }
            },confirmButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})
        }
    }
}
@Composable
private fun CatalogRow(file:CatalogFile,onOpen:()->Unit,onMenu:()->Unit){
    val context=LocalContext.current
    val e=file.entry
    val apk by produceState<ApkDetails?>(null,e.file.path,e.modified){if(e.file.extension.equals("apk",true))value=readApkDetails(context,e.file)}
    Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).heightIn(min=64.dp).padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        FilePreview(e,Modifier.size(38.dp),MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)){
            Text(apk?.label?:e.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(listOfNotNull(apk?.version?.takeIf{it.isNotBlank()},e.file.parentFile?.name,Formatter.formatFileSize(context,e.bytes)).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=onMenu){Icon(Icons.Default.MoreVert,null)}
    }
}
