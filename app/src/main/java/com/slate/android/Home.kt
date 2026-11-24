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
import com.slate.android.storage.rememberAppUsage
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
    val usage=rememberAppUsage().snapshot
    val cache=remember(usage.rows){usage.rows.sumOf{it.cacheBytes}}
    val appsTotal=remember(usage.rows){usage.rows.sumOf{it.total}}
    val users=remember(usage.rows){usage.rows.count{!it.system}}
    val growing=history.insights.filter{(it.increase ?: 0)>=50L*1024*1024}.maxByOrNull{it.increase ?: 0}
    val suggestion=growing ?: history.insights.filter{it.bytes>=1024L*1024*1024}.maxByOrNull{it.bytes}
    LazyVerticalGrid(columns=GridCells.Adaptive(155.dp),state=scrollState,modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
        item(key="storage",span={GridItemSpan(maxLineSpan)}) {
            Card(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(20.dp))
                        Text(tr("Internal storage"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text(if(capacity>0)"${(fraction*100).toInt()}%" else "—",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        StorageValue("Used",if(capacity>0)fmt(used)else "—",Modifier.weight(1f))
                        StorageValue("Available",if(capacity>0)fmt(free)else "—",Modifier.weight(1f))
                        StorageValue("Total",if(capacity>0)fmt(capacity)else "—",Modifier.weight(1f))
                    }
                    FilledTonalButton(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()){
                        Icon(Icons.Default.Search,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(tr("View storage"))
                    }
                }
            }
        }
        item(key="device"){
            Card(onClick=device,modifier=Modifier.fillMaxWidth().heightIn(min=176.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        DeviceGlyph(R.drawable.ic_device_cpu,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Device"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                    Text(Build.MODEL,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("Android "+Build.VERSION.RELEASE,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                        Column(Modifier.weight(1f).clickable{openDevicePage(1)},horizontalAlignment=Alignment.CenterHorizontally){
                            CompactGauge(ram?.fraction ?: 0f,ram?.fraction?.let{"${(it*100).toInt()}%"} ?: "—")
                            Text("RAM",style=MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f).clickable{openDevicePage(3)},horizontalAlignment=Alignment.CenterHorizontally){
                            CompactGauge(battery?.fraction ?: 0f,battery?.value?.takeUnless{it=="Unavailable"} ?: "—")
                            Text(tr("Battery"),style=MaterialTheme.typography.labelSmall,maxLines=1)
                        }
                    }
                    Text(memoryValue("Used RAM (estimate)")+" / "+memoryValue("Total RAM"),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        DeviceGlyph(R.drawable.ic_device_thermal,modifier=Modifier.size(16.dp))
                        Text(temperature,style=MaterialTheme.typography.labelMedium)
                        if(chargingPower!=null)Text("· "+chargingPower,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                }
            }
        }
        item(key="apps"){
            Card(onClick=openApps,modifier=Modifier.fillMaxWidth().heightIn(min=176.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        DeviceGlyph(R.drawable.ic_device_overview,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Apps"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                    Text(if(usage.loaded)fmt(appsTotal)else "—",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary,maxLines=1)
                    Text(if(usage.loaded)androidx.compose.ui.res.stringResource(R.string.apps_count,users)else tr(if(usage.allowed)"Loading…" else "Usage access"),style=MaterialTheme.typography.bodySmall)
                    if(usage.loaded){
                        HorizontalDivider()
                        Text(tr("Cache"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(cache),style=MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item(key="cleanup"){
            Card(onClick=openCleanup,modifier=Modifier.fillMaxWidth().heightIn(min=116.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Cleanup"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                    Text(if(usage.loaded)fmt(cache)else tr("Find"),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                    Text(tr(if(usage.loaded)"Reported app cache" else "Temporary files"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if("insights" in dashboard.visible) item(key="insights"){
            val low=capacity>0 && free.toDouble()/capacity<0.10
            val recommendCache=usage.loaded && cache>=512L*1024*1024
            val action:()->Unit=when{low->{ {analyze(primary)} };recommendCache->{openApps};suggestion!=null->{ {review(File(primary,suggestion.name))} };else->{openHistory}}
            Card(onClick=action,modifier=Modifier.fillMaxWidth().heightIn(min=116.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Info,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Storage insights"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2)
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(16.dp))
                    }
                    Text(when{low->tr("Low storage");recommendCache->tr("Review app cache");suggestion!=null->suggestion.name;else->tr("History")},style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(when{low->fmt(free);recommendCache->fmt(cache);suggestion!=null->fmt(suggestion.bytes);else->tr("Scan history")},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                }
            }
        }
        item(key="history",span={GridItemSpan(maxLineSpan)}) {
            TextButton(onClick=openHistory,modifier=Modifier.fillMaxWidth()){Text(tr("History"))}
        }
        val removable=volumes.filter{it.root!=primary && "external" in dashboard.visible}
        items(removable,key={it.root.path},span={GridItemSpan(maxLineSpan)}){volume->
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
