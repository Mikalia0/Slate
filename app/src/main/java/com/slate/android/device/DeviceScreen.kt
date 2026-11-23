// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
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
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val tab = pager.currentPage
    fun select(page: Int) { scope.launch { pager.animateScrollToPage(page) } }
    var fixed by remember { mutableStateOf<StaticInfo?>(null) }
    var live by remember { mutableStateOf<LiveInfo?>(null) }
    var storage by remember { mutableStateOf<List<Section>>(emptyList()) }
    var connection by remember { mutableStateOf<List<Section>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    // Data collection belongs to the visible screen, not to the selected tab.
    // Switching pages neither resets the last readings nor restarts EGL probing.
    LaunchedEffect(reader, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coroutineScope {
                launch {
                    if (fixed == null) {
                        fixed = withContext(Dispatchers.IO) { runCatching { reader.static() }.getOrNull() }
                        error = fixed == null
                    }
                }
                launch {
                    while (true) {
                        val sample = withContext(Dispatchers.IO) { runCatching { reader.live() }.getOrNull() }
                        if (sample != null) { live = sample; connection = listOf(sample.connection) }
                        delay(2000)
                    }
                }
                launch {
                    while (true) {
                        val sample = withContext(Dispatchers.IO) { runCatching { reader.storage() }.getOrNull() }
                        if (sample != null) storage = sample
                        delay(10000)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack,"Back") }
            Text("Device information", style = MaterialTheme.typography.headlineSmall)
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, name -> Tab(selected = tab == i, onClick = { select(i) }, text = { Text(name) }, selectedContentColor=MaterialTheme.colorScheme.primary, unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalPager(state=pager, modifier=Modifier.weight(1f), beyondViewportPageCount=1, verticalAlignment=Alignment.Top) { page ->
            LazyColumn(Modifier.fillMaxSize(), state=rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                if (page == 0) {
                    deviceOverview(live, fixed, ::select)
                } else {
                    val sections = when (page) {
                        1 -> fixed?.hardware.orEmpty() + listOfNotNull(live?.memory, live?.cpu, live?.gpu, live?.thermal, live?.display)
                        2 -> fixed?.system.orEmpty() + listOfNotNull(live?.uptime, live?.thermal)
                        3 -> listOfNotNull(live?.battery)
                        4 -> storage
                        5 -> connection
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
