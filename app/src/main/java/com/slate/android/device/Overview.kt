// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slate.android.R

private fun Section?.value(label:String)=this?.rows?.firstOrNull{it.label==label}?.value?:UNKNOWN

internal fun LazyListScope.deviceOverview(live:LiveInfo?, fixed:StaticInfo?, navigate:(Int)->Unit) {
    val cpu=fixed?.hardware?.firstOrNull()
    val gpu=fixed?.hardware?.getOrNull(1)
    val display=fixed?.hardware?.getOrNull(2)
    item(key="profile") {
        Card(onClick={navigate(2)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                ManufacturerIcon()
                Column(Modifier.weight(1f)) {
                    Text(Build.MANUFACTURER.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.labelMedium)
                    Text(Build.MODEL,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    Text("Android "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT,style=MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.KeyboardArrowRight,null)
            }
        }
    }
    item(key="hardware-summary") {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SummaryTile("Processor",cpu.value("SoC").takeUnless{it==UNKNOWN}?:cpu.value("Hardware"),live?.let{"${it.cores.size} cores"}?:cpu.value("Detected cores"),Modifier.weight(1f)){navigate(1)}
                SummaryTile("Graphics",gpu.value("GPU renderer").removeSuffix(" (TM)"),live?.gpu.value("Frequency"),Modifier.weight(1f)){navigate(1)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SummaryTile("Display",live?.display.value("Resolution").takeUnless{it==UNKNOWN}?:display.value("Physical mode"),live?.display.value("Refresh rate")+" · "+display.value("Density"),Modifier.weight(1f)){navigate(1)}
                SummaryTile("Battery temperature",live?.battery.value("Temperature"),live?.thermal.value("Thermal throttling status").let{"Thermal status: "+it},Modifier.weight(1f)){navigate(3)}
            }
        }
    }
    item(key="usage") {
        if(live==null) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                UsageLine(live.meters[0]){navigate(1)}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                UsageLine(live.meters[1]){navigate(4)}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                UsageLine(live.meters[2]){navigate(3)}
            }
        }
    }
    if(live!=null) {
        item(key="uptime") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SummaryTile("Uptime",live.uptime.value("Since boot"),"Since boot",Modifier.weight(1f)){navigate(2)}
                SummaryTile("Deep sleep",live.uptime.value("Deep sleep (estimate)"),"Estimated",Modifier.weight(1f)){navigate(2)}
            }
        }
        item(key="cores") {
            Panel("CPU frequencies","Hardware",{navigate(1)}) {
                
                live.cores.chunked(2).forEach { cores ->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        cores.forEach { core ->
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("CPU ${core.id}",modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                                    Text(if(core.online==false)"Offline" else core.current?.let{"${it/1000} MHz"}?:"—",style=MaterialTheme.typography.labelMedium)
                                }
                                val fraction=if(core.current!=null&&core.maximum!=null&&core.maximum>0)(core.current.toDouble()/core.maximum).toFloat().coerceIn(0f,1f) else null
                                if(fraction!=null) LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(4.dp))
                                Text(core.maximum?.let{"Max ${it/1000} MHz"}?:"Max unavailable",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if(cores.size==1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item(key="battery") {
            Panel("Battery details","Battery",{navigate(3)}) {
                DataLine("Power source",live.battery.value("Power source"))
                live.battery.powerReading()?.let{DataLine(it.label,it.value)}
                DataLine("Voltage",live.battery.value("Voltage"))
                DataLine("Current",live.battery.value("Instant current (+ charging)"))
                DataLine("Remaining charge",live.battery.value("Remaining charge"))
                DataLine("Health",live.battery.value("Health (system status)"))
            }
        }
        item(key="graphics") {
            Panel("Graphics & display","Hardware",{navigate(1)}) {
                DataLine("OpenGL ES",gpu.value("OpenGL ES"))
                DataLine("Vulkan",gpu.value("Vulkan API (reported)"))
                DataLine("GPU utilization",live.gpu.value("Utilization"))
                DataLine("Maximum clock",live.gpu.value("Maximum frequency"))
                DataLine("Refresh rates",display.value("Supported refresh rates"))
            }
        }
        item(key="thermal") {
            Panel("Thermals & connection","System",{navigate(2)}) {
                DataLine("Thermal status",live.thermal.value("Thermal throttling status"))
                live.thermal.rows.drop(1).take(4).forEach{DataLine(it.label,it.value)}
                DataLine("Network",live.connection.value("Connection"))
            }
        }
    }
    item(key="quick") {
        Panel("Quick details","System",{navigate(2)}) {
            DataLine("Device",Build.DEVICE)
            DataLine("Build",Build.DISPLAY)
            DataLine("Security patch",Build.VERSION.SECURITY_PATCH)
            DataLine("ABI",Build.SUPPORTED_ABIS.firstOrNull()?:UNKNOWN)
        }
    }
}

@Composable
internal fun ManufacturerIcon() {
    val brand=Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
    val logo=when(brand) {
        "motorola"->R.drawable.brand_motorola
        else->0
    }
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,modifier=Modifier.size(50.dp)) {
        Box(contentAlignment=Alignment.Center) {
            if(logo!=0) Image(painterResource(logo),Build.MANUFACTURER,Modifier.size(30.dp),colorFilter=ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            else Icon(Icons.Default.Info,Build.MANUFACTURER,Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SummaryTile(label:String,value:String,subtitle:String,modifier:Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                DeviceGlyph(when(label){"Processor"->R.drawable.ic_device_cpu;"Graphics"->R.drawable.ic_device_gpu;"Display"->R.drawable.ic_device_display;else->R.drawable.ic_device_thermal},modifier=Modifier.size(18.dp))
                Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageLine(meter:Meter,onClick:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            DeviceGlyph(when(meter.title){"Battery"->R.drawable.ic_device_battery;"Storage"->R.drawable.ic_device_storage;else->R.drawable.ic_device_cpu},modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(meter.title,style=MaterialTheme.typography.titleSmall)
                Text(meter.value+" · "+meter.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){Text(meter.fraction?.let{"${(it*100).toInt()}%"}?:"Details")}
        }
        meter.fraction?.let{ LinearProgressIndicator(progress={it},modifier=Modifier.fillMaxWidth().height(5.dp)) }
    }
}

@Composable
private fun Panel(title:String,action:String,onClick:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){Text(action+" ›")}}
            content()
        }
    }
}
@Composable
private fun DataLine(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(label,modifier=Modifier.weight(.45f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,modifier=Modifier.weight(.55f),style=MaterialTheme.typography.bodySmall)
    }
}
