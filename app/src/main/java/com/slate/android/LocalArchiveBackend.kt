// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import com.github.junrar.Archive
import com.slate.android.storage.protectedAppPath
import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption
import java.util.zip.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.sevenz.*
import org.apache.commons.compress.compressors.bzip2.*
import org.apache.commons.compress.compressors.lz4.*
import org.tukaani.xz.*

internal enum class LocalArchiveFormat(val suffix:String,val label:String){
    ZIP("zip","ZIP"),SEVEN("7z","7z"),TAR("tar","TAR"),GZIP("tar.gz","TAR + Gzip"),BZIP("tar.bz2","TAR + BZip2"),XZ("tar.xz","TAR + XZ"),ZSTD("tar.zst","TAR + Zstandard"),LZ4("tar.lz4","TAR + LZ4")
}
private val archiveSuffixes=listOf(".tar.gz",".tar.bz2",".tar.xz",".tar.zstd",".tar.zst",".tar.lz4",".tgz",".tbz2",".txz",".tzst",".zip",".7z",".rar",".tar",".gz",".bz2",".xz",".zstd",".zst",".lz4")
internal fun localArchiveSupported(name:String)=archiveSuffixes.any{name.endsWith(it,true)}
private fun checkLocal(root:File,file:File){require(safeFile(root,file)&&!protectedAppPath(file.toPath()))}
private suspend fun streamCopy(input:InputStream,output:OutputStream,buffer:ByteArray,account:(Int)->Unit={}){
    while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;if(n>0){account(n);output.write(buffer,0,n)}}
}
private data class ArchiveSource(val file:File,val name:String,val directory:Boolean,val size:Long)
private suspend fun archiveSources(root:File,files:List<File>):List<ArchiveSource>{
    val list=ArrayList<ArchiveSource>()
    suspend fun visit(file:File,name:String){
        coroutineContext.ensureActive();checkLocal(root,file);require(list.size<100000)
        val a=Files.readAttributes(file.toPath(),java.nio.file.attribute.BasicFileAttributes::class.java,NOFOLLOW_LINKS)
        require(a.isDirectory||a.isRegularFile)
        list.add(ArchiveSource(file,name,a.isDirectory,a.size()))
        if(a.isDirectory)Files.newDirectoryStream(file.toPath()).use{children->for(child in children)visit(child.toFile(),name+"/"+child.fileName)}
    }
    for(file in files.distinct())visit(file,file.name)
    require(list.map{it.name}.distinct().size==list.size)
    return list
}
internal suspend fun createLocalArchive(root:File,parent:File,files:List<File>,name:String,format:LocalArchiveFormat,level:Int):File{
    checkLocal(root,parent);require(parent.isDirectory&&parent.canWrite()&&files.isNotEmpty()&&validFileName(name))
    val sources=archiveSources(root,files)
    val destination=File(parent,if(name.endsWith("."+format.suffix,true))name else name+"."+format.suffix)
    require(!destination.exists())
    val temp=Files.createTempFile(parent.toPath(),".slate-archive-",".tmp").toFile()
    val buffer=ByteArray(256*1024)
    try{
        when(format){
            LocalArchiveFormat.ZIP->ZipOutputStream(temp.outputStream().buffered(buffer.size)).use{out->
                out.setLevel(level.coerceIn(0,9))
                for(source in sources){coroutineContext.ensureActive();checkLocal(root,source.file);out.putNextEntry(ZipEntry(source.name+if(source.directory)"/"else "").apply{time=source.file.lastModified()});if(!source.directory)source.file.inputStream().use{streamCopy(it,out,buffer)};out.closeEntry()}
            }
            LocalArchiveFormat.SEVEN->SevenZOutputFile(temp).use{out->
                out.setContentCompression(if(level==0)SevenZMethod.COPY else SevenZMethod.LZMA2)
                for(source in sources){coroutineContext.ensureActive();checkLocal(root,source.file);out.putArchiveEntry(out.createArchiveEntry(source.file,source.name));if(!source.directory)source.file.inputStream().use{input->while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}};out.closeArchiveEntry()}
            }
            else->temp.outputStream().buffered(buffer.size).use{raw->
                val encoded:OutputStream=when(format){
                    LocalArchiveFormat.GZIP->GZIPOutputStream(raw,buffer.size)
                    LocalArchiveFormat.BZIP->BZip2CompressorOutputStream(raw,level.coerceIn(1,9))
                    LocalArchiveFormat.XZ->XZOutputStream(raw,LZMA2Options(level.coerceIn(0,6)))
                    LocalArchiveFormat.ZSTD->ZstdOutputStream(raw,level.coerceIn(1,9))
                    LocalArchiveFormat.LZ4->FramedLZ4CompressorOutputStream(raw)
                    else->raw
                }
                TarArchiveOutputStream(encoded).use{out->
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    for(source in sources){coroutineContext.ensureActive();checkLocal(root,source.file);val entry=TarArchiveEntry(source.file,source.name);out.putArchiveEntry(entry);if(!source.directory)source.file.inputStream().use{streamCopy(it,out,buffer)};out.closeArchiveEntry()}
                }
            }
        }
        coroutineContext.ensureActive();Files.move(temp.toPath(),destination.toPath());return destination
    }finally{temp.delete()}
}
internal suspend fun extractLocalArchive(root:File,file:File):File{
    checkLocal(root,file);require(file.isFile&&localArchiveSupported(file.name))
    val lower=file.name.lowercase()
    if(lower.endsWith(".zip"))return extractSlateZip(root,file)
    val parent=checkNotNull(file.parentFile);checkLocal(root,parent)
    val suffix=archiveSuffixes.first{lower.endsWith(it)}
    val base=file.name.dropLast(suffix.length).ifBlank{"archive"}
    require(validFileName(base))
    val staging=Files.createTempDirectory(parent.toPath(),".slate-extract-").toFile()
    val buffer=ByteArray(256*1024)
    var count=0;var bytes=0L
    val limit=minOf(parent.usableSpace,32L*1024*1024*1024)
    fun account(n:Int){bytes+=n;require(bytes<=limit)}
    fun target(name:String):File{require(++count<=100000);return archiveTarget(staging,name)}
    fun directory(name:String){Files.createDirectories(target(name).toPath())}
    fun output(name:String):OutputStream{
        val dest=target(name);Files.createDirectories(dest.parentFile!!.toPath())
        return object:FilterOutputStream(Files.newOutputStream(dest.toPath(),StandardOpenOption.CREATE_NEW).buffered(buffer.size)){
            override fun write(b:Int){account(1);out.write(b)}
            override fun write(b:ByteArray,off:Int,len:Int){account(len);out.write(b,off,len)}
        }
    }
    try{
        when(suffix){
            ".7z"->SevenZFile(file).use{archive->
                while(true){coroutineContext.ensureActive();val e=archive.nextEntry?:break;require(!e.isAntiItem);if(e.isDirectory)directory(e.name)else output(e.name).use{out->while(true){coroutineContext.ensureActive();val n=archive.read(buffer);if(n<0)break;if(n>0)out.write(buffer,0,n)}}}
            }
            ".rar"->Archive(file).use{archive->while(true){coroutineContext.ensureActive();val e=archive.nextFileHeader()?:break;val name=e.fileName.replace('\\','/');if(e.isDirectory)directory(name)else output(name).use{archive.extractFile(e,it)}}}
            else->file.inputStream().buffered(buffer.size).use{raw->
                val decoded:InputStream=when{
                    suffix.endsWith("gz")->GZIPInputStream(raw,buffer.size)
                    suffix.endsWith("bz2")->BZip2CompressorInputStream(raw)
                    suffix.endsWith("xz")->XZInputStream(raw)
                    suffix.endsWith("zst")||suffix.endsWith("zstd")->ZstdInputStream(raw)
                    suffix.endsWith("lz4")->FramedLZ4CompressorInputStream(raw)
                    else->raw
                }
                decoded.use{input->
                    if(suffix.startsWith(".tar")||suffix in listOf(".tgz",".tbz2",".txz",".tzst"))TarArchiveInputStream(input).use{tar->
                        while(true){coroutineContext.ensureActive();val e=tar.nextTarEntry?:break;require(!e.isLink&&!e.isSymbolicLink);if(e.isDirectory)directory(e.name)else{require(e.isFile);output(e.name).use{streamCopy(tar,it,buffer)}}}
                    }else output(base).use{streamCopy(input,it,buffer)}
                }
            }
        }
        coroutineContext.ensureActive()
        for(i in 0..9999){val dest=File(parent,if(i==0)base else "$base ($i)");try{Files.move(staging.toPath(),dest.toPath());return dest}catch(_:java.nio.file.FileAlreadyExistsException){}catch(_:java.nio.file.DirectoryNotEmptyException){}}
        error("No available directory name")
    }finally{if(staging.exists())staging.deleteRecursively()}
}
@Composable internal fun LocalArchiveDialog(files:List<File>,onClose:()->Unit,onCreate:(List<File>,String,LocalArchiveFormat,Int)->Unit){
    var name by remember{mutableStateOf(files.singleOrNull()?.nameWithoutExtension?.ifBlank{"archive"}?:"archive")}
    var format by remember{mutableStateOf(LocalArchiveFormat.ZIP)}
    var level by remember{mutableIntStateOf(3)}
    LaunchedEffect(format){level=if(format==LocalArchiveFormat.SEVEN)6 else 3}
    var menu by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onClose,title={Text(tr("Compress"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(name,{name=it},label={Text(tr("Name"))},singleLine=true)
        Box{OutlinedButton(onClick={menu=true}){Text(format.label)};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){LocalArchiveFormat.entries.forEach{option->DropdownMenuItem(text={Text(option.label)},onClick={format=option;menu=false})}}}
        if(format in listOf(LocalArchiveFormat.ZIP,LocalArchiveFormat.BZIP,LocalArchiveFormat.XZ,LocalArchiveFormat.ZSTD,LocalArchiveFormat.SEVEN)){
            Text(tr("Compression"))
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(0 to "Store",3 to "Fast",6 to "Normal",9 to "Maximum").filter{format==LocalArchiveFormat.ZIP||format==LocalArchiveFormat.SEVEN||it.first>0}.filter{format!=LocalArchiveFormat.SEVEN||it.first in listOf(0,6)}.filter{format!=LocalArchiveFormat.XZ||it.first<=6}.forEach{(value,label)->FilterChip(selected=level==value,onClick={level=value},label={Text(tr(label))})}}
        }
        Text(name+"."+format.suffix,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=validFileName(name),onClick={onCreate(files,name,format,level)}){Text(tr("Compress"))}},dismissButton={TextButton(onClick=onClose){Text(tr("Cancel"))}})
}
