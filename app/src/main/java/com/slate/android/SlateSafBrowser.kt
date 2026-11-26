// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SafEntry(val doc:DocumentFile,val name:String,val directory:Boolean,val bytes:Long,val mime:String)
@Composable
internal fun SlateSafBrowser(tree:Uri,onClose:()->Unit,onForget:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var stack by remember(tree){mutableStateOf<List<Uri>>(listOf(tree))}
    var title by remember(tree){mutableStateOf("")}
    var entries by remember(tree){mutableStateOf<List<SafEntry>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")}
    var searching by remember{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var forget by remember{mutableStateOf(false)}
    val current=stack.last()
    fun back(){if(searching){searching=false;query=""}else if(stack.size>1){stack=stack.dropLast(1);query=""}else onClose()}
    BackHandler{back()}
    LaunchedEffect(current,revision){
        loading=true;failed=false;entries=emptyList()
        try{
            val result=withContext(Dispatchers.IO){
                val doc=if(stack.size==1)DocumentFile.fromTreeUri(context,tree)else DocumentFile.fromTreeUri(context,tree)?.let{
                    // Build a tree-scoped URI for the selected child, retaining the original grant.
                    val id=android.provider.DocumentsContract.getDocumentId(current)
                    DocumentFile.fromSingleUri(context,android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,id))
                }
                val directory=checkNotNull(doc)
                val parentId=if(stack.size==1)android.provider.DocumentsContract.getTreeDocumentId(tree) else android.provider.DocumentsContract.getDocumentId(current)
                val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
                val result=mutableListOf<SafEntry>()
                val columns=arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,android.provider.DocumentsContract.Document.COLUMN_SIZE)
                context.contentResolver.query(children,columns,null,null,null)?.use{cursor->
                    while(cursor.moveToNext()){
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val uri=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0))
                        val child=DocumentFile.fromSingleUri(context,uri)?:continue
                        val mime=cursor.getString(2)?:"application/octet-stream"
                        result.add(SafEntry(child,cursor.getString(1)?:"—",mime==android.provider.DocumentsContract.Document.MIME_TYPE_DIR,if(cursor.isNull(3))0 else cursor.getLong(3),mime))
                    }
                }?:error("Provider unavailable")
                directory.name.orEmpty() to result.sortedWith(compareByDescending<SafEntry>{it.directory}.thenBy{it.name.lowercase()})
            }
            title=result.first;entries=result.second
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){failed=true}
        finally{loading=false}
    }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={back()}){Icon(Icons.Default.ArrowBack,tr("Back"))}
            if(searching)TextField(query,{query=it},Modifier.weight(1f),singleLine=true,placeholder={Text(stringResource(R.string.file_search))})
            else Column(Modifier.weight(1f)){
                Text(title.ifBlank{stringResource(R.string.browser_document_source)},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.file_items,entries.size),style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={searching=!searching;query=""}){Icon(Icons.Default.Search,stringResource(R.string.file_search))}
            Box{
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(tr("Refresh"))},onClick={menu=false;revision++})
                    DropdownMenuItem(text={Text(stringResource(R.string.browser_forget_source))},onClick={menu=false;forget=true})
                }
            }
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed)Text(stringResource(R.string.file_failed),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f)){
            items(entries.filter{it.name.contains(query,true)},key={it.doc.uri.toString()}){item->
                Row(Modifier.fillMaxWidth().clickable{
                    if(item.directory){stack=stack+item.doc.uri;query=""}
                    else runCatching{
                        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(item.doc.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent,null))
                    }.onFailure{failed=true}
                }.heightIn(min=52.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(painterResource(if(item.directory)R.drawable.ic_storage_folder else R.drawable.ic_storage_file),null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(item.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(!item.directory)Text(Formatter.formatFileSize(context,item.bytes),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
            IconButton(onClick=onClose){Icon(Icons.Default.Home,stringResource(R.string.browser_home))}
            IconButton(onClick={stack=listOf(tree);query=""}){Icon(Icons.Default.ArrowBack,tr("Back"))}
            IconButton(onClick={revision++}){Icon(Icons.Default.Refresh,tr("Refresh"))}
        }
    }
    if(forget)AlertDialog(onDismissRequest={forget=false},title={Text(stringResource(R.string.browser_forget_source))},text={Text(stringResource(R.string.browser_forget_source_desc))},confirmButton={TextButton(onClick=onForget){Text(tr("Delete"))}},dismissButton={TextButton(onClick={forget=false}){Text(tr("Cancel"))}})
}
