// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.github.luben.zstd.ZstdInputStream
import com.github.junrar.Archive
import com.reandroid.apk.ApkModule
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class InputFile(val name:String,val file:File)
internal fun displayName(context:Context,uri:Uri):String{
    val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}
    return name?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf{it.isNotBlank()}?:"file"
}
internal suspend fun stageInput(context:Context,uri:Uri):InputFile{
    require(uri.scheme=="content")
    val name=displayName(context,uri)
    val temp=File.createTempFile("input-",".bin",context.cacheDir)
    try{
        val max=minOf(context.cacheDir.usableSpace,8L*1024*1024*1024)
        var total=0L
        checkNotNull(context.contentResolver.openInputStream(uri)).use{input->temp.outputStream().use{out->
            val buffer=ByteArray(65536)
            while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;require(total<=max);out.write(buffer,0,n)}
        }}
        return InputFile(name,temp)
    }catch(e:Throwable){temp.delete();throw e}
}
internal data class ManifestInfo(val xml:String,val label:String,val version:String,val code:String)
internal fun readManifest(input:InputFile):ManifestInfo{
    val apk=ApkModule.loadApkFile(input.file)
    try{
        val m=checkNotNull(apk.androidManifest)
        return ManifestInfo(m.serializeToXml(),"",m.versionName.orEmpty(),m.versionCode?.toString().orEmpty())
    }finally{apk.close()}
}
internal fun editManifest(context:Context,input:InputFile,destination:Uri,label:String,version:String,code:String,autoSign:Boolean=true,progress:(ApkWorkStage)->Unit={}){
    require(label.length<=200&&version.length<=100)
    val number=code.toIntOrNull()?.also{require(it>0)}
    require(code.isBlank()||number!=null)
    val tmp=File.createTempFile("edited-",".apk",context.cacheDir)
    var signed:File?=null
    try{
        progress(ApkWorkStage.READING)
        val apk=ApkModule.loadApkFile(input.file)
        progress(ApkWorkStage.EDITING)
        try{
            val manifest=checkNotNull(apk.androidManifest)
            if(label.isNotBlank())manifest.setApplicationLabel(label)
            if(version.isNotBlank())manifest.setVersionName(version)
            if(number!=null)manifest.setVersionCode(number)
            java.util.zip.ZipFile(input.file).use{zip->zip.entries().asSequence().map{it.name}.filter{it.uppercase(java.util.Locale.ROOT).let{n->n.startsWith("META-INF/")&&(n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")||n.endsWith(".EC")||n=="META-INF/MANIFEST.MF")}}.toList().forEach{apk.removeInputSource(it)}}
            apk.setApkSignatureBlock(null)
            progress(ApkWorkStage.REBUILDING)
            apk.writeApk(tmp)
        }finally{apk.close()}
        val output=signEditedApk(context,input.file,tmp,autoSign,progress)
        if(output!=tmp)signed=output
        progress(ApkWorkStage.SAVING)
        checkNotNull(context.contentResolver.openOutputStream(destination,"wt")).use{out->output.inputStream().use{it.copyTo(out)}}
    }finally{tmp.delete();signed?.delete()}
}
internal fun readHex(input:InputFile):String=input.file.inputStream().use{stream->
    val bytes=ByteArray(16*1024)
    var count=0
    while(count<bytes.size){val n=stream.read(bytes,count,bytes.size-count);if(n<=0)break;count+=n}
    buildString{
        for(offset in 0 until count step 16){
            append("%08X  ".format(offset))
            for(i in 0 until 16)append(if(offset+i<count)"%02X ".format(bytes[offset+i].toInt() and 255)else "   ")
            append("  ")
            for(i in offset until minOf(offset+16,count)){val n=bytes[i].toInt() and 255;append(if(n in 32..126)n.toChar()else '.')}
            append('\n')
        }
    }
}

private fun validParts(name:String):List<String>{
    require(name.isNotBlank()&&!name.startsWith('/')&&!name.startsWith('\\')&&!name.contains('\\')&&!name.contains(':')&&!name.contains('\u0000'))
    return name.trimEnd('/').split('/').also{parts->require(parts.size<=64&&parts.all{it.isNotBlank()&&it!="."&&it!=".."&&it.length<=255})}
}
private class Destination(private val context:Context,private val root:DocumentFile){
    private val dirs=hashMapOf("" to root)
    private val files=hashSetOf<String>()
    private var count=0
    private var total=0L
    private fun dir(parts:List<String>):DocumentFile{
        var current=root
        var key=""
        for(part in parts){key=if(key.isEmpty())part else "$key/$part";require(key !in files);current=dirs.getOrPut(key){checkNotNull(current.createDirectory(part))}}
        return current
    }
    fun directory(name:String){require(++count<=100000);dir(validParts(name))}
    fun write(name:String,copy:(OutputStream)->Unit){
        require(++count<=100000)
        val parts=validParts(name)
        val key=parts.joinToString("/")
        require(key !in dirs&&files.add(key))
        val dest=checkNotNull(dir(parts.dropLast(1)).createFile("application/octet-stream",parts.last()))
        checkNotNull(context.contentResolver.openOutputStream(dest.uri,"w")).use{raw->
            val bounded=object:FilterOutputStream(raw){
                override fun write(b:Int){total++;require(total<=32L*1024*1024*1024);out.write(b)}
                override fun write(b:ByteArray,off:Int,len:Int){total+=len;require(total<=32L*1024*1024*1024);out.write(b,off,len)}
            }
            copy(bounded)
        }
    }
}
internal suspend fun extractAdvanced(context:Context,input:InputFile,tree:Uri):String{
    val parent=checkNotNull(DocumentFile.fromTreeUri(context,tree))
    require(parent.isDirectory&&parent.canWrite())
    val name=input.name.substringBeforeLast('.').take(100).ifBlank{"archive"}
    val root=checkNotNull(parent.createDirectory("$name-${System.currentTimeMillis()}"))
    val dest=Destination(context,root)
    val lower=input.name.lowercase()
    try{
        when{
            lower.endsWith(".7z")->SevenZFile(input.file).use{archive->
                val buffer=ByteArray(65536)
                while(true){
                    coroutineContext.ensureActive()
                    val entry=archive.nextEntry?:break
                    if(entry.isDirectory){dest.directory(entry.name);continue}
                    require(!entry.isAntiItem)
                    dest.write(entry.name){out->while(true){val n=archive.read(buffer);if(n<0)break;if(n>0)out.write(buffer,0,n)}}
                }
            }
            lower.endsWith(".rar")->Archive(input.file).use{archive->
                while(true){
                    coroutineContext.ensureActive()
                    val entry=archive.nextFileHeader()?:break
                    val path=entry.fileName.replace('\\','/')
                    if(entry.isDirectory)dest.directory(path)else dest.write(path){out->archive.extractFile(entry,out)}
                }
            }
            else->{
                val raw=input.file.inputStream().buffered()
                raw.use{
                    val decoded=when{
                        lower.endsWith(".tzst")||lower.endsWith(".zst")->ZstdInputStream(raw)
                        lower.endsWith(".tgz")||lower.endsWith(".gz")->GZIPInputStream(raw)
                        lower.endsWith(".txz")||lower.endsWith(".xz")->XZInputStream(raw)
                        lower.endsWith(".tbz2")||lower.endsWith(".bz2")->BZip2CompressorInputStream(raw)
                        lower.endsWith(".lz4")->FramedLZ4CompressorInputStream(raw)
                        else->raw
                    }
                    decoded.use{stream->
                        val tar=lower.endsWith(".tar")||lower.contains(".tar.")||lower.endsWith(".tzst")||lower.endsWith(".tgz")||lower.endsWith(".txz")||lower.endsWith(".tbz2")
                        if(tar)TarArchiveInputStream(stream).use{archive->
                            while(true){
                                coroutineContext.ensureActive()
                                val entry=archive.nextTarEntry?:break
                                require(!entry.isSymbolicLink&&!entry.isLink)
                                if(entry.isDirectory)dest.directory(entry.name)
                                else{require(entry.isFile);dest.write(entry.name){out->archive.copyTo(out,65536)}}
                            }
                        }else dest.write(input.name.substringBeforeLast('.')){out->stream.copyTo(out,65536)}
                    }
                }
            }
        }
        return root.name?:name
    }catch(e:Throwable){runCatching{root.delete()};throw e}
}
