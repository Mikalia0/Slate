// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.format.Formatter
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
import java.io.File
import java.nio.file.Files
internal fun safeFile(root:File,file:File):Boolean=runCatching{
    file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !Files.isSymbolicLink(file.toPath())
}.getOrDefault(false)
internal fun validFileName(name:String)=name.isNotBlank()&&name!="."&&name!=".."&&name.none{it=='/'||it=='\\'||it.code<32}
private data class FileRow(val file:File,val directory:Boolean,val size:Long,val modified:Long)
private data class ClipboardFile(val root:File,val files:List<FileRow>,val move:Boolean)
private fun tabKey(root:File,folder:File)=root.path+"\n"+folder.path
private fun tabFiles(key:String)=File(key.substringBefore('\n')) to File(key.substringAfter('\n'))
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileManagerScreen(granted:Boolean,volumes:List<Volume>,request:()->Unit,onChanged:()->Unit,model:com.slate.android.storage.StorageModel){
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
        val title=if(folder==root)tr(volumes.firstOrNull{it.root.path==rootPath}?.label?:"Internal storage")else folder.name
        Row(Modifier.fillMaxWidth().padding(start=8.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={volumeMenu=true},enabled=!busy){Icon(Icons.Default.Menu,stringResource(R.string.file_volumes))}
            Column(Modifier.weight(1f)){
                Text(title,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.tool_files),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={searching=!searching;if(!searching)query=""},enabled=(granted||shizuku)&&!busy){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
            IconButton(onClick={grid=!grid;prefs.edit().putBoolean("grid",grid).apply()},enabled=(granted||shizuku)&&!busy){Icon(painterResource(if(grid)R.drawable.ic_storage_file else R.drawable.ic_storage_folder),stringResource(R.string.file_view))}
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
                val (_,dir)=tabFiles(key)
                FilterChip(selected=index==tab,onClick={if(!busy){tab=index;selected=null;marked=emptySet();query=""}},label={Text(dir.name.ifBlank{"/"},maxLines=1)})
            }
            IconButton(enabled=!busy&&tabs.size<12,onClick={tabs=ArrayList(tabs).apply{add(tabKey(root,folder))};tab=tabs.lastIndex;marked=emptySet()}){Icon(Icons.Default.Add,stringResource(R.string.file_new_tab))}
            if(tabs.size>1)IconButton(enabled=!busy,onClick={tabs=ArrayList(tabs).apply{removeAt(tab)};tab=tab.coerceAtMost(tabs.lastIndex);marked=emptySet()}){Icon(Icons.Default.Close,stringResource(R.string.file_close_tab))}
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(folder==root)showHome=true else go(folder.parentFile?:root)},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            TextButton(onClick={go(root)},enabled=!busy){Text(tr(volumes.firstOrNull{it.root.path==rootPath}?.label?:"Internal storage"))}
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
        val shown=remember(rows,query,sort,descending){
            val comparator=when(sort){1->compareBy<FileRow>{it.size};2->compareBy<FileRow>{it.modified};3->compareBy<FileRow>{it.file.extension.lowercase()};else->compareBy<FileRow>{it.file.name.lowercase()}}
            rows.filter{it.file.name.contains(query,true)}.sortedWith(compareByDescending<FileRow>{it.directory}.then(if(descending)comparator.reversed()else comparator))
        }
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(stringResource(R.string.file_items,shown.size),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={sorting=true},enabled=!busy){Text(stringResource(listOf(R.string.file_sort_name,R.string.file_sort_size,R.string.file_sort_date,R.string.file_sort_type)[sort])+" "+if(descending)"↓" else "↑")}
        }
        HorizontalDivider()
        if(shown.isEmpty()&&!loading)Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))
        fun tap(row:FileRow){
            if(marked.isNotEmpty())marked=if(row.file.path in marked)marked-row.file.path else marked+row.file.path
            else if(row.directory)go(row.file)
            else if(!openSharedFile(context,root,row.file,false))message=context.getString(R.string.file_failed)
        }
        if(grid)LazyVerticalGrid(columns=GridCells.Adaptive(105.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            items(shown,key={it.file.path}){row->
                Surface(shape=MaterialTheme.shapes.medium,color=if(row.file.path in marked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface){
                    Column(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={tap(row)},onLongClick={marked=marked+row.file.path}).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        FilePreview(Entry(row.file,row.directory,row.size,0,0,row.modified),Modifier.size(40.dp),MaterialTheme.colorScheme.primary)
                        Text(row.file.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        if(!row.directory)Text(Formatter.formatFileSize(context,row.size),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=2.dp)){
            items(shown,key={it.file.path}){row->
                Row(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={tap(row)},onLongClick={marked=marked+row.file.path}).background(if(row.file.path in marked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).heightIn(min=62.dp).padding(start=16.dp,end=4.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    if(row.file.path in marked)Icon(Icons.Default.Check,null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary) else FilePreview(Entry(row.file,row.directory,row.size,0,0,row.modified),Modifier.size(30.dp),MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){
                        Text(row.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(if(row.directory)null else Formatter.formatFileSize(context,row.size),if(row.modified>0)DateFormat.getDateInstance(DateFormat.SHORT).format(Date(row.modified))else null).joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={selected=row},enabled=!busy){Icon(Icons.Default.MoreVert,null)}
                }
                HorizontalDivider(Modifier.padding(start=60.dp),thickness=.5.dp)
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
                Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=sort==index,onClick={sort=index});Text(stringResource(label))}
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
