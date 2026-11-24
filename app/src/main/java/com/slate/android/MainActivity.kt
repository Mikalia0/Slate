// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slate.android.storage.*
import com.slate.android.device.DeviceScreen
import java.io.File


class MainActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var volumes by mutableStateOf<List<Volume>>(emptyList())
    private val legacyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted = hasStorageAccess() }
    private fun hasStorageAccess(): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onResume() { super.onResume(); granted = hasStorageAccess(); volumes = storageVolumes(this) }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName))) }
            catch (_: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        granted = hasStorageAccess()
        volumes = storageVolumes(this)
        setContent {
            val prefs = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            var mode by remember { mutableStateOf(runCatching { SlateTheme.valueOf(prefs.getString("theme","SYSTEM") ?: "SYSTEM") }.getOrDefault(SlateTheme.SYSTEM)) }
            var dynamic by remember { mutableStateOf(prefs.getBoolean("dynamic",false)) }
            var setup by remember { mutableStateOf(prefs.getBoolean("setup",false)) }
            var settings by rememberSaveable { mutableStateOf(false) }
            val onMode: (SlateTheme)->Unit = { mode=it; prefs.edit().putString("theme",it.name).apply() }
            val onDynamic: (Boolean)->Unit = { dynamic=it; prefs.edit().putBoolean("dynamic",it).apply() }
            SlateAppearance(mode,dynamic,this@MainActivity) {
                when {
                    !setup -> Onboarding(granted,::requestStorage,mode,dynamic,onMode,onDynamic) { setup=true; prefs.edit().putBoolean("setup",true).apply() }
                    settings -> {
                        BackHandler { settings=false }
                        Scaffold(contentWindowInsets=WindowInsets.safeDrawing) { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={settings=false}){Icon(Icons.Default.ArrowBack,"Back")};Text("Settings",style=MaterialTheme.typography.headlineSmall)}
                                Text("Appearance",style=MaterialTheme.typography.titleMedium)
                                ThemeOptions(mode,dynamic,onMode,onDynamic)
                                HorizontalDivider()
                                ListItem(headlineContent={Text("Storage access")},supportingContent={Text(if(granted)"Granted" else "Not granted")},trailingContent={TextButton(onClick=::requestStorage){Text("Manage")}})
                                HorizontalDivider()
                                UpdateSettings()
                                Text("GPL-3.0-only")
                            }
                        }
                    }
                    else -> Slate(granted, volumes, ::requestStorage, onSettings={settings=true})
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slate(granted: Boolean, volumes: List<Volume>, request: () -> Unit, onSettings: () -> Unit, model: StorageModel = viewModel()) {
    val context = LocalContext.current
    val primary = remember { Environment.getExternalStorageDirectory() }
    var selectedPath by rememberSaveable { mutableStateOf(primary.absolutePath) }
    val root = remember(selectedPath) { File(selectedPath) }
    val selectedVolume = volumes.firstOrNull { it.root.absolutePath == selectedPath }
    val state by model.state.collectAsState()
    var analyzer by rememberSaveable { mutableStateOf(false) }
    var device by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var showMap by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deletionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var largeFiles by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Entry?>(null) }
    val fmt: (Long) -> String = { Formatter.formatFileSize(context, it) }
    val directory = state.directory?.takeIf { it.toPath().startsWith(root.toPath()) } ?: root
    fun openFolder(file: File) { selection = emptySet(); largeFiles = false; query = ""; search = false; model.open(file) }
    fun selectEntry(entry: Entry) { if (entry.directory) openFolder(entry.file) else detail = entry }
    fun back() {
        if (deleting) return
        if (selection.isNotEmpty()) selection = emptySet()
        else if (search) { search = false; query = "" }
        else if (largeFiles) { largeFiles = false; query = "" }
        else if (directory != root && directory.parentFile != null) openFolder(directory.parentFile!!)
        else { model.cancel(); analyzer = false }
    }
    BackHandler(analyzer || device) { if (device) device = false else back() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        if (device) {
            Box(Modifier.fillMaxSize().padding(padding)) { DeviceScreen(onBack = { device = false }) }
        } else Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (analyzer) IconButton(onClick = ::back) { Icon(Icons.Default.ArrowBack,"Back") }
                else Image(painterResource(R.drawable.ic_slate), null, Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (analyzer) "Storage" else "Slate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (analyzer) IconButton(onClick = { selection = emptySet(); model.open(directory, refresh = true) }, enabled = granted && !state.running && !deleting) { Icon(Icons.Default.Refresh,"Refresh") }
                else { IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings")}; IconButton(onClick={about=true}){Icon(Icons.Default.Info,"About")} }
            }
            if (!analyzer) {
                HomeScreen(primary,volumes,analyze={file ->
                    selectedPath=file.absolutePath; analyzer=true; selection=emptySet(); largeFiles=false; query=""; search=false; if(granted)model.open(file, refresh=true)
                },device={device=true})
            } else if (!granted) {
                LaunchedEffect(granted) { model.cancel() }
                Text("Storage access", style = MaterialTheme.typography.titleLarge)
                Text("Allow access to shared storage to measure folders. Slate does not delete anything during a scan.")
                Button(onClick = request) { Text("Grant access") }
            } else {
                LaunchedEffect(granted, analyzer, selectedPath) {
                    if (state.directory == null || !state.directory!!.toPath().startsWith(root.toPath())) model.open(root)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(directory != root) TextButton(onClick={openFolder(directory.parentFile ?: root)},enabled=!deleting){Text("Up")}
                    Text(if(directory==root) selectedVolume?.label ?: "Internal storage" else directory.relativeTo(root).path,
                        modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                }
                if (state.running) ScanLoading(state, model::cancel)
                if (state.error != null && !state.running) {
                    Text(state.error.orEmpty(),style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={model.open(directory,refresh=true)}){Text("Scan again")}
                }
                val snapshot = state.snapshot?.takeIf { it.directory == directory }
                if (snapshot != null) {
                    val source = if(largeFiles) snapshot.largestFiles else snapshot.entries
                    val entries = remember(snapshot, sortMode, largeFiles, query) {
                        val filtered=source.filter{it.file.name.contains(query,true) || (largeFiles && it.file.relativeTo(snapshot.directory).path.contains(query,true))}
                        when(sortMode) {
                            1 -> filtered.sortedBy{it.file.name.lowercase()}
                            2 -> filtered.sortedBy{it.bytes}
                            3 -> filtered.sortedBy{it.file.extension.lowercase()}
                            else -> filtered.sortedByDescending{it.bytes}
                        }
                    }
                    val selected = source.filter{it.file.path in selection}
                    fun toggle(entry:Entry) { selection = if(entry.file.path in selection) selection-entry.file.path else selection+entry.file.path }
                    if(selection.isNotEmpty()) {
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    IconButton(onClick={selection=emptySet()},enabled=!deleting){Icon(Icons.Default.Close,"Clear selection")}
                                    Column(Modifier.weight(1f)) {
                                        Text("${selected.size} selected",style=MaterialTheme.typography.titleMedium)
                                        Text(fmt(selected.sumOf{it.bytes}),style=MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick={confirmDelete=true},enabled=!deleting && selected.isNotEmpty()){Icon(Icons.Default.Delete,"Delete selected")}
                                }
                                TextButton(onClick={selection=entries.map{it.file.path}.toSet()},enabled=!deleting){Text("Select visible")}
                            }
                        }
                    }
                    if(deleting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Deleting selected items…") }
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp)) {
                        item {
                            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(fmt(snapshot.bytes),style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                                    Text(java.text.NumberFormat.getIntegerInstance().format(snapshot.files)+" files · "+snapshot.entries.count{it.directory}+" folders",style=MaterialTheme.typography.bodySmall)
                                    if(snapshot.skipped>0) Text("${snapshot.skipped} items inaccessible · scan may be incomplete",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                FilterChip(selected=!largeFiles,onClick={largeFiles=false;selection=emptySet();query=""},enabled=!deleting,label={Text("Folders")})
                                FilterChip(selected=largeFiles,onClick={largeFiles=true;selection=emptySet();query=""},enabled=!deleting,label={Text("Largest files")})
                            }
                            if(!largeFiles && snapshot.bytes>0) {
                                TextButton(onClick={showMap=!showMap}){Text(if(showMap)"Hide storage map" else "Show storage map")}
                                if(showMap) TreemapView(snapshot.entries,fmt){ if(!deleting) { if(selection.isNotEmpty()) toggle(it) else selectEntry(it) } }
                            }
                            if(largeFiles) Text("100 largest accessible files",style=MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(if(largeFiles)"Files" else "Contents",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                IconButton(onClick={search=!search;if(!search)query=""},enabled=!deleting){Icon(Icons.Default.Search,"Search")}
                                Box {
                                    TextButton(onClick={sortMenu=true},enabled=!deleting){Text(listOf("Size ↓","Name","Size ↑","Type")[sortMode])}
                                    DropdownMenu(expanded=sortMenu,onDismissRequest={sortMenu=false}) {
                                        listOf("Largest first","Name","Smallest first","File type").forEachIndexed{index,label->
                                            DropdownMenuItem(text={Text(label)},onClick={sortMode=index;sortMenu=false})
                                        }
                                    }
                                }
                            }
                            if(search) OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("Search files")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp))
                            if(entries.isEmpty()) Text(if(snapshot.rootUnreadable)"Folder not accessible" else "No matching items")
                        }
                        items(entries,key={it.file.path}){entry->
                            val checked=entry.file.path in selection
                            ListItem(
                                modifier=Modifier.fillMaxWidth().combinedClickable(enabled=!deleting,onClick={if(selection.isNotEmpty())toggle(entry) else selectEntry(entry)},onLongClick={toggle(entry)}),
                                headlineContent={Text(entry.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                                supportingContent={
                                    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                        Text(if(largeFiles)entry.file.relativeTo(snapshot.directory).path else if(entry.directory)"${entry.files} files" else entry.file.extension.uppercase().ifBlank{"File"},maxLines=1,overflow=TextOverflow.Ellipsis)
                                        LinearProgressIndicator(progress={if(snapshot.bytes>0)(entry.bytes.toDouble()/snapshot.bytes).toFloat().coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth().height(3.dp))
                                    }
                                },
                                leadingContent={
                                    if(selection.isNotEmpty()) Checkbox(checked=checked,onCheckedChange={toggle(entry)},enabled=!deleting)
                                    else Icon(painterResource(if(entry.directory)R.drawable.ic_storage_folder else R.drawable.ic_storage_file),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                },
                                trailingContent={Column(horizontalAlignment=Alignment.End){Text(fmt(entry.bytes),color=MaterialTheme.colorScheme.primary);if(entry.skipped>0) Text("Incomplete",style=MaterialTheme.typography.labelSmall)}},
                                colors=ListItemDefaults.colors(containerColor=if(checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                            )
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Permanently delete ${selected.size} items?")},
                        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("Measured size: "+fmt(selected.sumOf{it.bytes}))
                            Text("Selected files and folders will be deleted permanently. This cannot be undone. Inaccessible or incomplete items will be skipped.")
                            selected.take(4).forEach{Text(it.file.relativeTo(root).path,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
                            if(selected.size>4)Text("And ${selected.size-4} more")
                        }},
                        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}},
                        confirmButton={TextButton(onClick={
                            confirmDelete=false;deleting=true
                            val targets=selected.toList()
                            scope.launch {
                                try {
                                    val result=withContext(Dispatchers.IO){deleteSelected(root,targets)}
                                    deletionMessage="${result.first} items deleted · ${result.second} failed or skipped. A failed folder may have been partially deleted."
                                    selection=emptySet()
                                    model.open(directory,refresh=true)
                                } finally {deleting=false}
                            }
                        }){Text("Delete permanently",color=MaterialTheme.colorScheme.error)}})
                }
                deletionMessage?.let { message -> AlertDialog(onDismissRequest={deletionMessage=null},title={Text("Deletion result")},text={Text(message)},confirmButton={TextButton(onClick={deletionMessage=null}){Text("Done")}}) }

            }
        }
    }
    detail?.let { entry ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(entry.file.name) },
            text = { Text(entry.file.path + "\n\n" + fmt(entry.bytes) + if (entry.skipped > 0) "\nSize unavailable or incomplete" else "") },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Slate") },
        text = { Text("0.5.0-dev • GPL-3.0-only\nProvided without warranty. SD Maid SE is a feature reference; no upstream code is included yet. See the project LICENSE for redistribution terms.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } })
}
