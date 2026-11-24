// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    val prefs=remember{context.getSharedPreferences("monitor",0)}
    var style by remember{mutableStateOf(hudStyle(prefs.getString("style",null)))}
    var mask by remember{mutableIntStateOf(prefs.getInt("metrics",HUD_DEFAULT).and(HUD_ALL).let{if(it==0)HUD_MINIMAL else it})}
    var scale by remember{mutableFloatStateOf(prefs.getFloat("scale",1f).coerceIn(.7f,1.4f))}
    var opacity by remember{mutableFloatStateOf(prefs.getFloat("opacity",.88f).coerceIn(.35f,1f))}
    fun change(value:Int){if(value!=0){mask=value;prefs.edit().putInt("metrics",mask).apply()}}
    LaunchedEffect(Unit){
        while(true){
            running=PerformanceOverlayService.running;authorized=shizukuGranted()
            overlayAllowed=Settings.canDrawOverlays(context);packageName=PerformanceOverlayService.packageName;fps=PerformanceOverlayService.fps
            delay(500)
        }
    }
    val preview=remember(mask,fps,packageName,style,running){
        HudSnapshot(if(running)fps else "115",packageName?:"com.example.game",if(mask.shows(HudMetric.FRAMES))listOf(8.4,8.7,9.0,8.3,8.9,8.4,10.2,8.5,9.1,8.3,8.8,8.7)else emptyList(),buildList{
            if(mask.shows(HudMetric.GPU))add(HudRow("GPU","96%","", "56°C"))
            if(mask.shows(HudMetric.CPU))add(HudRow("CPU","84%","1.80 GHz"))
            if(mask.shows(HudMetric.RAM))add(HudRow("RAM","85%",extra="2.9/3.4 GiB",fraction=.85f))
            if(mask.shows(HudMetric.BATTERY))add(HudRow("BAT","19%","3.1 W","37°C"))
        },if(style==HudStyle.CLASSIC&&mask.shows(HudMetric.CPU))(0..7).map{HudCore(it,listOf(14,9,11,12,17,20,1,2)[it],if(it<4)1300 else if(it<7)1800 else 2400,if(it<4)"0,1,2,3" else if(it<7)"4,5,6" else "7")}else emptyList())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(stringResource(R.string.tool_monitors),style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.hud_style),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            HudStyle.entries.forEach{option->FilterChip(selected=style==option,onClick={style=option;prefs.edit().putString("style",option.name).apply()},label={Text(stringResource(option.label))},modifier=Modifier.weight(1f))}
        }
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
            Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(stringResource(R.string.hud_preview),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                AndroidView(factory={HudOverlayView(it)},update={it.update(mask,preview,scale,opacity,style)})
                if(style==HudStyle.CLASSIC)Text(stringResource(R.string.hud_classic_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(running)Text(packageName?:stringResource(R.string.monitor_no_app),style=MaterialTheme.typography.bodySmall)
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
        if(running&&fps==null&&mask.shows(HudMetric.FPS))Text(stringResource(R.string.monitor_waiting),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.hud_presets),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf(HUD_MINIMAL to R.string.hud_only_fps,HUD_COMPACT to R.string.hud_compact,HUD_DEFAULT to R.string.hud_detailed,HUD_ALL to R.string.hud_all).forEach{(value,label)->FilterChip(selected=mask==value,onClick={change(value)},label={Text(stringResource(label))})}
        }
        Text(stringResource(R.string.hud_metrics),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        HudMetric.entries.forEach{metric->
            Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(metric.label),Modifier.weight(1f))
                Switch(checked=mask.shows(metric),enabled=mask!=metric.bit,onCheckedChange={value->change(if(value)mask or metric.bit else mask and metric.bit.inv())})
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.hud_scale)+" · "+(scale*100).toInt()+"%",style=MaterialTheme.typography.titleSmall)
        Slider(value=scale,onValueChange={scale=it;prefs.edit().putFloat("scale",it).apply()},valueRange=.7f..1.4f)
        Text(stringResource(R.string.hud_opacity)+" · "+(opacity*100).toInt()+"%",style=MaterialTheme.typography.titleSmall)
        Slider(value=opacity,onValueChange={opacity=it;prefs.edit().putFloat("opacity",it).apply()},valueRange=.35f..1f)
    }
}
