// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import com.slate.android.tr

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    val pageState=rememberSaveableStateHolder()
    var testMode by rememberSaveable{mutableStateOf(false)}
    if(testMode){DeviceTests(onBack={testMode=false});return}
    fun select(page:Int){scope.launch{if(kotlin.math.abs(page-pager.currentPage)==1) pager.animateScrollToPage(page,animationSpec=tween(160)) else pager.scrollToPage(page)}}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
            Text(tr("Device information"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            TextButton(onClick={testMode=true}){Text(tr("Tests"))}
        }
        val wide=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=760
        if(!wide)ScrollableTabRow(selectedTabIndex=pager.currentPage,edgePadding=0.dp,containerColor=MaterialTheme.colorScheme.background) {
            tabNames.forEachIndexed { i,name ->
                Tab(selected=pager.currentPage==i,onClick={select(i)},text={Text(tr(name),style=MaterialTheme.typography.labelMedium,maxLines=1,softWrap=false)},
                    icon={Icon(painterResource(tabIcons[i]),null,Modifier.size(20.dp))},
                    selectedContentColor=MaterialTheme.colorScheme.primary,unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()){
        if(wide){
            Column(Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                tabNames.forEachIndexed{i,name->
                    NavigationDrawerItem(label={Text(tr(name),maxLines=2)},selected=pager.currentPage==i,onClick={select(i)},icon={Icon(painterResource(tabIcons[i]),null,Modifier.size(22.dp))})
                }
            }
            VerticalDivider(Modifier.fillMaxHeight())
        }
        HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxHeight(),userScrollEnabled=!wide,beyondViewportPageCount=1,verticalAlignment=Alignment.Top) { page ->
            pageState.SaveableStateProvider(page) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
            LazyColumn(Modifier.widthIn(max=860.dp).fillMaxSize().padding(horizontal=if(wide)16.dp else 0.dp),state=rememberLazyListState(),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                if(page==0) deviceOverview(live,fixed,::select)
                else {
                    val processor=fixed?.hardware?.firstOrNull()
                    val graphics=fixed?.hardware?.getOrNull(1)
                    val display=fixed?.hardware?.getOrNull(2)
                    when(page) {
                        1 -> {
                            item(key="processor"){ProcessorPanel(processor,live?.cores.orEmpty())}
                            item(key="graphics"){GraphicsPanel(graphics,live?.gpu)}
                            item(key="memory"){MemoryPanel(live?.memory,live?.meters?.firstOrNull{it.title=="Memory"})}
                            item(key="display"){DisplayPanel(display,live?.display)}
                            live?.thermal?.let{thermal->item(key="thermal"){ThermalPanel(thermal)}}
                        }
                        2 -> {
                            item(key="system-hero"){
                                FeaturePanel(R.drawable.ic_device_system,"Android "+Build.VERSION.RELEASE,"API "+Build.VERSION.SDK_INT){
                                    MetricGrid(listOf(Info("Security patch",Build.VERSION.SECURITY_PATCH),Info("Uptime",live?.uptime.reading("Since boot"))))
                                }
                            }
                            item(key="uptime"){
                                val meter=live?.meters?.firstOrNull{it.title=="Uptime"}
                                FeaturePanel(R.drawable.ic_device_system,live?.uptime.reading("Deep sleep (estimate)"),"Deep sleep"){
                                    meter?.fraction?.let{UsageBar(it)}
                                    MetricGrid(listOf(Info("Awake",live?.uptime.reading("Awake")),Info("Deep sleep (estimate)",live?.uptime.reading("Deep sleep (estimate)"))))
                                }
                            }
                            fixed?.system.orEmpty().forEachIndexed{index,section->
                                val trimmed=section.copy(rows=section.rows.filterNot{it.label in setOf("Android version","API level","Security patch")})
                                item(key="system-$index"){InfoCard(trimmed,sectionIcon(section,page),initiallyExpanded=false)}
                            }
                        }
                        3 -> item(key="battery"){BatteryPanel(live)}
                        4 -> {
                            status.storage.forEachIndexed{index,section->item(key="volume-$index"){VolumePanel(section)}}
                            if(status.storage.isEmpty())item(key="storage-loading"){Text(tr("Loading…"))}
                        }
                        5 -> item(key="connection"){NetworkPanel(live?.connection)}
                        6,7 -> {
                            val sections=if(page==6)fixed?.sensors.orEmpty() else fixed?.cameras.orEmpty()
                            item(key="count"){
                                FeaturePanel(tabIcons[page],if(fixed==null)"—" else sections.size.toString(),if(page==6)"Sensors" else "Cameras"){}
                            }
                            if(page==6&&pager.settledPage==6)item(key="live-sensors"){LiveSensors()}
                            itemsIndexed(sections,key={index,section->"$index:${section.title}"}){_,section->
                                if(page==7) CameraPanel(section) else InfoCard(section,tabIcons[page],initiallyExpanded=false)
                            }
                            if(fixed!=null&&sections.isEmpty())item(key="empty"){Text(tr("No information available"))}
                        }
                    }
                    if(status.error && fixed==null)item(key="error"){Text(tr("No information available"),style=MaterialTheme.typography.bodySmall)}
                }
            }
            }
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
                    Text(tr(label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr(value),style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            content()
        }
    }
}
@Composable
private fun Metric(label:String,value:String,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(tr(label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr(value),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
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
                    Text(tr(battery.reading("Status")),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text(power?.value?.takeUnless{it==UNKNOWN} ?: "—",style=MaterialTheme.typography.headlineMedium)
                    Text(tr(power?.label ?: "Battery power"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Metric("Temperature",battery.reading("Temperature"),Modifier.weight(1f))
                Metric("Voltage",battery.reading("Voltage"),Modifier.weight(1f))
                Metric("Current",battery.reading("Instant current (+ charging)"),Modifier.weight(1f))
            }
            val estimate=battery.reading("Time to full (system estimate)")
            if(estimate!="—")Metric("Time to full",estimate,Modifier.fillMaxWidth())
            val full=battery.reading("Full capacity (reported)")
            val approximate=battery.reading("Estimated full capacity")
            val health=battery.reading("Capacity health (reported)")
            if(full!="—" || approximate!="—" || health!="—") Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Metric(if(full!="—")"Full capacity" else "Estimated capacity",if(full!="—")full else approximate,Modifier.weight(1f))
                if(health!="—")Metric("Capacity health",health,Modifier.weight(1f))else Spacer(Modifier.weight(1f))
            }

            val shown=setOf("Level","Status","Charging power","Discharge power","Battery power","Temperature","Voltage","Instant current (+ charging)","Time to full (system estimate)","Capacity health (reported)")+if(full!="—")setOf("Full capacity (reported)") else setOf("Estimated full capacity")
            DetailsRows(battery?.rows.orEmpty().filterNot{it.label in shown})
        }
    }
}

internal fun visibleReadings(rows:List<Info>):List<Info> =
    rows.filter{it.value.isNotBlank() && it.value!=UNKNOWN && it.value!="—"}.distinctBy{it.label}

private fun Section?.metrics(vararg labels:String):List<Info> =
    labels.mapNotNull{label->this?.rows?.firstOrNull{it.label==label}}.let(::visibleReadings)

@Composable
private fun UsageBar(fraction:Float){
    LinearProgressIndicator(progress={fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
private fun MetricGrid(rows:List<Info>){
    visibleReadings(rows).chunked(2).forEach{pair->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            pair.forEach{info->
                Surface(modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh){
                    Metric(info.label,info.value,Modifier.padding(10.dp))
                }
            }
            if(pair.size==1)Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProcessorPanel(section:Section?,cores:List<CoreReading>){
    val name=section.reading("SoC").takeUnless{it=="—"}?:section.reading("Hardware")
    FeaturePanel(R.drawable.ic_device_cpu,name,"Processor"){
        MetricGrid(listOf(Info("Cores",if(cores.isNotEmpty())cores.size.toString() else section.reading("Detected cores")),Info("Maximum frequency",cores.mapNotNull{it.maximum}.maxOrNull()?.let{"${it/1000} MHz"}?:"—")))
        if(cores.isNotEmpty()){
            Text(tr("CPU frequencies"),style=MaterialTheme.typography.titleSmall)
            cores.chunked(2).forEach{group->
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    group.forEach{core->
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text("CPU ${core.id}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                                Text(if(core.online==false)tr("Offline") else core.current?.let{"${it/1000} MHz"}?:"—",style=MaterialTheme.typography.labelMedium)
                            }
                            val max=core.maximum
                            if(max!=null&&max>0&&core.current!=null)UsageBar((core.current.toDouble()/max).toFloat())
                        }
                    }
                    if(group.size==1)Spacer(Modifier.weight(1f))
                }
            }
        }
        DetailsRows(section?.rows.orEmpty().filterNot{it.label in setOf("SoC","Detected cores") || (it.label=="Hardware" && it.value==name)})
    }
}

@Composable
private fun GraphicsPanel(section:Section?,live:Section?){
    FeaturePanel(R.drawable.ic_device_gpu,section.reading("GPU renderer").removeSuffix(" (TM)"),"Graphics"){
        val rows=live.metrics("Frequency","Maximum frequency","Utilization")
        MetricGrid(rows+section.metrics("Vulkan API (reported)"))
        val load=live.reading("Utilization").substringBefore(' ').replace(',','.').toFloatOrNull()
        if(load!=null && load in 0f..100f)UsageBar(load/100f)
        val gl=section.reading("OpenGL ES")
        val shortGl=Regex("OpenGL ES [0-9]+[.][0-9]+").find(gl)?.value
        if(shortGl!=null)Text(shortGl,style=MaterialTheme.typography.titleSmall)
        DetailsRows(section?.rows.orEmpty().filterNot{it.label in setOf("GPU renderer","Vulkan API (reported)")})
    }
}

@Composable
private fun MemoryPanel(section:Section?,meter:Meter?){
    FeaturePanel(R.drawable.ic_device_cpu,section.reading("Total RAM"),"Memory"){
        meter?.fraction?.let{fraction->
            UsageBar(fraction)
            Text(tr("Used")+" · "+(fraction*100).toInt()+"%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
        MetricGrid(section.metrics("Used RAM (estimate)","Available RAM","Cached RAM","Free RAM","Swap used","Swap total","zRAM capacity"))
        DetailsRows(section.metrics("Low memory"))
    }
}

@Composable
private fun DisplayPanel(section:Section?,live:Section?){
    val resolution=live.reading("Resolution").takeUnless{it=="—"}?:section.reading("Physical mode")
    FeaturePanel(R.drawable.ic_device_display,resolution,"Display"){
        MetricGrid(listOf(Info("Refresh rate",live.reading("Refresh rate").takeUnless{it=="—"}?:section.reading("Refresh rate")))+section.metrics("Density","HDR formats"))
        DetailsRows(section?.rows.orEmpty().filterNot{it.label in setOf("Physical mode","Refresh rate","Density","HDR formats")})
    }
}

@Composable
private fun ThermalPanel(section:Section){
    val readings=visibleReadings(section.rows).filterNot{it.label=="Thermal throttling status"}
    FeaturePanel(R.drawable.ic_device_thermal,section.reading("Thermal throttling status"),"Thermals"){
        MetricGrid(readings.filter{it.label.contains("cpu",true)||it.label.contains("gpu",true)||it.label.contains("battery",true)}.take(4))
        DetailsRows(readings)
    }
}

@Composable
private fun VolumePanel(section:Section){
    FeaturePanel(R.drawable.ic_device_storage,section.reading("Capacity"),section.title){
        section.fraction?.let{fraction->
            UsageBar(fraction)
            Text(tr("Used")+" · "+(fraction*100).toInt()+"%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
        MetricGrid(section.metrics("Used","Available"))
        DetailsRows(section.metrics("Mount path"))
    }
}

@Composable
private fun NetworkPanel(section:Section?){
    val primary=section.metrics("Wi-Fi standard","Wi-Fi signal","Link speed","Frequency","Operator","Radio access","Signal level","Estimated downlink","Estimated uplink")
    FeaturePanel(R.drawable.ic_device_network,section.reading("Connection"),"Connection"){
        MetricGrid(primary)
        DetailsRows(section?.rows.orEmpty().filterNot{it.label=="Connection"||primary.any{p->p.label==it.label}})
    }
}

@Composable
private fun CameraPanel(section:Section){
    FeaturePanel(R.drawable.ic_device_camera,section.reading("Megapixels"),section.title){
        MetricGrid(section.metrics("Sensor resolution","Apertures","Focal lengths","Sensor size"))
        DetailsRows(section.rows.filterNot{it.label in setOf("Megapixels","Sensor resolution","Apertures","Focal lengths","Sensor size")})
    }
}

@Composable
private fun DetailsRows(rows:List<Info>){
    val valid=visibleReadings(rows)
    if(valid.isEmpty())return
    var expanded by rememberSaveable{mutableStateOf(false)}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically){
        Text(tr("Technical details"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Icon(if(expanded)Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null)
    }
    if(expanded)valid.forEach{ReadingRow(it)}
}

@Composable
private fun ReadingRow(info:Info){
    if(info.value.length>100||info.value.contains('\n')){
        var full by rememberSaveable(info.label){mutableStateOf(false)}
        Column(Modifier.fillMaxWidth().clickable{full=!full}.padding(vertical=5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(tr(info.label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(info.value,style=MaterialTheme.typography.bodySmall,maxLines=if(full)Int.MAX_VALUE else 2,overflow=TextOverflow.Ellipsis)
            Text(tr(if(full)"Show less" else "Show all"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }else Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
        Text(tr(info.label),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr(info.value),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.End)
    }
}

@Composable
private fun InfoCard(section:Section,icon:Int,initiallyExpanded:Boolean){
    var expanded by rememberSaveable(section.title){mutableStateOf(initiallyExpanded)}
    val rows=visibleReadings(section.rows)
    if(rows.isEmpty())return
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(horizontal=14.dp,vertical=8.dp)){
            Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                DeviceGlyph(icon)
                Column(Modifier.weight(1f)){
                    Text(tr(section.title),style=MaterialTheme.typography.titleMedium)
                    if(!expanded)Text(rows.first().value,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(if(expanded)Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null)
            }
            if(expanded)rows.forEach{ReadingRow(it)}
        }
    }
}
