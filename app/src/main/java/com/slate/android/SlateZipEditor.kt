// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.net.Uri
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal fun checkedArchiveName(name:String):String {
    require(name.isNotBlank()&&!name.startsWith("/")&&!name.contains('\\')&&!name.contains('\u0000'))
    require(name.trimEnd('/').split('/').all{it.isNotBlank()&&it!="."&&it!=".."})
    return name
}
internal data class ZipChange(val from:String?,val to:String?,val data:ByteArray?=null,val source:Uri?=null)
internal suspend fun rewriteSlateZip(context:Context,root:File,archive:File,change:ZipChange):File {
    require(safeFile(root,archive)&&archive.isFile&&archive.canRead()&&!com.slate.android.storage.protectedAppPath(archive.toPath()))
    val parent=checkNotNull(archive.parentFile)
    require(safeFile(root,parent)&&parent.canWrite())
    change.from?.let{checkedArchiveName(it)}
    change.to?.let{checkedArchiveName(it)}
    val originalSize=archive.length();val originalTime=archive.lastModified()
    val apk=archive.extension.equals("apk",true)
    val stage=Files.createTempFile(parent.toPath(),".slate-zip-",".tmp").toFile()
    var total=0L
    val max=32L*1024*1024*1024
    val buffer=ByteArray(64*1024)
    suspend fun copy(input:InputStream,out:OutputStream){
        while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=max);out.write(buffer,0,n)}
    }
    try{
        ZipFile(archive).use{input->
            val entries=input.entries().asSequence().toList()
            require(entries.size<=100000&&entries.map{it.name}.toSet().size==entries.size)
            val from=change.from;val to=change.to
            fun matches(name:String)=from!=null&&(name==from||(from.endsWith("/")&&name.startsWith(from)))
            if(from!=null)require(entries.any{matches(it.name)})
            if(to!=null&&from!=to)require(entries.none{!matches(it.name)&&(it.name==to||(to.endsWith("/")&&it.name.startsWith(to)))})
            ZipOutputStream(BufferedOutputStream(stage.outputStream())).use{out->
                input.comment?.let{out.setComment(it)}
                val written=HashSet<String>()
                fun put(name:String,old:ZipEntry?=null){
                    require(written.add(name));checkedArchiveName(name)
                    val e=ZipEntry(name)
                    if(old!=null){if(old.time>=0)e.time=old.time;e.comment=old.comment;if(old.method==ZipEntry.STORED){e.method=ZipEntry.STORED;e.size=old.size;e.crc=old.crc}}
                    out.putNextEntry(e)
                }
                for(entry in entries){
                    coroutineContext.ensureActive()
                    if(matches(entry.name)){
                        if(to==null||change.source!=null||change.data!=null)continue
                        val dest=to+entry.name.removePrefix(from!!)
                        put(dest,entry)
                    }else{
                        // Rebuilding APKs invalidates signatures; do not retain stale v1 signature records.
                        if(apk&&entry.name.uppercase().let{it.startsWith("META-INF/")&&(it.endsWith(".SF")||it.endsWith(".RSA")||it.endsWith(".DSA")||it.endsWith(".EC")||it=="META-INF/MANIFEST.MF")})continue
                        put(entry.name,entry)
                    }
                    if(!entry.isDirectory)input.getInputStream(entry).use{copy(it,out)}
                    out.closeEntry()
                }
                if(to!=null&&(change.source!=null||change.data!=null)){
                    put(to)
                    if(change.source!=null)checkNotNull(context.contentResolver.openInputStream(change.source)).use{copy(it,out)}
                    else ByteArrayInputStream(checkNotNull(change.data)).use{copy(it,out)}
                    out.closeEntry()
                }else if(from==null&&to!=null){put(to);out.closeEntry()}
            }
        }
        ZipFile(stage).use{zip->require(zip.size()<=100001)}
        coroutineContext.ensureActive()
        require(archive.length()==originalSize&&archive.lastModified()==originalTime)
        if(apk){
            for(i in 0..9999){
                val dest=File(parent,archive.nameWithoutExtension+"-edited"+(if(i==0)""else "-$i")+".apk")
                try{Files.move(stage.toPath(),dest.toPath());return dest}catch(_:java.nio.file.FileAlreadyExistsException){}
            }
            error("No available APK name")
        }
        // Atomic replacement: if the filesystem cannot do it, leave the source unchanged.
        Files.move(stage.toPath(),archive.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        return archive
    }finally{stage.delete()}
}

internal suspend fun copyArchiveEntry(context:Context,archive:File,name:String,dest:Uri){
    ZipFile(archive).use{zip->
        val entry=checkNotNull(zip.getEntry(name));require(!entry.isDirectory)
        zip.getInputStream(entry).use{input->checkNotNull(context.contentResolver.openOutputStream(dest,"wt")).use{out->
            val buffer=ByteArray(65536);var total=0L
            while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=32L*1024*1024*1024);out.write(buffer,0,n)}
        }}
    }
}
internal fun readArchiveText(archive:File,name:String):String{
    ZipFile(archive).use{zip->
        val entry=checkNotNull(zip.getEntry(name));require(!entry.isDirectory&&entry.size in 0..1048576)
        return zip.getInputStream(entry).use{input->
            val bytes=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;require(bytes.size()+n<=1048576);bytes.write(buffer,0,n)}
            val data=bytes.toByteArray()
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString().also{require(!it.contains('\u0000'))}
        }
    }
}
