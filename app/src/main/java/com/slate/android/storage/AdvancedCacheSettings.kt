// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

internal fun shizukuAvailable():Boolean=runCatching{Shizuku.pingBinder() && Shizuku.getVersion()>=12}.getOrDefault(false)
internal fun shizukuGranted():Boolean=shizukuAvailable() && runCatching{Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}.getOrDefault(false)
internal suspend fun <T> withCacheService(context:Context,action:suspend (ICacheService)->T):T {
    val args=Shizuku.UserServiceArgs(ComponentName(context,CacheUserService::class.java))
        .daemon(false).processNameSuffix("cache_service").version(4)
    val connected=CompletableDeferred<ICacheService>()
    val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder){
            connected.complete(ICacheService.Stub.asInterface(binder))
        }
        override fun onServiceDisconnected(name:ComponentName){
            connected.completeExceptionally(IllegalStateException("Shizuku disconnected"))
        }
    }
    try {
        Shizuku.bindUserService(args,connection)
        val service=withTimeout(15_000){connected.await()}
        return withContext(Dispatchers.IO){action(service)}
    }finally{runCatching{Shizuku.unbindUserService(args,connection,true)}}
}

