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
        val context=currentCoroutineContext()
        val original=folders[directory] ?: return null
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
        return original.copy(largestFiles=queue.sortedByDescending{it.bytes})
    }
}

class StorageScanner {
    private fun children(file: File): Array<File>? = try { file.listFiles() } catch (_: SecurityException) { null }
    private fun attributes(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: java.io.IOException) { null } catch (_: SecurityException) { null }

    suspend fun scan(directory: File, onProgress: (ScanProgress) -> Unit = {}): Snapshot =
        scanIndexed(directory,onProgress).root

    internal suspend fun scanIndexed(directory: File, onProgress: (ScanProgress)->Unit = {}): IndexedScan {
        val context=currentCoroutineContext()
        if(attributes(directory)?.isDirectory!=true) return IndexedScan(Snapshot(directory,emptyList(),true),emptyMap())
        val rootChildren=children(directory) ?: return IndexedScan(Snapshot(directory,emptyList(),true),emptyMap())
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
        val largest=PriorityQueue<Entry>(compareBy<Entry>{it.bytes}.thenBy{it.file.path})
        val index=HashMap<File,Snapshot>()
        var retained=0L
        var indexing=true
        class Frame(val file:File,val children:Iterator<File>,val modified:Long=0L) {
            val entries=ArrayList<Entry>()
            var bytes=0L
            var files=0L
            var skipped=0L
            fun add(entry:Entry) {
                bytes+=entry.bytes; files+=entry.files; skipped+=entry.skipped
                if(indexing || file==directory) entries.add(entry)
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
                val snapshot=Snapshot(frame.file,if(indexing || frame.file==directory) frame.entries.sortedByDescending{it.bytes} else emptyList())
                if(frame.file==directory) {
                    report(true)
                    return IndexedScan(snapshot.copy(largestFiles=largest.sortedByDescending{it.bytes}),index)
                }
                if(indexing) index[frame.file]=snapshot
                stack.last().add(Entry(frame.file,true,frame.bytes,frame.files,frame.skipped,frame.modified))
                continue
            }
            val file=frame.children.next()
            val attr=attributes(file)
            visited++;lastPath=file.path
            when {
                attr==null || attr.isSymbolicLink -> frame.add(Entry(file,false,0,0,1))
                attr.isDirectory -> {
                    val list=children(file)
                    if(list==null) {
                        frame.add(Entry(file,true,0,0,1))
                        if(indexing) index[file]=Snapshot(file,emptyList(),true)
                    } else stack.addLast(Frame(file,list.iterator(),attr.lastModifiedTime().toMillis()))
                }
                attr.isRegularFile -> {
                    val entry=Entry(file,false,attr.size(),1,0,attr.lastModifiedTime().toMillis())
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
