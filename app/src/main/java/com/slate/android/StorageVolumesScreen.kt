// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import com.slate.android.storage.Volume
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class VolumeReading(val volume:Volume,val capacity:Long,val available:Long)
@Composable
internal fun StorageVolumesScreen(primary:File,volumes:List<Volume>,history:StorageHistory,onBack:()->Unit,onOpen:(File)->Unit){
    val context=LocalContext.current
    val listed=remember(primary,volumes){(listOf(Volume(primary,"Internal storage"))+volumes).distinctBy{it.root.absolutePath}}
    var refresh by remember{mutableIntStateOf(0)}
    val readings by produceState<List<VolumeReading>?>(null,listed,refresh){
        value=withContext(Dispatchers.IO){listed.map{v->
            val total=runCatching{v.root.totalSpace}.getOrDefault(0L).coerceAtLeast(0)
            val free=runCatching{v.root.usableSpace}.getOrDefault(0L).coerceIn(0,total)
            VolumeReading(v,total,free)
        }}
    }
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Text(stringResource(R.string.volume_title),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,tr("Refresh"))}
        }
        if(readings==null)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            items(readings.orEmpty(),key={it.volume.root.absolutePath}){reading->
                val volume=reading.volume
                val total=reading.capacity
                val used=(total-reading.available).coerceAtLeast(0)
                val ratio=if(total>0)(used.toDouble()/total).toFloat().coerceIn(0f,1f)else 0f
                val fmt:(Long)->String={Formatter.formatFileSize(context,it)}
                Card(onClick={onOpen(volume.root)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            Icon(painterResource(R.drawable.ic_device_storage),null,Modifier.size(27.dp),tint=MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)){
                                Text(tr(volume.label),style=MaterialTheme.typography.titleMedium)
                                Text(volume.root.absolutePath,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight,null)
                        }
                        if(total>0){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text(fmt(used),style=MaterialTheme.typography.headlineSmall)
                                Text(" / "+fmt(total),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                Text("${(ratio*100).toInt()}%",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(progress={ratio},modifier=Modifier.fillMaxWidth().height(8.dp))
                            Text(stringResource(R.string.volume_available,fmt(reading.available)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(volume.root==primary)history.sessionChange?.takeIf{it!=0L}?.let{delta->
                                HorizontalDivider()
                                Text(stringResource(R.string.volume_change,(if(delta>0)"+"else "−")+fmt(kotlin.math.abs(delta))),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }else Text(stringResource(R.string.volume_unavailable),style=MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.volume_analyze),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
