// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val reader = remember(context) { DeviceReader(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tabs = listOf("Overview", "Hardware", "System", "Battery", "Storage", "Network", "Sensors", "Camera")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fixed by remember { mutableStateOf<StaticInfo?>(null) }
    var live by remember { mutableStateOf<LiveInfo?>(null) }
    var extra by remember { mutableStateOf<List<Section>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(reader, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (fixed == null) {
                fixed = withContext(Dispatchers.IO) { runCatching { reader.static() }.getOrNull() }
                error = fixed == null
            }
        }
    }
    LaunchedEffect(reader, lifecycle, tab) {
        extra = emptyList()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            when (tab) {
                0, 1, 2, 3 -> while (true) {
                    live = withContext(Dispatchers.IO) { runCatching { reader.live() }.getOrNull() }
                    delay(2000)
                }
                4 -> extra = withContext(Dispatchers.IO) { runCatching { reader.storage() }.getOrDefault(emptyList()) }
                5 -> while (true) {
                    extra = withContext(Dispatchers.IO) { runCatching { listOf(reader.network()) }.getOrDefault(emptyList()) }
                    delay(3000)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Device information", style = MaterialTheme.typography.headlineSmall)
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, name -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) }) }
        }
        key(tab) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                if (tab == 0) {
                    item { DeviceOverview(live, fixed) { tab = it } }
                } else {
                    val sections = when (tab) {
                        1 -> fixed?.hardware.orEmpty() + listOfNotNull(live?.memory, live?.cpu, live?.gpu, live?.thermal, live?.display)
                        2 -> fixed?.system.orEmpty() + listOfNotNull(live?.uptime, live?.thermal)
                        3 -> listOfNotNull(live?.battery)
                        4, 5 -> extra
                        6 -> fixed?.sensors.orEmpty()
                        7 -> fixed?.cameras.orEmpty()
                        else -> emptyList()
                    }
                    if (sections.isEmpty()) item { Text(if (error || fixed != null) "No information available" else "Reading device information…") }
                    items(sections) { section -> InfoCard(section) }
                    item { Text("Unavailable means Android or the device did not expose this value.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(section: Section) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            section.rows.forEach { info ->
                if (info.value.length > 100 || info.value.contains('\n')) {
                    var expanded by remember(info.value) { mutableStateOf(false) }
                    Text(info.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(if (expanded) info.value else info.value.take(100) + "…", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show all") }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(info.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.44f), style = MaterialTheme.typography.bodyMedium)
                    Text(info.value, modifier = Modifier.weight(0.56f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
