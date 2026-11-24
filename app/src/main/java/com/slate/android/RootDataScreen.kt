// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.slate.android.storage.*
import kotlinx.coroutines.*

@Composable
internal fun RootDataScreen(onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var result by remember{mutableStateOf<RootDataResult?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    var scanned by remember{mutableLongStateOf(0)}
    var error by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    var detail by remember{mutableStateOf<RootFile?>(null)}
    var query by remember{mutableStateOf("")}
    var search by remember{mutableStateOf(false)}
    var ascending by remember{mutableStateOf(false)}
    val running=job!=null
    fun start(){
        if(job!=null)return
        scanned=0;error=false;result=null
        job=scope.launch{
            try{result=scanRootData{n->scanned=n}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error=true}
            finally{job=null}
        }
    }
    fun back(){job?.cancel();onBack()}
    BackHandler{if(search){search=false;query=""}else back()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=::back){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Column(Modifier.weight(1f)){
                Text(stringResource(R.string.root_data_title),style=MaterialTheme.typography.headlineSmall)
                Text("/data · root",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(result!=null)IconButton(onClick={search=!search;if(!search)query=""}){Icon(Icons.Default.Search,tr("Search"))}
            IconButton(onClick={confirm=true},enabled=!running){Icon(Icons.Default.Refresh,tr("Refresh"))}
        }
        if(running){
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(R.string.root_data_progress,scanned),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={job?.cancel()}){Text(tr("Cancel"))}
            }
        }else if(result==null){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(stringResource(if(error)R.string.root_data_denied else R.string.root_data_explain),style=MaterialTheme.typography.bodyMedium,color=if(error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.root_data_scan))}
            }
        }
        result?.let{data->
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(R.string.root_data_count,data.files.size,data.scanned),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={ascending=!ascending}){Text(tr(if(ascending)"Size ↑" else "Size ↓"))}
            }
            if(data.incomplete)Text(stringResource(R.string.root_data_partial),Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if(search)OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text(tr("Search files"))},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp))
            val visible=remember(data,query,ascending){data.files.filter{it.path.contains(query,true)}.let{if(ascending)it.asReversed()else it}}
            val max=data.files.firstOrNull()?.bytes?:0
            LazyColumn(Modifier.weight(1f)){
                items(visible,key={it.path}){file->
                    val fraction=if(max>0)(file.bytes.toDouble()/max).toFloat().coerceIn(0f,1f)else 0f
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable{detail=file}){
                        Box(Modifier.matchParentSize()){Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary.copy(alpha=.16f)))}
                        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(painterResource(R.drawable.ic_storage_file),null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)){
                                Text(file.path.substringAfterLast('/'),style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(file.path.substringBeforeLast('/'),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Formatter.formatFileSize(context,file.bytes),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(thickness=.5.dp)
                }
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.root_data_title))},text={Text(stringResource(R.string.root_data_explain))},dismissButton={TextButton(onClick={confirm=false}){Text(tr("Cancel"))}},confirmButton={TextButton(onClick={confirm=false;start()}){Text(stringResource(R.string.root_data_scan))}})
    detail?.let{file->AlertDialog(onDismissRequest={detail=null},title={Text(file.path.substringAfterLast('/'))},text={Text(file.path+"\n\n"+Formatter.formatFileSize(context,file.bytes))},dismissButton={TextButton(onClick={detail=null}){Text(tr("Close"))}},confirmButton={TextButton(onClick={
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Path",file.path));detail=null
    }){Text(stringResource(R.string.root_data_copy))}})}
}
