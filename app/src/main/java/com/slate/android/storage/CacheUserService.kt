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
    private val cacheApi by lazy { runCatching { PackageCacheApi() }.getOrNull() }
    override fun supportsCacheOnly():Boolean =
        privileged() && cacheApi?.supportsSelective()==true
    override fun clearCache(user:Int,packageName:String):Boolean {
        if(!privileged() || user !in 0..99999 || !validCachePackage(packageName))return false
        return runCatching { cacheApi?.clearPackage(packageName,user)==true }.getOrDefault(false)
    }
    override fun trim(target:Long):Int {
        if(!privileged() || target!=GLOBAL_CACHE_TARGET)return -1
        return runCatching { cacheApi?.trimAll() ?: -1 }.getOrDefault(-1)
    }
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
