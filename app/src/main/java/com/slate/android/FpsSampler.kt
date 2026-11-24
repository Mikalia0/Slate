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
import java.util.concurrent.FutureTask

internal fun focusedPackage(dump:String):String? {
    val component=Regex("""(?:^|[ {])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.$]+""")
    return dump.lineSequence().filter{it.contains("mCurrentFocus=")||it.contains("mFocusedApp=")||it.contains("mResumedActivity")||it.contains("topResumedActivity")}
        .sortedBy{when{it.contains("mCurrentFocus=")->0;it.contains("mFocusedApp=")->1;else->2}}
        .mapNotNull{component.find(it)?.groupValues?.get(1)}.firstOrNull()
}
internal fun belongsToLayer(layer:String,pkg:String):Boolean =
    Regex("""(?<![A-Za-z0-9_.])"""+Regex.escape(pkg)+"""(?![A-Za-z0-9_.])""").containsMatchIn(layer)
internal fun presentedFrames(raw:String):List<Long> = raw.lineSequence().mapNotNull{line->
    val columns=line.trim().split(Regex("\\s+"))
    if(columns.size!=3)null else columns[1].toLongOrNull()?.takeIf{it>0&&it<Long.MAX_VALUE}
}.distinct().sorted().toList()
internal fun layerPriority(layer:String):Int = when {
    layer.contains("SurfaceView",true)&&layer.contains("BLAST",true)->3
    layer.contains("SurfaceView",true)->2
    layer.contains("BLAST",true)->1
    else->0
}
internal fun renderLayer(layer:String,pkg:String):Boolean=belongsToLayer(layer,pkg)&&
    listOf("Background for","Splash Screen","Input Consumer","Dim Layer","SnapshotStartingWindow","TaskSnapshot","Letterbox").none{layer.contains(it,true)}
internal fun recentFrames(timestamps:Collection<Long>,now:Long):List<Long> =
    timestamps.filter{it in (now-1_000_000_000L)..(now+50_000_000L)}.distinct().sorted()
internal fun measuredFrameRate(timestamps:Collection<Long>,now:Long):Double?{
    val recent=recentFrames(timestamps,now)
    if(recent.size<2||now-recent.last()>350_000_000L)return null
    val span=recent.last()-recent.first()
    if(span<100_000_000L)return null
    return ((recent.size-1)*1_000_000_000.0/span).takeIf{it.isFinite()&&it in 0.0..1000.0}
}
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

internal fun cpuTimes(line:String):Pair<Long,Long>?{
    val parts=line.trim().split(Regex("\\s+"))
    if(parts.firstOrNull()?.matches(Regex("cpu[0-9]*"))!=true||parts.size<5)return null
    val values=parts.drop(1).take(8).map{it.toLongOrNull()?:return null}
    if(values.any{it<0})return null
    return values.sum() to (values[3]+(values.getOrNull(4)?:0L))
}
internal fun cpuPercent(before:Pair<Long,Long>?,after:Pair<Long,Long>?):Int?{
    if(before==null||after==null)return null
    val elapsed=after.first-before.first
    val idle=after.second-before.second
    if(elapsed<=0||idle !in 0..elapsed)return null
    return ((elapsed-idle)*100.0/elapsed).toInt()
}
internal fun frameIntervals(frames:Collection<Long>):List<Double> =
    frames.distinct().sorted().zipWithNext{a,b->(b-a)/1_000_000.0}.filter{it.isFinite()&&it>0&&it<=1000}.takeLast(48)

