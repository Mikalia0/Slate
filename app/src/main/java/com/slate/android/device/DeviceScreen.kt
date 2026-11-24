// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import com.slate.android.R

internal class DeviceStatus {
    var fixed by mutableStateOf<StaticInfo?>(null)
    var live by mutableStateOf<LiveInfo?>(null)
    var storage by mutableStateOf<List<Section>>(emptyList())
    var error by mutableStateOf(false)
}
@Composable
internal fun rememberDeviceStatus():DeviceStatus {
    val context=LocalContext.current.applicationContext
    val reader=remember(context){DeviceReader(context)}
    val state=remember(reader){DeviceStatus()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reader,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                launch {
                    if(state.fixed==null) {
                        state.fixed=withContext(Dispatchers.IO){runCatching{reader.static()}.getOrNull()}
                        state.error=state.fixed==null
                    }
                }
                launch {
                    while(true) {
                        withContext(Dispatchers.IO){runCatching{reader.live()}.getOrNull()}?.let{state.live=it}
                        delay(2000)
                    }
                }
                launch {
                    while(true) {
                        withContext(Dispatchers.IO){runCatching{reader.storage()}.getOrNull()}?.let{state.storage=it}
                        delay(10000)
                    }
                }
            }
        }
    }
    return state
}
internal fun Section?.reading(label:String):String = this?.rows?.firstOrNull{it.label==label}?.value?.takeUnless{it==UNKNOWN} ?: "—"
internal fun Section?.powerReading():Info? = this?.rows?.firstOrNull{it.label in setOf("Charging power","Discharge power","Battery power")}

private val tabNames=listOf("Overview","Hardware","System","Battery","Storage","Network","Sensors","Camera")
private val tabIcons=listOf(R.drawable.ic_device_overview,R.drawable.ic_device_cpu,R.drawable.ic_device_system,R.drawable.ic_device_battery,R.drawable.ic_device_storage,R.drawable.ic_device_network,R.drawable.ic_device_sensors,R.drawable.ic_device_camera)

