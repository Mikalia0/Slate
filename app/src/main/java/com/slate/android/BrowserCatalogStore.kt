// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.util.AtomicFile
import com.slate.android.storage.Entry
import com.slate.android.storage.Volume
import com.slate.android.storage.protectedAppPath
import java.io.*

/** Non-privileged, advisory snapshot only. File actions still validate live paths. */
internal class BrowserCatalogStore(cacheDir:File){
    private val file=AtomicFile(File(cacheDir,"browser-catalog-v1"))
    private val maxEntries=300000
    fun read(volumes:List<Volume>):FileCatalog?=runCatching{
        val roots=volumes.map{it.root}.distinctBy{it.path}.sortedBy{it.path}
        DataInputStream(file.openRead().buffered(128*1024)).use{input->
            require(input.readInt()==1)
            require(input.readInt()==roots.size)
            roots.forEach{require(input.readUTF()==it.path)}
            val count=input.readInt();require(count in 0..maxEntries)
            val rows=ArrayList<CatalogFile>(count)
            repeat(count){
                val root=roots[input.readInt()]
                val relative=input.readUTF()
                require(relative.isNotBlank()&&!relative.startsWith('/')&&relative.split('/').none{it=="."||it==".."})
                val target=File(root,relative)
                val bytes=input.readLong();val modified=input.readLong()
                if(!protectedAppPath(target.toPath()))rows.add(CatalogFile(root,Entry(target,false,bytes,1,0,modified)))
            }
            FileCatalog(rows,false)
        }
    }.getOrNull()
    fun write(volumes:List<Volume>,catalog:FileCatalog){
        if(catalog.files.size>maxEntries||catalog.incomplete)return
        val roots=volumes.map{it.root}.distinctBy{it.path}.sortedBy{it.path}
        var stream:FileOutputStream?=null
        try{
            val raw=file.startWrite();stream=raw
            val out=DataOutputStream(raw.buffered(128*1024))
            out.writeInt(1);out.writeInt(roots.size);roots.forEach{out.writeUTF(it.path)}
            out.writeInt(catalog.files.size)
            for(row in catalog.files){
                val rootIndex=roots.indexOfFirst{it.path==row.root.path};require(rootIndex>=0)
                out.writeInt(rootIndex);out.writeUTF(row.entry.file.relativeTo(row.root).invariantSeparatorsPath);out.writeLong(row.entry.bytes);out.writeLong(row.entry.modified)
            }
            out.flush();file.finishWrite(raw);stream=null
        }catch(_:Exception){stream?.let{file.failWrite(it)}}
    }
}
