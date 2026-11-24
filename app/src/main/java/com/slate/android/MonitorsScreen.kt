// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slate.android.storage.ShizukuAccessRow
import com.slate.android.storage.shizukuGranted
import kotlinx.coroutines.delay

@Composable
internal fun MonitorsScreen(){
    val context=LocalContext.current
    var running by remember{mutableStateOf(PerformanceOverlayService.running)}
    var authorized by remember{mutableStateOf(shizukuGranted())}
    var overlayAllowed by remember{mutableStateOf(Settings.canDrawOverlays(context))}
    var packageName by remember{mutableStateOf(PerformanceOverlayService.packageName)}
    var fps by remember{mutableStateOf(PerformanceOverlayService.fps)}
    var failed by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        while(true){
            running=PerformanceOverlayService.running
            authorized=shizukuGranted()
            overlayAllowed=Settings.canDrawOverlays(context)
            packageName=PerformanceOverlayService.packageName
            fps=PerformanceOverlayService.fps
            delay(500)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(stringResource(R.string.tool_monitors),style=MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("FPS",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fps?:"—",style=MaterialTheme.typography.displayMedium)
                Text(packageName?:stringResource(R.string.monitor_no_app),style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(!authorized)ShizukuAccessRow()
        if(!overlayAllowed&&!running)OutlinedButton(onClick={
            failed=runCatching{context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+context.packageName)))}.isFailure
        },modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.monitor_overlay_permission))}
        Button(enabled=running||(authorized&&overlayAllowed),onClick={
            failed=runCatching{
                if(running)context.stopService(Intent(context,PerformanceOverlayService::class.java))
                else androidx.core.content.ContextCompat.startForegroundService(context,Intent(context,PerformanceOverlayService::class.java))
            }.isFailure
        },modifier=Modifier.fillMaxWidth()){Text(stringResource(if(running)R.string.monitor_stop else R.string.monitor_start))}
        if(failed)Text(stringResource(R.string.monitor_failed),color=MaterialTheme.colorScheme.error)
        if(running&&fps==null)Text(stringResource(R.string.monitor_waiting),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
