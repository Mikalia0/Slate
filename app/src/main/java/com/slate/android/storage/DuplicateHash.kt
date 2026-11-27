// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun hexDigest(bytes:ByteArray):String{
    val alphabet="0123456789abcdef"
    return String(CharArray(bytes.size*2){i->val b=bytes[i/2].toInt() and 255;alphabet[if(i%2==0)b ushr 4 else b and 15]})
}
internal suspend fun sampleFile(file:File,size:Long):String{
    val ctx=currentCoroutineContext()
    val digest=MessageDigest.getInstance("SHA-256")
    val buffer=ByteArray(4096)
    RandomAccessFile(file,"r").use{input->
        require(input.length()==size)
        for(offset in listOf(0L,(size/2-2048).coerceAtLeast(0), (size-4096).coerceAtLeast(0)).distinct()){
            ctx.ensureActive();input.seek(offset)
            val n=minOf(buffer.size.toLong(),size-offset).toInt()
            input.readFully(buffer,0,n);digest.update(buffer,0,n)
        }
    }
    return hexDigest(digest.digest())
}