class FpsUserService:IFpsService.Stub(){
    private var previousCpu:Pair<Long,Long>?=null
    private var previousCores=emptyMap<Int,Pair<Long,Long>>()
    private val clusters=mutableMapOf<Int,String>()
    private var frameHistory=emptyList<Double>()
    private var wantsFrames=false
    private var pkg:String?=null
    private var focusAt=0L
    private var lastFocusSuccess=0L
    private var lastLatencyProbe=0L
    private var lastStatsEnable=0L
    private var statsMisses=0
    private var preferredLayer:String?=null
    private var probeOffset=0
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
        val reader=FutureTask<String>{
            proc.inputStream.bufferedReader().use{input->
                val out=StringBuilder();val buf=CharArray(4096)
                while(true){val n=input.read(buf);if(n<0)break;if(out.length+n>4_000_000)error("dump too large");out.append(buf,0,n)}
                out.toString()
            }
        }
        Thread(reader,"slate-fps-dump").apply{isDaemon=true;start()}
        return try{
            check(proc.waitFor(3,TimeUnit.SECONDS)&&proc.exitValue()==0)
            reader.get(500,TimeUnit.MILLISECONDS)
        }finally{if(proc.isAlive)proc.destroyForcibly();reader.cancel(true)}
    }
    private fun dump(vararg args:String):String?=runCatching{command(*args)}.getOrNull()
    private fun reset(){
        layers=emptyList();layerAt=0;frames.clear();latencyFailures=0
        preferredLayer=null;probeOffset=0;lastLatencyProbe=0L;statsMisses=0
        statsPrevious=emptyMap();statsAt=0;timeStats=false
    }
    private fun latency(target:String,now:Long):Double?{
        if(layerAt==0L||now-layerAt>=2500){
            val listed=dump("SurfaceFlinger","--list")
            if(listed!=null){
                layers=listed.lineSequence().map{it.trim()}.filter{renderLayer(it,target)}
                    .distinct().sortedByDescending(::layerPriority).take(16).toList()
                frames.keys.retainAll(layers.toSet())
                if(preferredLayer !in layers)preferredLayer=null
                layerAt=now
            }
        }
        val candidates=(listOfNotNull(preferredLayer)+layers).distinct()
        val bestCandidates=mutableListOf<Pair<String,Double>>()
        // A working surface is sampled first; scan other candidates only on loss.
        val probes=if(preferredLayer!=null)candidates.take(1)else if(candidates.isEmpty())emptyList()else{
            val offset=probeOffset%candidates.size
            (candidates.drop(offset)+candidates.take(offset)).take(4).also{probeOffset=(offset+it.size)%candidates.size}
        }
        for(layer in probes){
            val timestamps=dump("SurfaceFlinger","--latency",layer)?.let(::presentedFrames).orEmpty()
            val clock=System.nanoTime()
            val history=frames.getOrPut(layer){mutableSetOf()}
            history.addAll(recentFrames(timestamps,clock))
            history.retainAll(recentFrames(history,clock).toSet())
            val rate=measuredFrameRate(history,clock)
            if(rate!=null)bestCandidates.add(layer to rate)
        }
        val best=bestCandidates.maxWithOrNull(compareBy<Pair<String,Double>>{layerPriority(it.first)}.thenBy{it.second})
        if(best==null){
            if(preferredLayer!=null){frames.remove(preferredLayer);preferredLayer=null}
            layerAt=0
            return null
        }
        preferredLayer=best.first
        if(wantsFrames)frameHistory=frameIntervals(frames[best.first].orEmpty())
        return best.second
    }
    private fun ensureStats(now:Long){
        if(lastStatsEnable!=0L&&now-lastStatsEnable<8000)return
        lastStatsEnable=now
        // Do not disable or clear counters already owned by another consumer.
        if(dump("SurfaceFlinger","--timestats","-enable")!=null)startedStats=true
    }
    @Synchronized override fun sample(includeCpu:Boolean,includeFrames:Boolean,includeCores:Boolean):Array<String>{
        wantsFrames=includeFrames;frameHistory=emptyList()
        val cpuLines=if(includeCpu||includeCores)runCatching{java.io.File("/proc/stat").useLines{lines->lines.takeWhile{it.startsWith("cpu")}.toList()}}.getOrDefault(emptyList())else emptyList()
        val current=cpuLines.firstOrNull()?.let(::cpuTimes)
        val cpu=cpuPercent(previousCpu,current);previousCpu=current
        val cores=if(includeCores){
            val values=cpuLines.drop(1).mapNotNull{line->
                val id=line.substringBefore(' ').removePrefix("cpu").toIntOrNull()?:return@mapNotNull null
                cpuTimes(line)?.let{id to it}
            }.toMap()
            val result=values.toSortedMap().map{(id,times)->
                val dir="/sys/devices/system/cpu/cpu$id/cpufreq"
                val freq=runCatching{java.io.File("$dir/scaling_cur_freq").readText().trim().toLongOrNull()?.div(1000)?.toInt()}.getOrNull()
                val cluster=clusters.getOrPut(id){
                    runCatching{java.io.File("$dir/related_cpus").readText().trim().split(Regex("\\s+")).mapNotNull{it.toIntOrNull()}.sorted().joinToString(",").ifEmpty{id.toString()}}.getOrDefault(id.toString())
                }
                listOf(id.toString(),cpuPercent(previousCores[id],times)?.toString().orEmpty(),freq?.toString().orEmpty(),cluster).joinToString(":")
            }.joinToString(";")
            previousCores=values;result
        }else{previousCores=emptyMap();""}
        val fps=sampleFps()
        return fps+arrayOf(cpu?.toString().orEmpty(),frameHistory.joinToString(","),cores)
    }
    private fun sampleFps():Array<String>{
        val now=SystemClock.elapsedRealtime()
        if(focusAt==0L||now-focusAt>=1000){
            val found=dump("window","displays")?.let(::focusedPackage)
                ?:dump("window","windows")?.let(::focusedPackage)
                ?:dump("activity","activities")?.let(::focusedPackage)
            if(found!=null){
                lastFocusSuccess=now
                if(found!=pkg){pkg=found;reset()}
            }else if(now-lastFocusSuccess>4000){pkg=null;reset()}
            focusAt=now
        }
        val target=pkg?:return arrayOf("","")
        if(target=="com.slate.android"||target=="moe.shizuku.privileged.api")return arrayOf(target,"")
        // Retry the primary source even after switching to cumulative counters.
        if(!timeStats||lastLatencyProbe==0L||now-lastLatencyProbe>=5000){
            lastLatencyProbe=now
            val rate=latency(target,now)
            if(rate!=null){
                latencyFailures=0;timeStats=false;statsMisses=0
                statsPrevious=emptyMap();statsAt=0
                return arrayOf(target,rate.toString())
            }
            if(++latencyFailures>=2)timeStats=true
        }
        if(!timeStats)return arrayOf(target,"")
        val current=dump("SurfaceFlinger","--timestats","-dump")?.let(::timeStatsLayers)
            ?.filter{renderLayer(it.name,target)}.orEmpty()
        if(current.isEmpty()){
            statsPrevious=emptyMap();statsAt=0
            ensureStats(now)
            return arrayOf(target,"")
        }
        val sampleAt=System.nanoTime()
        val elapsed=sampleAt-statsAt
        val values=if(statsAt==0L)emptyList()else current.mapNotNull{layer->
            statsPrevious[layer.name]?.let{previous->frameRateFromDelta(previous,layer.count,elapsed)}?.let{layer.name to it}
        }
        statsPrevious=current.associate{it.name to it.count};statsAt=sampleAt
        val best=values.filter{it.second>0}.maxWithOrNull(compareBy<Pair<String,Double>>{layerPriority(it.first)}.thenBy{it.second})
        if(best!=null){statsMisses=0;return arrayOf(target,best.second.toString())}
        if(++statsMisses>=3){ensureStats(now);statsMisses=0;lastLatencyProbe=0L}
        // A measured unchanged counter is zero, not an unavailable sample.
        return arrayOf(target,if(values.any{it.second==0.0})"0"else "")
    }
    @Synchronized override fun finish(){
        // TimeStats is shared system state. Leave it intact instead of disabling another monitor.
        startedStats=false;timeStats=false;reset();pkg=null;focusAt=0;lastFocusSuccess=0;lastStatsEnable=0;previousCpu=null;previousCores=emptyMap();clusters.clear();frameHistory=emptyList()
    }
    override fun destroy(){finish();kotlin.system.exitProcess(0)}
}
internal suspend fun withFpsService(context:Context,block:suspend(IFpsService)->Unit){
    check(shizukuGranted())
    val args=Shizuku.UserServiceArgs(ComponentName(context,FpsUserService::class.java)).daemon(false).processNameSuffix("fps_service").version(4)
    val ready=CompletableDeferred<IFpsService>()
    var remote:IFpsService?=null
    val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){ready.complete(IFpsService.Stub.asInterface(binder))}
        override fun onServiceDisconnected(name:ComponentName){ready.completeExceptionally(IllegalStateException("Shizuku disconnected"))}
    }
    try{
        Shizuku.bindUserService(args,connection)
        val service=withTimeoutOrNull(15000){ready.await()} ?: error("FPS service connection timed out")
        remote=service
        withContext(Dispatchers.IO){block(service)}
    }finally{
        withContext(NonCancellable+Dispatchers.IO){runCatching{remote?.finish()}}
        runCatching{Shizuku.unbindUserService(args,connection,true)}
    }
}
