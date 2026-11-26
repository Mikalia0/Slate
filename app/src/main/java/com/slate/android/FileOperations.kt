// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.os.ParcelFileDescriptor
import com.slate.android.storage.*
import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** File manager operations only: protected access is scoped to the current user's Android/data and obb. */
internal class FileOperations(private val remote:ProtectedReadClient?){
    private fun relative(file:File)=protectedRelative(file)
    private fun checked(root:File,file:File,mutation:Boolean=true){
        val path=file.absoluteFile.toPath().normalize()
        val base=root.absoluteFile.toPath().normalize()
        require(path.startsWith(base))
        val rel=relative(file)
        if(rel!=null){
            require(remote!=null&&shizukuGranted())
            if(mutation)require(validWriteRelative(rel))
        }else{
            require(!protectedAppPath(path)&&safeFile(root,file))
            var ancestor=file.absoluteFile
            while(ancestor.toPath().normalize()!=base){
                require(!Files.isSymbolicLink(ancestor.toPath()))
                ancestor=ancestor.parentFile?:error("Outside volume")
            }
            if(mutation)require(path!=base&&!(file.name=="Android"&&file.parentFile==root))
        }
    }
    private fun children(root:File,dir:File):List<Pair<File,Boolean>>{
        checked(root,dir,false)
        val rel=relative(dir)
        if(rel!=null)return remote!!.list(rel).map{
            require(it.readable){"Unsupported file type"}
            File(dir,it.name) to it.directory
        }
        return (dir.listFiles()?:throw IOException("Unreadable directory")).map{
            checked(root,it,false)
            require(!Files.isSymbolicLink(it.toPath()))
            require(Files.isDirectory(it.toPath(),NOFOLLOW_LINKS)||Files.isRegularFile(it.toPath(),NOFOLLOW_LINKS))
            it to Files.isDirectory(it.toPath(),NOFOLLOW_LINKS)
        }
    }
    fun mkdir(root:File,file:File){
        checked(root,file)
        relative(file)?.let{remote!!.mkdir(it);return}
        Files.createDirectory(file.toPath())
    }
    fun rename(root:File,source:File,target:File){
        checked(root,source);checked(root,target)
        val a=relative(source);val b=relative(target)
        if(a!=null&&b!=null){remote!!.rename(a,b);return}
        require(a==null&&b==null)
        Files.move(source.toPath(),target.toPath())
    }
    private fun deleteSingle(root:File,file:File){
        checked(root,file)
        relative(file)?.let{remote!!.delete(it);return}
        Files.delete(file.toPath())
    }
    // Preflight the whole tree before deleting. No links or protected roots.
    suspend fun delete(root:File,file:File,directory:Boolean){
        val ctx=currentCoroutineContext()
        val pending=ArrayDeque<Pair<File,Boolean>>()
        val post=ArrayList<File>()
        pending.addLast(file to directory)
        while(pending.isNotEmpty()){
            ctx.ensureActive()
            val (node,isDir)=pending.removeLast()
            checked(root,node)
            require(post.size<150000){"Too many items"}
            post.add(node)
            if(isDir)children(root,node).forEach{pending.addLast(it)}
        }
        for(node in post.asReversed()){ctx.ensureActive();deleteSingle(root,node)}
    }
    private fun input(root:File,file:File):InputStream{
        checked(root,file,false)
        return relative(file)?.let{ParcelFileDescriptor.AutoCloseInputStream(remote!!.open(it))}
            ?:Files.newInputStream(file.toPath(),NOFOLLOW_LINKS)
    }
    private fun output(root:File,file:File):OutputStream{
        checked(root,file)
        return relative(file)?.let{ParcelFileDescriptor.AutoCloseOutputStream(remote!!.create(it))}
            ?:Files.newOutputStream(file.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE,NOFOLLOW_LINKS)
    }
    suspend fun copy(sourceRoot:File,source:File,targetRoot:File,target:File,directory:Boolean,move:Boolean,onBytes:suspend (Long)->Unit={}){
        checked(sourceRoot,source,move);checked(targetRoot,target)
        require(!target.absoluteFile.toPath().normalize().startsWith(source.absoluteFile.toPath().normalize()))
        if(move&&relative(source)!=null&&relative(target)!=null){
            // Same emulated volume, no overwrite.
            remote!!.rename(relative(source)!!,relative(target)!!);return
        }
        val ctx=currentCoroutineContext()
        val pending=ArrayDeque<Triple<File,File,Boolean>>()
        pending.addLast(Triple(source,target,directory))
        var count=0
        while(pending.isNotEmpty()){
            ctx.ensureActive();require(++count<=150000)
            val (from,to,isDir)=pending.removeLast()
            checked(sourceRoot,from,false);checked(targetRoot,to)
            if(isDir){
                val list=children(sourceRoot,from)
                mkdir(targetRoot,to)
                list.forEach{(child,dir)->pending.addLast(Triple(child,File(to,child.name),dir))}
            }else{
                var created=false
                try{
                    input(sourceRoot,from).use{ins->
                        output(targetRoot,to).use{out->
                            created=true
                            val buf=ByteArray(128*1024)
                            while(true){ctx.ensureActive();val n=ins.read(buf);if(n<0)break;out.write(buf,0,n);onBytes(n.toLong())}
                            out.flush()
                        }
                    }
                }catch(e:Exception){if(created)runCatching{deleteSingle(targetRoot,to)};throw e}
            }
        }
        // Only delete the original after the entire copy completes.
        if(move)delete(sourceRoot,source,directory)
    }
}
internal suspend fun <T> withFileOperations(context:Context,privileged:Boolean,block:suspend (FileOperations)->T):T =
    if(privileged)withProtectedRead(context){block(FileOperations(it))}else block(FileOperations(null))
