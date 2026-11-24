// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
internal fun CleanupScreen(root:File,granted:Boolean,request:()->Unit,onBack:()->Unit,onChanged:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
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
        job=scope.launch {
            try{
                result=withContext(Dispatchers.IO){findCleanup(root,mode,emptySet()){n->visited=n}}
            }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Scan failed"}
            finally{busy=false}
        }
    }
    BackHandler(deleting){}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!deleting){Icon(Icons.Default.ArrowBack,"Back")}
            Text("Cleanup",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
        }
        if(!granted){
            Text("Storage access required")
            Button(onClick=request){Text("Grant access")}
        }else{
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                CleanupKind.entries.forEach{mode->FilterChip(selected=kind==mode,onClick={kind=mode;result=null;selected=emptySet();message=null},enabled=!busy&&!deleting,label={Text(when(mode){CleanupKind.DUPLICATES->"Duplicates";CleanupKind.TEMPORARY->"Temporary";else->"Empty"})})}
            }
            if(busy || deleting){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(deleting)"Deleting…" else "$visited items scanned",style=MaterialTheme.typography.bodySmall)
                if(!deleting)TextButton(onClick={job?.cancel()}){Text("Cancel")}
            }else{
                val data=result
                if(data==null){
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(32.dp))
                            Text(kind.title,style=MaterialTheme.typography.titleLarge)
                            Text(when(kind){CleanupKind.DUPLICATES->"Compare file contents.";CleanupKind.TEMPORARY->"Temporary extensions, unchanged for 7 days.";else->"Folders without files."},style=MaterialTheme.typography.bodyMedium)
                            Button(onClick={scan()},modifier=Modifier.fillMaxWidth()){Text("Find")}
                        }
                    }
                }else{
                    val chosen=data.items.filter{it.file.path in selected}
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("${data.items.size} candidates",style=MaterialTheme.typography.titleMedium)
                            if(data.limited)Text("Scan limit reached",style=MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick={scan()}){Text("Refresh")}
                    }
                    if(kind==CleanupKind.DUPLICATES)Text("Keep at least one copy in each group.",style=MaterialTheme.typography.labelSmall)
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
                        Icon(Icons.Default.Delete,null);Spacer(Modifier.width(8.dp));Text("Delete ${chosen.size} · "+Formatter.formatFileSize(context,chosen.sumOf{it.bytes}))
                    }
                    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Delete ${chosen.size} items?")},text={Text("This cannot be undone.")},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}},confirmButton={TextButton(onClick={
                        confirm=false;deleting=true
                        scope.launch{
                            try{
                                val done=withContext(Dispatchers.IO){deleteCleanup(root,chosen,kind)}
                                message="${done.first} deleted · ${done.second} skipped"
                                result=null;selected=emptySet();onChanged()
                            }finally{deleting=false}
                        }
                    }){Text("Delete",color=MaterialTheme.colorScheme.error)}})
                }
            }
            message?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
        }
    }
}
