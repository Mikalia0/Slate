// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.os.Build
import com.slate.android.device.ManufacturerIcon
import com.slate.android.device.DeviceStatus
import com.slate.android.device.DeviceGlyph
import com.slate.android.device.powerReading
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.slate.android.storage.Volume
import java.io.File

@Composable
internal fun HomeScreen(primary: File, volumes: List<Volume>, analyze: (File) -> Unit, device: () -> Unit, history: StorageHistory, review: (File)->Unit, status: DeviceStatus, openDevicePage: (Int)->Unit, openApps: ()->Unit, openCleanup: ()->Unit,openHistory:()->Unit,dashboard:DashboardPreferences,scrollState:LazyGridState) {
    val context = LocalContext.current
    val capacity = primary.totalSpace
    val free = primary.usableSpace
    val used = (capacity-free).coerceAtLeast(0)
    val fraction = if(capacity>0) (used.toDouble()/capacity).toFloat().coerceIn(0f,1f) else 0f
    val fmt: (Long)->String = { Formatter.formatFileSize(context,it) }
    val live=status.live
    val ram=live?.meters?.firstOrNull{it.title=="Memory"}
    val battery=live?.meters?.firstOrNull{it.title=="Battery"}
    fun memoryValue(label:String)=live?.memory?.rows?.firstOrNull{it.label==label}?.value?.takeUnless{it=="Unavailable"} ?: "—"
    val temperature=live?.battery?.rows?.firstOrNull{it.label=="Temperature"}?.value?.takeUnless{it=="Unavailable"}?.replace(" ","") ?: "—"
    val batteryState=live?.battery?.rows?.firstOrNull{it.label=="Status"}?.value?.takeUnless{it=="Unavailable"}
    val chargingPower=live?.battery.powerReading()?.takeIf{it.label=="Charging power" && it.value!="Unavailable"}?.value
    LazyVerticalGrid(columns=GridCells.Adaptive(320.dp),state=scrollState,modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(20.dp))
                        Text(tr("Internal storage"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text(tr(if(capacity>0)"${(fraction*100).toInt()}%" else "—"),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        StorageValue("Used",if(capacity>0)fmt(used)else "—",Modifier.weight(1f))
                        StorageValue("Available",if(capacity>0)fmt(free)else "—",Modifier.weight(1f))
                        StorageValue("Total",if(capacity>0)fmt(capacity)else "—",Modifier.weight(1f))
                    }
                    Button(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text(tr(if(history.scannedAt>0)"View storage" else "Analyze storage"))
                    }
                }
            }
        }
        if("device" in dashboard.visible) item {
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
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top) {
                        HomeGauge("RAM",ram?.fraction ?: 0f,ram?.fraction?.let{"${(it*100).toInt()}%"} ?: "—",Modifier.weight(1f).clickable{openDevicePage(1)},
                            details=listOf(memoryValue("Used RAM (estimate)")+" used",memoryValue("Total RAM")+" total",memoryValue("Available RAM")+" free"))
                        HomeGauge("Battery",battery?.fraction ?: 0f,battery?.value?.takeUnless{it=="Unavailable"} ?: "—",Modifier.weight(1f).clickable{openDevicePage(3)},
                            details=listOfNotNull(batteryState,chargingPower))
                        Column(Modifier.weight(1f).clickable{openDevicePage(3)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(58.dp),contentAlignment=Alignment.Center) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                    DeviceGlyph(R.drawable.ic_device_thermal,modifier=Modifier.size(20.dp))
                                    Text(temperature,style=MaterialTheme.typography.titleSmall,maxLines=1,softWrap=false)
                                }
                            }
                            Text(tr("Temperature"),style=MaterialTheme.typography.labelMedium,maxLines=1)
                        }
                    }
                }
            }
        }
        if("tools" in dashboard.visible) item(span={GridItemSpan(maxLineSpan)}) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Card(onClick=openApps,modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp).heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DeviceGlyph(R.drawable.ic_device_overview)
                        Text(tr("App storage"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                }
                Card(onClick=openCleanup,modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp).heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                        Text(tr("Cleanup"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                }
            }
        }
        if("folders" in dashboard.visible && history.scannedAt>0) {
            val common=listOf("Download","DCIM","Pictures","Movies","Music","Documents")
            val shortcuts=common.mapNotNull{name->history.insights.firstOrNull{it.name.equals(name,true)}}
            if(shortcuts.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(tr("Quick folders"),style=MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        shortcuts.forEach{folder->
                            Card(onClick={review(File(primary,folder.name))},modifier=Modifier.width(112.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_storage_folder),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                                    Text(folder.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(fmt(folder.bytes),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        if("insights" in dashboard.visible) {
        val change=history.sessionChange
        if(change!=null && kotlin.math.abs(change)>=10L*1024*1024) item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Since last session"),style=MaterialTheme.typography.labelMedium)
                        Text((if(change>0)"+" else "−")+fmt(kotlin.math.abs(change)),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    Text(tr(if(change>0)"Used space increased" else "Space recovered"),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(span={GridItemSpan(maxLineSpan)}) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Storage insights"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick=openHistory){Text(tr("History"))}
            }
        }
        if(capacity>0 && free.toDouble()/capacity<0.10) item(span={GridItemSpan(maxLineSpan)}) {
            Card(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                ListItem(headlineContent={Text(tr("Low storage"))},supportingContent={Text(fmt(free)+" available")},leadingContent={Icon(Icons.Default.Warning,null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.errorContainer))
            }
        }
        if(history.scannedAt==0L) item(span={GridItemSpan(maxLineSpan)}) {
            Card(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) {
                ListItem(headlineContent={Text(tr("Analyze storage for insights"))},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)})
            }
        } else {
            val growing=history.insights.filter{(it.increase ?: 0)>=50L*1024*1024}.sortedByDescending{it.increase}.take(2)
            val suggestions=(growing+history.insights.filter{it.bytes>=200L*1024*1024}).distinctBy{it.name}.take(3)
            if(suggestions.isEmpty()) item { Text(tr("No large folders found"),style=MaterialTheme.typography.bodyMedium) }
            items(suggestions,key={"insight:"+it.name}){insight->
                Card(onClick={review(File(primary,insight.name))},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                    ListItem(headlineContent={Text(insight.name)},supportingContent={Text(fmt(insight.bytes)+(insight.increase?.takeIf{it>=50L*1024*1024}?.let{" · +"+fmt(it)+" since scan"} ?: " · Large folder"))},
                        leadingContent={Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,"Review")},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                }
            }
        }
        }
        val removable=volumes.filter{it.root!=primary && "external" in dashboard.visible}
        if(removable.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text(tr("External storage"),style=MaterialTheme.typography.titleMedium) }
        items(removable,key={it.root.path}){volume->
            val total=volume.root.totalSpace
            val available=volume.root.usableSpace
            val occupied=(total-available).coerceAtLeast(0)
            Card(onClick={analyze(volume.root)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        DeviceGlyph(R.drawable.ic_device_storage)
                        Spacer(Modifier.width(8.dp))
                        Text(volume.label,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.KeyboardArrowRight,null)
                    }
                    if(total>0){
                        LinearProgressIndicator(progress={(occupied.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            StorageValue("Used",fmt(occupied),Modifier.weight(1f))
                            StorageValue("Available",fmt(available),Modifier.weight(1f))
                            StorageValue("Total",fmt(total),Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun StorageValue(label:String,value:String,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(tr(label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.titleSmall,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeGauge(label:String,fraction:Float,value:String,modifier:Modifier,details:List<String> = emptyList()) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(58.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=5.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
            Text(value,style=MaterialTheme.typography.titleMedium,maxLines=1,softWrap=false)
        }
        Text(tr(label),style=MaterialTheme.typography.labelMedium)
        details.filterNot{it.startsWith("—")}.forEach{detail->
            Text(tr(detail),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
        }
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
        OutlinedButton(onClick=cancel){Text(tr("Cancel"))}
    }
}
