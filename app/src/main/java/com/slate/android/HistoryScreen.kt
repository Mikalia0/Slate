// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
internal fun HistoryScreen(history:StorageHistory,onBack:()->Unit) {
    val context=LocalContext.current
    val fmt:(Long)->String={Formatter.formatFileSize(context,it)}
    var selected by remember{mutableStateOf<ScanRecord?>(null)}
    var confirm by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Text(tr("Scan history"),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            TextButton(onClick={confirm=true},enabled=history.records.isNotEmpty()){Text(tr("Clear"))}
        }
        if(history.records.isEmpty())Text(tr("No completed scans yet."),style=MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)){
            items(history.records,key={it.at}){record->
                Card(onClick={selected=record},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(record.at)),style=MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Text(fmt(record.bytes),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                            record.delta?.let{Text((if(it>=0)"+" else "−")+fmt(abs(it)),color=MaterialTheme.colorScheme.primary)}
                        }
                        Text("${record.files} files · ${record.skipped} skipped",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    selected?.let{record->AlertDialog(onDismissRequest={selected=null},title={Text(tr("Folder changes"))},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            val changes=record.folders.filter{it.increase!=null}.sortedByDescending{abs(it.increase ?: 0)}.take(8)
            if(changes.isEmpty())Text(tr("No comparable previous folders."))
            changes.forEach{item->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(item.name,Modifier.weight(1f))
                    val diff=item.increase ?: 0
                    Text((if(diff>=0)"+" else "−")+fmt(abs(diff)),color=MaterialTheme.colorScheme.primary)
                }
            }
        }
    },confirmButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Clear scan history?"))},text={Text(tr("Files are not affected."))},dismissButton={TextButton(onClick={confirm=false}){Text(tr("Cancel"))}},confirmButton={TextButton(onClick={history.clear();confirm=false}){Text(tr("Clear"))}})
}
