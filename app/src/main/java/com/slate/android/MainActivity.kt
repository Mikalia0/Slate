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
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slate.android.storage.*
import com.slate.android.device.DeviceScreen
import com.slate.android.device.rememberDeviceStatus
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
            val storageModel:StorageModel = viewModel()
            val history=storageModel.history
            val dashboard=remember{DashboardPreferences(this@MainActivity)}
            var settings by rememberSaveable { mutableStateOf(false) }
            val screenState=rememberSaveableStateHolder()
            var manualUpdateRequest by remember { mutableIntStateOf(0) }
            val onMode: (SlateTheme)->Unit = { mode=it; prefs.edit().putString("theme",it.name).apply() }
            val onDynamic: (Boolean)->Unit = { dynamic=it; prefs.edit().putBoolean("dynamic",it).apply() }
            SlateAppearance(mode,dynamic,this@MainActivity) {
                when {
                    !setup -> Onboarding(granted,::requestStorage,mode,dynamic,onMode,onDynamic) { setup=true; prefs.edit().putBoolean("setup",true).apply() }
                    settings -> {
                        BackHandler { settings=false }
                        Scaffold(contentWindowInsets=WindowInsets.safeDrawing) { padding ->
                            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={settings=false}){Icon(Icons.Default.ArrowBack,"Back")};Text(tr("Settings"),style=MaterialTheme.typography.headlineSmall)}
                                Text(tr("Appearance"),style=MaterialTheme.typography.titleMedium)
                                ThemeOptions(mode,dynamic,onMode,onDynamic)
                                HorizontalDivider()
                                ListItem(headlineContent={Text(tr("Storage access"))},supportingContent={Text(tr(if(granted)"Granted" else "Not granted"))},trailingContent={TextButton(onClick=::requestStorage){Text(tr("Manage"))}})
                                ShizukuAccessRow()
                                HorizontalDivider()
                                DashboardSettings(dashboard)
                                HorizontalDivider()
                                ScheduledCleanupSettings()
                                HorizontalDivider()
                                ExclusionEditor(storageModel.exclusions,onChanged={storageModel.invalidate()})
                                HorizontalDivider()
                                UpdateSettings(onCheck={manualUpdateRequest++})
                                Text("GPL-3.0-only")
                            }
                        }
                    }
                    else -> screenState.SaveableStateProvider("slate") { Slate(granted, volumes, ::requestStorage, onSettings={settings=true}, history=history,model=storageModel,dashboard=dashboard) }
                }
                if(setup) UpdatePrompt(manualUpdateRequest)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slate(granted: Boolean, volumes: List<Volume>, request: () -> Unit, onSettings: () -> Unit, history: StorageHistory, model: StorageModel = viewModel(),dashboard:DashboardPreferences) {
    val context = LocalContext.current
    val primary = remember { Environment.getExternalStorageDirectory() }
    var selectedPath by rememberSaveable { mutableStateOf(primary.absolutePath) }
    val root = remember(selectedPath) { File(selectedPath) }
    val selectedVolume = volumes.firstOrNull { it.root.absolutePath == selectedPath }
    val state by model.state.collectAsState()
    LaunchedEffect(granted) { if(granted)model.checkSession() }
    var analyzer by rememberSaveable { mutableStateOf(false) }
    var device by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable{mutableStateOf(false)}
    var cleanup by rememberSaveable{mutableStateOf(false)}
    var apps by rememberSaveable { mutableStateOf(false) }
    var returnToApps by rememberSaveable{mutableStateOf(false)}
    var appCacheSort by rememberSaveable{mutableStateOf(false)}
    val appsState=rememberSaveableStateHolder()
    var personalOnly by rememberSaveable{mutableStateOf(false)}
    var devicePage by rememberSaveable { mutableIntStateOf(0) }
    val deviceStatus=rememberDeviceStatus()
    val homeScroll=rememberLazyGridState()
    var about by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    val scrollStates=remember{mutableMapOf<String,Pair<Int,Int>>()}
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
    var expandedCategories by rememberSaveable{mutableStateOf(false)}
    var selectedCategory by remember{mutableStateOf<CategoryStats?>(null)}
    val directory = state.directory?.takeIf { it.toPath().startsWith(root.toPath()) } ?: root
    fun openFolder(file: File) { selection = emptySet(); largeFiles = false; query = ""; search = false; model.open(file) }
    fun selectEntry(entry: Entry) { if (entry.directory) openFolder(entry.file) else detail = entry }
    fun back() {
        if (deleting) return
        if (selection.isNotEmpty()) selection = emptySet()
        else if (search) { search = false; query = "" }
        else if (largeFiles) { largeFiles = false; query = "" }
        else if (directory != root && directory.parentFile != null) openFolder(directory.parentFile!!)
        else if(personalOnly){personalOnly=false}
        else { model.cancel(); analyzer = false; if(returnToApps){returnToApps=false;apps=true;selectedPath=primary.absolutePath} }
    }
    BackHandler(analyzer || device || apps || cleanup || showHistory) { if(showHistory)showHistory=false else if(cleanup)cleanup=false else if(apps) {apps=false;appsState.removeState("apps")} else if (device) device = false else back() }
    val config=LocalConfiguration.current
    val landscape=config.screenWidthDp>config.screenHeightDp && config.screenWidthDp>=600
    val screenKey=when{showHistory->"history";cleanup->"cleanup";apps->"apps";device->"device";analyzer->"storage";else->"home"}
    val screenAlpha=remember{Animatable(1f)}
    var previousScreen by remember{mutableStateOf(screenKey)}
    LaunchedEffect(screenKey){if(previousScreen!=screenKey){previousScreen=screenKey;screenAlpha.snapTo(.82f);screenAlpha.animateTo(1f,tween(120))}}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing){padding->
        Row(Modifier.fillMaxSize().padding(padding)){
            Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer{alpha=screenAlpha.value}){
        if(showHistory) {
            Box(Modifier.fillMaxSize()){HistoryScreen(history,onBack={showHistory=false})}
        } else if(cleanup) {
            Box(Modifier.fillMaxSize()){CleanupScreen(primary,granted,request,excluded=model.exclusions.paths,onBack={cleanup=false},onChanged={model.invalidate()},review={folder->cleanup=false;analyzer=true;selectedPath=primary.absolutePath;selection=emptySet();largeFiles=false;query="";search=false;model.open(folder)})}
        } else if(apps) {
            appsState.SaveableStateProvider("apps"){Box(Modifier.fillMaxSize()){AppsStorageScreen(onBack={apps=false},reviewFolder={folder->returnToApps=true;apps=false;analyzer=true;personalOnly=false;selectedPath=folder.absolutePath;openFolder(folder)},initialCacheSort=appCacheSort,onCacheChanged={model.invalidate()})}}
        } else if (device) {
            Box(Modifier.fillMaxSize()) { DeviceScreen(onBack = { device = false },status=deviceStatus,initialPage=devicePage) }
        } else Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (analyzer) IconButton(onClick = ::back) { Icon(Icons.Default.ArrowBack,"Back") }
                else Image(painterResource(R.drawable.ic_slate), null, Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (analyzer) tr("Storage") else "Slate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (analyzer) IconButton(onClick = { selection = emptySet(); model.open(directory, refresh = true) }, enabled = granted && !state.running && !deleting) { Icon(Icons.Default.Refresh,"Refresh") }
                else { IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Settings")}; IconButton(onClick={about=true}){Icon(Icons.Default.Info,"About")} }
            }
            if (!analyzer) {
                HomeScreen(primary,volumes,analyze={file ->
                    personalOnly=false;selectedPath=file.absolutePath; analyzer=true; selection=emptySet(); largeFiles=false; query=""; search=false; if(granted)model.open(file)
                },device={devicePage=0;device=true},history=history,review={folder->
                    personalOnly=false;selectedPath=primary.absolutePath; analyzer=true; selection=emptySet(); largeFiles=false; query="";search=false
                    if(granted) model.open(folder)
                },status=deviceStatus,openDevicePage={page->devicePage=page;device=true},openApps={appCacheSort=false;appsState.removeState("apps");apps=true},openCache={appCacheSort=true;appsState.removeState("apps");apps=true},openCleanup={cleanup=true},openHistory={showHistory=true},dashboard=dashboard,scrollState=homeScroll)
            } else if (!granted) {
                LaunchedEffect(granted) { model.cancel() }
                Text(tr("Storage access"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Allow access to shared storage to measure folders. Slate does not delete anything during a scan."))
                Button(onClick = request) { Text(tr("Grant access")) }
            } else {
                LaunchedEffect(granted, analyzer, selectedPath) {
                    if (state.directory == null || !state.directory!!.toPath().startsWith(root.toPath())) model.open(root)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={openFolder(root)},enabled=!deleting){Text(if(returnToApps)root.name else selectedVolume?.label?.let{tr(it)} ?: tr("Internal storage"))}
                    if(directory!=root) {
                        var ancestor=root
                        directory.relativeTo(root).path.split(File.separatorChar).forEach { part ->
                            val next=File(ancestor,part)
                            Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick={openFolder(next)},enabled=!deleting){Text(part)}
                            ancestor=next
                        }
                    }
                }
                if(state.partialRefresh && !state.saved) Row(verticalAlignment=Alignment.CenterVertically){
                    Text(tr("Partially refreshed index"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={selection=emptySet();model.open(root,refresh=true)}){Text(tr("Refresh all"))}
                }
                if(state.saved) Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(tr("Saved results"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={selection=emptySet();model.open(root,refresh=true)}){Text(tr("Refresh"))}
                }
                if (state.running) ScanLoading(state, model::cancel)
                if (state.error != null && !state.running) {
                    Text(tr(state.error.orEmpty()),style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={model.open(directory,refresh=true)}){Text(tr("Scan again"))}
                }
                val snapshot = state.snapshot?.takeIf { it.directory == directory }
                val categories by produceState<List<CategoryStats>?>(null,state.scanId,expandedCategories,analyzer){
                    value=if(expandedCategories && directory==primary && snapshot!=null)model.personalCategories()else null
                }
                if (snapshot != null) {
                    val measuredEntries=remember(snapshot,personalOnly){snapshot.entries.filterNot{personalOnly && directory==primary && it.file.name=="Android"}}
                    val source = if(largeFiles) snapshot.largestFiles.filterNot{personalOnly && directory==primary && it.file.toPath().startsWith(File(primary,"Android").toPath())} else measuredEntries
                    val entries = remember(snapshot, sortMode, largeFiles, query,personalOnly) {
                        val filtered=source.filter{it.file.name.contains(query,true) || (largeFiles && it.file.relativeTo(snapshot.directory).path.contains(query,true))}
                        when(sortMode) {
                            1 -> filtered.sortedBy{it.file.name.lowercase()}
                            2 -> filtered.sortedBy{it.bytes}
                            3 -> filtered.sortedBy{it.file.extension.lowercase()}
                            4 -> filtered.sortedByDescending{it.modified}
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
                                        Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.selected_count,selected.size),style=MaterialTheme.typography.titleMedium)
                                        Text(fmt(selected.sumOf{it.bytes}),style=MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick={confirmDelete=true},enabled=!returnToApps && !deleting && !state.saved && !state.partialRefresh && selected.isNotEmpty()){Icon(Icons.Default.Delete,"Delete selected")}
                                }
                                TextButton(onClick={selection=entries.map{it.file.path}.toSet()},enabled=!deleting){Text(tr("Select visible"))}
                            }
                        }
                    }
                    if(deleting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(tr("Deleting selected items…")) }
                    val scrollKey=directory.path+":"+largeFiles+":"+gridView+":"+personalOnly
                    val restored=scrollStates[scrollKey] ?: (0 to 0)
                    key(scrollKey) {
                    val listState=rememberLazyGridState(restored.first,restored.second)
                    LaunchedEffect(listState) { snapshotFlow{listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset}.collect{scrollStates[scrollKey]=it} }
                    LazyVerticalGrid(columns=GridCells.Fixed(if(gridView)if(landscape)3 else 2 else 1),state=listState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        item(span={GridItemSpan(maxLineSpan)}) {
                            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(tr("Measured size"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(fmt(if(personalOnly && directory==primary)measuredEntries.sumOf{it.bytes}else snapshot.bytes),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                                    Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.files_folders_count,java.text.NumberFormat.getIntegerInstance().format(measuredEntries.sumOf{it.files}),measuredEntries.count{it.directory}),style=MaterialTheme.typography.bodySmall)
                                    if(measuredEntries.sumOf{it.skipped}>0 || snapshot.rootUnreadable) Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.inaccessible_count,measuredEntries.sumOf{it.skipped}+if(snapshot.rootUnreadable)1L else 0L),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if(directory==primary && selection.isEmpty() && !largeFiles && !personalOnly) {
                                val android=snapshot.entries.firstOrNull{it.file.name=="Android" && it.directory}
                                val personal=snapshot.entries.filter{it.file.name!="Android"}.sumOf{it.bytes}
                                Card(Modifier.fillMaxWidth().padding(top=8.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column {
                                        ListItem(modifier=Modifier.clickable{personalOnly=true;selection=emptySet()},headlineContent={Text(tr("User files"))},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){Text(fmt(personal),color=MaterialTheme.colorScheme.primary);Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(18.dp))}},leadingContent={Icon(painterResource(R.drawable.ic_storage_folder),null,Modifier.size(24.dp))},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                                        TextButton(onClick={expandedCategories=!expandedCategories},modifier=Modifier.padding(horizontal=8.dp)){Text(tr(if(expandedCategories)"Hide categories" else "File categories"))}
                                        if(expandedCategories){
                                            val breakdown=categories
                                            if(breakdown==null)Text(tr("Category details require a complete index."),Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
                                            else breakdown.forEach{stats->
                                                ListItem(modifier=Modifier.clickable{selectedCategory=stats},headlineContent={Text(tr(stats.category.label))},
                                                    trailingContent={Text(fmt(stats.bytes),color=MaterialTheme.colorScheme.primary)},
                                                    colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                                            }
                                        }
                                        if(android!=null) ListItem(modifier=Modifier.clickable{openFolder(android.file)},headlineContent={Text(tr("Android shared files"))},trailingContent={Text(fmt(android.bytes),color=MaterialTheme.colorScheme.primary)},leadingContent={Icon(painterResource(R.drawable.ic_device_system),null,Modifier.size(24.dp))},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                                        ListItem(modifier=Modifier.clickable{appCacheSort=false;appsState.removeState("apps");apps=true},headlineContent={Text(tr("App storage"))},trailingContent={Icon(Icons.Default.KeyboardArrowRight,"Open")},leadingContent={Icon(painterResource(R.drawable.ic_device_overview),null,Modifier.size(24.dp))},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                                        
                                    }
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                FilterChip(selected=!largeFiles,onClick={largeFiles=false;selection=emptySet();query=""},enabled=!deleting,label={Text(tr("Folders"))})
                                FilterChip(selected=largeFiles,onClick={largeFiles=true;selection=emptySet();query=""},enabled=!deleting,label={Text(tr("Largest files"))})
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(tr(if(personalOnly && directory==primary)"User files" else if(largeFiles)"Files" else "Contents"),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                IconButton(onClick={gridView=!gridView}){Icon(painterResource(if(gridView)R.drawable.ic_device_storage else R.drawable.ic_device_overview),"Change layout",Modifier.size(22.dp))}
                                IconButton(onClick={search=!search;if(!search)query=""},enabled=!deleting){Icon(Icons.Default.Search,"Search")}
                                Box {
                                    TextButton(onClick={sortMenu=true},enabled=!deleting){Text(tr(listOf("Size ↓","Name","Size ↑","Type","Date ↓")[sortMode]))}
                                    DropdownMenu(expanded=sortMenu,onDismissRequest={sortMenu=false}) {
                                        listOf("Largest first","Name","Smallest first","File type","Newest first").forEachIndexed{index,label->
                                            DropdownMenuItem(text={Text(tr(label))},onClick={sortMode=index;sortMenu=false})
                                        }
                                    }
                                }
                            }
                            if(search) OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text(tr("Search files"))},singleLine=true,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp))
                            if(largeFiles && state.largestLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else if(entries.isEmpty()) Text(tr(if(snapshot.rootUnreadable)"Folder not accessible" else "No matching items"))
                            }
                        }
                        items(entries,key={it.file.path}){entry->
                            val checked=entry.file.path in selection
                            if(gridView) {
                                Card(Modifier.fillMaxWidth().padding(vertical=4.dp).combinedClickable(enabled=!deleting,onClick={if(selection.isNotEmpty())toggle(entry)else selectEntry(entry)},onLongClick={if(!returnToApps)toggle(entry)}),
                                    colors=CardDefaults.cardColors(containerColor=if(checked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            Icon(painterResource(fileIcon(entry)),null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.weight(1f))
                                            if(selection.isNotEmpty()) Checkbox(checked=checked,onCheckedChange={toggle(entry)},enabled=!deleting)
                                        }
                                        Text(entry.file.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                        Text(fmt(entry.bytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else Card(Modifier.fillMaxWidth().padding(vertical=2.dp),colors=CardDefaults.cardColors(containerColor=if(checked)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                            ListItem(
                                modifier=Modifier.fillMaxWidth().combinedClickable(enabled=!deleting,onClick={if(selection.isNotEmpty())toggle(entry) else selectEntry(entry)},onLongClick={toggle(entry)}),
                                headlineContent={Text(entry.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                                supportingContent={
                                    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                        Text(if(largeFiles)entry.file.relativeTo(snapshot.directory).path else if(entry.directory)androidx.compose.ui.res.stringResource(com.slate.android.R.string.files_count,java.text.NumberFormat.getIntegerInstance().format(entry.files)) else entry.file.extension.uppercase().ifBlank{"File"},maxLines=1,overflow=TextOverflow.Ellipsis)
                                    }
                                },
                                leadingContent={
                                    if(selection.isNotEmpty()) Checkbox(checked=checked,onCheckedChange={toggle(entry)},enabled=!deleting)
                                    else Icon(painterResource(fileIcon(entry)),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                },
                                trailingContent={Column(horizontalAlignment=Alignment.End){Text(fmt(entry.bytes),color=MaterialTheme.colorScheme.primary);if(entry.skipped>0) Text(tr("Incomplete"),style=MaterialTheme.typography.labelSmall)}},
                                colors=ListItemDefaults.colors(containerColor=if(checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            )
                            }
                        }
                    }
                    }
                    if(confirmDelete && !returnToApps && !state.saved && !state.partialRefresh) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.confirm_delete_count,selected.size))},
                        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(fmt(selected.sumOf{it.bytes}),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.error)
                            Text(tr("This cannot be undone. Inaccessible or incomplete items are skipped."))
                            selected.take(4).forEach{Text(it.file.relativeTo(root).path,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}
                            if(selected.size>4)Text(androidx.compose.ui.res.stringResource(com.slate.android.R.string.additional_count,selected.size-4))
                        }},
                        dismissButton={TextButton(onClick={confirmDelete=false}){Text(tr("Cancel"))}},
                        confirmButton={Button(colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={
                            confirmDelete=false;deleting=true
                            val targets=selected.toList()
                            scope.launch {
                                try {
                                    val result=withContext(Dispatchers.IO){deleteSelected(root,targets)}
                                    deletionMessage=context.getString(R.string.delete_result_count,result.first,result.second)
                                    selection=emptySet()
                                    model.open(directory,refresh=true)
                                } finally {deleting=false}
                            }
                        }){Text(tr("Delete permanently"))}})
                }
                deletionMessage?.let { message -> AlertDialog(onDismissRequest={deletionMessage=null},title={Text(tr("Deletion result"))},text={Text(tr(message))},confirmButton={TextButton(onClick={deletionMessage=null}){Text(tr("Done"))}}) }

            }
        }
            }
        }
    }
    selectedCategory?.let{stats->StorageCategoryDetails(stats,fmt,onDismiss={selectedCategory=null},onFile={detail=it})}
    detail?.let { entry ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(entry.file.name) },
            text = { Text(entry.file.path + "\n\n" + fmt(entry.bytes) + if (entry.skipped > 0) "\nSize unavailable or incomplete" else "") },
            confirmButton = { TextButton(onClick = { detail=null; if(!openSharedFile(context,root,entry.file,false)) deletionMessage="Unable to open file" }) { Text(tr("Open")) } },
            dismissButton = { TextButton(onClick={detail=null;if(!openSharedFile(context,root,entry.file,true))deletionMessage="Unable to share file"}){Text(tr("Share"))} })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Slate") },
        text = { Text("0.5.0-dev • GPL-3.0-only\nProvided without warranty. SD Maid SE is a feature reference; no upstream code is included yet. See the project LICENSE for redistribution terms.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text(tr("Close")) } })
}
