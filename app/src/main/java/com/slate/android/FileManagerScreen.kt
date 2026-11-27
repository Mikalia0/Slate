// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.format.Formatter
import android.text.format.DateUtils
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Calendar
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.slate.android.storage.Entry
import com.slate.android.storage.Volume
import com.slate.android.storage.protectedRelative
import com.slate.android.storage.protectedAppPath
import com.slate.android.storage.shizukuGranted
import com.slate.android.storage.withProtectedRead
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Files
internal fun safeFile(root:File,file:File):Boolean=runCatching{
    file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !Files.isSymbolicLink(file.toPath())
}.getOrDefault(false)
internal fun validFileName(name:String)=name.isNotBlank()&&name!="."&&name!=".."&&name.none{it=='/'||it=='\\'||it.code<32}
private data class FileRow(val file:File,val directory:Boolean,val size:Long,val modified:Long)
private val folderSummarySlots=kotlinx.coroutines.sync.Semaphore(2)
private val folderSummaries=android.util.LruCache<String,FolderSummary>(512)
private val folderListings=object:android.util.LruCache<String,List<FileRow>>(12000){override fun sizeOf(key:String,value:List<FileRow>)=value.size.coerceAtLeast(1)}
private data class FolderSummary(val count:String,val preview:File?)
private fun folderSummary(root:File,folder:File,hidden:Boolean):FolderSummary?=runCatching{
    if(!safeFile(root,folder)||protectedAppPath(folder.toPath()))return@runCatching null
    val cacheKey=root.path+"|"+folder.path+"|"+folder.lastModified()+"|"+hidden
    folderSummaries.get(cacheKey)?.let{return@runCatching it}
    var count=0
    var scanned=0
    var preview:File?=null
    Files.newDirectoryStream(folder.toPath()).use{stream->
        for(child in stream){
            if(Thread.currentThread().isInterrupted||++scanned>1500)break
            if(!hidden&&child.fileName.toString().startsWith("."))continue
            count++
            if(count<=32&&preview==null){
                val file=child.toFile()
                if(file.extension.lowercase() in setOf("jpg","jpeg","png","webp","mp4","mkv","apk")&&
                    !protectedAppPath(child)&&safeFile(root,file)&&file.isFile)preview=file
            }
            if(count>1000)break
        }
    }
    FolderSummary(if(count>1000)"1000+" else if(scanned>1500)"$count+" else count.toString(),preview).also{folderSummaries.put(cacheKey,it)}
}.getOrNull()

private fun fileDay(time:Long):Long=Calendar.getInstance().apply{
    timeInMillis=time
    set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
}.timeInMillis

