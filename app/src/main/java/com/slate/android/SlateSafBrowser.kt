// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.format.Formatter
import android.text.format.DateUtils
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slate.android.storage.fileCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class SafEntry(val uri:Uri,val name:String,val directory:Boolean,val bytes:Long?,val modified:Long,val mime:String,val flags:Int){
    fun supports(flag:Int)=flags and flag!=0
}
private data class SafListing(val title:String,val flags:Int,val entries:List<SafEntry>)
private val safCache=object:LruCache<String,SafListing>(6000){
    override fun sizeOf(key:String,value:SafListing)=value.entries.size.coerceAtLeast(1)
}
private val safThumbSlots=Semaphore(2)
private val safThumbnails=object:LruCache<String,android.graphics.Bitmap>(8*1024*1024){
    override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount
}
private fun safId(tree:Uri,current:Uri)=if(tree==current)DocumentsContract.getTreeDocumentId(tree)else DocumentsContract.getDocumentId(current)
private fun safList(context:Context,tree:Uri,current:Uri):SafListing{
    val resolver=context.contentResolver
    val parentId=safId(tree,current)
    val parent=DocumentsContract.buildDocumentUriUsingTree(tree,parentId)
    val parentInfo=resolver.query(parent,arrayOf(Document.COLUMN_DISPLAY_NAME,Document.COLUMN_FLAGS),null,null,null)?.use{if(it.moveToFirst())it.getString(0).orEmpty() to it.getInt(1) else null}?:error("Folder unavailable")
    val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
    val columns=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS)
    val rows=resolver.query(children,columns,null,null,null)?.use{cursor->
        buildList<SafEntry>{
            while(cursor.moveToNext()){
                val uri=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0))
                val mime=cursor.getString(2)?:"application/octet-stream"
                add(SafEntry(uri,cursor.getString(1)?:"—",mime==Document.MIME_TYPE_DIR,if(cursor.isNull(3))null else cursor.getLong(3),if(cursor.isNull(4))0L else cursor.getLong(4),mime,cursor.getInt(5)))
                require(size<=100000){"Too many documents"}
            }
        }
    }?:error("Provider unavailable")
    return SafListing(parentInfo.first,parentInfo.second,rows)
}
@Composable private fun SafIcon(item:SafEntry,modifier:Modifier){
    val context=LocalContext.current
    val ext=item.name.substringAfterLast('.', "").lowercase()
    val key=item.uri.toString()+"|"+item.modified+"|"+item.bytes
    val bitmap by produceState(safThumbnails.get(key),key){
        if(value==null&&!item.directory&&item.supports(Document.FLAG_SUPPORTS_THUMBNAIL)&&android.os.Build.VERSION.SDK_INT>=29){
            value=safThumbSlots.withPermit{withContext(Dispatchers.IO){
                runCatching{context.contentResolver.loadThumbnail(item.uri,android.util.Size(96,96),null)}.getOrNull()?.also{safThumbnails.put(key,it)}
            }}
        }
    }
    BrowserEntryIcon(item.directory,ext,modifier){
        val image=bitmap
        if(image!=null)androidx.compose.foundation.Image(image.asImageBitmap(),null,Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        else Icon(painterResource(if(item.directory)R.drawable.ic_storage_folder else categoryIcon(fileCategory(ext))),null,Modifier.size(23.dp),tint=if(item.directory)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun SlateSafBrowser(tree:Uri,onClose:()->Unit,onForget:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val compact=useTopNavigation()
    var stack by rememberSaveable(tree){mutableStateOf(arrayListOf(tree.toString()))}
    var labels by rememberSaveable(tree){mutableStateOf(arrayListOf(""))}
    var grid by rememberSaveable(tree){mutableStateOf(context.getSharedPreferences("file_manager",0).getBoolean("grid",false))}
    var sort by rememberSaveable{mutableIntStateOf(0)}
    var descending by rememberSaveable{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    var query by rememberSaveable{mutableStateOf("")}
    var searching by rememberSaveable{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var forget by remember{mutableStateOf(false)}
    var sorting by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())}
    var action by remember{mutableStateOf<String?>(null)}
    var name by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    val current=Uri.parse(stack.last())
    val cacheKey=tree.toString()+"|"+current
    val grant=context.contentResolver.persistedUriPermissions.firstOrNull{it.uri==tree}
    val writable=grant?.isWritePermission==true
    var listing by remember(cacheKey){mutableStateOf(if(grant?.isReadPermission==true)safCache.get(cacheKey)else null)}
    var loading by remember(cacheKey){mutableStateOf(true)}
    var failed by remember(cacheKey){mutableStateOf(false)}
    val listState=key(cacheKey){rememberLazyListState()}
    val gridState=key(cacheKey){rememberLazyGridState()}
    fun jump(index:Int){
        stack=ArrayList(stack.take(index+1));labels=ArrayList(labels.take(index+1));query="";selected=emptySet()
    }
    fun back(){if(busy)return;if(selected.isNotEmpty())selected=emptySet()else if(searching){searching=false;query=""}else if(stack.size>1)jump(stack.lastIndex-1)else onClose()}
    BackHandler{back()}
    LaunchedEffect(cacheKey,revision){
        loading=true;failed=false
        if(grant?.isReadPermission!=true){listing=null;loading=false;failed=true;return@LaunchedEffect}
        try{
            val data=withContext(Dispatchers.IO){safList(context,tree,current)}
            listing=data;safCache.put(cacheKey,data)
            labels=ArrayList(labels).apply{set(lastIndex,data.title)}
        }catch(e:CancellationException){throw e}catch(_:Exception){failed=true}
        finally{loading=false}
    }
    val entries=listing?.entries.orEmpty()
    val shown=remember(entries,query,sort,descending){
        val comparator=when(sort){1->compareBy<SafEntry>{it.bytes?:-1L};2->compareBy<SafEntry>{it.modified};3->compareBy<SafEntry>{it.name.substringAfterLast('.', "").lowercase()};else->compareBy<SafEntry>{it.name.lowercase()}}
        entries.filter{it.name.contains(query,true)}.sortedWith(compareByDescending<SafEntry>{it.directory}.then(if(descending)comparator.reversed()else comparator))
    }
    val chosen=entries.filter{it.uri.toString() in selected}
    val single=chosen.singleOrNull()
    val canCreate=writable&&listing?.let{it.flags and Document.FLAG_DIR_SUPPORTS_CREATE!=0}==true&&!failed
    fun runTask(block:suspend ()->Unit){
        if(busy)return
        busy=true;status=null
        scope.launch{
            try{withContext(Dispatchers.IO){block()};selected=emptySet();revision++}
            catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:context.getString(R.string.file_failed)}
            finally{busy=false}
        }
    }
    var copySource by remember{mutableStateOf<SafEntry?>(null)}
    val copyPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){dest->
        val source=copySource;copySource=null
        if(dest!=null&&source!=null)runTask{
            checkNotNull(context.contentResolver.openInputStream(source.uri)).use{input->checkNotNull(context.contentResolver.openOutputStream(dest,"wt")).use{out->
                val buffer=ByteArray(65536)
                while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}
            }}
        }
    }
    fun tap(item:SafEntry){
        if(selected.isNotEmpty())selected=if(item.uri.toString() in selected)selected-item.uri.toString()else selected+item.uri.toString()
        else if(item.directory){stack=ArrayList(stack+item.uri.toString());labels=ArrayList(labels+item.name);query=""}
        else runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),null))}.onFailure{status=context.getString(R.string.file_failed)}
    }
    val tools:@Composable ()->Unit={
        IconButton(enabled=!busy,onClick={searching=!searching;query=""}){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
        IconButton(enabled=!busy,onClick={grid=!grid;context.getSharedPreferences("file_manager",0).edit().putBoolean("grid",grid).apply()}){Icon(painterResource(if(grid)R.drawable.ic_browser_list else R.drawable.ic_browser_grid),stringResource(R.string.file_view),Modifier.size(23.dp))}
        IconButton(enabled=canCreate&&!busy,onClick={name="";action="mkdir"}){Icon(Icons.Default.Add,stringResource(R.string.file_new_folder))}
        IconButton(enabled=!busy,onClick={revision++}){Icon(Icons.Default.Refresh,tr("Refresh"))}
        IconButton(enabled=!busy,onClick={sorting=true}){Icon(painterResource(R.drawable.ic_browser_sort),stringResource(R.string.file_sort),Modifier.size(23.dp))}
        IconButton(enabled=!busy&&shown.isNotEmpty(),onClick={val all=shown.map{it.uri.toString()}.toSet();selected=if(selected==all)emptySet()else all}){Icon(painterResource(R.drawable.ic_browser_select),stringResource(R.string.cache_select_all))}
    }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(enabled=!busy,onClick={back()}){Icon(if(selected.isNotEmpty())Icons.Default.Close else Icons.Default.ArrowBack,tr("Back"))}
            if(selected.isNotEmpty()){
                Text(selected.size.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                if(single!=null&&!single.directory)IconButton(enabled=!busy,onClick={copySource=single;copyPicker.launch(single.name)}){Icon(painterResource(R.drawable.ic_browser_copy),stringResource(R.string.file_copy))}
                if(single!=null&&writable&&single.supports(Document.FLAG_SUPPORTS_RENAME))IconButton(enabled=!busy&&!failed,onClick={name=single.name;action="rename"}){Icon(Icons.Default.Edit,stringResource(R.string.file_rename))}
                if(writable&&chosen.isNotEmpty()&&chosen.all{it.supports(Document.FLAG_SUPPORTS_DELETE)})IconButton(enabled=!busy&&!failed,onClick={action="delete"}){Icon(Icons.Default.Delete,tr("Delete"))}
            }else if(searching)TextField(query,{query=it},Modifier.weight(1f),singleLine=true,placeholder={Text(stringResource(R.string.file_search))})
            else Column(Modifier.weight(1f)){
                Text(listing?.title?.takeIf{it.isNotBlank()}?:labels.last().ifBlank{stringResource(R.string.browser_document_source)},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.browser_counts,shown.count{it.directory},shown.count{!it.directory}),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(compact&&selected.isEmpty()&&!searching&&androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=620)tools()
            Box{
                IconButton(enabled=!busy,onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    if(compact){DropdownMenuItem(text={Text(stringResource(R.string.file_search))},onClick={menu=false;searching=!searching;query=""});DropdownMenuItem(text={Text(stringResource(R.string.file_view))},onClick={menu=false;grid=!grid});DropdownMenuItem(text={Text(stringResource(R.string.file_sort))},onClick={menu=false;sorting=true});if(canCreate)DropdownMenuItem(text={Text(stringResource(R.string.file_new_folder))},onClick={menu=false;name="";action="mkdir"})}
                    DropdownMenuItem(text={Text(tr("Refresh"))},onClick={menu=false;revision++})
                    DropdownMenuItem(text={Text(stringResource(R.string.browser_forget_source))},onClick={menu=false;forget=true})
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            labels.forEachIndexed{index,label->
                if(index>0)Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp))
                TextButton(enabled=!busy,onClick={jump(index)},contentPadding=PaddingValues(horizontal=6.dp)){Text(label.ifBlank{stringResource(R.string.browser_document_source)},maxLines=1)}
            }
        }
        DelayedBrowserProgress(loading||busy)
        if(failed)Text(stringResource(R.string.file_failed),Modifier.padding(horizontal=12.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        status?.let{Text(it,Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)}
        if(!loading&&shown.isEmpty())Text(stringResource(R.string.file_empty),Modifier.padding(14.dp))
        if(grid)LazyVerticalGrid(columns=GridCells.Adaptive(105.dp),state=gridState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            items(shown,key={it.uri.toString()}){item->
                Surface(shape=MaterialTheme.shapes.medium,color=if(item.uri.toString() in selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface){
                    Column(Modifier.fillMaxWidth().combinedClickable(enabled=!busy,onClick={tap(item)},onLongClick={selected=selected+item.uri.toString()}).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        SafIcon(item,Modifier.size(40.dp));Text(item.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                        item.bytes?.takeIf{!item.directory}?.let{Text(Formatter.formatFileSize(context,it),style=MaterialTheme.typography.labelSmall)}
                    }
                }
            }
        }else LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(vertical=2.dp)){
            items(shown,key={it.uri.toString()}){item->
                val date=if(item.modified>0)DateUtils.getRelativeTimeSpanString(item.modified,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS,DateUtils.FORMAT_ABBREV_RELATIVE).toString()else "—"
                val meta=item.bytes?.takeIf{!item.directory}?.let{Formatter.formatFileSize(context,it)}
                BrowserEntryRow(item.name,date,meta,item.uri.toString() in selected,compact,Modifier.combinedClickable(enabled=!busy,onClick={tap(item)},onLongClick={selected=selected+item.uri.toString()})){SafIcon(item,Modifier.size(if(compact)32.dp else 40.dp))}
            }
        }
        if(!compact)Surface(color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){tools()}}
    }
    if(sorting)AlertDialog(onDismissRequest={sorting=false},title={Text(stringResource(R.string.file_sort))},text={
        Column{
            listOf(R.string.connection_sort_name,R.string.connection_sort_size,R.string.connection_sort_date,R.string.connection_sort_type).forEachIndexed{index,res->Row(Modifier.fillMaxWidth().clickable{sort=index;sorting=false},verticalAlignment=Alignment.CenterVertically){RadioButton(sort==index,onClick={sort=index;sorting=false});Text(stringResource(res))}}
            Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.connection_descending),Modifier.weight(1f));Switch(descending,{descending=it})}
        }
    },confirmButton={TextButton(onClick={sorting=false}){Text(tr("Close"))}})
    action?.let{op->
        AlertDialog(onDismissRequest={action=null},title={Text(when(op){"mkdir"->stringResource(R.string.file_new_folder);"rename"->stringResource(R.string.file_rename);else->tr("Delete")})},text={
            if(op=="delete")Text(chosen.joinToString("\n"){it.name})else OutlinedTextField(name,{name=it},singleLine=true)
        },confirmButton={TextButton(enabled=!busy&&!failed&&(op=="delete"||validFileName(name)),onClick={
            val targets=chosen.toList();val input=name;action=null
            runTask{
                val r=context.contentResolver
                when(op){
                    "mkdir"->{require(canCreate);checkNotNull(DocumentsContract.createDocument(r,DocumentsContract.buildDocumentUriUsingTree(tree,safId(tree,current)),Document.MIME_TYPE_DIR,input))}
                    "rename"->{val item=targets.single();require(writable&&item.supports(Document.FLAG_SUPPORTS_RENAME));checkNotNull(DocumentsContract.renameDocument(r,item.uri,input))}
                    "delete"->{require(writable&&targets.all{it.supports(Document.FLAG_SUPPORTS_DELETE)});targets.forEach{check(DocumentsContract.deleteDocument(r,it.uri))}}
                }
            }
        }){Text(stringResource(android.R.string.ok))}},dismissButton={TextButton(onClick={action=null}){Text(tr("Cancel"))}})
    }
    if(forget)AlertDialog(onDismissRequest={forget=false},title={Text(stringResource(R.string.browser_forget_source))},text={Text(stringResource(R.string.browser_forget_source_desc))},confirmButton={TextButton(onClick={safCache.evictAll();safThumbnails.evictAll();onForget()}){Text(tr("Delete"))}},dismissButton={TextButton(onClick={forget=false}){Text(tr("Cancel"))}})
}
