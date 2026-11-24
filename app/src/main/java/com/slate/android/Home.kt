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
    LazyVerticalGrid(columns=GridCells.Adaptive(320.dp),state=scrollState,modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
        item(key="storage") {
            Card(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(20.dp))
                        Text(tr("Internal storage"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.KeyboardArrowRight,tr("View storage"),Modifier.size(20.dp))
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(if(capacity>0)fmt(used)else "—",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                        Text(if(capacity>0)"${(fraction*100).toInt()}%" else "—",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        StorageValue("Available",if(capacity>0)fmt(free)else "—",Modifier.weight(1f))
                        StorageValue("Total",if(capacity>0)fmt(capacity)else "—",Modifier.weight(1f))
                    }
                }
            }
        }
        item(key="device") {
            Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick=device),verticalAlignment=Alignment.CenterVertically) {
                        ManufacturerIcon()
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Build.MODEL,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(Build.MANUFACTURER.replaceFirstChar{it.uppercase()}+" · Android "+Build.VERSION.RELEASE,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.KeyboardArrowRight,tr("Device information"),Modifier.size(20.dp))
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable{openDevicePage(1)}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CompactGauge(ram?.fraction ?: 0f,ram?.fraction?.let{"${(it*100).toInt()}%"} ?: "—")
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text("RAM",style=MaterialTheme.typography.titleSmall)
                            Text(memoryValue("Used RAM (estimate)")+" / "+memoryValue("Total RAM"),style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment=Alignment.End){
                            Text(memoryValue("Available RAM"),style=MaterialTheme.typography.bodyMedium,maxLines=1)
                            Text(tr("Available"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().clickable{openDevicePage(3)}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        CompactGauge(battery?.fraction ?: 0f,battery?.value?.takeUnless{it=="Unavailable"} ?: "—")
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text(tr("Battery"),style=MaterialTheme.typography.titleSmall)
                            val stateText=batteryState?.let{tr(it)} ?: "—"
                            Text(if(chargingPower!=null)"$stateText · $chargingPower" else stateText,style=MaterialTheme.typography.bodySmall,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            DeviceGlyph(R.drawable.ic_device_thermal,modifier=Modifier.size(18.dp))
                            Text(temperature,style=MaterialTheme.typography.titleSmall,maxLines=1,softWrap=false)
                        }
                    }
                }
            }
        }
        item(key="tools",span={GridItemSpan(maxLineSpan)}) {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                    HomeAction(tr("Apps"),R.drawable.ic_device_overview,Modifier.weight(1f),openApps)
                    HomeAction(tr("Cleanup"),null,Modifier.weight(1f),openCleanup)
                    HomeAction(tr("History"),R.drawable.ic_device_storage,Modifier.weight(1f),openHistory)
                }
            }
        }
        if("insights" in dashboard.visible) {
            val low=capacity>0 && free.toDouble()/capacity<0.10
            val growing=history.insights.filter{(it.increase ?: 0)>=50L*1024*1024}.sortedByDescending{it.increase}.take(2)
            val suggestions=(growing+history.insights.filter{it.bytes>=1024L*1024*1024}).distinctBy{it.name}.take(2)
            if(low || suggestions.isNotEmpty()) item(key="insights",span={GridItemSpan(maxLineSpan)}){
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                    Column{
                        Text(tr("Storage insights"),Modifier.padding(start=14.dp,top=12.dp,bottom=4.dp),style=MaterialTheme.typography.titleSmall)
                        if(low) ListItem(modifier=Modifier.clickable{analyze(primary)},headlineContent={Text(tr("Low storage"))},supportingContent={Text(androidx.compose.ui.res.stringResource(R.string.size_available,fmt(free)))},leadingContent={Icon(Icons.Default.Warning,null,tint=MaterialTheme.colorScheme.error)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                        suggestions.forEach{insight->
                            ListItem(modifier=Modifier.clickable{review(File(primary,insight.name))},headlineContent={Text(insight.name,maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text(insight.increase?.takeIf{it>=50L*1024*1024}?.let{androidx.compose.ui.res.stringResource(R.string.growth_since_scan,fmt(insight.bytes),fmt(it))} ?: androidx.compose.ui.res.stringResource(R.string.large_folder,fmt(insight.bytes)))},
                                leadingContent={Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_storage_folder),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                        }
                    }
                }
            }
        }
        val removable=volumes.filter{it.root!=primary && "external" in dashboard.visible}
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
private fun HomeAction(label:String,icon:Int?,modifier:Modifier,onClick:()->Unit){
    Column(modifier.clickable(onClick=onClick).padding(vertical=10.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
        if(icon!=null)DeviceGlyph(icon,modifier=Modifier.size(22.dp))
        else Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactGauge(fraction:Float,value:String){
    Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){
        CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=3.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
        Text(value,style=MaterialTheme.typography.labelMedium,maxLines=1,softWrap=false)
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
internal fun ScanLoading(state: com.slate.android.storage.ScanState, cancel: ()->Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(76.dp),strokeWidth=5.dp)
        Text(Formatter.formatFileSize(context,state.measuredBytes),style=MaterialTheme.typography.headlineMedium)
        Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.scan_items_count,java.text.NumberFormat.getIntegerInstance().format(state.visited),state.elapsedMs/1000),style=MaterialTheme.typography.bodyMedium)
        Text(state.currentPath.ifBlank{state.directory?.path.orEmpty()},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=cancel){Text(tr("Cancel"))}
    }
}
