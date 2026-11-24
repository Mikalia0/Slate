// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.slate.android.storage.shizukuGranted
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

internal fun focusedPackage(dump:String):String? {
    val component=Regex("""(?:^|[ {])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+""")
    return dump.lineSequence().filter{it.contains("mCurrentFocus=")||it.contains("mFocusedApp=")}
        .sortedBy{if(it.contains("mCurrentFocus="))0 else 1}
        .mapNotNull{component.find(it)?.groupValues?.get(1)}.firstOrNull()
}
internal fun belongsToLayer(layer:String,pkg:String):Boolean =
    Regex("""(?<![A-Za-z0-9_.])"""+Regex.escape(pkg)+"""(?![A-Za-z0-9_.])""").containsMatchIn(layer)
internal fun presentedFrames(raw:String):List<Long> = raw.lineSequence().mapNotNull{line->
    val columns=line.trim().split(Regex("\\s+"))
    if(columns.size!=3)null else columns[1].toLongOrNull()?.takeIf{it>0&&it<Long.MAX_VALUE}
}.distinct().sorted().toList()
internal fun layerPriority(layer:String):Int = if(layer.contains("SurfaceView",true)) 2 else if(layer.contains("BLAST",true)) 1 else 0
internal data class LayerCount(val name:String,val count:Long)
internal fun timeStatsLayers(raw:String):List<LayerCount>{
    val output=mutableListOf<LayerCount>()
    var name:String?=null
    for(line in raw.lineSequence()){
        val s=line.trim()
        if(s.startsWith("layerName ="))name=s.substringAfter('=').trim()
        else if(s.startsWith("totalFrames =")){
            val n=s.substringAfter('=').trim().toLongOrNull()
            val layer=name
            if(layer!=null&&n!=null&&n>=0)output.add(LayerCount(layer,n))
            name=null
        }
    }
    return output
}
internal fun frameRateFromDelta(before:Long,after:Long,elapsedNs:Long):Double? {
    if(before<0||after<before||elapsedNs<200_000_000L)return null
    return ((after-before)*1_000_000_000.0/elapsedNs).takeIf{it in 0.0..1000.0}
}

class FpsUserService:IFpsService.Stub(){
    private var pkg:String?=null
    private var focusAt=0L
    private var layers=emptyList<String>()
    private var layerAt=0L
    private var latencyFailures=0
    private var timeStats=false
    private var startedStats=false
    private var statsPrevious=emptyMap<String,Long>()
    private var statsAt=0L
    private val frames=mutableMapOf<String,MutableSet<Long>>()
    private fun command(vararg args:String):String{
        check(android.os.Process.myUid() in listOf(0,2000))
        val proc=ProcessBuilder(listOf("/system/bin/dumpsys","-t","2")+args).redirectErrorStream(true).start()
        return try{
            val text=proc.inputStream.bufferedReader().use{reader->
                val out=StringBuilder();val buf=CharArray(4096)
                while(true){val n=reader.read(buf);if(n<0)break;if(out.length+n>2_000_000)error("dump too large");out.append(buf,0,n)}
                out.toString()
            }
            check(proc.waitFor(3,TimeUnit.SECONDS)&&proc.exitValue()==0)
            text
        }finally{if(proc.isAlive)proc.destroyForcibly()}
    }
    private fun reset(){
        layers=emptyList();layerAt=0;frames.clear();latencyFailures=0
        statsPrevious=emptyMap();statsAt=0
    }
    @Synchronized override fun sample():Array<String>{
        val now=SystemClock.elapsedRealtime()
        if(focusAt==0L||now-focusAt>=1000){
            val found=focusedPackage(command("window","displays"))?:focusedPackage(command("window","windows"))
            if(found!=pkg){pkg=found;reset()}
            focusAt=now
        }
        val target=pkg?:return arrayOf("","")
        if(target=="com.slate.android"||target=="moe.shizuku.privileged.api")return arrayOf(target,"")
        if(!timeStats){
            if(layerAt==0L||now-layerAt>=3000){
                layers=command("SurfaceFlinger","--list").lineSequence().map{it.trim()}
                    .filter{belongsToLayer(it,target)&&!it.contains("Background for")&&!it.contains("Splash Screen")&&!it.contains("Input Consumer")}
                    .sortedByDescending(::layerPriority).take(4).toList()
                frames.keys.retainAll(layers.toSet());layerAt=now
            }
            val result=mutableListOf<Pair<String,Double>>()
            for(layer in layers){
                val timestamps=presentedFrames(command("SurfaceFlinger","--latency",layer))
                if(timestamps.isEmpty())continue
                val current=System.nanoTime()
                val history=frames.getOrPut(layer){mutableSetOf()}
                history.addAll(timestamps.filter{it<=current+50_000_000L&&it>=current-1_100_000_000L})
                history.removeAll{it<current-1_000_000_000L}
                if(history.isNotEmpty())result.add(layer to history.size.toDouble())
            }
            val best=result.filter{it.second>0}.maxWithOrNull(compareBy<Pair<String,Double>>{layerPriority(it.first)}.thenBy{it.second})
            if(best!=null){latencyFailures=0;return arrayOf(target,best.second.toString())}
            if(++latencyFailures<3)return arrayOf(target,"")
            timeStats=true
            val existing=timeStatsLayers(command("SurfaceFlinger","--timestats","-dump"))
            if(existing.isEmpty()){
                command("SurfaceFlinger","--timestats","-enable")
                startedStats=true
            }
        }
        val current=timeStatsLayers(command("SurfaceFlinger","--timestats","-dump"))
            .filter{belongsToLayer(it.name,target)&&!it.name.contains("Background for")&&!it.name.contains("Splash Screen")}
        val sampleAt=System.nanoTime()
        val elapsed=sampleAt-statsAt
        val values=if(statsAt==0L)emptyList()else current.mapNotNull{layer->
            statsPrevious[layer.name]?.let{previous->frameRateFromDelta(previous,layer.count,elapsed)}?.let{layer.name to it}
        }
        statsPrevious=current.associate{it.name to it.count};statsAt=sampleAt
        val best=values.filter{it.second>0}.maxWithOrNull(compareBy<Pair<String,Double>>{layerPriority(it.first)}.thenBy{it.second})
        return arrayOf(target,best?.second?.toString()?:"")
    }
    @Synchronized override fun finish(){
        if(startedStats)runCatching{command("SurfaceFlinger","--timestats","-disable")}
        startedStats=false;timeStats=false;reset();pkg=null;focusAt=0
    }
    override fun destroy(){finish();kotlin.system.exitProcess(0)}
}
internal suspend fun withFpsService(context:Context,block:suspend(IFpsService)->Unit){
    check(shizukuGranted())
    val args=Shizuku.UserServiceArgs(ComponentName(context,FpsUserService::class.java)).daemon(false).processNameSuffix("fps_service").version(1)
    val ready=CompletableDeferred<IFpsService>()
    var remote:IFpsService?=null
    val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){ready.complete(IFpsService.Stub.asInterface(binder))}
        override fun onServiceDisconnected(name:ComponentName){ready.completeExceptionally(IllegalStateException("Shizuku disconnected"))}
    }
    try{
        Shizuku.bindUserService(args,connection)
        val service=withTimeout(15000){ready.await()}
        remote=service
        withContext(Dispatchers.IO){block(service)}
    }finally{
        withContext(NonCancellable+Dispatchers.IO){runCatching{remote?.finish()}}
        runCatching{Shizuku.unbindUserService(args,connection,true)}
    }
}
