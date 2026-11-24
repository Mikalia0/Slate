// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.util.concurrent.TimeUnit

// No caller-supplied command or file path. Android chooses which caches to trim.
internal fun trimCaches(target:Long,root:Boolean):Int {
    require(target in 1..(16L*1024*1024*1024*1024))
    val args=if(root)listOf("su","-c","/system/bin/pm trim-caches $target internal")
        else listOf("/system/bin/pm","trim-caches",target.toString(),"internal")
    val process=ProcessBuilder(args).redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
    return try {
        if(process.waitFor(120,TimeUnit.SECONDS))process.exitValue() else -1
    }finally{if(process.isAlive)process.destroyForcibly()}
}

internal fun validCachePackage(name:String):Boolean =
    name.length in 1..255 && name.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*"))

class CacheUserService:ICacheService.Stub(){
    private fun privileged()=android.os.Process.myUid() in listOf(0,2000)
    private val cacheOnlySupported by lazy {
        if(!privileged()) false else runCatching {
            val process=ProcessBuilder("/system/bin/pm","help").redirectErrorStream(true).start()
            try {
                val help=process.inputStream.bufferedReader().use{it.readText()}
                process.waitFor(10,TimeUnit.SECONDS) &&
                    help.lineSequence().any{it.trimStart().startsWith("clear ") && it.contains("--cache-only")}
            } finally { if(process.isAlive)process.destroyForcibly() }
        }.getOrDefault(false)
    }
    override fun supportsCacheOnly():Boolean=cacheOnlySupported
    override fun clearCache(user:Int,packageName:String):Boolean {
        if(!privileged() || user !in 0..99999 || !validCachePackage(packageName) || !cacheOnlySupported)return false
        // Never fall back to pm clear without the cache-only flag.
        return runCatching {
            val process=ProcessBuilder("/system/bin/pm","clear","--user",user.toString(),"--cache-only",packageName)
                .redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
            try { process.waitFor(30,TimeUnit.SECONDS) && process.exitValue()==0 }
            finally { if(process.isAlive)process.destroyForcibly() }
        }.getOrDefault(false)
    }
    override fun trim(target:Long):Int = try {
        if(android.os.Process.myUid() !in listOf(0,2000)) -1 else trimCaches(target,false)
    }catch(_:Exception){-1}
    override fun listProtected(user:Int):Array<String> {
        check(android.os.Process.myUid() in listOf(0,2000))
        return listProtectedCandidates(user,false).map{(if(it.empty)"1" else "0")+"\t"+it.relative}.toTypedArray()
    }
    override fun removeEmpty(user:Int,path:String):Boolean {
        check(android.os.Process.myUid() in listOf(0,2000))
        return removeProtectedEmpty(user,path,false)
    }
    override fun destroy(){kotlin.system.exitProcess(0)}
}