@Composable
internal fun DeviceGlyph(id:Int, description:String?=null, modifier:Modifier=Modifier.size(22.dp)) {
    Icon(painterResource(id),description,modifier,tint=MaterialTheme.colorScheme.primary)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceScreen(onBack:()->Unit, status:DeviceStatus, initialPage:Int=0) {
    val fixed=status.fixed
    val live=status.live
    val pager=rememberPagerState(initialPage=initialPage,pageCount={tabNames.size})
    val scope=rememberCoroutineScope()
    fun select(page:Int){scope.launch{pager.scrollToPage(page)}}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Text("Device information",style=MaterialTheme.typography.headlineSmall)
        }
        ScrollableTabRow(selectedTabIndex=pager.currentPage,edgePadding=0.dp,containerColor=MaterialTheme.colorScheme.background) {
            tabNames.forEachIndexed { i,name ->
                Tab(selected=pager.currentPage==i,onClick={select(i)},text={Text(name,style=MaterialTheme.typography.labelMedium)},
                    icon={Icon(painterResource(tabIcons[i]),null,Modifier.size(22.dp))},
                    selectedContentColor=MaterialTheme.colorScheme.primary,unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalPager(state=pager,modifier=Modifier.weight(1f),beyondViewportPageCount=1,verticalAlignment=Alignment.Top) { page ->
            LazyColumn(Modifier.fillMaxSize(),state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                if(page==0) deviceOverview(live,fixed,::select)
                else {
                    when(page) {
                        1 -> item(key="hardware-hero") {
                            val processor=fixed?.hardware?.firstOrNull()
                            val gpu=fixed?.hardware?.getOrNull(1)
                            FeaturePanel(R.drawable.ic_device_cpu,processor.reading("SoC").takeUnless{it=="—"} ?: processor.reading("Hardware"),"Processor") {
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Metric("Cores",live?.cores?.size?.toString() ?: processor.reading("Detected cores"),Modifier.weight(1f))
                                    Metric("GPU",gpu.reading("GPU renderer").removeSuffix(" (TM)"),Modifier.weight(1f))
                                }
                                if(live!=null) {
                                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                                    live.cores.chunked(2).forEach { cores ->
                                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                            cores.forEach { core ->
                                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                                        Text("CPU ${core.id}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                                                        Text(if(core.online==false)"Offline" else core.current?.let{"${it/1000} MHz"} ?: "—",style=MaterialTheme.typography.labelMedium)
                                                    }
                                                    if(core.current!=null && core.maximum!=null && core.maximum>0) LinearProgressIndicator(progress={(core.current.toDouble()/core.maximum).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp))
                                                }
                                            }
                                            if(cores.size==1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        2 -> item(key="system-hero") {
                            FeaturePanel(R.drawable.ic_device_system,"Android "+Build.VERSION.RELEASE,"API "+Build.VERSION.SDK_INT) {
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    Metric("Security patch",Build.VERSION.SECURITY_PATCH.ifBlank{"—"},Modifier.weight(1f))
                                    Metric("Uptime",live?.uptime.reading("Since boot"),Modifier.weight(1f))
                                }
                            }
                        }
                        3 -> item(key="battery-hero"){BatteryPanel(live)}
                        4 -> item(key="storage-hero") {
                            val meter=live?.meters?.firstOrNull{it.title=="Storage"}
                            FeaturePanel(R.drawable.ic_device_storage,meter?.value ?: "—","Internal storage") {
                                meter?.fraction?.let{fraction ->
                                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(6.dp))
                                    Text("${(fraction*100).toInt()}% · ${meter.subtitle}",style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        5 -> item(key="network-hero") {
                            FeaturePanel(R.drawable.ic_device_network,live?.connection.reading("Connection"),"Connection") {
                                val rows=live?.connection?.rows.orEmpty().filter{it.label!="Connection" && it.value!=UNKNOWN}.take(2)
                                rows.forEach{Metric(it.label,it.value,Modifier.fillMaxWidth())}
                            }
                        }
                        6 -> item(key="sensor-hero"){FeaturePanel(R.drawable.ic_device_sensors,fixed?.sensors?.size?.toString() ?: "—","Sensors"){}}
                        7 -> item(key="camera-hero"){FeaturePanel(R.drawable.ic_device_camera,fixed?.cameras?.size?.toString() ?: "—","Cameras"){}}
                    }
                    val sections=when(page) {
                        1 -> fixed?.hardware.orEmpty()+listOfNotNull(live?.memory,live?.cpu,live?.gpu,live?.thermal,live?.display)
                        2 -> fixed?.system.orEmpty()+listOfNotNull(live?.uptime,live?.thermal)
                        3 -> listOfNotNull(live?.battery)
                        4 -> status.storage
                        5 -> listOfNotNull(live?.connection)
                        6 -> fixed?.sensors.orEmpty()
                        7 -> fixed?.cameras.orEmpty()
                        else -> emptyList()
                    }
                    if(sections.isEmpty()) item {Text(if(status.error || fixed!=null)"No information available" else "Loading…",style=MaterialTheme.typography.bodyMedium)}
                    itemsIndexed(sections,key={index,section->"$index:${section.title}"}){_,section->InfoCard(section,sectionIcon(section,page),initiallyExpanded=page!=6 && page!=7)}
                }
            }
        }
    }
}
private fun sectionIcon(section:Section,page:Int):Int=when(section.title) {
    "Processor","CPU frequencies"->R.drawable.ic_device_cpu
    "Graphics","GPU activity"->R.drawable.ic_device_gpu
    "Memory"->R.drawable.ic_device_cpu
    "Display"->R.drawable.ic_device_display
    "Thermals"->R.drawable.ic_device_thermal
    else->tabIcons[page]
}

@Composable
private fun FeaturePanel(icon:Int,value:String,label:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(44.dp)) {
                    Box(contentAlignment=Alignment.Center){DeviceGlyph(icon,modifier=Modifier.size(24.dp))}
                }
                Column(Modifier.weight(1f)){
                    Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            content()
        }
    }
}
@Composable
private fun Metric(label:String,value:String,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable
private fun BatteryPanel(live:LiveInfo?) {
    val meter=live?.meters?.firstOrNull{it.title=="Battery"}
    val battery=live?.battery
    val power=battery.powerReading()
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(88.dp),contentAlignment=Alignment.Center) {
                    CircularProgressIndicator(progress={meter?.fraction ?: 0f},modifier=Modifier.fillMaxSize(),strokeWidth=6.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        DeviceGlyph(R.drawable.ic_device_battery,modifier=Modifier.size(22.dp))
                        Text(meter?.value ?: "—",style=MaterialTheme.typography.titleLarge)
                    }
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(battery.reading("Status"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text(power?.value?.takeUnless{it==UNKNOWN} ?: "—",style=MaterialTheme.typography.headlineMedium)
                    Text(power?.label ?: "Battery power",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric("Temperature",battery.reading("Temperature"),Modifier.weight(1f))
                Metric("Voltage",battery.reading("Voltage"),Modifier.weight(1f))
                Metric("Current",battery.reading("Instant current (+ charging)"),Modifier.weight(1f))
            }
            Text("Net power at battery",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(section:Section,icon:Int,initiallyExpanded:Boolean) {
    var expanded by rememberSaveable(section.title){mutableStateOf(initiallyExpanded)}
    val preview=section.rows.firstOrNull{it.value!=UNKNOWN}?.value.orEmpty()
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal=14.dp,vertical=8.dp)) {
            Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                DeviceGlyph(icon)
                Column(Modifier.weight(1f)) {
                    Text(section.title,style=MaterialTheme.typography.titleMedium)
                    if(!expanded && preview.isNotBlank()) Text(preview,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(expanded)"Collapse" else "Expand")
            }
            if(expanded) section.rows.forEachIndexed { index,info ->
                if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                if(info.value.length>100 || info.value.contains('\n')) {
                    var full by rememberSaveable(info.label){mutableStateOf(false)}
                    Column(Modifier.fillMaxWidth().clickable{full=!full}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(info.label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(info.value,style=MaterialTheme.typography.bodyMedium,maxLines=if(full)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                        Text(if(full)"Show less" else "Show all",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                } else Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(info.label,Modifier.weight(.46f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(info.value.takeUnless{it==UNKNOWN} ?: "—",Modifier.weight(.54f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,textAlign=TextAlign.End)
                }
            }
        }
    }
}
