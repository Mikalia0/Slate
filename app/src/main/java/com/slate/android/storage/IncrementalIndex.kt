// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

// Refresh a measured subtree without reading unrelated folders. The rest of
// the index remains a snapshot of its previous scan, not a live filesystem view.
internal suspend fun IndexedScan.replaceSubtree(replacement:IndexedScan):IndexedScan? {
    val context=currentCoroutineContext()
    val target=replacement.root.directory
    val base=root.directory
    if(target==base || !target.toPath().startsWith(base.toPath()) || replacement.root.rootUnreadable)return null
    if(!folders.containsKey(target) || (replacement.root.entries.any{it.directory && it.skipped==0L} && replacement.folders.isEmpty()))return null
    val updated=HashMap<File,Snapshot>()
    for((file,snapshot) in folders){
        context.ensureActive()
        if(!file.toPath().startsWith(target.toPath()))updated[file]=snapshot
    }
    updated.putAll(replacement.folders)
    updated[target]=replacement.root.copy(largestFiles=emptyList())
    var child=target
    var current=replacement.root
    var newRoot=root
    while(child!=base){
        context.ensureActive()
        val parent=child.parentFile ?: return null
        val old=if(parent==base)root else updated[parent] ?: return null
        if(old.entries.none{it.file==child && it.directory})return null
        val entries=old.entries.map{entry->
            if(entry.file==child)entry.copy(bytes=current.bytes,files=current.files,skipped=current.skipped)else entry
        }.sortedByDescending{it.bytes}
        current=old.copy(entries=entries,largestFiles=emptyList())
        if(parent==base)newRoot=current else updated[parent]=current
        child=parent
    }
    // Reject incomplete/over-limit indexes rather than producing misleading totals.
    var count=newRoot.entries.size.toLong()
    for(snapshot in updated.values){
        context.ensureActive()
        count+=snapshot.entries.size
        if(count>150_000)return null
        if(snapshot.entries.any{it.directory && it.skipped==0L && !updated.containsKey(it.file)})return null
    }
    if(newRoot.entries.any{it.directory && it.skipped==0L && !updated.containsKey(it.file)})return null
    val all=IndexedScan(newRoot,updated)
    return all.copy(root=newRoot.copy(largestFiles=all.largestUnder(newRoot)))
}
