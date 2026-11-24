// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

// Only the current Android user's emulated Android/data and Android/obb.
internal fun validReadRelative(relative:String):Boolean {
    val parts=relative.split('/')
    return relative.length in 1..16384 && parts.size>=2 && parts[0]=="Android" &&
        parts[1] in listOf("data","obb") && parts.none{it.isEmpty()||it=="."||it==".."||it.indexOf('\u0000')>=0}
}
internal fun validWriteRelative(relative:String)=validReadRelative(relative)&&relative.split('/').size>=3
internal fun protectedRelative(file:File,primary:File=Environment.getExternalStorageDirectory()):String?{
    val base=primary.absoluteFile.toPath().normalize()
    val target=file.absoluteFile.toPath().normalize()
    if(!target.startsWith(base))return null
    return base.relativize(target).toString().takeIf(::validReadRelative)
}
internal data class ProtectedReadEntry(val name:String,val directory:Boolean,val size:Long,val modified:Long,val readable:Boolean=true)
internal class ProtectedReadClient(private val service:IProtectedReadService){
    fun list(relative:String):List<ProtectedReadEntry>{
        check(shizukuGranted()&&validReadRelative(relative))
        val fd=service.listDirectory(relative) ?: error("Protected storage unavailable")
        return DataInputStream(BufferedInputStream(ParcelFileDescriptor.AutoCloseInputStream(fd))).use{input->
            val count=input.readInt()
            require(count in 0..150000)
            List(count){
                val name=input.readUTF()
                require(name.isNotEmpty()&&name!="."&&name!=".."&&'/' !in name&&'\u0000' !in name)
                ProtectedReadEntry(name,input.readBoolean(),input.readLong().also{require(it>=0)},input.readLong(),input.readBoolean())
            }
        }
    }
    fun create(relative:String):ParcelFileDescriptor{
        check(shizukuGranted()&&validWriteRelative(relative))
        return service.createNewFile(relative) ?: error("Unable to create file")
    }
    fun mkdir(relative:String){check(shizukuGranted()&&validWriteRelative(relative)&&service.createDirectory(relative))}
    fun rename(source:String,destination:String){check(shizukuGranted()&&validWriteRelative(source)&&validWriteRelative(destination)&&service.renameEntry(source,destination))}
    fun delete(relative:String){check(shizukuGranted()&&validWriteRelative(relative)&&service.deleteEntry(relative))}
    fun open(relative:String):ParcelFileDescriptor{
        check(shizukuGranted()&&validReadRelative(relative))
        return service.openReadOnly(relative) ?: error("Protected file unavailable")
    }
}
private val protectedReadMutex=Mutex()
internal suspend fun <T> withProtectedRead(context:Context,block:suspend (ProtectedReadClient)->T):T=protectedReadMutex.withLock{
    check(shizukuGranted())
    val args=Shizuku.UserServiceArgs(ComponentName(context,ProtectedReadUserService::class.java))
        .daemon(false).processNameSuffix("protected_read").version(2)
    val connected=CompletableDeferred<IProtectedReadService>()
    val connection=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){connected.complete(IProtectedReadService.Stub.asInterface(binder))}
        override fun onServiceDisconnected(name:ComponentName){connected.completeExceptionally(IllegalStateException("Shizuku disconnected"))}
    }
    try{
        Shizuku.bindUserService(args,connection)
        val service=withTimeoutOrNull(15000){connected.await()}?:error("Shizuku connection timeout")
        withContext(Dispatchers.IO){block(ProtectedReadClient(service))}
    }finally{runCatching{Shizuku.unbindUserService(args,connection,true)}}
}
class ProtectedReadUserService:IProtectedReadService.Stub(){
    private fun target(relative:String):File{
        check(android.os.Process.myUid() in listOf(0,2000))
        require(validReadRelative(relative))
        val user=Binder.getCallingUid()/100000
        val base=File("/storage/emulated/$user").canonicalFile
        var current=base
        for(part in relative.split('/')){
            current=File(current,part)
            require(!Files.isSymbolicLink(current.toPath()))
        }
        require(current.canonicalFile==current && current.toPath().startsWith(base.toPath()))
        return current
    }
    override fun listDirectory(relative:String):ParcelFileDescriptor{
        val dir=target(relative)
        require(Files.isDirectory(dir.toPath(),LinkOption.NOFOLLOW_LINKS))
        val children=dir.listFiles()?:throw IOException("Directory unavailable")
        require(children.size<=150000)
        val pipe=ParcelFileDescriptor.createReliablePipe()
        Thread({
            val write=pipe[1]
            try{
                DataOutputStream(BufferedOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(write))).use{out->
                    out.writeInt(children.size)
                    for(child in children){
                        val attr=runCatching{Files.readAttributes(child.toPath(),BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)}.getOrNull()
                        val valid=attr!=null&&(attr.isDirectory||attr.isRegularFile)&&!attr.isSymbolicLink
                        out.writeUTF(child.name)
                        out.writeBoolean(attr?.isDirectory==true)
                        out.writeLong(if(attr?.isRegularFile==true)attr.size().coerceAtLeast(0)else 0)
                        out.writeLong(attr?.lastModifiedTime()?.toMillis()?:0)
                        out.writeBoolean(valid)
                    }
                }
            }catch(_:Exception){runCatching{write.closeWithError("Directory read interrupted")}}
        },"slate-protected-list").apply{isDaemon=true;start()}
        return pipe[0]
    }
    override fun openReadOnly(relative:String):ParcelFileDescriptor{
        val file=target(relative)
        require(Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS))
        val fd=Os.open(file.path,OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC,0)
        try{
            require(OsConstants.S_ISREG(Os.fstat(fd).st_mode))
            return ParcelFileDescriptor.dup(fd)
        }finally{Os.close(fd)}
    }
    private fun writable(relative:String):File{
        require(validWriteRelative(relative))
        val file=target(relative)
        require(file.parentFile?.isDirectory==true)
        return file
    }
    override fun createNewFile(relative:String):ParcelFileDescriptor{
        val file=writable(relative)
        val fd=Os.open(file.path,OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC,384)
        try{
            require(OsConstants.S_ISREG(Os.fstat(fd).st_mode))
            return ParcelFileDescriptor.dup(fd)
        }finally{Os.close(fd)}
    }
    override fun createDirectory(relative:String):Boolean=runCatching{
        Files.createDirectory(writable(relative).toPath());true
    }.getOrDefault(false)
    override fun renameEntry(source:String,destination:String):Boolean=runCatching{
        val from=writable(source);val to=writable(destination)
        require(from!=to&&!to.toPath().startsWith(from.toPath()))
        require(Files.isRegularFile(from.toPath(),LinkOption.NOFOLLOW_LINKS)||Files.isDirectory(from.toPath(),LinkOption.NOFOLLOW_LINKS))
        Files.move(from.toPath(),to.toPath());true
    }.getOrDefault(false)
    override fun deleteEntry(relative:String):Boolean=runCatching{
        val file=writable(relative)
        require(Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)||Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS))
        Files.delete(file.toPath());true
    }.getOrDefault(false)
    override fun destroy(){kotlin.system.exitProcess(0)}
}
