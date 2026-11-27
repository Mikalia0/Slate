// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import android.util.AtomicFile
import java.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

/** Persist only a complete full-permission snapshot and require a matching MediaStore generation. */
internal object GalleryCatalogStore {
    private const val MAX_ROWS=150000
    private fun file(context:Context)=AtomicFile(File(context.cacheDir,"gallery-catalog-v1"))
    suspend fun read(context:Context,stamp:String):List<MediaItem>? {
        val ctx=currentCoroutineContext()
        return try{
            DataInputStream(file(context).openRead().buffered(128*1024)).use{input->
                if(input.readInt()!=1||input.readUTF()!=stamp)return null
                val count=input.readInt();require(count in 0..MAX_ROWS)
                List(count){
                    ctx.ensureActive()
                    val id=input.readLong();val video=input.readBoolean()
                    val name=input.readUTF();val bucket=input.readUTF();val folder=input.readUTF()
                    val date=input.readLong();val bytes=input.readLong();val duration=input.readLong()
                    val relative=input.readUTF();val volume=input.readUTF();val modified=input.readLong()
                    val width=input.readInt();val height=input.readInt();val mime=input.readUTF()
                    require(volume.isNotBlank()&&id>=0&&bytes>=0)
                    val collection=if(video)MediaStore.Video.Media.getContentUri(volume)else MediaStore.Images.Media.getContentUri(volume)
                    MediaItem(id,ContentUris.withAppendedId(collection,id),video,name,bucket,folder,date,bytes,duration,relative,volume,modified,width,height,mime)
                }.also{require(input.read()==-1)}
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
    suspend fun write(context:Context,stamp:String,rows:List<MediaItem>){
        if(rows.size>MAX_ROWS)return
        val ctx=currentCoroutineContext();val target=file(context)
        var stream:FileOutputStream?=null
        try{
            val raw=target.startWrite();stream=raw
            val out=DataOutputStream(raw.buffered(128*1024))
            out.writeInt(1);out.writeUTF(stamp);out.writeInt(rows.size)
            for(row in rows){
                ctx.ensureActive()
                out.writeLong(row.id);out.writeBoolean(row.video);out.writeUTF(row.name);out.writeUTF(row.bucket);out.writeUTF(row.folder)
                out.writeLong(row.date);out.writeLong(row.bytes);out.writeLong(row.duration);out.writeUTF(row.relative);out.writeUTF(row.volume)
                out.writeLong(row.modified);out.writeInt(row.width);out.writeInt(row.height);out.writeUTF(row.mime)
            }
            out.flush();target.finishWrite(raw);stream=null
        }catch(e:CancellationException){throw e}catch(_:Exception){}finally{stream?.let{target.failWrite(it)}}
    }
    fun clear(context:Context){file(context).delete()}
}
