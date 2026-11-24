// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class CleanupKind(val title:String) { DUPLICATES("Duplicates"), TEMPORARY("Temporary files"), EMPTY("Empty folders") }
internal data class CleanupItem(val file:File,val bytes:Long,val modified:Long,val group:String="")
internal data class CleanupResult(val items:List<CleanupItem>,val limited:Boolean)
internal suspend fun findCleanup(root:File,kind:CleanupKind,excluded:Set<String>,progress:(Long)->Unit):CleanupResult {
    val context=currentCoroutineContext()
    val stack=ArrayDeque<File>();stack.add(root)
    val results=ArrayList<CleanupItem>()
    val candidates=HashMap<Long,MutableList<CleanupItem>>()
    var visited=0L;var limited=false;var bytesHashed=0L
    fun skip(file:File):Boolean {
        val relative=file.relativeTo(root).path
        return relative=="Android" || relative.startsWith("Android/") || excluded.any{relative==it || relative.startsWith("$it/")}
    }
    while(stack.isNotEmpty()){
        context.ensureActive()
        if(visited>=150_000L){limited=true;break}
        val file=stack.removeLast()
        if(file!=root && skip(file))continue
        val attr=runCatching{Files.readAttributes(file.toPath(),BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)}.getOrNull() ?: continue
        if(attr.isSymbolicLink)continue
        visited++
        if(visited%128==0L)progress(visited)
        if(attr.isDirectory){
            val children=runCatching{file.listFiles()}.getOrNull() ?: continue
            if(kind==CleanupKind.EMPTY && file!=root && children.isEmpty())results.add(CleanupItem(file,0,attr.lastModifiedTime().toMillis()))
            else children.forEach{stack.add(it)}
        } else if(attr.isRegularFile) {
            val item=CleanupItem(file,attr.size(),attr.lastModifiedTime().toMillis())
            when(kind){
                CleanupKind.DUPLICATES->if(item.bytes>=4096)candidates.getOrPut(item.bytes){ArrayList()}.add(item)
                CleanupKind.TEMPORARY->if(file.extension.lowercase() in setOf("tmp","temp","part") && item.modified>0 && System.currentTimeMillis()-item.modified>=7L*24*60*60*1000)results.add(item)
                else->Unit
            }
        }
        if(results.size>=5000){limited=true;break}
    }
    if(kind==CleanupKind.DUPLICATES){
        val buffer=ByteArray(128*1024)
        outer@for((size,files) in candidates.entries.sortedByDescending{it.key}){
            if(files.size<2)continue
            val groups=HashMap<String,MutableList<CleanupItem>>()
            for(item in files) {
                context.ensureActive()
                if(size>2L*1024*1024*1024-bytesHashed){limited=true;break@outer}
                val digest=MessageDigest.getInstance("SHA-256")
                val hash=try{
                    item.file.inputStream().buffered().use{input->
                        while(true){
                            context.ensureActive()
                            val n=input.read(buffer)
                            if(n<0)break
                            bytesHashed+=n
                            if(bytesHashed>2L*1024*1024*1024)break
                            digest.update(buffer,0,n)
                        }
                    }
                    if(bytesHashed>2L*1024*1024*1024){limited=true;break@outer}
                    if(item.file.length()!=item.bytes || item.file.lastModified()!=item.modified)continue
                    digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
                }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){continue}
                groups.getOrPut(hash){ArrayList()}.add(item)
            }
            groups.values.filter{it.size>1}.forEach{group->results.addAll(group.map{it.copy(group=group.first().file.path)})}
            if(results.size>=5000){limited=true;break}
        }
    }
    progress(visited)
    return CleanupResult(results,limited)
}
internal fun deleteCleanup(root:File,items:List<CleanupItem>,kind:CleanupKind):Pair<Int,Int> {
    var ok=0;var failed=0
    val base=root.toPath().toAbsolutePath().normalize()
    for(item in items){
        try{
            val target=item.file.toPath().toAbsolutePath().normalize()
            require(target!=base && target.startsWith(base))
            val relative=base.relativize(target)
            require(!relative.startsWith("Android"))
            var ancestor=base
            for(part in relative){ancestor=ancestor.resolve(part);require(!Files.isSymbolicLink(ancestor))}
            val attr=Files.readAttributes(target,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            require(attr.lastModifiedTime().toMillis()==item.modified)
            if(kind==CleanupKind.EMPTY)require(attr.isDirectory)
            else require(attr.isRegularFile && attr.size()==item.bytes)
            // Non-recursive deletion: a folder that gained files will not be removed.
            Files.delete(target);ok++
        }catch(_:Exception){failed++}
    }
    return ok to failed
}
