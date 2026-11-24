// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.util.PriorityQueue
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class Entry(val file: File, val directory: Boolean, val bytes: Long, val files: Long, val skipped: Long, val modified: Long = 0L)
data class Snapshot(val directory: File, val entries: List<Entry>, val rootUnreadable: Boolean = false, val largestFiles: List<Entry> = emptyList()) {
    val bytes = entries.sumOf { it.bytes }
    val files = entries.sumOf { it.files }
    val skipped = entries.sumOf { it.skipped } + if (rootUnreadable) 1 else 0
}

data class ScanProgress(val visited: Long, val path: String, val bytes: Long, val elapsedMs: Long)

internal data class IndexedScan(val root: Snapshot, val folders: Map<File,Snapshot>) {
    suspend fun snapshot(directory: File): Snapshot? {
        if(directory==root.directory) return root
        val original=folders[directory] ?: return null
        return original.copy(largestFiles=largestUnder(original))
    }
    internal suspend fun largestUnder(original:Snapshot):List<Entry> {
        val context=currentCoroutineContext()
        val queue=PriorityQueue<Entry>(compareBy<Entry>{it.bytes}.thenBy{it.file.path})
        val pending=ArrayDeque<Iterator<Entry>>()
        pending.addLast(original.entries.iterator())
        while(pending.isNotEmpty()) {
            context.ensureActive()
            val iterator=pending.last()
            if(!iterator.hasNext()) { pending.removeLast(); continue }
            val entry=iterator.next()
            if(entry.directory) folders[entry.file]?.let{pending.addLast(it.entries.iterator())}
            else if(entry.files==1L && entry.skipped==0L) {
                if(queue.size<100) queue.add(entry)
                else if(entry.bytes>queue.peek()!!.bytes) { queue.poll();queue.add(entry) }
            }
        }
        return queue.sortedByDescending{it.bytes}
    }
}

class StorageScanner {
    private fun children(file: File): Array<File>? = try { file.listFiles() } catch (_: SecurityException) { null }
    private fun attributes(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: java.io.IOException) { null } catch (_: SecurityException) { null }

    suspend fun scan(directory: File, onProgress: (ScanProgress) -> Unit = {}): Snapshot =
        scanIndexed(directory,onProgress=onProgress).root

