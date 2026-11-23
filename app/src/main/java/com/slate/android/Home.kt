// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Internal storage",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(used),style=MaterialTheme.typography.headlineMedium)
                            Text("of "+fmt(capacity),style=MaterialTheme.typography.bodySmall)
                        }
                        Box(Modifier.size(70.dp),contentAlignment=Alignment.Center) {
                            CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),strokeWidth=6.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
                            Text("${(fraction*100).toInt()}%",style=MaterialTheme.typography.titleMedium)
                        }
                    }
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Text(fmt(free)+" available",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("Tools",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp)) }
        item { ElevatedCard(onClick={analyze(primary)},modifier=Modifier.fillMaxWidth()) { ListItem(headlineContent={Text("Storage analyzer")},supportingContent={Text("Explore files and find what takes up space")},leadingContent={Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) } }
        item { ElevatedCard(onClick=device,modifier=Modifier.fillMaxWidth()) { ListItem(headlineContent={Text("Device information")},supportingContent={Text("Hardware, system, battery and display")},leadingContent={Icon(Icons.Default.Info,null,tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) } }
        val removable=volumes.filter{it.root!=primary}
        if(removable.isNotEmpty()) item { Text("External storage",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp)) }
        items(removable,key={it.root.path}){volume-> ElevatedCard(onClick={analyze(volume.root)},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(volume.label)},supportingContent={Text(fmt(volume.root.totalSpace)+" capacity")},leadingContent={Icon(Icons.Default.List,null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))} }
    }
}

@Composable
internal fun ScanLoading(state: com.slate.android.storage.ScanState, cancel: ()->Unit) {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(116.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(Modifier.fillMaxSize(),strokeWidth=6.dp)
            Icon(Icons.Default.Search,null,Modifier.size(38.dp),tint=MaterialTheme.colorScheme.primary)
        }
        Text("Scanning storage",style=MaterialTheme.typography.headlineSmall)
        Text(java.text.NumberFormat.getIntegerInstance().format(state.visited)+" items visited",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Current scan",style=MaterialTheme.typography.labelLarge)
                Text(state.currentPath.ifBlank{state.directory?.name.orEmpty()},maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Measured so far");Text(Formatter.formatFileSize(context,state.measuredBytes))}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Elapsed");Text("${state.elapsedMs/60000}m ${state.elapsedMs/1000%60}s")}
            }
        }
        OutlinedButton(onClick=cancel){Text("Cancel scan")}
    }
}
