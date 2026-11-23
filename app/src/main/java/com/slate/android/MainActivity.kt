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
                                Text("Slate 0.5.0-dev · GPL-3.0-only")
                            }
                        }
                    }
                    else -> Slate(granted, volumes, ::requestStorage, onSettings={settings=true})
                }
            }
        }
    }
}

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
    var sortName by rememberSaveable { mutableStateOf(false) }
    var largeFiles by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Entry?>(null) }
    val fmt: (Long) -> String = { Formatter.formatFileSize(context, it) }
    val directory = state.directory?.takeIf { it.toPath().startsWith(root.toPath()) } ?: root
    fun openFolder(file: File) { largeFiles = false; query = ""; model.open(file) }
    fun selectEntry(entry: Entry) { if (entry.directory) openFolder(entry.file) else detail = entry }
    fun back() {
        if (largeFiles) { largeFiles = false; query = "" }
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
                if (analyzer) IconButton(onClick = { model.open(directory) }, enabled = granted && !state.running) { Icon(Icons.Default.Refresh,"Refresh") }
                else { IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings")}; IconButton(onClick={about=true}){Icon(Icons.Default.Info,"About")} }
            }
            if (!analyzer) {
                HomeScreen(primary,volumes,analyze={file ->
                    selectedPath=file.absolutePath; analyzer=true; largeFiles=false; query=""; if(granted)model.open(file)
                },device={device=true})
            } else if (!granted) {
                LaunchedEffect(granted) { model.cancel() }
                Text("Storage access", style = MaterialTheme.typography.titleLarge)
                Text("Allow access to shared storage to measure folders. Slate does not delete anything during a scan.")
                Button(onClick = request) { Text("Grant access") }
            } else {
                LaunchedEffect(granted, analyzer, selectedPath) {
                    if (state.directory == null || !state.directory!!.toPath().startsWith(root.toPath()) || state.error == "Scan cancelled") model.open(root)
                }
                Text(if (directory == root) selectedVolume?.label ?: root.name else directory.relativeTo(root).path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.running) ScanLoading(state, model::cancel)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val snapshot = state.snapshot?.takeIf { it.directory == directory }
                if (snapshot != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(fmt(snapshot.bytes), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
                    Text(snapshot.files.toString() + " files • measured logical size")
                    if (snapshot.skipped > 0) Text(snapshot.skipped.toString() + " inaccessible or skipped entries. Totals may be incomplete.", color = MaterialTheme.colorScheme.error)
                    val entries = remember(snapshot, sortName, largeFiles, query) {
                        val source = if (largeFiles) snapshot.largestFiles else snapshot.entries
                        val filtered = source.filter { it.file.name.contains(query, ignoreCase = true) || (largeFiles && it.file.relativeTo(snapshot.directory).path.contains(query, ignoreCase = true)) }
                        if (sortName && !largeFiles) filtered.sortedBy { it.file.name.lowercase() } else filtered
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !largeFiles, onClick = { largeFiles = false; query = "" }, label = { Text("Folders") })
                                FilterChip(selected = largeFiles, onClick = { largeFiles = true; query = "" }, label = { Text("Largest files") })
                            }
                            if (!largeFiles && snapshot.bytes > 0) {
                                TreemapView(snapshot.entries, fmt, ::selectEntry)
                                Text("Tap a tile to explore • small items grouped as Other", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            if (largeFiles) Text("Up to 100 largest accessible files in this folder and its subfolders.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (largeFiles) "Largest files" else "Contents", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (!largeFiles) TextButton(onClick = { sortName = !sortName }) { Text(if (sortName) "Name" else "Size") }
                            }
                            if (entries.isEmpty()) Text(if (snapshot.rootUnreadable) "Folder not accessible" else "No files found")
                        }
                        items(entries, key = { it.file.path }) { entry ->
                            Column(Modifier.fillMaxWidth().clickable { selectEntry(entry) }.padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (entry.directory) "▸" else "·", color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (largeFiles) Text(entry.file.relativeTo(snapshot.directory).path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(fmt(entry.bytes) + if (entry.skipped > 0) " *" else "", color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { if (snapshot.bytes > 0) (entry.bytes.toDouble() / snapshot.bytes).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(3.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
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
