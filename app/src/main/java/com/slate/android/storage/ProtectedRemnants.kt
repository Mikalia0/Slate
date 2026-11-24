// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slate.android.tr
import kotlinx.coroutines.*

@Composable
internal fun ProtectedRemnants(excluded:Set<String>,onChanged:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val user=android.os.Process.myUid()/100000
    var rows by remember{mutableStateOf<List<ProtectedCandidate>?>(null)}
    var root by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var target by remember{mutableStateOf<ProtectedCandidate?>(null)}
    var authorized by remember{mutableStateOf(shizukuGranted())}
    LaunchedEffect(Unit){while(true){authorized=shizukuGranted();delay(1500)}}
    fun allowed(row:ProtectedCandidate)=excluded.none{row.relative==it || row.relative.startsWith("$it/") || it.startsWith("${row.relative}/")}
    fun scan(useRoot:Boolean){
        root=useRoot;loading=true;message=null;rows=null
        scope.launch{
            try{
                val found=if(useRoot)withContext(Dispatchers.IO){listProtectedCandidates(user,true)}
                else withCacheService(context){parseProtectedRows(it.listProtected(user).toList())}
                rows=found.filter{allowed(it)}
            }catch(e:CancellationException){throw e}catch(_:Exception){message="Protected storage unavailable"}
            finally{loading=false}
        }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(tr("Protected remnants"),style=MaterialTheme.typography.titleMedium)
        Text(tr("Android/data and obb · only empty folders can be removed."),style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton(onClick={scan(false)},enabled=authorized&&!loading){Text("Shizuku")}
            OutlinedButton(onClick={scan(true)},enabled=!loading){Text("Root")}
        }
        if(!authorized)Text(tr("Authorize Shizuku in Settings."),style=MaterialTheme.typography.labelSmall)
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(tr(it),style=MaterialTheme.typography.bodySmall)}
        rows?.let{found->
            if(found.isEmpty())Text(tr("No candidates found"))
            LazyColumn(Modifier.weight(1f)){
                items(found,key={it.relative}){row->
                    ListItem(headlineContent={Text(row.relative.substringAfterLast('/'))},supportingContent={Text(row.relative)},
                        trailingContent={if(row.empty)TextButton(onClick={target=row},enabled=!loading){Text(tr("Delete"))}else Text(tr("Not empty"),style=MaterialTheme.typography.labelSmall)})
                }
            }
        }
    }
    target?.let{candidate->
        AlertDialog(onDismissRequest={target=null},title={Text(tr("Delete empty folder?"))},text={Text(candidate.relative)},dismissButton={TextButton(onClick={target=null}){Text(tr("Cancel"))}},confirmButton={TextButton(onClick={
            target=null;loading=true
            scope.launch{
                try{
                    val ok=allowed(candidate) && (if(root)withContext(Dispatchers.IO){removeProtectedEmpty(user,candidate.relative,true)}else withCacheService(context){it.removeEmpty(user,candidate.relative)})
                    if(ok){rows=rows?.filterNot{it==candidate};onChanged();message="Done"}else message="Folder changed or access denied"
                }catch(e:CancellationException){throw e}catch(_:Exception){message="Protected storage unavailable"}finally{loading=false}
            }
        }){Text(tr("Delete"))}})
    }
}