@Composable
private fun BrowserFileIcon(row:FileRow,preview:File?,modifier:Modifier=Modifier.size(40.dp)){
    val color=MaterialTheme.colorScheme
    val ext=row.file.extension.lowercase()
    Box(modifier,contentAlignment=Alignment.Center){
        BrowserEntryIcon(row.directory,ext,Modifier.fillMaxSize()){
            FilePreview(Entry(row.file,row.directory,row.size,0,0,row.modified),Modifier.size(if(row.directory)25.dp else 40.dp),if(row.directory)color.onPrimaryContainer else color.onSecondaryContainer)
        }
        if(preview!=null)Box(Modifier.align(Alignment.BottomEnd).size(18.dp).background(color.surface,RoundedCornerShape(4.dp))){
            FilePreview(Entry(preview,false,0,0,0,row.modified),Modifier.fillMaxSize(),color.primary)
        }
    }
}
private data class ClipboardFile(val root:File,val files:List<FileRow>,val move:Boolean)
private fun tabKey(root:File,folder:File)=root.path+"\n"+folder.path
private fun tabFiles(key:String)=File(key.substringBefore('\n')) to File(key.substringAfter('\n'))
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileManagerScreen(granted:Boolean,volumes:List<Volume>,request:()->Unit,onChanged:()->Unit,model:com.slate.android.storage.StorageModel,onBrowsingChanged:(Boolean)->Unit={},onSettings:()->Unit={},onDashboard:()->Unit={},onDestination:(Int)->Unit={},onNavigationBlocked:(Boolean)->Unit={},initialFolder:String?=null,onInitialFolderConsumed:()->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val primary=Environment.getExternalStorageDirectory()
    val prefs=remember{context.getSharedPreferences("file_manager",0)}
    var tabs by rememberSaveable{mutableStateOf(runCatching{val saved=org.json.JSONArray(prefs.getString("tabs","[]"));ArrayList((0 until saved.length()).map{saved.getString(it)}.filter{it.contains('\n')}.take(12)).ifEmpty{arrayListOf(tabKey(primary,primary))}}.getOrElse{arrayListOf(tabKey(primary,primary))})}
    var tab by rememberSaveable{mutableIntStateOf(prefs.getInt("active_tab",0).coerceIn(tabs.indices))}
    LaunchedEffect(tabs,tab){prefs.edit().putString("tabs",org.json.JSONArray(tabs).toString()).putInt("active_tab",tab).apply()}
    val (root,folder)=tabFiles(tabs[tab.coerceIn(tabs.indices)])
    val rootPath=root.path
    val path=folder.path
    val listState=key(rootPath,path,tab){rememberLazyListState()}
    val gridState=key(rootPath,path,tab){rememberLazyGridState()}
    val overviewState=rememberSaveableStateHolder()
    val protected=protectedRelative(folder,primary)!=null
    var shizuku by remember{mutableStateOf(shizukuGranted())}
    LaunchedEffect(Unit){while(true){shizuku=shizukuGranted();delay(1000)}}
    var bookmarks by remember{mutableStateOf(prefs.getStringSet("bookmarks",emptySet()).orEmpty().toSet())}
    var grid by rememberSaveable{mutableStateOf(prefs.getBoolean("grid",false))}
    var searching by rememberSaveable{mutableStateOf(false)}
    var sorting by remember{mutableStateOf(false)}
    var descending by rememberSaveable{mutableStateOf(false)}
    var marked by remember{mutableStateOf<Set<String>>(emptySet())}
    var revision by remember{mutableIntStateOf(0)}
    var catalogRevision by remember{mutableIntStateOf(0)}
    var query by rememberSaveable{mutableStateOf("")}
    var hidden by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableIntStateOf(0)}
    var menu by remember{mutableStateOf(false)}
    val useTopNavigationValue=useTopNavigation()
    val drawer=key(useTopNavigationValue){rememberDrawerState(DrawerValue.Closed)}
    var landscapeDrawer by remember(useTopNavigationValue){mutableStateOf(false)}
    var overviewPage by rememberSaveable{mutableStateOf("")}
    var overviewEpoch by remember{mutableIntStateOf(0)}
    var history by remember{mutableStateOf(readBrowserHistory(prefs))}
    var sources by remember{mutableStateOf(prefs.getStringSet("saf_sources",emptySet()).orEmpty().toSet())}
    var safSource by rememberSaveable{mutableStateOf<String?>(null)}
    var addSourceDialog by remember{mutableStateOf(false)}
    var editFavorites by remember{mutableStateOf(false)}
    var dualPane by rememberSaveable{mutableStateOf(prefs.getBoolean("dual_pane",false))}
    var otherPath by rememberSaveable{mutableStateOf(primary.path)}
    val addStorage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null)runCatching{
            context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            sources=sources+uri.toString();prefs.edit().putStringSet("saf_sources",sources).apply();safSource=uri.toString()
        }.onFailure{
            runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);sources=sources+uri.toString();prefs.edit().putStringSet("saf_sources",sources).apply();safSource=uri.toString()}
        }
    }
    var selected by remember{mutableStateOf<FileRow?>(null)}
    var viewer by remember{mutableStateOf<File?>(null)}
    var properties by remember{mutableStateOf<FileRow?>(null)}
    var handler by remember{mutableStateOf<File?>(null)}
    var toolsScreen by remember{mutableStateOf(false)}
    var viewerTab by remember{mutableIntStateOf(0)}
    var actionMenu by remember{mutableStateOf(false)}
    val pathClipboard=LocalClipboardManager.current
    var clipboard by remember{mutableStateOf<ClipboardFile?>(null)}
    var operation by remember{mutableStateOf<String?>(null)}
    var archiveSelection by remember{mutableStateOf<List<File>>(emptyList())}
    var name by remember{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    val navigationCallback by rememberUpdatedState(onNavigationBlocked)
    SideEffect{navigationCallback(busy)}
    DisposableEffect(Unit){onDispose{navigationCallback(false)}}
    var transferJob by remember{mutableStateOf<Job?>(null)}
    var transferBytes by remember{mutableLongStateOf(0L)}
    var transferTotal by remember{mutableLongStateOf(0L)}
    var transferSpeed by remember{mutableLongStateOf(0L)}
    var transferring by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var rows by remember{mutableStateOf<List<FileRow>>(emptyList())}
    var showHome by rememberSaveable{mutableStateOf(true)}
    val browsingCallback by rememberUpdatedState(onBrowsingChanged)
    LaunchedEffect(showHome){browsingCallback(!showHome)}
    DisposableEffect(Unit){onDispose{browsingCallback(false)}}
    val availableVolumes=volumes.ifEmpty{listOf(Volume(primary,"Internal storage"))}
    val catalog by produceState<FileCatalog?>(if(granted)model.cachedFileCatalog(availableVolumes)else null,granted,availableVolumes,catalogRevision,showHome,overviewPage){
        if(!granted)value=null
        else if(showHome&&overviewPage!="installed_apps"){
            value=model.cachedFileCatalog(availableVolumes)?:model.restoreFileCatalog(availableVolumes)
            if(value!=null)delay(1200)
            value=model.fileCatalog(availableVolumes)
        }
    }
    fun go(file:File,volume:File=root){
        val key=tabKey(volume,file)
        tabs=ArrayList(tabs).apply{set(tab,key)}
        history=(listOf(key)+history.filter{it!=key}).take(30)
        prefs.edit().putString("history",org.json.JSONArray(history).toString()).apply()
        query="";searching=false;selected=null;marked=emptySet();message=null
    }
    LaunchedEffect(initialFolder){initialFolder?.let{key->val (volume,dir)=tabFiles(key);if(safeFile(volume,dir)&&dir.isDirectory){go(dir,volume);showHome=false};onInitialFolderConsumed()}}
    fun home(page:String=""){overviewState.removeState(overviewEpoch);overviewPage=page;overviewEpoch++;showHome=true;query="";searching=false;marked=emptySet()}
    fun closeDrawer(){if(useTopNavigationValue)landscapeDrawer=false else scope.launch{drawer.close()}}
    fun openDrawer(){if(useTopNavigationValue)landscapeDrawer=true else scope.launch{drawer.open()}}
    val sortKey="sort:"+tabKey(root,folder)
    LaunchedEffect(sortKey){val saved=prefs.getInt(sortKey,0);sort=saved and 3;descending=saved and 4!=0}
    fun persistSort(){prefs.edit().putInt(sortKey,sort or (if(descending)4 else 0)).apply()}
    
    fun saveBookmark(){
        val key=tabKey(root,folder)
        bookmarks=if(key in bookmarks)bookmarks-key else bookmarks+key
        prefs.edit().putStringSet("bookmarks",bookmarks).apply()
    }
    fun writable(file:File)=file!=root&&!(file.name=="Android"&&file.parentFile==root)&&(!protectedAppPath(file.toPath())|| (shizuku&&protectedRelative(file,primary)?.let{com.slate.android.storage.validWriteRelative(it)}==true))
    fun runOperation(block:suspend ()->Unit){
        busy=true;message=null
        scope.launch{
            try{withContext(Dispatchers.IO){block()};selected=null;marked=emptySet()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){message=context.getString(R.string.file_failed)}
            finally{busy=false;revision++;catalogRevision++;onChanged()}
        }
    }
    fun pasteInto(destinationRoot:File,destination:File){
        val clip=clipboard?:return
        if(busy)return
        busy=true;transferring=true;message=null;transferBytes=0;transferSpeed=0
        transferTotal=if(clip.files.any{it.directory})0L else clip.files.sumOf{it.size}
        transferJob=scope.launch{
            try{
                withContext(Dispatchers.IO){
                    val elevated=protectedRelative(destination,primary)!=null||clip.files.any{protectedRelative(it.file,primary)!=null}
                    var done=0L
                    val started=android.os.SystemClock.elapsedRealtime()
                    var last=started
                    withFileOperations(context,elevated){ops->
                        for(row in clip.files){
                            val target=File(destination,row.file.name)
                            ops.copy(clip.root,row.file,destinationRoot,target,row.directory,clip.move,onBytes={n->
                                done+=n
                                val now=android.os.SystemClock.elapsedRealtime()
                                if(now-last>=250){last=now;withContext(Dispatchers.Main){transferBytes=done;transferSpeed=done*1000/(now-started).coerceAtLeast(1)}}
                            })
                            MediaScannerConnection.scanFile(context,arrayOf(target.path,row.file.path),null,null)
                        }
                    }
                }
                clipboard=null;marked=emptySet()
            }catch(e:CancellationException){clipboard=null;throw e}
            catch(_:Exception){message=context.getString(R.string.file_failed);clipboard=null}
            finally{busy=false;transferring=false;transferJob=null;revision++;catalogRevision++;onChanged()}
        }
    }
    val folderKey="$rootPath|$path|$hidden"
    // Never retain privileged listings after a permission change.
    val cacheable=granted&&!protected
    var loadedKey by remember{mutableStateOf("")}
    var observedChange by remember{mutableLongStateOf(0L)}
    LaunchedEffect(granted,shizuku){if(!granted){folderListings.evictAll();folderSummaries.evictAll()}}
    DisposableEffect(path,cacheable,showHome,safSource){
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val changed=Runnable{observedChange++;model.invalidateFileCatalog()}
        val observer=if(cacheable&&!showHome&&safSource==null)runCatching{
            @Suppress("DEPRECATION")
            object:android.os.FileObserver(path,android.os.FileObserver.CREATE or android.os.FileObserver.DELETE or android.os.FileObserver.MOVED_FROM or android.os.FileObserver.MOVED_TO or android.os.FileObserver.CLOSE_WRITE or android.os.FileObserver.ATTRIB or android.os.FileObserver.DELETE_SELF or android.os.FileObserver.MOVE_SELF){
                override fun onEvent(event:Int,name:String?){handler.removeCallbacks(changed);handler.postDelayed(changed,180)}
            }.apply{startWatching()}
        }.getOrNull()else null
        onDispose{observer?.stopWatching();handler.removeCallbacks(changed)}
    }
    LaunchedEffect(folderKey,revision,observedChange,granted,shizuku,showHome,safSource){
        if(showHome||safSource!=null)return@LaunchedEffect
        message=null
        if(loadedKey!=folderKey){rows=if(cacheable)folderListings.get(folderKey).orEmpty()else emptyList();loadedKey=folderKey}
        if((!granted&&!shizuku)||(protected&&!shizuku)){rows=emptyList();loading=false;return@LaunchedEffect}
        loading=true
        try{
            val relative=protectedRelative(folder,primary)
            val result=if(relative!=null&&shizuku)withProtectedRead(context){client->
                client.list(relative).filter{it.readable&&(hidden||!it.name.startsWith(".") )}.map{FileRow(File(folder,it.name),it.directory,it.size,it.modified)}
            }else withContext(Dispatchers.IO){
                check(safeFile(root,folder))
                val list=folder.listFiles()?:error("unreadable")
                list.asSequence().filter{hidden||!it.name.startsWith(".")}.filter{safeFile(root,it)}.mapNotNull{file->runCatching{val attr=Files.readAttributes(file.toPath(),java.nio.file.attribute.BasicFileAttributes::class.java,java.nio.file.LinkOption.NOFOLLOW_LINKS);FileRow(file,attr.isDirectory,if(attr.isRegularFile)attr.size()else 0L,attr.lastModifiedTime().toMillis())}.getOrNull()}.toList()
            }
            rows=result
            if(cacheable)folderListings.put(folderKey,result)
        }catch(e:CancellationException){throw e}
        catch(_:Exception){message=context.getString(R.string.file_failed);if(!cacheable)rows=emptyList()}
        finally{loading=false}
    }
    BackHandler(!showHome){if(!busy){when{marked.isNotEmpty()->marked=emptySet();selected!=null->selected=null;searching->{searching=false;query=""};else->if(folder==root)showHome=true else go(folder.parentFile?.takeIf{safeFile(root,it)}?:root)}}}
    if(safSource!=null){SlateSafBrowser(Uri.parse(safSource),onClose={safSource=null},onForget={val old=safSource; if(old!=null){sources=sources-old;prefs.edit().putStringSet("saf_sources",sources).apply();runCatching{val uri=Uri.parse(old);val grant=context.contentResolver.persistedUriPermissions.firstOrNull{it.uri==uri};if(grant!=null)context.contentResolver.releasePersistableUriPermission(uri,(if(grant.isReadPermission)Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or (if(grant.isWritePermission)Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))}};safSource=null});return}
    if(toolsScreen){SlateDocumentsScreen(onClose={toolsScreen=false});return}
    viewer?.let{file->
        val before=remember(file){Triple(file.length(),file.lastModified(),file.parentFile?.lastModified())}
        SlateFileViewer(root,file,onClose={viewer=null;scope.launch{val changed=withContext(Dispatchers.IO){before!=Triple(file.length(),file.lastModified(),file.parentFile?.lastModified())};if(changed){revision++;catalogRevision++;onChanged()}}},onExternal={openSharedFile(context,root,file,false)},initialApkTab=viewerTab)
        return
    }
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    val topTools=useTopNavigation()
    val expanded=config.screenWidthDp>=1100&&config.screenHeightDp>=480
    val drawerBody:@Composable ()->Unit={
            SlateBrowserDrawer(
                volumes=availableVolumes,bookmarks=bookmarks.toList().sorted(),history=history,sources=sources.toList().sorted(),shizuku=shizuku,
                onHome={if(!busy){closeDrawer();home()}},onDashboard={if(!busy){closeDrawer();onDashboard()}},
                onFolder={key->val (volume,dir)=tabFiles(key);if(!busy&&((safeFile(volume,dir)&&dir.isDirectory)||(shizuku&&protectedRelative(dir,primary)!=null))){go(dir,volume);showHome=false};closeDrawer()},
                onCategory={page->closeDrawer();home(page)},onAddStorage={closeDrawer();addSourceDialog=true},
                onSource={closeDrawer();safSource=it},onFavorites={closeDrawer();editFavorites=true},
                onDocuments={closeDrawer();toolsScreen=true},onSettings={closeDrawer();onSettings()}
            )
    }
    val browserContent:@Composable ()->Unit={
    if(showHome){
        overviewState.SaveableStateProvider(overviewEpoch){FilesOverview(granted,availableVolumes,catalog,request,onRefresh={onChanged();revision++;catalogRevision++},onOpenFolder={volume,folder->go(folder,volume);showHome=false},onChanged={revision++;catalogRevision++;onChanged()},initialPage=overviewPage,onMenu={if(!expanded)openDrawer()},bookmarks=bookmarks.toList().sorted(),onOpenBookmark={key->val(volume,dir)=tabFiles(key);if((safeFile(volume,dir)&&dir.isDirectory)||(shizuku&&protectedRelative(dir,primary)!=null)){go(dir,volume);showHome=false}},sources=sources.toList().sorted(),onSource={safSource=it},onAddSource={addSourceDialog=true})}
    }else Column(Modifier.fillMaxSize()){
        val rootLabel=tr(availableVolumes.firstOrNull{it.root.path==rootPath}?.label?:"Internal storage")
        val title=if(folder==root)rootLabel else folder.name
        val storage by produceState<Pair<Long,Long>?>(null,rootPath,revision,granted){
            value=withContext(Dispatchers.IO){runCatching{val total=root.totalSpace; if(total>0)(total-root.usableSpace).coerceIn(0,total) to total else null}.getOrNull()}
        }
        val shown by key(folderKey,granted,shizuku){produceState<List<FileRow>>(emptyList(),rows,query,sort,descending,loadedKey,folderKey,granted,protected,shizuku){
            val source=(if(loadedKey==folderKey&&(if(protected)shizuku else granted))rows else if(cacheable)folderListings.get(folderKey).orEmpty()else emptyList())
            value=withContext(Dispatchers.Default){
            val comparator=when(sort){1->compareBy<FileRow>{it.size};2->compareBy<FileRow>{it.modified};3->compareBy<FileRow>{it.file.extension.lowercase()};else->compareBy<FileRow>{it.file.name.lowercase()}}
            val filtered=source.filter{it.file.name.contains(query,true)}
            val order=if(descending)comparator.reversed()else comparator
            filtered.sortedWith(if(sort==2)order.thenBy{it.file.name.lowercase()}else compareByDescending<FileRow>{it.directory}.then(order))
            }
        }}
        val tools:@Composable ()->Unit={
                IconButton(onClick={marked=emptySet();searching=!searching;if(!searching)query=""},enabled=!busy){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
                IconButton(onClick={grid=!grid;prefs.edit().putBoolean("grid",grid).apply()},enabled=!busy){Icon(painterResource(if(grid)R.drawable.ic_browser_list else R.drawable.ic_browser_grid),stringResource(R.string.file_view),Modifier.size(23.dp))}
                IconButton(onClick={name="";operation="mkdir"},enabled=!busy&&(!protected||shizuku)){Icon(Icons.Default.Add,stringResource(R.string.file_new_folder))}
                IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Default.Refresh,tr("Refresh"))}
                TextButton(onClick={sorting=true},enabled=!busy,contentPadding=PaddingValues(horizontal=4.dp)){
                    Icon(painterResource(R.drawable.ic_browser_sort),stringResource(R.string.file_sort),Modifier.size(23.dp))
                }
                IconButton(onClick={val all=shown.map{it.file.path}.toSet();marked=if(marked==all)emptySet()else all},enabled=!busy&&shown.isNotEmpty()){Icon(painterResource(R.drawable.ic_browser_select),stringResource(R.string.cache_select_all))}
        }
        val chosen=rows.filter{it.file.path in marked}
        val single=chosen.singleOrNull()
        val canModify=chosen.isNotEmpty()&&chosen.all{writable(it.file)}
        if(marked.isNotEmpty()){
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={marked=emptySet();actionMenu=false}){Icon(Icons.Default.Close,tr("Close"))}
                Column(Modifier.weight(1f)){
                    Text(marked.size.toString(),style=MaterialTheme.typography.titleMedium)
                    Text(Formatter.formatFileSize(context,chosen.sumOf{it.size}),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(enabled=!busy&&canModify,onClick={operation="batch_delete"}){Icon(Icons.Default.Delete,tr("Delete"))}
                IconButton(enabled=!busy&&canModify,onClick={clipboard=ClipboardFile(root,chosen,false);marked=emptySet()}){Icon(painterResource(R.drawable.ic_browser_copy),stringResource(R.string.file_copy))}
                IconButton(enabled=!busy&&canModify,onClick={clipboard=ClipboardFile(root,chosen,true);marked=emptySet()}){Icon(painterResource(R.drawable.ic_browser_cut),stringResource(R.string.file_move))}
                IconButton(enabled=!busy&&single!=null&&canModify,onClick={single?.let{selected=it;name=it.file.name;operation="rename"}}){Icon(Icons.Default.Edit,stringResource(R.string.file_rename))}
                Box{
                    IconButton(onClick={actionMenu=true},enabled=!busy){Icon(Icons.Default.MoreVert,tr("More"))}
                    DropdownMenu(expanded=actionMenu,onDismissRequest={actionMenu=false}){
                        DropdownMenuItem(text={Text(stringResource(R.string.cache_select_all))},onClick={marked=shown.map{it.file.path}.toSet();actionMenu=false})
                        if(canModify&&!protected&&chosen.all{!protectedAppPath(it.file.toPath())})DropdownMenuItem(text={Text(tr("Compress"))},onClick={archiveSelection=chosen.map{it.file};actionMenu=false})
                        if(single!=null){
                            if(!single.directory&&!protectedAppPath(single.file.toPath())&&localArchiveSupported(single.file.name))DropdownMenuItem(text={Text(tr("Extract here"))},onClick={actionMenu=false;runOperation{extractLocalArchive(root,single.file)}})
                            DropdownMenuItem(text={Text(stringResource(R.string.browser_properties))},onClick={properties=single;actionMenu=false})
                            if(!single.directory){
                                DropdownMenuItem(text={Text(stringResource(R.string.browser_open_with))},onClick={handler=single.file;actionMenu=false})
                                DropdownMenuItem(text={Text(tr("Share"))},onClick={openSharedFile(context,root,single.file,true);actionMenu=false})
                                if(!protectedAppPath(single.file.toPath()))DropdownMenuItem(text={Text(stringResource(R.string.slate_export_documents))},onClick={
                                    actionMenu=false
                                    runOperation{exportSlateDocument(context,root,single.file)}
                                })
                            }
                            DropdownMenuItem(text={Text(stringResource(R.string.browser_copy_path))},onClick={pathClipboard.setText(AnnotatedString(single.file.path));actionMenu=false})
                            if(single.directory)DropdownMenuItem(text={Text(stringResource(R.string.file_bookmark))},onClick={
                                bookmarks=bookmarks+tabKey(root,single.file);prefs.edit().putStringSet("bookmarks",bookmarks).apply();actionMenu=false
                            })
                        }
                    }
                }
            }
        }else Row(Modifier.fillMaxWidth().padding(end=4.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(searching){searching=false;query=""}else if(!expanded)openDrawer()},enabled=!busy){Icon(if(searching)Icons.Default.ArrowBack else Icons.Default.Menu,stringResource(if(searching)R.string.browser_back else R.string.file_volumes))}
            if(searching){
                val focus=remember{FocusRequester()}
                LaunchedEffect(Unit){focus.requestFocus()}
                TextField(value=query,onValueChange={query=it},modifier=Modifier.weight(1f).focusRequester(focus),singleLine=true,placeholder={Text(stringResource(R.string.file_search))},colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface))
                IconButton(onClick={query="";searching=false}){Icon(Icons.Default.Close,tr("Close"))}
            }else Column(Modifier.weight(1f)){
                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.browser_counts,shown.count{it.directory},shown.count{!it.directory}),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(topTools&&!searching&&config.screenWidthDp>=620)Row(verticalAlignment=Alignment.CenterVertically){tools()}
            Box{
                IconButton(onClick={menu=true},enabled=!busy&&(granted||shizuku)){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    if(topTools){
                        if(config.screenWidthDp<620||searching){
                            DropdownMenuItem(text={Text(stringResource(R.string.file_search))},onClick={menu=false;searching=!searching;if(!searching)query=""})
                            DropdownMenuItem(text={Text(stringResource(R.string.file_view))},onClick={menu=false;grid=!grid;prefs.edit().putBoolean("grid",grid).apply()})
                            DropdownMenuItem(text={Text(stringResource(R.string.cache_select_all))},onClick={menu=false;marked=shown.map{it.file.path}.toSet()})
                        }
                    }
                    DropdownMenuItem(text={Text(stringResource(R.string.file_new_folder))},enabled=!protected||shizuku,onClick={menu=false;name="";operation="mkdir"})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_hidden))},onClick={hidden=!hidden;menu=false},trailingIcon={Checkbox(checked=hidden,onCheckedChange=null)})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_sort))},onClick={sorting=true;menu=false})
                    DropdownMenuItem(text={Text(stringResource(R.string.browser_global_search))},onClick={menu=false;home("search")})
                    DropdownMenuItem(text={Text(stringResource(R.string.browser_dual_pane))},onClick={dualPane=!dualPane;prefs.edit().putBoolean("dual_pane",dualPane).apply();menu=false},trailingIcon={Checkbox(checked=dualPane,onCheckedChange=null)})
                    DropdownMenuItem(text={Text(stringResource(if(tabKey(root,folder) in bookmarks)R.string.file_unbookmark else R.string.file_bookmark))},onClick={saveBookmark();menu=false})
                    DropdownMenuItem(text={Text(tr("Refresh"))},onClick={revision++;menu=false})
                    DropdownMenuItem(text={Text(stringResource(R.string.slate_documents))},onClick={menu=false;toolsScreen=true})
                }
            }
        }
        if(!granted&&!(protected&&shizuku)){Text(tr("Storage access"));Button(onClick=request){Text(tr("Grant access"))};return@Column}
        if(!topTools||tabs.size>1)Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
            tabs.forEachIndexed{index,key->
                val (volume,dir)=tabFiles(key)
                val label=if(dir==volume)tr(availableVolumes.firstOrNull{it.root.path==volume.path}?.label?:"Internal storage")else dir.name
                val active=index==tab
                Column(Modifier.widthIn(max=180.dp).clickable(enabled=!busy){tab=index;selected=null;marked=emptySet();query="";searching=false},horizontalAlignment=Alignment.CenterHorizontally){
                    Text(label,Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.bodyMedium,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Box(Modifier.width(64.dp).height(2.dp).background(if(active)MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
            IconButton(enabled=!busy&&tabs.size<12,onClick={tabs=ArrayList(tabs).apply{add(tabKey(root,folder))};tab=tabs.lastIndex;marked=emptySet()}){Icon(Icons.Default.Add,stringResource(R.string.file_new_tab))}
            if(tabs.size>1)IconButton(enabled=!busy,onClick={tabs=ArrayList(tabs).apply{removeAt(tab)};tab=tab.coerceAtMost(tabs.lastIndex);marked=emptySet()}){Icon(Icons.Default.Close,stringResource(R.string.file_close_tab))}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(folder==root)showHome=true else go(folder.parentFile?:root)},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            IconButton(onClick={home()},enabled=!busy){Icon(Icons.Default.Home,stringResource(R.string.browser_home),Modifier.size(19.dp))}
            if(topTools&&tabs.size==1)IconButton(enabled=!busy,onClick={tabs=ArrayList(tabs).apply{add(tabKey(root,folder))};tab=tabs.lastIndex}){Icon(Icons.Default.Add,stringResource(R.string.file_new_tab),Modifier.size(20.dp))}
            if(folder==root)storage?.let{(used,total)->
                Text(Formatter.formatFileSize(context,used)+" / "+Formatter.formatFileSize(context,total),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(folder!=root)Text(rootLabel,Modifier.clickable(enabled=!busy){go(root)}.padding(horizontal=4.dp),style=MaterialTheme.typography.bodySmall,maxLines=1)
            var crumb=root
            folder.path.removePrefix(root.path).trim('/').split('/').filter{it.isNotEmpty()}.forEach{part->
                crumb=File(crumb,part)
                val dest=crumb
                Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp))
                Text(part,Modifier.clickable(enabled=!busy){go(dest)}.padding(horizontal=5.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium,maxLines=1)
            }
        }

        if(protected&&shizuku)Text(stringResource(R.string.protected_file_access),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        if(protected&&!shizuku)com.slate.android.storage.ShizukuAccessRow()
        clipboard?.let{clip->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(R.string.tool_selected,clip.files.size),Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                TextButton(enabled=!busy&&(!protected||shizuku),onClick={pasteInto(root,folder)}){Text(stringResource(R.string.file_paste))}
                TextButton(enabled=!busy,onClick={clipboard=null}){Text(tr("Cancel"))}
            }
        }
        if(transferring){
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(Formatter.formatFileSize(context,transferBytes)+" · "+Formatter.formatFileSize(context,transferSpeed)+"/s",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={transferJob?.cancel()},enabled=clipboard?.move!=true){Text(tr("Cancel"))}
                }
                if(transferTotal>0)LinearProgressIndicator(progress={(transferBytes.toFloat()/transferTotal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }else DelayedBrowserProgress(loading||busy)
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        fun tap(row:FileRow){
            if(marked.isNotEmpty())marked=if(row.file.path in marked)marked-row.file.path else marked+row.file.path
            else if(row.directory)go(row.file)
            else if(row.file.extension.equals("apk",true)&&canViewSlateFile(root,row.file))handler=row.file
            else if(canViewSlateFile(root,row.file)){viewerTab=0;viewer=row.file}
            else if(!openSharedFile(context,root,row.file,false))message=context.getString(R.string.file_failed)
        }
        val wide=topTools||config.screenWidthDp>=if(expanded)1100 else 700
        Row(Modifier.weight(1f).fillMaxWidth()){
        Column(Modifier.weight(1f).fillMaxHeight()){
        if(grid&&shown.isEmpty()&&!loading)Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))
        if(grid)LazyVerticalGrid(columns=GridCells.Adaptive(105.dp),state=gridState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            items(shown,key={it.file.path}){row->
                Surface(shape=MaterialTheme.shapes.medium,color=if(row.file.path in marked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface){
                    Column(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={tap(row)},onLongClick={marked=marked+row.file.path}).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        BrowserFileIcon(row,null)
                        Text(row.file.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        if(!row.directory)Text(Formatter.formatFileSize(context,row.size),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }else {
            val groups=remember(shown,sort){
                if(sort==2)shown.groupBy{if(it.modified>0)fileDay(it.modified)else 0L}.toList()
                else listOf(0L to shown)
            }
            LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(vertical=2.dp)){
                groups.forEach{(day,entries)->
                    if(sort==2&&entries.isNotEmpty())item(key="day-$day"){
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(if(day>0)DateUtils.getRelativeTimeSpanString(day,System.currentTimeMillis(),DateUtils.DAY_IN_MILLIS,DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH).toString()else "—",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entries.size.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(entries,key={it.file.path}){row->
                        val summary by produceState<FolderSummary?>(null,row.file.path,row.modified,protected,hidden,rootPath){
                            value=null
                            if(row.directory&&!protected)value=folderSummarySlots.withPermit{withContext(Dispatchers.IO){folderSummary(root,row.file,hidden)}}
                        }
                        val active=row.file.path in marked
                        val date=if(row.modified>0)DateUtils.getRelativeTimeSpanString(row.modified,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS,DateUtils.FORMAT_ABBREV_RELATIVE).toString()else "—"
                        val meta=if(row.directory)summary?.count?.let{"($it)"}else Formatter.formatFileSize(context,row.size)
                        BrowserEntryRow(row.file.name,date,meta,active,topTools,Modifier.combinedClickable(enabled=!busy,onClick={tap(row)},onLongClick={marked=marked+row.file.path})){
                            BrowserFileIcon(row,summary?.preview,Modifier.size(if(topTools)32.dp else 40.dp))
                        }
                    }
                }
                if(shown.isEmpty()&&!loading)item{Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))}
            }
        }
        }
        if(dualPane&&wide){
            VerticalDivider(Modifier.fillMaxHeight())
            SlateSecondPane(root=primary,folder=File(otherPath),revision=revision,enabled=!busy,canPaste=clipboard!=null,onFolder={otherPath=it.path},onPaste={pasteInto(primary,File(otherPath))},modifier=Modifier.weight(1f).fillMaxHeight())
        }
        }
        if(dualPane&&!wide)SlateSecondPane(root=primary,folder=File(otherPath),revision=revision,enabled=!busy,canPaste=clipboard!=null,onFolder={otherPath=it.path},onPaste={pasteInto(primary,File(otherPath))},modifier=Modifier.fillMaxWidth().height(180.dp))
        if(!topTools)Surface(color=MaterialTheme.colorScheme.surface){
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){tools()}
        }
    }
    }
    if(expanded)Row(Modifier.fillMaxSize()){
        Surface(Modifier.width(270.dp).fillMaxHeight(),color=MaterialTheme.colorScheme.surfaceContainerLow){drawerBody()}
        VerticalDivider(Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()){browserContent()}
    }else if(topTools){
        Box(Modifier.fillMaxSize()){browserContent()}
        if(landscapeDrawer)androidx.compose.ui.window.Dialog(onDismissRequest={closeDrawer()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterStart){
                Box(Modifier.fillMaxSize().clickable{closeDrawer()})
                Surface(Modifier.widthIn(max=320.dp).fillMaxHeight().padding(8.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer){drawerBody()}
            }
        }
    }else ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=!busy,drawerContent={
        ModalDrawerSheet(Modifier.widthIn(max=320.dp)){drawerBody()}
    }){browserContent()}
    if(archiveSelection.isNotEmpty())LocalArchiveDialog(archiveSelection,onClose={archiveSelection=emptyList()},onCreate={files,name,format,level->archiveSelection=emptyList();runOperation{createLocalArchive(root,folder,files,name,format,level)}})
    if(addSourceDialog)StorageConnectionDialog(onClose={addSourceDialog=false},onPick={addSourceDialog=false;addStorage.launch(null)})
    if(editFavorites)SlateFavoritesDialog(bookmarks.toList().sorted(),onClose={editFavorites=false},onRemove={key->bookmarks=bookmarks-key;prefs.edit().putStringSet("bookmarks",bookmarks).apply()})
    properties?.let{row->
        AlertDialog(onDismissRequest={properties=null},title={Text(stringResource(R.string.browser_properties))},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(row.file.name,style=MaterialTheme.typography.titleMedium)
                Text(row.file.path,style=MaterialTheme.typography.bodySmall)
                if(!row.directory)Text(Formatter.formatFileSize(context,row.size))
                if(row.modified>0)Text(DateFormat.getDateTimeInstance().format(Date(row.modified)))
            }
        },confirmButton={TextButton(onClick={properties=null}){Text(tr("Close"))}})
    }
    handler?.let{file->
        if(file.extension.equals("apk",true)&&canViewSlateFile(root,file)){
            SlateApkActions(root,file,onClose={handler=null},onInspect={tabIndex->handler=null;viewerTab=tabIndex;viewer=file})
        }else AlertDialog(onDismissRequest={handler=null},title={Text(stringResource(R.string.browser_open_with))},text={
            Column{
                if(canViewSlateFile(root,file))TextButton(onClick={handler=null;viewerTab=0;viewer=file}){Text(stringResource(R.string.browser_internal))}
                TextButton(onClick={handler=null;openSharedFile(context,root,file,false)}){Text(stringResource(R.string.browser_external))}
            }
        },confirmButton={TextButton(onClick={handler=null}){Text(tr("Close"))}})
    }
    if(sorting)AlertDialog(onDismissRequest={sorting=false},title={Text(stringResource(R.string.file_sort))},text={
        Column{
            listOf(R.string.file_sort_name,R.string.file_sort_size,R.string.file_sort_date,R.string.file_sort_type).forEachIndexed{index,label->
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=sort==index,onClick={if(sort!=index){sort=index;descending=index==2;persistSort()}});Text(stringResource(label))}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=descending,onCheckedChange={descending=it;persistSort()});Text(stringResource(R.string.file_descending))}
        }
    },confirmButton={TextButton(onClick={sorting=false}){Text(tr("Close"))}})
    operation?.let{op->
        val target=selected?.file
        AlertDialog(onDismissRequest={operation=null;selected=null},title={Text(stringResource(when(op){"mkdir"->R.string.file_new_folder;"rename"->R.string.file_rename;else->R.string.file_delete_confirm}))},text={
            if(op=="batch_delete")Text(stringResource(R.string.file_recursive_delete,marked.size))
            else if(op=="delete")Text(stringResource(R.string.file_recursive_delete,1)+"\n\n"+target?.name.orEmpty())
            else OutlinedTextField(value=name,onValueChange={name=it},singleLine=true)
        },dismissButton={TextButton(onClick={operation=null;selected=null}){Text(tr("Cancel"))}},confirmButton={
            TextButton(enabled=op=="delete"||op=="batch_delete"||validFileName(name),onClick={
                val input=name
                val batch=rows.filter{it.file.path in marked}
                val targetRow=selected
                operation=null;selected=null
                runOperation{
                    val elevated=protected||target?.let{protectedRelative(it,primary)!=null}==true||batch.any{protectedRelative(it.file,primary)!=null}
                    withFileOperations(context,elevated){ops->
                        when(op){
                            "mkdir"->{check(validFileName(input));ops.mkdir(root,File(folder,input))}
                            "rename"->{val source=checkNotNull(target);val dest=File(folder,input);check(validFileName(input));ops.rename(root,source,dest);MediaScannerConnection.scanFile(context,arrayOf(source.path,dest.path),null,null)}
                            "batch_delete"->{for(row in batch)ops.delete(root,row.file,row.directory)}
                            "delete"->{val row=checkNotNull(targetRow);ops.delete(root,row.file,row.directory)}
                        }
                    }
                }
            }){Text(tr(if(op=="delete"||op=="batch_delete")"Delete" else "Continue"))}
        })
    }
}
