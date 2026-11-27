// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

internal data class NativeFileStat(val file:File,val kind:Int,val size:Long,val modified:Long){
    val isDirectory get()=kind==1
    val isRegularFile get()=kind==2
    val isSymbolicLink get()=kind==3
}
internal object NativeDirectory{
    private val available by lazy{runCatching{System.loadLibrary("slatefs")}.isSuccess}
    private external fun readNative(path:ByteArray):ByteArray?
    fun list(folder:File):List<NativeFileStat>?{
        if(!available)return null
        return runCatching{
            val data=readNative(folder.absolutePath.toByteArray(Charsets.UTF_8))?:return null
            decode(folder,data)
        }.getOrNull()
    }
    internal fun decode(folder:File,data:ByteArray):List<NativeFileStat>{
        val b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val rows=ArrayList<NativeFileStat>()
        val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        while(b.hasRemaining()){
            val n=b.int;require(n in 1..4096&&b.remaining()>=n+17)
            val kind=b.get().toInt();val size=b.long;val modified=b.long
            val bytes=ByteArray(n);b.get(bytes)
            val name=decoder.decode(ByteBuffer.wrap(bytes)).toString()
            require(name!="."&&name!=".."&&!name.contains('/')&&!name.contains('\u0000'))
            rows.add(NativeFileStat(File(folder,name),kind,size,modified))
        }
        return rows
    }
}
