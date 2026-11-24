// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slate.android.device.DeviceStatus
import com.slate.android.device.reading
import com.slate.android.device.powerReading
import kotlinx.coroutines.delay
@Composable
private fun MonitorGraph(label:String,value:String,fraction:Float?,history:List<Float>,modifier:Modifier=Modifier){
    val color=MaterialTheme.colorScheme.primary
    Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(label,style=MaterialTheme.typography.labelMedium)
            Text(value,style=MaterialTheme.typography.headlineSmall)
            if(fraction!=null)LinearProgressIndicator(progress={fraction.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            if(history.size>1)Canvas(Modifier.fillMaxWidth().height(48.dp)){
                for(i in 1 until history.size){
                    fun point(j:Int)=Offset(j*size.width/59f,size.height*(1-history[j].coerceIn(0f,1f)))
                    drawLine(color,point(i-1),point(i),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                }
            }
        }
    }
}
@Composable
internal fun MonitorsScreen(status:DeviceStatus){
    val context=LocalContext.current
    val live=status.live
    val memory=live?.meters?.firstOrNull{it.title=="Memory"}
    val gpu=live?.gpu.reading("Utilization").substringBefore(' ').replace(',','.').toFloatOrNull()?.div(100f)?.takeIf{it in 0f..1f}
    val core=live?.cores.orEmpty()
    val clock=core.mapNotNull{it.current}.maxOrNull()
    val max=core.mapNotNull{it.maximum}.maxOrNull()
    val fraction=if(clock!=null&&max!=null&&max>0)(clock.toDouble()/max).toFloat().coerceIn(0f,1f)else null
    var ramHistory by remember{mutableStateOf<List<Float>>(emptyList())}
    var gpuHistory by remember{mutableStateOf<List<Float>>(emptyList())}
    var cpuHistory by remember{mutableStateOf<List<Float>>(emptyList())}
    LaunchedEffect(live){
        memory?.fraction?.let{ramHistory=(ramHistory+it).takeLast(60)}
        gpu?.let{gpuHistory=(gpuHistory+it).takeLast(60)}
        fraction?.let{cpuHistory=(cpuHistory+it).takeLast(60)}
    }
    var overlay by remember{mutableStateOf(PerformanceOverlayService.running)}
    LaunchedEffect(Unit){while(true){overlay=PerformanceOverlayService.running;delay(500)}}
    var failed by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(stringResource(R.string.tool_monitors),style=MaterialTheme.typography.headlineSmall)
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(R.string.monitor_overlay),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.monitor_overlay_desc),style=MaterialTheme.typography.bodySmall)
                Button(onClick={
                    failed=runCatching{
                        if(overlay)context.stopService(Intent(context,PerformanceOverlayService::class.java))
                        else if(!Settings.canDrawOverlays(context))context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+context.packageName)))
                        else androidx.core.content.ContextCompat.startForegroundService(context,Intent(context,PerformanceOverlayService::class.java))
                    }.isFailure
                },modifier=Modifier.fillMaxWidth()){Text(stringResource(if(overlay)R.string.monitor_stop else R.string.monitor_start))}
                if(failed)Text(stringResource(R.string.monitor_failed),color=MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MonitorGraph(stringResource(R.string.monitor_cpu_clock),clock?.let{"${it/1000} MHz"}?:"—",fraction,cpuHistory,Modifier.weight(1f))
            MonitorGraph("GPU",gpu?.let{"${(it*100).toInt()}%"}?:"—",gpu,gpuHistory,Modifier.weight(1f))
        }
        MonitorGraph("RAM",memory?.value?:"—",memory?.fraction,ramHistory,Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            MonitorGraph(tr("Temperature"),live?.battery.reading("Temperature"),null,emptyList(),Modifier.weight(1f))
            MonitorGraph(tr("Battery power"),live?.battery.powerReading()?.value?:"—",null,emptyList(),Modifier.weight(1f))
        }
        Text(stringResource(R.string.monitor_fps_pending),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
