// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.util.PriorityQueue
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class FileCategory(val label:String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"),
    ARCHIVES("Archives"), INSTALLERS("Installers"), OTHER("Other files")
}
internal data class CategoryStats(val category:FileCategory,val bytes:Long,val count:Long,val largest:List<Entry>)
internal fun fileCategory(extension:String):FileCategory=when(extension.lowercase(Locale.ROOT)){
    "jpg","jpeg","png","webp","gif","bmp","heic","heif","avif","svg","dng"->FileCategory.IMAGES
    "mp4","mkv","webm","mov","avi","3gp","m4v","mpeg","mpg"->FileCategory.VIDEOS
    "mp3","wav","ogg","opus","flac","aac","m4a","wma"->FileCategory.AUDIO
    "pdf","doc","docx","xls","xlsx","ppt","pptx","txt","odt","ods","csv","epub","rtf"->FileCategory.DOCUMENTS
    "zip","rar","7z","tar","gz","xz","bz2","zst"->FileCategory.ARCHIVES
    "apk","apks","xapk","apkm"->FileCategory.INSTALLERS
    else->FileCategory.OTHER
}

internal suspend fun IndexedScan.personalCategories():List<CategoryStats>? {
    val context=currentCoroutineContext()
    val counts=LongArray(FileCategory.entries.size)
    val bytes=LongArray(FileCategory.entries.size)
    val largest=Array(FileCategory.entries.size){PriorityQueue<Entry>(compareBy<Entry>{it.bytes}.thenBy{it.file.path})}
    val pending=ArrayDeque<Iterator<Entry>>()
    pending.add(root.entries.filterNot{it.file.name=="Android" && it.directory}.iterator())
    while(pending.isNotEmpty()){
        context.ensureActive()
        val iterator=pending.last()
        if(!iterator.hasNext()){pending.removeLast();continue}
        val entry=iterator.next()
        if(entry.directory) {
            val folder=folders[entry.file]
            if(folder==null && entry.files>0)return null
            if(folder!=null)pending.add(folder.entries.iterator())
        }else if(entry.skipped==0L && entry.files==1L){
            val i=fileCategory(entry.file.extension).ordinal
            counts[i]++;bytes[i]+=entry.bytes
            val queue=largest[i]
            if(queue.size<100)queue.add(entry)
            else if(entry.bytes>queue.peek()!!.bytes){queue.poll();queue.add(entry)}
        }
    }
    return FileCategory.entries.map{category->
        val i=category.ordinal
        CategoryStats(category,bytes[i],counts[i],largest[i].sortedByDescending{it.bytes})
    }.filter{it.count>0}.sortedByDescending{it.bytes}
}
