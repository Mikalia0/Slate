// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Private, bounded, disposable index. Not an authority for deleting files. */
internal class IndexStore(private val file:File,private val root:File) {
    private val limit=150_001
    suspend fun save(index:IndexedScan) {
        if(index.root.directory!=root || index.root.rootUnreadable)return
        if(index.root.entries.any{it.directory && it.files>0 && it.file !in index.folders}){clear();return}
        val context=currentCoroutineContext()
        val tmp=File(file.parentFile,file.name+".tmp")
        try {
            DataOutputStream(GZIPOutputStream(BufferedOutputStream(tmp.outputStream()))).use { out ->
                out.writeInt(2)
                out.writeUTF(root.absolutePath)
                var count=0
                fun writeEntry(entry:Entry) {
                    require(++count<=limit+100)
                    out.writeUTF(entry.file.relativeTo(root).path)
                    out.writeBoolean(entry.directory)
                    out.writeLong(entry.bytes)
                    out.writeLong(entry.files)
                    out.writeLong(entry.skipped)
                    out.writeLong(entry.modified)
                }
                fun writeSnapshot(snapshot:Snapshot) {
                    out.writeUTF(snapshot.directory.relativeTo(root).path)
                    out.writeBoolean(snapshot.rootUnreadable)
                    out.writeInt(snapshot.entries.size)
                    snapshot.entries.forEach(::writeEntry)
                }
                writeSnapshot(index.root)
                out.writeInt(index.root.largestFiles.size)
                index.root.largestFiles.forEach(::writeEntry)
                out.writeInt(index.folders.size)
                for(snapshot in index.folders.values){context.ensureActive();writeSnapshot(snapshot)}
            }
            context.ensureActive()
            if(!tmp.renameTo(file))throw IOException("Cannot replace index")
        } finally {tmp.delete()}
    }
    suspend fun load():IndexedScan? {
        if(!file.isFile || file.length()>32L*1024*1024)return null
        val context=currentCoroutineContext()
        return try {
            DataInputStream(GZIPInputStream(BufferedInputStream(file.inputStream()))).use { input ->
                require(input.readInt()==2 && input.readUTF()==root.absolutePath)
                var count=0
                fun path():File {
                    val relative=input.readUTF()
                    require(relative.isEmpty() || (!File(relative).isAbsolute && relative.split(File.separatorChar).none{it==".." || it=="." || it.isEmpty()}))
                    return if(relative.isEmpty())root else File(root,relative)
                }
                fun readEntry():Entry {
                    require(++count<=limit+100)
                    return Entry(path(),input.readBoolean(),input.readLong().also{require(it>=0)},input.readLong().also{require(it>=0)},input.readLong().also{require(it>=0)},input.readLong())
                }
                fun readSnapshot():Snapshot {
                    val dir=path()
                    val unreadable=input.readBoolean()
                    val n=input.readInt()
                    require(n in 0..limit)
                    val entries=List(n){readEntry().also{require(it.file.parentFile==dir)}}
                    return Snapshot(dir,entries,unreadable)
                }
                val main=readSnapshot()
                require(main.directory==root && !main.rootUnreadable)
                val n=input.readInt();require(n in 0..100)
                val largest=List(n){readEntry()}
                val folders=input.readInt();require(folders in 0..limit)
                val map=HashMap<File,Snapshot>()
                repeat(folders){context.ensureActive();val s=readSnapshot();require(s.directory!=root);map[s.directory]=s}
                require(input.read()==-1)
                IndexedScan(main.copy(largestFiles=largest),map)
            }
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){null}
    }
    fun clear(){file.delete()}
}
