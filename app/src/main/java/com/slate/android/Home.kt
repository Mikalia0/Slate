// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.slate.android.device.reading
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
internal fun HomeScreen(primary: File, volumes: List<Volume>, openVolumes:()->Unit, analyze: (File) -> Unit, device: () -> Unit, history: StorageHistory, review: (File)->Unit, status: DeviceStatus, openDevicePage: (Int)->Unit, openApps: ()->Unit, openCache:()->Unit, openCleanup: ()->Unit,openHistory:()->Unit,dashboard:DashboardPreferences,scrollState:LazyGridState) {
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
        val chargingPower=live?.battery.powerReading()?.takeIf{it.label=="Charging power" && it.value!="Unavailable"}?.value
    val usage=rememberAppUsage().snapshot
    val cache=remember(usage.rows){usage.rows.sumOf{it.cacheBytes}}
    val appsTotal=remember(usage.rows){usage.rows.sumOf{it.total}}
    val appCount=usage.rows.size
    val growing=history.insights.filter{(it.increase ?: 0)>=50L*1024*1024}.maxByOrNull{it.increase ?: 0}
    val suggestion=growing ?: history.insights.filter{it.bytes>=1024L*1024*1024}.maxByOrNull{it.bytes}
    val displayResolution=live?.display.reading("Resolution").takeUnless{it=="—"}?:status.fixed?.hardware?.getOrNull(2).reading("Physical mode").takeUnless{it=="—"}
    val displayRefresh=live?.display.reading("Refresh rate").takeUnless{it=="—"}?:status.fixed?.hardware?.getOrNull(2).reading("Refresh rate").takeUnless{it=="—"}
    val rawCpu=status.fixed?.hardware?.firstOrNull().reading("SoC").takeUnless{it=="—"}
        ?:status.fixed?.hardware?.firstOrNull().reading("Hardware").takeUnless{it=="—"}
        ?:(if(Build.VERSION.SDK_INT>=31)Build.SOC_MODEL.takeUnless{it.isBlank()||it.equals("unknown",true)}else null)
    val cpu=when(rawCpu?.uppercase(java.util.Locale.ROOT)){
        "SM4350"->"Snapdragon 480"
        "SM4350-AC","SM4350AC"->"Snapdragon 480+"
        else->rawCpu
    }
    val processor=if(rawCpu.equals("SM4350",true)&&Build.MODEL.equals("moto g53 5G",true))"Snapdragon 480+"else cpu
    val removable=volumes.filter{it.root!=primary&&"external" in dashboard.visible}
    val low=capacity>0&&free.toDouble()/capacity<0.10
    val recommendCache=usage.loaded&&cache>=512L*1024*1024
    val insightAction:()->Unit=when{
        low->{ {analyze(primary)} }
        recommendCache->openCache
        suggestion!=null->{ {review(File(primary,suggestion.name))} }
        else->openHistory
    }
    val insightTitle=when{low->tr("Low storage");recommendCache->tr("Review app cache");suggestion!=null->suggestion.name;else->tr("History")}
    val insightDetail=when{low->fmt(free);recommendCache->fmt(cache);suggestion!=null->fmt(suggestion.bytes);else->tr("Scan history")}
    val appsDetail=if(usage.loaded)androidx.compose.ui.res.stringResource(R.string.apps_count,appCount)else tr(if(usage.allowed)"Loading…" else "Usage access")
    val summary:@Composable (Boolean)->Unit={compact->
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
            Column(Modifier.padding(if(compact)10.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(if(compact)5.dp else 10.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                    OverviewGauge(tr("Storage"),fraction,if(capacity>0)"${(fraction*100).toInt()}%"else "—",if(capacity>0)fmt(used)+" / "+fmt(capacity)else "—",Modifier.weight(1f),openVolumes,compact)
                    OverviewGauge("RAM",ram?.fraction?:0f,ram?.fraction?.let{"${(it*100).toInt()}%"}?:"—",memoryValue("Used RAM (estimate)")+" / "+memoryValue("Total RAM"),Modifier.weight(1f),{openDevicePage(1)},compact)
                    OverviewGauge(tr("Battery"),battery?.fraction?:0f,battery?.value?.takeUnless{it=="Unavailable"}?:"—",listOfNotNull(temperature,chargingPower).joinToString(" · "),Modifier.weight(1f),{openDevicePage(3)},compact)
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().clickable(onClick=device).padding(vertical=if(compact)2.dp else 6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    ManufacturerIcon(size=if(compact)32.dp else 42.dp)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Text(Build.MODEL,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(processor!=null)Text(processor,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("Android "+Build.VERSION.RELEASE,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.KeyboardArrowRight,null)
                }
                Row(Modifier.fillMaxWidth().clickable{openDevicePage(1)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    DeviceGlyph(R.drawable.ic_device_display,modifier=Modifier.size(20.dp))
                    Text(tr("Display"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    Text(listOfNotNull(displayResolution,displayRefresh).joinToString(" · ").ifBlank{"—"},style=MaterialTheme.typography.bodyMedium,maxLines=2)
                    Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                }
                if(compact)removable.forEach{volume->
                    val total=volume.root.totalSpace
                    val occupied=(total-volume.root.usableSpace).coerceAtLeast(0)
                    Row(Modifier.fillMaxWidth().clickable{analyze(volume.root)}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(18.dp))
                        Text(tr(volume.label),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(total>0)Text(fmt(occupied)+" / "+fmt(total),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    val shortcuts:@Composable ()->Unit={
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
            Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)){
                DashboardShortcut(R.drawable.ic_device_overview,tr("Apps"),appsDetail,openApps)
                HorizontalDivider()
                DashboardShortcut(null,tr("Cleanup"),if(usage.loaded)fmt(cache)else tr("Temporary files"),openCleanup)
                if("insights" in dashboard.visible){HorizontalDivider();DashboardShortcut(R.drawable.ic_slate_insights,tr("Storage insights"),insightTitle+" · "+insightDetail,insightAction)}
                HorizontalDivider()
                DashboardShortcut(R.drawable.ic_slate_history,tr("History"),tr("Scan history"),openHistory)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val horizontal=useTopNavigation()&&maxWidth>=480.dp
        if(horizontal){
            Row(Modifier.fillMaxSize().padding(bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Column(Modifier.weight(1.35f).fillMaxHeight().verticalScroll(rememberScrollState())){summary(true)}
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())){shortcuts()}
            }
        }else{
            val columns=if(maxWidth>=1000.dp)4 else if(maxWidth>=600.dp)3 else 2
            LazyVerticalGrid(columns=GridCells.Fixed(columns),state=scrollState,modifier=Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=10.dp)){
                item(key="storage",span={GridItemSpan(maxLineSpan)}){summary(false)}
                item(key="apps"){
                    DashboardTile(R.drawable.ic_device_overview,tr("Apps"),if(usage.loaded)fmt(appsTotal)else "—",appsDetail,openApps)
                }
                item(key="cleanup"){
                    DashboardTile(null,tr("Cleanup"),if(usage.loaded)fmt(cache)else tr("Find"),tr(if(usage.loaded)"Cache"else "Temporary files"),openCleanup)
                }
                if("insights" in dashboard.visible)item(key="insights"){
                    DashboardTile(R.drawable.ic_slate_insights,tr("Storage insights"),insightTitle,insightDetail,insightAction)
                }
                item(key="history"){
                    DashboardTile(R.drawable.ic_slate_history,tr("History"),"",tr("Scan history"),openHistory)
                }
                items(removable,key={it.root.path},span={GridItemSpan(maxLineSpan)}){volume->
                    val total=volume.root.totalSpace
                    val available=volume.root.usableSpace
                    val occupied=(total-available).coerceAtLeast(0)
                    Card(onClick={analyze(volume.root)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                DeviceGlyph(R.drawable.ic_device_storage,modifier=Modifier.size(20.dp))
                                Text(tr(volume.label),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                if(total>0)Text(fmt(occupied)+" / "+fmt(total),style=MaterialTheme.typography.bodySmall)
                                Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
                            }
                            if(total>0)LinearProgressIndicator(progress={(occupied.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DashboardShortcut(icon:Int?,title:String,detail:String,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=48.dp).padding(horizontal=4.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        if(icon!=null)DeviceGlyph(icon,modifier=Modifier.size(22.dp))else Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))
    }
}

@Composable private fun DashboardTile(icon:Int?,title:String,value:String,detail:String,onClick:()->Unit){
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=112.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                if(icon!=null)DeviceGlyph(icon,modifier=Modifier.size(20.dp))else Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=2)
            }
            if(value.isNotBlank())Text(value,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,maxLines=2)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        }
    }
}

@Composable
private fun HomeAction(label:String,icon:Int?,modifier:Modifier,onClick:()->Unit){
    Column(modifier.clickable(onClick=onClick).padding(vertical=10.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
        if(icon!=null)DeviceGlyph(icon,modifier=Modifier.size(22.dp))
        else Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Text(label,style=MaterialTheme.typography.labelMedium,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
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
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(36.dp),strokeWidth=3.dp)
        Text(Formatter.formatFileSize(context,state.measuredBytes),style=MaterialTheme.typography.titleLarge)
        Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.scan_items_count,java.text.NumberFormat.getIntegerInstance().format(state.visited),state.elapsedMs/1000),style=MaterialTheme.typography.bodyMedium)
        Text(state.currentPath.ifBlank{state.directory?.path.orEmpty()},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick=cancel){Text(tr("Cancel"))}
    }
}

@Composable private fun OverviewGauge(label:String,fraction:Float,value:String,detail:String,modifier:Modifier,onClick:()->Unit,compact:Boolean){
    Column(modifier.clickable(onClick=onClick).padding(vertical=if(compact)2.dp else 4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Box(Modifier.size(if(compact)56.dp else 70.dp),contentAlignment=Alignment.Center){
            CircularProgressIndicator(progress={fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxSize(),strokeWidth=4.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
            Text(value,style=MaterialTheme.typography.titleMedium,maxLines=1)
        }
        Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(detail,style=MaterialTheme.typography.bodySmall,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