    internal suspend fun scanIndexed(directory: File, excluded:Set<File> = emptySet(), previous:IndexedScan? = null, protectedReader:ProtectedReadClient?=null, onProgress: (ScanProgress)->Unit = {}): IndexedScan {
        val context=currentCoroutineContext()
        if(isExcluded(directory,excluded)) return IndexedScan(Snapshot(directory,emptyList(),true),emptyMap())
        val protectedEntries=HashMap<File,ProtectedReadEntry>()
        fun listChildren(dir:File):Array<File>?{
            val relative=if(protectedReader!=null)protectedRelative(dir)else null
            if(relative!=null){
                val rows=runCatching{protectedReader!!.list(relative)}.getOrNull()?:return null
                return rows.map{row->File(dir,row.name).also{protectedEntries[it]=row}}.toTypedArray()
            }
            val normal=children(dir)?:return null
            // Some Android versions hide these directory entries even from their parent.
            return if(protectedReader!=null&&dir==File(android.os.Environment.getExternalStorageDirectory(),"Android"))
                (normal.toList()+listOf(File(dir,"data"),File(dir,"obb"))).distinct().toTypedArray() else normal
        }
        if(protectedRelative(directory)==null||protectedReader==null){
            if(attributes(directory)?.isDirectory!=true)return IndexedScan(Snapshot(directory,emptyList(),true),emptyMap())
        }
        val rootChildren=listChildren(directory) ?: return IndexedScan(Snapshot(directory,emptyList(),true),emptyMap())
        var visited=0L
        var measured=0L
        val started=System.nanoTime()
        var lastReport=started
        var lastPath=directory.path
        fun report(force:Boolean=false) {
            val now=System.nanoTime()
            if(force || now-lastReport>=200_000_000L) {
                lastReport=now
                onProgress(ScanProgress(visited,lastPath,measured,(now-started)/1_000_000L))
            }
        }
        val baseline=previous?.takeIf{it.root.directory==directory && !it.root.rootUnreadable}
        val largest=PriorityQueue<Entry>(compareBy<Entry>{it.bytes}.thenBy{it.file.path})
        val index=HashMap<File,Snapshot>()
        var retained=0L
        var indexing=true
        class Frame(val file:File,val children:Iterator<File>,val modified:Long=0L) {
            val entries=ArrayList<Entry>()
            val old=if(file==directory)baseline?.root else baseline?.folders?.get(file)
            val prior=old?.entries?.associateBy{it.file}.orEmpty()
            var unchanged=old!=null && !old.rootUnreadable
            var bytes=0L
            var files=0L
            var skipped=0L
            fun add(entry:Entry) {
                bytes+=entry.bytes; files+=entry.files; skipped+=entry.skipped
                if(indexing || file==directory) {
                    val cached=prior[entry.file]
                    if(cached!=entry)unchanged=false
                    entries.add(if(cached==entry)cached else entry)
                }
                if(indexing && ++retained>150_000) { indexing=false; index.clear() }
            }
        }
        val stack=ArrayDeque<Frame>()
        stack.addLast(Frame(directory,rootChildren.iterator()))
        report(true)
        while(stack.isNotEmpty()) {
            context.ensureActive()
            val frame=stack.last()
            if(!frame.children.hasNext()) {
                stack.removeLast()
                val snapshot=if((indexing || frame.file==directory) && frame.unchanged && frame.old?.entries?.size==frame.entries.size) frame.old!!
                    else Snapshot(frame.file,if(indexing || frame.file==directory) frame.entries.sortedWith(compareByDescending<Entry>{it.bytes}.thenBy{it.file.path}) else emptyList())
                if(frame.file==directory) {
                    report(true)
                    return IndexedScan(snapshot.copy(largestFiles=largest.sortedWith(compareByDescending<Entry>{it.bytes}.thenBy{it.file.path})),index)
                }
                if(indexing) index[frame.file]=snapshot
                stack.last().add(Entry(frame.file,true,frame.bytes,frame.files,frame.skipped,frame.modified))
                continue
            }
            val file=frame.children.next()
            if(isExcluded(file,excluded)) { frame.add(Entry(file,false,0,0,1)); continue }
            val remote=protectedEntries.remove(file)
            val protectedRoot=protectedReader!=null&&protectedRelative(file) in listOf("Android/data","Android/obb")
            val attr=if(remote==null&&!protectedRoot)attributes(file)else null
            val isDir=remote?.directory?: (protectedRoot||attr?.isDirectory==true)
            val isRegular=remote?.let{it.readable&&!it.directory}?: (attr?.isRegularFile==true)
            val modified=remote?.modified?:attr?.lastModifiedTime()?.toMillis()?:0L
            visited++;lastPath=file.path
            when {
                remote?.readable==false || (!protectedRoot&&remote==null&&(attr==null||attr.isSymbolicLink)) -> frame.add(Entry(file,false,0,0,1))
                isDir -> {
                    val list=listChildren(file)
                    if(list==null) {
                        frame.add(Entry(file,true,0,0,1))
                        if(indexing) index[file]=Snapshot(file,emptyList(),true)
                    } else stack.addLast(Frame(file,list.iterator(),modified))
                }
                isRegular -> {
                    val entry=Entry(file,false,remote?.size?:attr?.size()?:0L,1,0,modified)
                    measured+=entry.bytes
                    frame.add(entry)
                    if(largest.size<100) largest.add(entry)
                    else if(entry.bytes>largest.peek()!!.bytes){largest.poll();largest.add(entry)}
                }
                else -> frame.add(Entry(file,false,0,0,1))
            }
            report()
        }
        error("Scan ended unexpectedly")
    }
}
