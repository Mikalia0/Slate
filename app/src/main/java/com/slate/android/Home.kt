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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slate.android.storage.Volume
import java.io.File

@Composable
internal fun HomeScreen(primary: File, volumes: List<Volume>, analyze: (File) -> Unit, device: () -> Unit) {
    val context = LocalContext.current
    val capacity = primary.totalSpace
    val free = primary.usableSpace
    val used = (capacity-free).coerceAtLeast(0)
    val fraction = if(capacity>0) (used.toDouble()/capacity).toFloat().coerceIn(0f,1f) else 0f
    val fmt: (Long)->String = { Formatter.formatFileSize(context,it) }
    var memory by remember { mutableStateOf(0f) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var temperature by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(context) {
        while (true) {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            memory = if (info.totalMem > 0) (1.0 - info.availMem.toDouble()/info.totalMem).toFloat().coerceIn(0f,1f) else 0f
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
            battery = if(level >= 0 && scale > 0) (100f*level/scale).toInt().coerceIn(0,100) else null
            temperature = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10f)
            delay(2000)
        }
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Internal storage",style=MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(fmt(used),style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
                        Text("${(fraction*100).toInt()}%",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Text(fmt(free)+" available · "+fmt(capacity)+" total",style=MaterialTheme.typography.bodySmall)
                    Button(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search,null); Spacer(Modifier.width(8.dp)); Text("Analyze storage")
                    }
                }
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Device",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                TextButton(onClick=device){Text("Details")}
            }
            Card(onClick=device,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
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
                        HomeGauge("Memory",memory,"${(memory*100).toInt()}%",Modifier.weight(1f))
                        HomeGauge("Battery",(battery ?: 0)/100f,battery?.let{"$it%"} ?: "—",Modifier.weight(1f))
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
                                Text(temperature?.let { "${it.toInt()}°C" } ?: "—",style=MaterialTheme.typography.titleMedium)
                            }
                            Text("Battery temp.",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        val removable=volumes.filter{it.root!=primary}
        if(removable.isNotEmpty()) item { Text("External storage",style=MaterialTheme.typography.titleMedium) }
        items(removable,key={it.root.path}){volume-> ElevatedCard(onClick={analyze(volume.root)},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(volume.label)},supportingContent={Text(fmt(volume.root.totalSpace)+" capacity")},leadingContent={Icon(Icons.Default.List,null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))} }
    }

}

@Composable
private fun HomeGauge(label: String, fraction: Float, value: String, modifier: Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(64.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=5.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
        Text(label,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ScanLoading(state: com.slate.android.storage.ScanState, cancel: ()->Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Analyzing storage",style=MaterialTheme.typography.headlineSmall)
        Text("Measuring files and folders",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(Formatter.formatFileSize(context,state.measuredBytes),style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                Text("Measured so far",style=MaterialTheme.typography.labelMedium)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column { Text("Items scanned",style=MaterialTheme.typography.labelMedium); Text(java.text.NumberFormat.getIntegerInstance().format(state.visited),style=MaterialTheme.typography.titleLarge) }
                    Column { Text("Elapsed",style=MaterialTheme.typography.labelMedium); Text("${state.elapsedMs/60000}m ${state.elapsedMs/1000%60}s",style=MaterialTheme.typography.titleLarge) }
                }
                Text("Current location",style=MaterialTheme.typography.labelMedium)
                Text(state.currentPath.ifBlank{state.directory?.path.orEmpty()},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick=cancel,modifier=Modifier.fillMaxWidth()) { Text("Cancel scan") }
    }
}
