// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.slate.android.safeFile
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

private val apkInstallMutex=Mutex()
internal fun shizukuInstallReady():Boolean=shizukuGranted()&&runCatching{
    Shizuku.getUid() in listOf(0,2000)
}.getOrDefault(false)

/** The caller supplies only an opened APK, not a shell command or a privileged file path. */
internal suspend fun installApkWithShizuku(context:Context,root:File,file:File):String=apkInstallMutex.withLock{
    check(shizukuInstallReady()){"Shizuku is unavailable or unauthorized"}
    require(file.extension.equals("apk",true)&&safeFile(root,file)&&file.isFile&&file.canRead())
    val args=Shizuku.UserServiceArgs(ComponentName(context,ApkInstallUserService::class.java))
        .daemon(false).processNameSuffix("apk_install").version(1)
    val connected=CompletableDeferred<IApkInstallService>()
    val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){connected.complete(IApkInstallService.Stub.asInterface(binder))}
        override fun onServiceDisconnected(name:ComponentName){connected.completeExceptionally(IllegalStateException("Shizuku disconnected"))}
    }
    try{
        Shizuku.bindUserService(args,connection)
        val service=withTimeout(15000){connected.await()}
        withContext(Dispatchers.IO){
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->
                val size=fd.statSize
                require(size>0)
                service.install(fd,size)?:error("No installer response")
            }
        }
    }finally{runCatching{Shizuku.unbindUserService(args,connection,true)}}
}

internal fun apkInstallCommand(user:Int,size:Long):List<String>{
    require(user>=0&&size>0)
    // Keep the normal package manager checks: no downgrade, test-only, signature bypass or force flags.
    return listOf("/system/bin/pm","install","-r","--user",user.toString(),"-S",size.toString(),"-")
}
class ApkInstallUserService:IApkInstallService.Stub(){
    @Synchronized override fun install(apk:ParcelFileDescriptor,size:Long):String{
        apk.use{source->
            check(Process.myUid() in listOf(0,2000))
            val caller=Binder.getCallingUid()
            require(caller>=10000)
            val user=caller/100000
            val stat=Os.fstat(source.fileDescriptor)
            require(OsConstants.S_ISREG(stat.st_mode)&&stat.st_size==size&&size>0)
            val process=ProcessBuilder(apkInstallCommand(user,size)).redirectErrorStream(true).start()
            val executor=Executors.newFixedThreadPool(2)
            try{
                // Bound the command lifetime, including time spent streaming large APKs.
                val output=executor.submit<String>{
                    process.inputStream.bufferedReader().use{reader->
                        val result=StringBuilder()
                        val buffer=CharArray(1024)
                        while(true){
                            val n=reader.read(buffer)
                            if(n<0)break
                            if(result.length<16384)result.append(buffer,0,minOf(n,16384-result.length))
                        }
                        result.toString().trim()
                    }
                }
                val transfer=executor.submit<Unit>{
                    ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(source.fileDescriptor)).use{input->
                        process.outputStream.buffered(65536).use{out->
                            val buffer=ByteArray(65536)
                            var remaining=size
                            while(remaining>0){
                                val n=input.read(buffer,0,minOf(remaining,buffer.size.toLong()).toInt())
                                if(n<0)throw IOException("APK ended unexpectedly")
                                out.write(buffer,0,n)
                                remaining-=n
                            }
                        }
                    }
                }
                if(!process.waitFor(10,TimeUnit.MINUTES)){process.destroyForcibly();error("Installation timed out")}
                val message=output.get(5,TimeUnit.SECONDS)
                val transferred=runCatching{transfer.get(5,TimeUnit.SECONDS)}.isSuccess
                if(process.exitValue()==0&&transferred&&message.lineSequence().any{it.trim()=="Success"})return "Success"
                return message.ifBlank{"Installation failed (exit "+process.exitValue()+")"}
                    .let{if(it=="Success")"Installation could not be verified"else it}
            }finally{
                process.destroyForcibly()
                executor.shutdownNow()
            }
        }
    }
    override fun destroy(){kotlin.system.exitProcess(0)}
}
