// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.slate.android.device.batteryWatts
import com.slate.android.gpu.KgslLoadFallback
import java.io.File
import java.util.Locale

internal enum class HudMetric(val bit:Int,val label:Int){
    FPS(1,R.string.hud_fps),FRAMES(2,R.string.hud_frames),GPU(4,R.string.hud_gpu),
    CPU(8,R.string.hud_cpu),RAM(16,R.string.hud_ram),BATTERY(32,R.string.hud_battery),
    PACKAGE(64,R.string.hud_package)
}
internal const val HUD_DEFAULT=1 or 2 or 4 or 8 or 16 or 32
internal const val HUD_MINIMAL=1
internal const val HUD_COMPACT=1 or 4 or 8 or 16
internal const val HUD_ALL=127
internal fun Int.shows(metric:HudMetric)=this and metric.bit!=0
internal data class HudRow(val label:String,val value:String,val detail:String="",val extra:String="",val fraction:Float?=null)
internal data class HudSnapshot(val fps:String?=null,val pkg:String?=null,val intervals:List<Double> = emptyList(),val rows:List<HudRow> = emptyList())
internal class HudMetrics(private val context:Context){
    private fun read(path:String)=runCatching{File(path).bufferedReader().use{it.readLine()?.trim()}}.getOrNull()
    private fun number(path:String)=read(path)?.toLongOrNull()
    private fun fmt(value:Double)=String.format(Locale.US,"%.1f",value)
    private val base="/sys/class/kgsl/kgsl-3d0"
    private val devfreq by lazy{runCatching{File("/sys/class/devfreq").listFiles()?.firstOrNull{it.name.contains("gpu",true)||it.name.contains("mali",true)||it.name.contains("kgsl",true)}?.path}.getOrNull()}
    private val gpuZones by lazy{runCatching{File("/sys/class/thermal").listFiles().orEmpty().filter{it.name.startsWith("thermal_zone")&&read(it.path+"/type")?.lowercase(Locale.ROOT)?.let{s->s.contains("gpu")||s.contains("gpuss")||s.contains("gfx")}==true}}.getOrDefault(emptyList())}
    private val cpuPaths by lazy{runCatching{File("/sys/devices/system/cpu").listFiles().orEmpty().filter{it.name.matches(Regex("cpu[0-9]+"))}.map{it.path+"/cpufreq/scaling_cur_freq"}}.getOrDefault(emptyList())}
    fun read(mask:Int,cpu:String?):List<HudRow> = buildList{
        if(mask.shows(HudMetric.GPU)){
            val busy=read("$base/gpu_busy_percentage")?.removeSuffix("%")?.trim()?.toDoubleOrNull()?.takeIf{it in 0.0..100.0}
                ?: KgslLoadFallback.busyPercent()?.toDouble()
            val hz=(number("$base/gpuclk")?:devfreq?.let{number("$it/cur_freq")})?.takeIf{it>0}
            val temp=gpuZones.mapNotNull{number(it.path+"/temp")?.takeIf{n->n in -40000L..150000L}}.maxOrNull()
            add(HudRow("GPU",busy?.toInt()?.let{"$it%"}?:"—",hz?.let{"${it/1000000} MHz"}.orEmpty(),temp?.let{"${(it/1000).toInt()}°C"}.orEmpty()))
        }
        if(mask.shows(HudMetric.CPU)){
            val hz=cpuPaths.mapNotNull{number(it)?.takeIf{n->n>0}}.maxOrNull()
            add(HudRow("CPU",cpu?.takeIf{it.isNotBlank()}?.let{"$it%"}?:"—",hz?.let{fmt(it/1_000_000.0)+" GHz"}.orEmpty()))
        }
        if(mask.shows(HudMetric.RAM)){
            val memory=ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
            val fraction=if(memory.totalMem>0)((memory.totalMem-memory.availMem).toDouble()/memory.totalMem).toFloat().coerceIn(0f,1f)else null
            val gib=1073741824.0
            add(HudRow("RAM",fraction?.let{"${(it*100).toInt()}%"}?:"—",extra=if(memory.totalMem>0)fmt((memory.totalMem-memory.availMem).coerceAtLeast(0)/gib)+"/"+fmt(memory.totalMem/gib)+" GiB"else "",fraction=fraction))
        }
        if(mask.shows(HudMetric.BATTERY)){
            val intent=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            fun int(key:String)=intent?.getIntExtra(key,Int.MIN_VALUE)?.takeUnless{it==Int.MIN_VALUE}
            val level=int(BatteryManager.EXTRA_LEVEL);val scale=int(BatteryManager.EXTRA_SCALE)
            val percent=if(level!=null&&scale!=null&&scale>0&&level in 0..scale)level*100/scale else null
            val current=context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val watts=batteryWatts(current,int(BatteryManager.EXTRA_VOLTAGE))
            add(HudRow("BAT",percent?.let{"$it%"}?:"—",watts?.let{fmt(it)+" W"}.orEmpty(),int(BatteryManager.EXTRA_TEMPERATURE)?.let{"${it/10}°C"}.orEmpty()))
        }
    }
}
