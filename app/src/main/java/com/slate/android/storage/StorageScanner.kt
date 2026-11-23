// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.util.PriorityQueue
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class Entry(val file: File, val directory: Boolean, val bytes: Long, val files: Long, val skipped: Long)
data class Snapshot(val directory: File, val entries: List<Entry>, val rootUnreadable: Boolean = false, val largestFiles: List<Entry> = emptyList()) {
    val bytes = entries.sumOf { it.bytes }
    val files = entries.sumOf { it.files }
    val skipped = entries.sumOf { it.skipped } + if (rootUnreadable) 1 else 0
}

data class ScanProgress(val visited: Long, val path: String, val bytes: Long, val elapsedMs: Long)

class StorageScanner {
    private fun children(file: File): Array<File>? = try { file.listFiles() } catch (_: SecurityException) { null }

    private fun attributes(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: java.io.IOException) { null } catch (_: SecurityException) { null }

    suspend fun scan(directory: File, onProgress: (ScanProgress) -> Unit = {}): Snapshot {
        val context = currentCoroutineContext()
        if (attributes(directory)?.isDirectory != true) return Snapshot(directory, emptyList(), true)
        val roots = children(directory) ?: return Snapshot(directory, emptyList(), true)
        var visited = 0L
        var measured = 0L
        val started = System.nanoTime()
        var lastReport = started
        var lastPath = directory.path
        fun report(force: Boolean = false) {
            val now = System.nanoTime()
            if (force || now - lastReport >= 200_000_000L) {
                lastReport = now
                onProgress(ScanProgress(visited, lastPath, measured, (now-started)/1_000_000L))
            }
        }
        report(true)
        val entries = ArrayList<Entry>(roots.size)
        val largest = PriorityQueue<Entry>(compareBy<Entry> { it.bytes }.thenBy { it.file.path })
        fun record(file: File, bytes: Long) {
            if (largest.size == 100 && bytes <= largest.peek()!!.bytes) return
            if (largest.size == 100) largest.poll()
            largest.add(Entry(file, false, bytes, 1, 0))
        }
        for (root in roots) {
            context.ensureActive()
            val rootAttributes = attributes(root)
            if (rootAttributes == null || rootAttributes.isSymbolicLink) {
                visited++; lastPath = root.path; report()
                entries += Entry(root, false, 0, 0, 1)
                continue
            }
            var bytes = 0L
            var files = 0L
            var skipped = 0L
            // Iterative depth-first traversal; retain only the current directory's entries.
            val stack = ArrayDeque<Iterator<File>>()
            stack.addLast(listOf(root).iterator())
            while (stack.isNotEmpty()) {
                context.ensureActive()
                val next = stack.last()
                if (!next.hasNext()) { stack.removeLast(); continue }
                val file = next.next()
                val attr = if (file == root) rootAttributes else attributes(file)
                visited++
                lastPath = file.path
                when {
                    attr == null || attr.isSymbolicLink -> skipped++
                    attr.isDirectory -> {
                        val list = children(file)
                        if (list == null) skipped++ else stack.addLast(list.iterator())
                    }
                    attr.isRegularFile -> { bytes += attr.size(); measured += attr.size(); files++; record(file, attr.size()) }
                    else -> skipped++
                }
                report()
            }
            entries += Entry(root, rootAttributes.isDirectory, bytes, files, skipped)
        }
        report(true)
        return Snapshot(directory, entries.sortedByDescending { it.bytes }, largestFiles = largest.sortedWith(compareByDescending<Entry> { it.bytes }.thenBy { it.file.path }))
    }
}
