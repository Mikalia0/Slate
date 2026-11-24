// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.util.PriorityQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

internal data class RootFile(val path:String,val bytes:Long)
internal data class RootDataResult(val files:List<RootFile>,val scanned:Long,val incomplete:Boolean)
internal const val ROOT_RESULT_LIMIT=2000
internal fun parseRootFile(line:String):RootFile?{
    val split=line.indexOf(' ')
    if(split<=0)return null
    val size=line.substring(0,split).toLongOrNull()?.takeIf{it>=0}?:return null
    val path=line.substring(split+1)
    if(!path.startsWith("/data/")||path.length>16384||path.any{it=='\u0000'||it=='\r'||it=='\n'}||path.split('/').any{it==".."||it=="."})return null
    return RootFile(path,size)
}
internal class RootLargest(private val limit:Int=ROOT_RESULT_LIMIT){
    private val compare=compareBy<RootFile>{it.bytes}.thenByDescending{it.path}
    private val heap=PriorityQueue(compare)
    var scanned=0L;private set
    fun add(file:RootFile){
        scanned++
        if(heap.size<limit)heap.add(file)
        else if(compare.compare(file,heap.peek())>0){heap.poll();heap.add(file)}
    }
    fun results()=heap.sortedWith(compareByDescending<RootFile>{it.bytes}.thenBy{it.path})
}

// Fixed read-only command: no UI-supplied path or shell fragments, no symlink
// traversal and no writes to private files. Skip names containing LF because
// toybox stat emits line-separated records. Do not cross foreign mount points.
private val scanCommand="""
    [ "$(/system/bin/id -u)" = 0 ] || exit 71
    printf 'SLATE_ROOT_OK\n'
    newline='
    '
    /system/bin/find /data -xdev -type f ! -path "*${'$'}newline*" -exec /system/bin/stat -c '%s %n' {} +
    result=$?
    printf 'SLATE_ROOT_END %s\n' "${'$'}result"
    exit "${'$'}result"
""".trimIndent().replace("newline='\n    '","newline='\n'")

internal suspend fun verifyRootDataAccess():Boolean=withContext(Dispatchers.IO){
    val process=try{ProcessBuilder("su","-c","[ \"\$(/system/bin/id -u)\" = 0 ] && [ -r /data ] && [ -x /data ]").redirectErrorStream(true).start()}catch(_:java.io.IOException){return@withContext false}
    try{
        runInterruptible{process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS)&&process.exitValue()==0}
    }catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(_:Exception){false}
    finally{if(process.isAlive)process.destroyForcibly()}
}
internal suspend fun scanRootData(onProgress:(Long)->Unit):RootDataResult=withContext(Dispatchers.IO){
    val process=ProcessBuilder("su","-c",scanCommand).redirectErrorStream(true).start()
    val largest=RootLargest()
    var authorized=false
    var finished=false
    var incomplete=false
    var lastReport=0L
    try{
        process.inputStream.bufferedReader().use{reader->
            val ctx=currentCoroutineContext()
            while(true){
                ctx.ensureActive()
                if(!reader.ready()){
                    if(!process.isAlive)break
                    delay(40)
                    continue
                }
                val line=runInterruptible{reader.readLine()}?:break
                when{
                    line=="SLATE_ROOT_OK"->authorized=true
                    line.startsWith("SLATE_ROOT_END ")->{finished=true;incomplete=incomplete||line!="SLATE_ROOT_END 0"}
                    authorized->{
                        val file=parseRootFile(line)
                        if(file!=null)largest.add(file)else incomplete=true
                    }
                }
                val now=android.os.SystemClock.elapsedRealtime()
                if(now-lastReport>=300){lastReport=now;onProgress(largest.scanned)}
            }
        }
        check(authorized){"Root access denied"}
        check(finished||largest.scanned>0){"Root scan failed"}
        onProgress(largest.scanned)
        RootDataResult(largest.results(),largest.scanned,incomplete||!finished)
    }finally{if(process.isAlive)process.destroyForcibly()}
}
