// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

internal const val UAD_SOURCE="https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
internal data class DebloatInfo(val risk:String,val description:String)
internal fun parseDebloatList(raw:String):Map<String,DebloatInfo>{
    val json=JSONObject(raw)
    val result=HashMap<String,DebloatInfo>()
    for(name in json.keys()){
        val obj=json.optJSONObject(name)?:continue
        if(!validCachePackage(name))continue
        val risk=obj.optString("removal","").lowercase(java.util.Locale.ROOT)
        val category=if(risk in setOf("recommended","advanced","expert","unsafe"))risk else "unknown"
        result[name]=DebloatInfo(category,obj.optString("description","").take(24000))
    }
    require(result.isNotEmpty())
    return result
}
internal data class DebloatDatabase(val rows:Map<String,DebloatInfo>,val stale:Boolean=false)
internal suspend fun loadDebloatDatabase(context:Context,refresh:Boolean):DebloatDatabase=withContext(Dispatchers.IO){
    val cache=AtomicFile(File(context.filesDir,"debloat-uad.json"))
    val old=runCatching{parseDebloatList(cache.readFully().toString(Charsets.UTF_8))}.getOrNull()
    if(old!=null&&!refresh)return@withContext DebloatDatabase(old)
    try{
        val conn=URL(UAD_SOURCE).openConnection() as HttpURLConnection
        conn.connectTimeout=12000;conn.readTimeout=15000;conn.instanceFollowRedirects=false
        val bytes=try{
            check(conn.responseCode==200)
            conn.inputStream.use{input->
                val out=java.io.ByteArrayOutputStream();val buf=ByteArray(16384)
                while(true){currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break;require(out.size()+n<=8*1024*1024);out.write(buf,0,n)}
                out.toByteArray()
            }
        }finally{conn.disconnect()}
        val parsed=parseDebloatList(bytes.toString(Charsets.UTF_8))
        val output=cache.startWrite()
        try{output.write(bytes);cache.finishWrite(output)}catch(e:Exception){cache.failWrite(output);throw e}
        DebloatDatabase(parsed)
    }catch(e:CancellationException){throw e}catch(e:Exception){if(old!=null)DebloatDatabase(old,true)else throw e}
}
internal const val DEBLOAT_DISABLE=0
internal const val DEBLOAT_REMOVE=1
internal const val DEBLOAT_RESTORE=2
internal const val DEBLOAT_ENABLE=3
internal fun debloatCommand(user:Int,pkg:String,action:Int):List<String>{
    require(user>=0&&validCachePackage(pkg))
    val prefix=listOf("/system/bin/pm")
    return prefix+when(action){
        DEBLOAT_DISABLE->listOf("disable-user","--user",user.toString(),pkg)
        DEBLOAT_REMOVE->listOf("uninstall","-k","--user",user.toString(),pkg)
        DEBLOAT_RESTORE->listOf("install-existing","--user",user.toString(),pkg)
        DEBLOAT_ENABLE->listOf("enable","--user",user.toString(),pkg)
        else->error("Invalid action")
    }
}
internal fun protectedDebloatPackage(pkg:String,own:String)=pkg==own||pkg in setOf("android","com.android.systemui","com.android.settings","com.android.shell","com.android.permissioncontroller","com.google.android.permissioncontroller","com.android.packageinstaller","com.google.android.packageinstaller","moe.shizuku.privileged.api")
class DebloatUserService:IDebloatService.Stub(){
    @Synchronized override fun change(packageName:String,action:Int):String{
        check(Process.myUid() in listOf(0,2000))
        val caller=Binder.getCallingUid();require(caller>=10000)
        require(validCachePackage(packageName))
        require(action in DEBLOAT_DISABLE..DEBLOAT_ENABLE)
        if(action==DEBLOAT_DISABLE||action==DEBLOAT_REMOVE)require(!protectedDebloatPackage(packageName,"com.slate.android"))
        val user=caller/100000
        val p=ProcessBuilder(debloatCommand(user,packageName,action)).redirectErrorStream(true).start()
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        try{
            val output=executor.submit<String>{p.inputStream.bufferedReader().use{r->
                val b=StringBuilder();val chars=CharArray(1024)
                while(true){val n=r.read(chars);if(n<0)break;if(b.length<8192)b.append(chars,0,minOf(n,8192-b.length))}
                b.toString().trim()
            }}
            if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();error("Package manager timeout")}
            val message=output.get(5,TimeUnit.SECONDS)
            val confirmed=when(action){
                DEBLOAT_REMOVE->message.lineSequence().any{it.trim()=="Success"}
                DEBLOAT_DISABLE->message.contains("disabled-user",true)
                DEBLOAT_ENABLE->message.contains("new state: enabled",true)
                DEBLOAT_RESTORE->message.contains("installed for user",true)||message.lineSequence().any{it.trim()=="Success"}
                else->false
            }
            return if(p.exitValue()==0&&confirmed)"Success"else message.ifBlank{"Package manager exit "+p.exitValue()}
                .let{if(it=="Success")"Operation not verified"else it}
        }finally{p.destroyForcibly();executor.shutdownNow()}
    }
    override fun destroy(){kotlin.system.exitProcess(0)}
}
private val debloatLock=Mutex()
internal suspend fun <T> withDebloatService(context:Context,block:suspend (IDebloatService)->T):T=debloatLock.withLock{
    check(shizukuInstallReady())
    val args=Shizuku.UserServiceArgs(ComponentName(context,DebloatUserService::class.java)).daemon(false).processNameSuffix("debloat").version(1)
    val ready=CompletableDeferred<IDebloatService>()
    val conn=object:ServiceConnection{
        override fun onServiceConnected(name:ComponentName,binder:IBinder){ready.complete(IDebloatService.Stub.asInterface(binder))}
        override fun onServiceDisconnected(name:ComponentName){ready.completeExceptionally(IllegalStateException("Shizuku disconnected"))}
    }
    try{Shizuku.bindUserService(args,conn);val svc=withTimeout(15000){ready.await()};withContext(Dispatchers.IO){block(svc)}}
    finally{runCatching{Shizuku.unbindUserService(args,conn,true)}}
}
