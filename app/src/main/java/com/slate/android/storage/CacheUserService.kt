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

class CacheUserService:ICacheService.Stub(){
    override fun trim(target:Long):Int = try {
        if(android.os.Process.myUid() !in listOf(0,2000)) -1 else trimCaches(target,false)
    }catch(_:Exception){-1}
    override fun destroy(){kotlin.system.exitProcess(0)}
}
