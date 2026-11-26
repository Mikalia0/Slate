// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.format.Formatter
import android.text.format.DateUtils
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Calendar
import androidx.activity.compose.BackHandler
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
private data class FolderSummary(val count:String,val preview:File?)
private fun folderSummary(root:File,folder:File,hidden:Boolean):FolderSummary?=runCatching{
    if(!safeFile(root,folder)||protectedAppPath(folder.toPath()))return@runCatching null
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
    FolderSummary(if(count>1000)"1000+" else if(scanned>1500)"$count+" else count.toString(),preview)
}.getOrNull()

private fun fileDay(time:Long):Long=Calendar.getInstance().apply{
    timeInMillis=time
    set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
}.timeInMillis

@Composable
private fun BrowserFileIcon(row:FileRow,preview:File?,modifier:Modifier=Modifier.size(40.dp)){
    val color=MaterialTheme.colorScheme
    val ext=row.file.extension.lowercase()
    val typeLabel=if(!row.directory&&ext in setOf("zip","rar","7z","tar","gz","xz","zst","tzst","bz2","dll","exe","msi","patch","diff","so","json","xml","ini","cfg","log","txt","pdf","bin","iso","sh","bat"))ext.uppercase() else null
    Box(modifier.background(if(row.directory)color.primaryContainer else color.secondaryContainer,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
        if(typeLabel!=null)Text(typeLabel,style=MaterialTheme.typography.labelSmall,color=color.onSecondaryContainer,maxLines=1)
        else FilePreview(Entry(row.file,row.directory,row.size,0,0,row.modified),Modifier.size(if(row.directory)25.dp else 40.dp),if(row.directory)color.onPrimaryContainer else color.onSecondaryContainer)
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
internal fun FileManagerScreen(granted:Boolean,volumes:List<Volume>,request:()->Unit,onChanged:()->Unit,model:com.slate.android.storage.StorageModel,onBrowsingChanged:(Boolean)->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val primary=Environment.getExternalStorageDirectory()
    val prefs=remember{context.getSharedPreferences("file_manager",0)}
    var tabs by rememberSaveable{mutableStateOf(arrayListOf(tabKey(primary,primary)))}
    var tab by rememberSaveable{mutableIntStateOf(0)}
    val (root,folder)=tabFiles(tabs[tab.coerceIn(tabs.indices)])
    val rootPath=root.path
    val path=folder.path
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
    var query by rememberSaveable{mutableStateOf("")}
    var hidden by rememberSaveable{mutableStateOf(false)}
    var sort by rememberSaveable{mutableIntStateOf(0)}
    var menu by remember{mutableStateOf(false)}
    var volumeMenu by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<FileRow?>(null)}
    var clipboard by remember{mutableStateOf<ClipboardFile?>(null)}
    var operation by remember{mutableStateOf<String?>(null)}
    var name by remember{mutableStateOf("")}
    var message by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var rows by remember{mutableStateOf<List<FileRow>>(emptyList())}
    var showHome by rememberSaveable{mutableStateOf(true)}
    val browsingCallback by rememberUpdatedState(onBrowsingChanged)
    LaunchedEffect(showHome){browsingCallback(!showHome)}
    DisposableEffect(Unit){onDispose{browsingCallback(false)}}
    val availableVolumes=volumes.ifEmpty{listOf(Volume(primary,"Internal storage"))}
    val catalog by produceState<FileCatalog?>(null,granted,availableVolumes,revision){
        value=null
        if(granted)value=model.fileCatalog(availableVolumes)
    }
    fun go(file:File,volume:File=root){tabs=ArrayList(tabs).apply{set(tab,tabKey(volume,file))};query="";searching=false;selected=null;marked=emptySet();message=null}
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
            finally{busy=false;revision++;onChanged()}
        }
    }
    LaunchedEffect(path,rootPath,revision,granted,hidden,shizuku){
        message=null
        rows=emptyList()
        if(!granted&&!shizuku)return@LaunchedEffect
        loading=true
        try{
            val relative=protectedRelative(folder,primary)
            rows=if(relative!=null&&shizuku)withProtectedRead(context){client->
                client.list(relative).filter{it.readable&&(hidden||!it.name.startsWith("."))}.map{FileRow(File(folder,it.name),it.directory,it.size,it.modified)}
            }else withContext(Dispatchers.IO){
                check(safeFile(root,folder))
                val list=folder.listFiles()?:error("unreadable")
                list.asSequence().filter{hidden||!it.name.startsWith(".")}.filter{safeFile(root,it)}.map{FileRow(it,it.isDirectory,if(it.isFile)it.length()else 0L,it.lastModified())}.toList()
            }
        }catch(e:CancellationException){throw e}
        catch(_:Exception){message=context.getString(R.string.file_failed)}
        finally{loading=false}
    }
    BackHandler(!showHome){if(!busy){when{marked.isNotEmpty()->marked=emptySet();selected!=null->selected=null;searching->{searching=false;query=""};else->if(folder==root)showHome=true else go(folder.parentFile?.takeIf{safeFile(root,it)}?:root)}}}
    if(showHome){
        FilesOverview(granted,availableVolumes,catalog,request,onRefresh={onChanged();revision++},onOpenFolder={volume,folder->go(folder,volume);showHome=false},onChanged={revision++;onChanged()})
        return
    }
    Column(Modifier.fillMaxSize()){
        val rootLabel=tr(availableVolumes.firstOrNull{it.root.path==rootPath}?.label?:"Internal storage")
        val title=if(folder==root)rootLabel else rootLabel+"/"+folder.path.removePrefix(root.path).trim('/')
        val storage by produceState<Pair<Long,Long>?>(null,rootPath,revision,granted){
            value=withContext(Dispatchers.IO){runCatching{val total=root.totalSpace; if(total>0)(total-root.usableSpace).coerceIn(0,total) to total else null}.getOrNull()}
        }
        val shown=remember(rows,query,sort,descending){
            val comparator=when(sort){1->compareBy<FileRow>{it.size};2->compareBy<FileRow>{it.modified};3->compareBy<FileRow>{it.file.extension.lowercase()};else->compareBy<FileRow>{it.file.name.lowercase()}}
            val filtered=rows.filter{it.file.name.contains(query,true)}
            val order=if(descending)comparator.reversed()else comparator
            filtered.sortedWith(if(sort==2)order.thenBy{it.file.name.lowercase()}else compareByDescending<FileRow>{it.directory}.then(order))
        }
        Row(Modifier.fillMaxWidth().padding(start=8.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={volumeMenu=true},enabled=!busy){Icon(Icons.Default.Menu,stringResource(R.string.file_volumes))}
            Column(Modifier.weight(1f)){
                Text(title,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.file_items,shown.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box{
                DropdownMenu(expanded=volumeMenu,onDismissRequest={volumeMenu=false}){
                    if(shizuku){
                        listOf("Android/data","Android/obb").forEach{relative->DropdownMenuItem(text={Text(relative)},onClick={go(File(primary,relative),primary);volumeMenu=false})}
                        HorizontalDivider()
                    }
                    (volumes.ifEmpty{listOf(Volume(primary,"Internal storage"))}).forEach{volume->
                        DropdownMenuItem(text={Text(tr(volume.label))},onClick={go(volume.root,volume.root);volumeMenu=false})
                    }
                    if(bookmarks.isNotEmpty()){
                        HorizontalDivider()
                        bookmarks.sorted().forEach{key->
                            val (volume,dir)=tabFiles(key)
                            DropdownMenuItem(text={Text(dir.name.ifBlank{dir.path})},onClick={if((safeFile(volume,dir)&&dir.isDirectory)||(shizuku&&protectedRelative(dir,primary)!=null)){go(dir,volume);volumeMenu=false}})
                        }
                    }
                }
            }
            Box{
                IconButton(onClick={menu=true},enabled=!busy&&(granted||shizuku)){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(stringResource(R.string.file_new_folder))},enabled=!protected||shizuku,onClick={menu=false;name="";operation="mkdir"})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_hidden))},onClick={hidden=!hidden;menu=false},trailingIcon={Checkbox(checked=hidden,onCheckedChange=null)})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_sort))},onClick={sorting=true;menu=false})
                    DropdownMenuItem(text={Text(stringResource(if(tabKey(root,folder) in bookmarks)R.string.file_unbookmark else R.string.file_bookmark))},onClick={saveBookmark();menu=false})
                    DropdownMenuItem(text={Text(tr("Refresh"))},onClick={revision++;menu=false})
                }
            }
        }
        if(!granted&&!(protected&&shizuku)){Text(tr("Storage access"));Button(onClick=request){Text(tr("Grant access"))};return@Column}
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
            tabs.forEachIndexed{index,key->
                val (volume,dir)=tabFiles(key)
                val label=if(dir==volume)tr(availableVolumes.firstOrNull{it.root.path==volume.path}?.label?:"Internal storage")else dir.name
                val active=index==tab
                Text(label,Modifier.heightIn(min=40.dp).clickable(enabled=!busy){tab=index;selected=null;marked=emptySet();query=""}.background(if(active)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,MaterialTheme.shapes.small).padding(horizontal=12.dp,vertical=10.dp),style=MaterialTheme.typography.bodyMedium,color=if(active)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(enabled=!busy&&tabs.size<12,onClick={tabs=ArrayList(tabs).apply{add(tabKey(root,folder))};tab=tabs.lastIndex;marked=emptySet()}){Icon(Icons.Default.Add,stringResource(R.string.file_new_tab))}
            if(tabs.size>1)IconButton(enabled=!busy,onClick={tabs=ArrayList(tabs).apply{removeAt(tab)};tab=tab.coerceAtMost(tabs.lastIndex);marked=emptySet()}){Icon(Icons.Default.Close,stringResource(R.string.file_close_tab))}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(folder==root)showHome=true else go(folder.parentFile?:root)},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            TextButton(onClick={go(root)},enabled=!busy){Text(rootLabel)}
            if(folder==root)storage?.let{(used,total)->
                Text(Formatter.formatFileSize(context,used)+" / "+Formatter.formatFileSize(context,total),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var crumb=root
            folder.path.removePrefix(root.path).trim('/').split('/').filter{it.isNotEmpty()}.forEach{part->
                crumb=File(crumb,part)
                val dest=crumb
                Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp))
                TextButton(onClick={go(dest)},enabled=!busy){Text(part,maxLines=1)}
            }
        }
        if(searching)OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text(stringResource(R.string.file_search))})
        if(protected&&shizuku)Text(stringResource(R.string.protected_file_access),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        if(protected&&!shizuku)com.slate.android.storage.ShizukuAccessRow()
        if(marked.isNotEmpty()){
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium){
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
                    Text(stringResource(R.string.tool_selected,marked.size),Modifier.padding(horizontal=8.dp))
                    TextButton(onClick={marked=rows.filter{it.file.name.contains(query,true)}.map{it.file.path}.toSet()},enabled=!busy){Text(stringResource(R.string.cache_select_all))}
                    val chosen=rows.filter{it.file.path in marked}
                    TextButton(enabled=!busy&&chosen.isNotEmpty()&&chosen.all{writable(it.file)},onClick={clipboard=ClipboardFile(root,chosen,false);marked=emptySet()}){Text(stringResource(R.string.file_copy))}
                    TextButton(enabled=!busy&&chosen.isNotEmpty()&&chosen.all{writable(it.file)},onClick={clipboard=ClipboardFile(root,chosen,true);marked=emptySet()}){Text(stringResource(R.string.file_move))}
                    TextButton(enabled=!busy&&chosen.isNotEmpty()&&chosen.all{writable(it.file)},onClick={operation="batch_delete"}){Text(tr("Delete"))}
                    if(chosen.size==1)IconButton(enabled=!busy,onClick={selected=chosen.single()}){Icon(Icons.Default.MoreVert,tr("More"))}
                    IconButton(onClick={marked=emptySet()}){Icon(Icons.Default.Close,null)}
                }
            }
        }
        clipboard?.let{clip->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(R.string.tool_selected,clip.files.size),Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                TextButton(enabled=!busy&&(!protected||shizuku),onClick={
                    runOperation{
                        val elevated=protected||clip.files.any{protectedRelative(it.file,primary)!=null}
                        withFileOperations(context,elevated){ops->
                            for(row in clip.files){
                                val target=File(folder,row.file.name)
                                ops.copy(clip.root,row.file,root,target,row.directory,clip.move)
                                MediaScannerConnection.scanFile(context,arrayOf(target.path,row.file.path),null,null)
                            }
                        }
                        withContext(Dispatchers.Main){clipboard=null}
                    }
                }){Text(stringResource(R.string.file_paste))}
                TextButton(enabled=!busy,onClick={clipboard=null}){Text(tr("Cancel"))}
            }
        }
        if(loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        fun tap(row:FileRow){
            if(marked.isNotEmpty())marked=if(row.file.path in marked)marked-row.file.path else marked+row.file.path
            else if(row.directory)go(row.file)
            else if(!openSharedFile(context,root,row.file,false))message=context.getString(R.string.file_failed)
        }
        if(grid&&shown.isEmpty()&&!loading)Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))
        if(grid)LazyVerticalGrid(columns=GridCells.Adaptive(105.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
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
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=2.dp)){
                groups.forEach{(day,entries)->
                    if(sort==2&&entries.isNotEmpty())item(key="day-$day"){
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(if(day>0)DateUtils.getRelativeTimeSpanString(day,System.currentTimeMillis(),DateUtils.DAY_IN_MILLIS,DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH).toString()else "—",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entries.size.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(entries,key={it.file.path}){row->
                        val summary by produceState<FolderSummary?>(null,row.file.path,row.modified,revision,protected,hidden,rootPath){
                            value=null
                            if(row.directory&&!protected)value=folderSummarySlots.withPermit{withContext(Dispatchers.IO){folderSummary(root,row.file,hidden)}}
                        }
                        val active=row.file.path in marked
                        Row(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={tap(row)},onLongClick={marked=marked+row.file.path}).background(if(active)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).heightIn(min=51.dp).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            if(active)Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){Icon(Icons.Default.Check,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)}
                            else BrowserFileIcon(row,summary?.preview)
                            Column(Modifier.weight(1f)){
                                Text(row.file.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                    Text(if(row.modified>0)DateUtils.getRelativeTimeSpanString(row.modified,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS,DateUtils.FORMAT_ABBREV_RELATIVE).toString()else "—",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    val metadata=if(row.directory)summary?.count?.let{"($it)"}else Formatter.formatFileSize(context,row.size)
                                    if(metadata!=null)Text(metadata,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                                }
                            }
                        }
                    }
                }
                if(shown.isEmpty()&&!loading)item{Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))}
            }
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainer){
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={searching=!searching;if(!searching)query=""},enabled=!busy){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
                IconButton(onClick={grid=!grid;prefs.edit().putBoolean("grid",grid).apply()},enabled=!busy){Icon(painterResource(if(grid)R.drawable.ic_storage_file else R.drawable.ic_storage_folder),stringResource(R.string.file_view),Modifier.size(23.dp))}
                IconButton(onClick={name="";operation="mkdir"},enabled=!busy&&(!protected||shizuku)){Icon(Icons.Default.Add,stringResource(R.string.file_new_folder))}
                IconButton(onClick={revision++},enabled=!busy){Icon(Icons.Default.Refresh,tr("Refresh"))}
                TextButton(onClick={sorting=true},enabled=!busy,contentPadding=PaddingValues(horizontal=4.dp)){
                    Text((when(sort){1->"KB";2->"◷";3->"Aa";else->"AZ"})+(if(descending)"↓"else"↑"),style=MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick={val all=shown.map{it.file.path}.toSet();marked=if(marked==all)emptySet()else all},enabled=!busy&&shown.isNotEmpty()){Icon(Icons.Default.Check,stringResource(R.string.cache_select_all))}
            }
        }
    }
    selected?.let{row->
        if(operation==null)AlertDialog(onDismissRequest={selected=null},title={Text(row.file.name,maxLines=2,overflow=TextOverflow.Ellipsis)},text={
            Column{
                Text(row.file.parent.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!row.directory)Text(Formatter.formatFileSize(context,row.size),style=MaterialTheme.typography.labelMedium)
                HorizontalDivider(Modifier.padding(vertical=8.dp))
                TextButton(enabled=writable(row.file),onClick={clipboard=ClipboardFile(root,listOf(row),false);selected=null}){Text(stringResource(R.string.file_copy))}
                TextButton(enabled=writable(row.file),onClick={clipboard=ClipboardFile(root,listOf(row),true);selected=null}){Text(stringResource(R.string.file_move))}
                if(!row.directory)TextButton(onClick={openSharedFile(context,root,row.file,true);selected=null}){Text(tr("Share"))}
                TextButton(enabled=writable(row.file),onClick={name=row.file.name;operation="rename"}){Text(stringResource(R.string.file_rename))}
                TextButton(enabled=writable(row.file),onClick={operation="delete"}){Text(tr("Delete"),color=MaterialTheme.colorScheme.error)}
            }
        },confirmButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})
    }
    if(sorting)AlertDialog(onDismissRequest={sorting=false},title={Text(stringResource(R.string.file_sort))},text={
        Column{
            listOf(R.string.file_sort_name,R.string.file_sort_size,R.string.file_sort_date,R.string.file_sort_type).forEachIndexed{index,label->
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=sort==index,onClick={if(sort!=index){sort=index;descending=index==2}});Text(stringResource(label))}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=descending,onCheckedChange={descending=it});Text(stringResource(R.string.file_descending))}
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
