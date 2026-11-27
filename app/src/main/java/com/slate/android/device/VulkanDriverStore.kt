// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class VulkanDriver(val id:String,val name:String,val library:String="",val version:String="",val author:String="",val minApi:Int=0){
    val system get()=id=="System"
}
internal object VulkanDriverStore{
    val system=VulkanDriver("System","System")
    private fun root(context:Context)=File(context.filesDir,"vulkan-drivers").apply{mkdirs()}
    private fun manifest(dir:File):VulkanDriver{
        val file=File(dir,"meta.json")
        require(file.isFile&&file.length() in 1..65536)
        val json=JSONObject(file.readText())
        val name=json.optString("name").trim()
        val lib=json.optString("libraryName").trim()
        require(name.isNotEmpty()&&name.length<=200)
        require(lib.matches(Regex("[A-Za-z0-9_.+-]+\\.so"))&&lib!="libGL.so")
        require(!json.optString("type").equals("freedreno",true)){"OpenGL package, not a Vulkan driver"}
        val library=File(dir,lib)
        require(library.isFile&&library.canonicalFile.parentFile==dir.canonicalFile){"Missing driver library"}
        require(library.length()>=64){"Invalid ELF"}
        library.inputStream().use{input->
            val header=ByteArray(20);java.io.DataInputStream(input).readFully(header)
            require(header[0]==0x7f.toByte()&&header[1]==69.toByte()&&header[2]==76.toByte()&&header[3]==70.toByte()&&header[4]==2.toByte()&&header[5]==1.toByte()&&header[16]==3.toByte()&&header[17]==0.toByte()&&header[18]==183.toByte()&&header[19]==0.toByte()){"Expected an ARM64 shared ELF library"}
        }
        return VulkanDriver(dir.name,name,lib,json.optString("driverVersion",json.optString("version")),json.optString("author"),json.optInt("minApi",0))
    }
    fun list(context:Context):List<VulkanDriver> = listOf(system)+root(context).listFiles().orEmpty().filter{it.isDirectory&&!it.name.startsWith(".")}.mapNotNull{runCatching{manifest(it)}.getOrNull()}.sortedBy{it.name.lowercase()}
    suspend fun import(context:Context,uri:Uri):VulkanDriver{
        val ctx=currentCoroutineContext()
        val root=root(context)
        val staging=File(root,".import-"+UUID.randomUUID()).apply{check(mkdir())}
        try{
            var total=0L;var count=0;val buffer=ByteArray(256*1024)
            val seen=HashSet<String>()
            checkNotNull(context.contentResolver.openInputStream(uri)).use{source->ZipInputStream(source.buffered()).use{zip->
                while(true){
                    ctx.ensureActive()
                    val entry=zip.nextEntry?:break
                    require(++count<=4096){"Too many ZIP entries"}
                    val name=entry.name.trimEnd('/')
                    require(name.isNotBlank()&&!name.startsWith("/")&&!name.contains('\\')&&!name.contains('\u0000')&&!name.contains('\n')&&!name.contains('\r'))
                    require(name.split('/').all{it.isNotBlank()&&it!="."&&it!=".."}){"Unsafe ZIP path"}
                    require(seen.add(name)){"Duplicate ZIP entry"}
                    val dst=File(staging,name).canonicalFile
                    require(dst.path.startsWith(staging.canonicalPath+File.separator))
                    if(entry.isDirectory){check(dst.isDirectory||dst.mkdirs())}
                    else{
                        check(dst.parentFile!!.isDirectory||dst.parentFile!!.mkdirs())
                        check(dst.createNewFile())
                        var size=0L
                        FileOutputStream(dst).use{out->while(true){ctx.ensureActive();val n=zip.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=512L*1024*1024&&total<=1024L*1024*1024){"ZIP exceeds limits"};out.write(buffer,0,n)}}
                    }
                    zip.closeEntry()
                }
            }}
            val dir=if(File(staging,"meta.json").isFile)staging else staging.listFiles()?.singleOrNull()?.takeIf{it.isDirectory}?:error("Missing meta.json")
            manifest(dir)
            val dest=File(root,UUID.randomUUID().toString())
            check(dir.renameTo(dest)){"Cannot finalize driver import"}
            return manifest(dest)
        }finally{staging.deleteRecursively()}
    }
    fun delete(context:Context,id:String){require(id!="System"&&runCatching{UUID.fromString(id)}.isSuccess);check(File(root(context),id).deleteRecursively())}
    fun encoded(context:Context,id:String):String{
        if(id=="System")return id
        require(Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")&&android.os.Process.is64Bit()){"Custom driver loading requires ARM64"}
        require(runCatching{UUID.fromString(id)}.isSuccess)
        val dir=File(root(context),id)
        val driver=manifest(dir)
        val temp=File(context.cacheDir,"vulkan-loader").apply{mkdirs()}
        return listOf(dir.absolutePath+"/",driver.library,context.applicationInfo.nativeLibraryDir+"/",temp.absolutePath+"/").joinToString("\n")
    }
}
