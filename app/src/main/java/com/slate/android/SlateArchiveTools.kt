// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal fun archiveTarget(root:File,name:String):File{
    require(name.isNotBlank()&&!name.startsWith("/")&&!name.contains('\\')&&!name.contains('\u0000'))
    require(name.split('/').none{it==".."||it=="."})
    val target=File(root,name).canonicalFile
    require(target!=root.canonicalFile&&target.toPath().startsWith(root.canonicalFile.toPath()))
    return target
}

/** Extract into a new private staging directory; never overwrite existing files. */
internal suspend fun extractSlateZip(root:File,archive:File):File{
    require(safeFile(root,archive)&&archive.isFile&&!com.slate.android.storage.protectedAppPath(archive.toPath()))
    val parent=checkNotNull(archive.parentFile)
    require(safeFile(root,parent)&&parent.canWrite()&&!com.slate.android.storage.protectedAppPath(parent.toPath()))
    val staging=Files.createTempDirectory(parent.toPath(),".slate-extract-").toFile()
    try{
        var count=0
        var bytes=0L
        val limit=minOf(32L*1024*1024*1024,parent.usableSpace)
        ZipFile(archive).use{zip->
            val entries=zip.entries()
            while(entries.hasMoreElements()){
                coroutineContext.ensureActive()
                val entry=entries.nextElement()
                require(++count<=100000)
                val target=archiveTarget(staging,entry.name)
                if(entry.isDirectory){Files.createDirectories(target.toPath());continue}
                Files.createDirectories(target.parentFile!!.toPath())
                zip.getInputStream(entry).use{input->
                    Files.newOutputStream(target.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE).use{output->
                        val buffer=ByteArray(64*1024)
                        while(true){
                            coroutineContext.ensureActive()
                            val n=input.read(buffer);if(n<0)break
                            bytes+=n;require(bytes<=limit)
                            output.write(buffer,0,n)
                        }
                    }
                }
            }
        }
        val base=archive.nameWithoutExtension.ifBlank{"archive"}
        for(i in 0..9999){
            val dest=File(parent,if(i==0)base else "$base ($i)")
            try{Files.move(staging.toPath(),dest.toPath());return dest}
            catch(_:java.nio.file.FileAlreadyExistsException){}
            catch(_:java.nio.file.DirectoryNotEmptyException){}
        }
        error("No available directory name")
    }finally{if(staging.exists())staging.deleteRecursively()}
}
