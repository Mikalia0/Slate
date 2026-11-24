// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.slate.android.device.ManufacturerIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.slate.android.device.DeviceReader
import com.slate.android.device.LiveInfo
import com.slate.android.device.StaticInfo
import com.slate.android.device.DeviceStatus
import com.slate.android.device.DeviceGlyph
import com.slate.android.device.powerReading
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slate.android.storage.Volume
import java.io.File

@Composable
internal fun HomeScreen(primary: File, volumes: List<Volume>, analyze: (File) -> Unit, device: () -> Unit, history: StorageHistory, review: (File)->Unit, status: DeviceStatus, openDevicePage: (Int)->Unit) {
    val context = LocalContext.current
    val capacity = primary.totalSpace
    val free = primary.usableSpace
    val used = (capacity-free).coerceAtLeast(0)
    val fraction = if(capacity>0) (used.toDouble()/capacity).toFloat().coerceIn(0f,1f) else 0f
    val fmt: (Long)->String = { Formatter.formatFileSize(context,it) }
    val live=status.live
    val fixed=status.fixed
    val ram=live?.meters?.firstOrNull{it.title=="Memory"}
    val battery=live?.meters?.firstOrNull{it.title=="Battery"}
    val temperature=live?.battery?.rows?.firstOrNull{it.label=="Temperature"}?.value ?: "—"
    val cpu=fixed?.hardware?.firstOrNull()?.rows?.firstOrNull{it.label=="SoC"}?.value?.takeUnless{it=="Unavailable"} ?: fixed?.hardware?.firstOrNull()?.rows?.firstOrNull{it.label=="Hardware"}?.value
    val gpu=fixed?.hardware?.getOrNull(1)?.rows?.firstOrNull{it.label=="GPU renderer"}?.value
    val display=live?.display?.rows?.joinToString(" · "){it.value}
    val network=live?.connection?.rows?.firstOrNull{it.label=="Connection"}?.value
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Internal storage",style=MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(fmt(used),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                        Text("${(fraction*100).toInt()}%",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Text(fmt(free)+" available · "+fmt(capacity)+" total",style=MaterialTheme.typography.bodySmall)
                    Button(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text(if(history.scannedAt>0)"View storage" else "Analyze storage")
                    }
                }
            }
        }
        item {
            Card(onClick=device,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ManufacturerIcon()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Build.MANUFACTURER.replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelMedium)
                            Text(Build.MODEL,style=MaterialTheme.typography.titleMedium)
                            Text("Android "+Build.VERSION.RELEASE,style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.KeyboardArrowRight,null)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        HomeGauge("RAM",ram?.fraction ?: 0f,ram?.fraction?.let{"${(it*100).toInt()}%"} ?: "—",Modifier.weight(1f))
                        HomeGauge("Battery",battery?.fraction ?: 0f,battery?.value ?: "—",Modifier.weight(1f))
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(56.dp),contentAlignment=Alignment.Center) {
                                Text(temperature,style=MaterialTheme.typography.titleMedium)
                            }
                            Text("Battery temp.",style=MaterialTheme.typography.labelSmall)
                            live?.battery.powerReading()?.takeIf{it.label=="Charging power" && it.value!="Unavailable"}?.let{Text(it.value,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
                        }
                    }
                }
            }
        }
        item {
            Card(onClick=device,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        DashboardValue("CPU",cpu ?: "—",Modifier.weight(1f),R.drawable.ic_device_cpu)
                        DashboardValue("GPU",gpu?.removeSuffix(" (TM)") ?: "—",Modifier.weight(1f),R.drawable.ic_device_gpu)
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        DashboardValue("Display",display ?: "—",Modifier.weight(1f),R.drawable.ic_device_display)
                        DashboardValue("Network",network ?: "—",Modifier.weight(1f),R.drawable.ic_device_network)
                    }
                    live?.uptime?.rows?.firstOrNull{it.label=="Since boot"}?.let {
                        Text("Uptime · "+it.value,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val change=history.sessionChange
        if(change!=null && kotlin.math.abs(change)>=10L*1024*1024) item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Since last session",style=MaterialTheme.typography.labelMedium)
                        Text((if(change>0)"+" else "−")+fmt(kotlin.math.abs(change)),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    Text(if(change>0)"Used space increased" else "Space recovered",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Storage insights",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                if(history.scannedAt>0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(history.scannedAt)),style=MaterialTheme.typography.labelSmall)
            }
        }
        if(history.scannedAt==0L) item {
            Card(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) {
                ListItem(headlineContent={Text("Analyze storage for insights")},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)})
            }
        } else {
            val growing=history.insights.filter{(it.increase ?: 0)>=50L*1024*1024}.sortedByDescending{it.increase}.take(2)
            val suggestions=(growing+history.insights.filter{it.bytes>=200L*1024*1024}).distinctBy{it.name}.take(3)
            if(suggestions.isEmpty()) item { Text("No large folders found",style=MaterialTheme.typography.bodyMedium) }
            items(suggestions,key={"insight:"+it.name}){insight->
                Card(onClick={review(File(primary,insight.name))},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(headlineContent={Text(insight.name)},supportingContent={Text(fmt(insight.bytes)+(insight.increase?.takeIf{it>0}?.let{" · +"+fmt(it)+" since scan"} ?: " · Large folder"))},
                        leadingContent={Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,"Review")},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
        val removable=volumes.filter{it.root!=primary}
        if(removable.isNotEmpty()) item { Text("External storage",style=MaterialTheme.typography.titleMedium) }
        items(removable,key={it.root.path}){volume-> ElevatedCard(onClick={analyze(volume.root)},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(volume.label)},supportingContent={Text(fmt(volume.root.totalSpace)+" capacity")},leadingContent={Icon(Icons.Default.List,null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))} }
    }

}

@Composable
private fun DashboardValue(label:String,value:String,modifier:Modifier,icon:Int) {
    Column(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            DeviceGlyph(icon,modifier=Modifier.size(18.dp))
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeGauge(label: String, fraction: Float, value: String, modifier: Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(56.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=5.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
        Text(label,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ScanLoading(state: com.slate.android.storage.ScanState, cancel: ()->Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(76.dp),strokeWidth=5.dp)
        Text(Formatter.formatFileSize(context,state.measuredBytes),style=MaterialTheme.typography.headlineMedium)
        Text(java.text.NumberFormat.getIntegerInstance().format(state.visited)+" items · "+state.elapsedMs/1000+" s",style=MaterialTheme.typography.bodyMedium)
        Text(state.currentPath.ifBlank{state.directory?.path.orEmpty()},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=cancel){Text("Cancel")}
    }
}
