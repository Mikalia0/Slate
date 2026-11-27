// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal object NativeSevenZip {
    private val decoderAvailable by lazy { runCatching { System.loadLibrary("slate7z") }.isSuccess }
    private external fun extractNative(fd:Int,sink:NativeSevenZipSink,limit:Long):Int

    suspend fun compress(context:Context?,parent:File,files:List<File>,output:File,level:Int):Boolean {
        if(context==null)return false
        val executable=File(context.applicationInfo.nativeLibraryDir,"libslate7zz.so")
        if(!executable.canExecute()||files.any{it.parentFile!=parent||it.name.any{ch->ch=='\n'||ch=='\r'}})return false
        val list=File.createTempFile("7z-list-",".txt",context.cacheDir)
        var process:Process?=null
        try {
            list.writeText(files.joinToString("\n"){ "./"+it.name }+"\n",Charsets.UTF_8)
            val threads=Runtime.getRuntime().availableProcessors().coerceIn(1,4)
            check(output.delete())
            val args=mutableListOf(executable.path,"a","-t7z","-mx="+level.coerceIn(0,9),"-mmt=$threads","-ms=64m","-sse","-spd","-scsUTF-8","-bso0","-bsp0","-bse0","-y",output.absolutePath,"@"+list.absolutePath)
            if(level>0){args.add(3,"-m0=LZMA2");args.add(4,"-md=8m")}
            process=try{ProcessBuilder(args).directory(parent).redirectErrorStream(true).redirectOutput(File("/dev/null")).start()}catch(_:IOException){return false}
            while(!process.waitFor(150,TimeUnit.MILLISECONDS))coroutineContext.ensureActive()
            coroutineContext.ensureActive()
            if(process.exitValue()!=0)throw IOException("7-Zip failed ("+process.exitValue()+")")
            check(output.isFile&&output.length()>0)
            return true
        }finally{
            process?.let{if(it.isAlive){it.destroyForcibly();it.waitFor()}}
            list.delete()
        }
    }
    suspend fun extract(file:File,limit:Long,makeDirectory:(String)->Unit,openOutput:(String)->OutputStream):Boolean {
        if(!decoderAvailable)return false
        val ctx=coroutineContext
        val sink=NativeSevenZipSink(makeDirectory,openOutput){ctx.ensureActive()}
        try{
            val code=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{extractNative(it.fd,sink,limit)}
            if(code==0)return true
            // Unsupported methods or limited memory can be retried by the caller in a clean staging area.
            if(code in setOf(2,4))return false
            throw IOException("7-Zip extraction failed ($code)")
        }finally{sink.close()}
    }
}
internal class NativeSevenZipSink(private val directory:(String)->Unit,private val output:(String)->OutputStream,private val checkActive:()->Unit){
    var started=false;private set
    private var current:OutputStream?=null
    private var expected=0L
    private var written=0L
    @androidx.annotation.Keep fun begin(name:String,isDirectory:Boolean,size:Long,attributes:Int){
        checkActive();check(current==null);started=true
        // Unix file type in upper 16 bits, Windows reparse point in lower bits.
        val kind=(attributes ushr 16) and 0xF000
        require(attributes and 0x400==0 && (kind==0||kind==(if(isDirectory)0x4000 else 0x8000)))
        require(size>=0)
        expected=if(isDirectory)0 else size;written=0
        if(isDirectory)directory(name)else current=output(name)
    }
    @androidx.annotation.Keep fun write(data:ByteArray,length:Int){checkActive();require(length in 0..data.size);written+=length;require(written<=expected);checkNotNull(current).write(data,0,length)}
    @androidx.annotation.Keep fun finish(){checkActive();require(written==expected);close()}
    fun close(){current?.close();current=null}
}
