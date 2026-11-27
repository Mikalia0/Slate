// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import com.slate.android.tr

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slate.android.R
import com.slate.android.device.DeviceGlyph
import java.io.File
import kotlinx.coroutines.*

@Composable
internal fun CleanupScreen(root:File,granted:Boolean,request:()->Unit,excluded:Set<String>,onBack:()->Unit,onChanged:()->Unit,review:(File)->Unit,index:()->IndexedScan?={null}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var protectedMode by remember{mutableStateOf(false)}
    var remnants by remember{mutableStateOf(false)}
    var remnantRows by remember{mutableStateOf<List<File>?>(null)}
    var kind by remember{mutableStateOf(CleanupKind.DUPLICATES)}
    var job by remember{mutableStateOf<Job?>(null)}
    var busy by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf(false)}
    var visited by remember{mutableLongStateOf(0)}
    var result by remember{mutableStateOf<CleanupResult?>(null)}
    var selected by remember{mutableStateOf(setOf<String>())}
    var confirm by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    fun scan(){
        job?.cancel();busy=true;visited=0;result=null;selected=emptySet();message=null
        val mode=kind
        val cached=index()
        job=scope.launch {
            try{
                result=withContext(Dispatchers.IO){findCleanup(root,mode,excluded,cached){n->visited=n}}
            }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Scan failed"}
            finally{busy=false}
        }
    }
    BackHandler(deleting){}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!deleting){Icon(Icons.Default.ArrowBack,"Back")}
            Text(tr("Cleanup"),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
        }
        if(!granted){
            Text(tr("Storage access required"))
            Button(onClick=request){Text(tr("Grant access"))}
        }else{
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                CleanupKind.entries.forEach{mode->FilterChip(selected=!protectedMode && !remnants && kind==mode,onClick={protectedMode=false;remnants=false;kind=mode;result=null;selected=emptySet();message=null},enabled=!busy&&!deleting,label={Text(tr(when(mode){CleanupKind.DUPLICATES->"Duplicates";CleanupKind.TEMPORARY->"Temporary";else->"Empty"}))})}
                FilterChip(selected=remnants&&!protectedMode,onClick={protectedMode=false;remnants=true;result=null;selected=emptySet();message=null},enabled=!busy&&!deleting,label={Text(tr("Remnants"))})
                FilterChip(selected=protectedMode,onClick={protectedMode=true;remnants=false},enabled=!busy&&!deleting,label={Text(tr("Protected remnants"))})
            }
            if(busy || deleting){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(deleting)tr("Deleting…") else androidx.compose.ui.res.stringResource(com.slate.android.R.string.cleanup_scanned_count,visited),style=MaterialTheme.typography.bodySmall)
                if(!deleting)TextButton(onClick={job?.cancel()}){Text(tr("Cancel"))}
            }else if(protectedMode){
                Column(Modifier.weight(1f)){ProtectedRemnants(excluded,onChanged)}
            }else if(remnants){
                Text(tr("Possible app remnants"),style=MaterialTheme.typography.titleMedium)
                Text(tr("Folders in Android/media with no installed package match. Review before removing."),style=MaterialTheme.typography.bodySmall)
                Button(onClick={busy=true;message=null;job=scope.launch{try{remnantRows=findMediaRemnants(context,root,excluded)}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Scan failed"}finally{busy=false}}}){Text(tr("Find"))}
                remnantRows?.let{rows->
                    if(rows.isEmpty())Text(tr("No candidates found"))
                    LazyColumn(Modifier.weight(1f)){
                        items(rows,key={it.path}){folder->ListItem(headlineContent={Text(folder.name)},supportingContent={Text(folder.relativeTo(root).path)},trailingContent={TextButton(onClick={review(folder)}){Text(tr("Review"))}});HorizontalDivider()}
                    }
                }
            }else{
                val data=result
                if(data==null){
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(32.dp))
                            Text(tr(kind.title),style=MaterialTheme.typography.titleLarge)
                            Text(tr(when(kind){CleanupKind.DUPLICATES->"Compare file contents.";CleanupKind.TEMPORARY->"Temporary extensions, unchanged for 7 days.";else->"Folders without files."}),style=MaterialTheme.typography.bodyMedium)
                            Button(onClick={scan()},modifier=Modifier.fillMaxWidth()){Text(tr("Find"))}
                        }
                    }
                }else{
                    val chosen=data.items.filter{it.file.path in selected}
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.cleanup_candidates,data.items.size),style=MaterialTheme.typography.titleMedium)
                            if(data.limited)Text(tr("Scan limit reached"),style=MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick={scan()}){Text(tr("Refresh"))}
                    }
                    if(kind==CleanupKind.DUPLICATES)Text(tr("Keep at least one copy in each group."),style=MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp)){
                        items(data.items,key={it.file.path}){item->
                            val checked=item.file.path in selected
                            val canSelect=kind!=CleanupKind.DUPLICATES || checked || data.items.any{it.group==item.group && it.file!=item.file && it.file.path !in selected}
                            ListItem(headlineContent={Text(item.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                                supportingContent={Text(item.file.relativeTo(root).path,maxLines=2,overflow=TextOverflow.Ellipsis)},
                                leadingContent={Checkbox(checked=checked,enabled=canSelect,onCheckedChange={selected=if(it)selected+item.file.path else selected-item.file.path})},
                                trailingContent={Text(Formatter.formatFileSize(context,item.bytes))})
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Button(onClick={confirm=true},enabled=chosen.isNotEmpty(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer)){
                        Icon(Icons.Default.Delete,null);Spacer(Modifier.width(8.dp));Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.cleanup_delete_size,chosen.size,Formatter.formatFileSize(context,chosen.sumOf{it.bytes})))
                    }
                    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.cleanup_confirm,chosen.size))},text={Text(tr("This cannot be undone."))},dismissButton={TextButton(onClick={confirm=false}){Text(tr("Cancel"))}},confirmButton={TextButton(onClick={
                        confirm=false;deleting=true
                        scope.launch{
                            try{
                                val done=withContext(Dispatchers.IO){deleteCleanup(root,chosen,kind)}
                                message=context.getString(R.string.cleanup_result,done.first,done.second)
                                result=null;selected=emptySet();onChanged()
                            }finally{deleting=false}
                        }
                    }){Text(tr("Delete"),color=MaterialTheme.colorScheme.error)}})
                }
            }
            message?.let{Text(tr(it),style=MaterialTheme.typography.bodyMedium)}
        }
    }
}
