// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.media.MediaScannerConnection
import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
internal fun safeFile(root:File,file:File):Boolean=runCatching{
    file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && !Files.isSymbolicLink(file.toPath())
}.getOrDefault(false)
internal fun validFileName(name:String)=name.isNotBlank()&&name!="."&&name!=".."&&name.none{it=='/'||it=='\\'||it.code<32}
private data class FileRow(val file:File,val directory:Boolean,val size:Long,val modified:Long)
private data class ClipboardFile(val root:File,val file:File,val move:Boolean)
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileManagerScreen(granted:Boolean,volumes:List<Volume>,request:()->Unit,onChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val primary=Environment.getExternalStorageDirectory()
    var rootPath by rememberSaveable{mutableStateOf(primary.path)}
    var path by rememberSaveable{mutableStateOf(primary.path)}
    val root=File(rootPath)
    val folder=File(path)
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
    fun go(file:File){path=file.path;query="";selected=null}
    fun runOperation(block:()->Unit){
        busy=true;message=null
        scope.launch{
            try{withContext(Dispatchers.IO){block()};revision++;selected=null;onChanged()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){message=context.getString(R.string.file_failed)}
            finally{busy=false}
        }
    }
    LaunchedEffect(path,rootPath,revision,granted,hidden){
        rows=emptyList()
        if(!granted)return@LaunchedEffect
        loading=true
        try{
            rows=withContext(Dispatchers.IO){
                check(safeFile(root,folder))
                val list=folder.listFiles()?:error("unreadable")
                list.asSequence().filter{hidden||!it.name.startsWith(".")}.filter{safeFile(root,it)}.map{FileRow(it,it.isDirectory,if(it.isFile)it.length()else 0L,it.lastModified())}.toList()
            }
        }catch(e:CancellationException){throw e}
        catch(_:Exception){message=context.getString(R.string.file_failed)}
        finally{loading=false}
    }
    BackHandler(selected!=null||path!=rootPath||busy){if(!busy){if(selected!=null)selected=null else go(folder.parentFile?.takeIf{safeFile(root,it)}?:root)}}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(stringResource(R.string.tool_files),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            Box{
                IconButton(onClick={volumeMenu=true},enabled=!busy){Icon(Icons.Default.Menu,stringResource(R.string.file_volumes))}
                DropdownMenu(expanded=volumeMenu,onDismissRequest={volumeMenu=false}){
                    (volumes.ifEmpty{listOf(Volume(primary,"Internal storage"))}).forEach{volume->
                        DropdownMenuItem(text={Text(tr(volume.label))},onClick={rootPath=volume.root.path;go(volume.root);volumeMenu=false})
                    }
                }
            }
            Box{
                IconButton(onClick={menu=true},enabled=!busy&&granted){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(stringResource(R.string.file_new_folder))},onClick={menu=false;name="";operation="mkdir"})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_hidden))},onClick={hidden=!hidden;menu=false},trailingIcon={Checkbox(checked=hidden,onCheckedChange=null)})
                    DropdownMenuItem(text={Text(stringResource(R.string.file_sort))},onClick={sort=(sort+1)%3;menu=false})
                    DropdownMenuItem(text={Text(tr("Refresh"))},onClick={revision++;menu=false})
                }
            }
        }
        if(!granted){Text(tr("Storage access"));Button(onClick=request){Text(tr("Grant access"))};return@Column}
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={go(folder.parentFile?.takeIf{safeFile(root,it)}?:root)},enabled=path!=rootPath&&!busy){Icon(Icons.Default.ArrowBack,null)}
            Column(Modifier.weight(1f)){
                Text(if(path==rootPath)tr(volumes.firstOrNull{it.root.path==rootPath}?.label?:"Internal storage")else folder.name,style=MaterialTheme.typography.titleMedium)
                Text(folder.path,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text(stringResource(R.string.file_search))})
        clipboard?.let{clip->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(clip.file.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                TextButton(enabled=!busy,onClick={
                    runOperation{
                        check(safeFile(clip.root,clip.file)&&safeFile(root,folder)&&clip.file.isFile)
                        val target=File(folder,clip.file.name)
                        check(safeFile(root,target)&&!target.exists())
                        Files.copy(clip.file.toPath(),target.toPath())
                        if(clip.move && !clip.file.delete())error("source retained")
                        MediaScannerConnection.scanFile(context,arrayOf(target.path,clip.file.path),null,null)
                    }
                    clipboard=null
                }){Text(stringResource(R.string.file_paste))}
                TextButton(onClick={clipboard=null}){Text(tr("Cancel"))}
            }
        }
        if(loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        val shown=remember(rows,query,sort){
            val comparator=when(sort){1->compareByDescending<FileRow>{it.size};2->compareByDescending<FileRow>{it.modified};else->compareBy<FileRow>{it.file.name.lowercase()}}
            rows.filter{it.file.name.contains(query,true)}.sortedWith(compareByDescending<FileRow>{it.directory}.then(comparator))
        }
        if(shown.isEmpty()&&!loading)Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp)){
            items(shown,key={it.file.path}){row->
                Row(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={
                    if(row.directory)go(row.file)
                    else if(!openSharedFile(context,root,row.file,false))message=context.getString(R.string.file_failed)
                },onLongClick={selected=row}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(painterResource(fileIcon(Entry(row.file,row.directory,row.size,0,0))),null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){
                        Text(row.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(!row.directory)Text(Formatter.formatFileSize(context,row.size),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={selected=row},enabled=!busy){Icon(Icons.Default.MoreVert,null)}
                }
            }
        }
    }
    selected?.let{row->
        if(operation==null)AlertDialog(onDismissRequest={selected=null},title={Text(row.file.name)},text={
            Column{
                if(!row.directory){
                    TextButton(onClick={clipboard=ClipboardFile(root,row.file,false);selected=null}){Text(stringResource(R.string.file_copy))}
                    TextButton(onClick={clipboard=ClipboardFile(root,row.file,true);selected=null}){Text(stringResource(R.string.file_move))}
                    TextButton(onClick={openSharedFile(context,root,row.file,true);selected=null}){Text(tr("Share"))}
                }
                TextButton(onClick={name=row.file.name;operation="rename"}){Text(stringResource(R.string.file_rename))}
                TextButton(onClick={operation="delete"}){Text(tr("Delete"),color=MaterialTheme.colorScheme.error)}
            }
        },confirmButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})
    }
    operation?.let{op->
        val target=selected?.file
        AlertDialog(onDismissRequest={operation=null;selected=null},title={Text(stringResource(when(op){"mkdir"->R.string.file_new_folder;"rename"->R.string.file_rename;else->R.string.file_delete_confirm}))},text={
            if(op=="delete")Text(stringResource(R.string.file_delete_description,target?.name.orEmpty()))
            else OutlinedTextField(value=name,onValueChange={name=it},singleLine=true)
        },dismissButton={TextButton(onClick={operation=null;selected=null}){Text(tr("Cancel"))}},confirmButton={
            TextButton(enabled=op=="delete"||validFileName(name),onClick={
                val input=name
                operation=null;selected=null
                runOperation{
                    when(op){
                        "mkdir"->{val child=File(folder,input);check(validFileName(input)&&safeFile(root,child)&&!child.exists()&&child.mkdir())}
                        "rename"->{val source=checkNotNull(target);val dest=File(folder,input);check(validFileName(input)&&safeFile(root,source)&&safeFile(root,dest)&&!dest.exists());Files.move(source.toPath(),dest.toPath());MediaScannerConnection.scanFile(context,arrayOf(source.path,dest.path),null,null)}
                        "delete"->{val source=checkNotNull(target);check(safeFile(root,source)&&source.canonicalFile!=root.canonicalFile);Files.delete(source.toPath());MediaScannerConnection.scanFile(context,arrayOf(source.path),null,null)}
                    }
                }
            }){Text(tr(if(op=="delete")"Delete" else "Continue"))}
        })
    }
}
