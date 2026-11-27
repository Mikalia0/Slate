// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object VulkanNative{
    init{System.loadLibrary("slatevulkan")}
    external fun queryCapabilities(driverName:String,context:Context):Array<String>
    external fun runGpuTransferTest(driverName:String,context:Context,preflight:Boolean):String
    external fun runGpuCubeBenchmark(driverName:String,context:Context,reporter:Any?):String
}
class VulkanProbeService:Service(){
    private val main=Handler(Looper.getMainLooper())
    private var started=false
    private fun end(){android.os.Process.killProcess(android.os.Process.myPid())}
    private val messenger=Messenger(Handler(Looper.getMainLooper()){msg->
        if(msg.what==2){end();return@Handler true}
        if(msg.what!=1||started)return@Handler true
        started=true
        val reply=msg.replyTo
        val id=msg.data.getString("driver")?:"System"
        val kind=msg.data.getString("kind")?:"report"
        // The native driver runs on a worker, leaving the watchdog responsive.
        main.postDelayed({end()},45_000)
        Thread{
            val data=Bundle()
            try{
                val driver=VulkanDriverStore.encoded(this,id)
                val rows=when(kind){
                    "cube"->arrayOf(VulkanNative.runGpuCubeBenchmark(driver,this,null))
                    "transfer"->arrayOf(VulkanNative.runGpuTransferTest(driver,this,false))
                    else->VulkanNative.queryCapabilities(driver,this)
                }
                require(rows.sumOf{it.length.toLong()}<=250_000){"Driver report exceeds IPC limit"}
                data.putStringArray("rows",rows)
            }catch(e:Throwable){data.putStringArray("rows",arrayOf("error\tVulkan\t"+(e.message?:"Driver probe failed")))}
            runCatching{reply.send(Message.obtain(null,1).apply{this.data=data})}
            main.postDelayed({end()},200)
        }.start()
        true
    })
    override fun onBind(intent:Intent):IBinder=messenger.binder
}
internal object VulkanProbeClient{
    private val lock=Mutex()
    suspend fun run(context:Context,driver:VulkanDriver,kind:String):List<String> = lock.withLock{
        withContext(Dispatchers.Main.immediate){
            val connected=CompletableDeferred<IBinder>()
            val output=CompletableDeferred<List<String>>()
            val dead=CompletableDeferred<Unit>()
            val death=IBinder.DeathRecipient{dead.complete(Unit);output.completeExceptionally(IllegalStateException("Vulkan driver process stopped"))}
            val connection=object:ServiceConnection{
                override fun onServiceConnected(name:ComponentName,service:IBinder){connected.complete(service)}
                override fun onServiceDisconnected(name:ComponentName){death.binderDied()}
                override fun onBindingDied(name:ComponentName){death.binderDied();connected.completeExceptionally(IllegalStateException("Driver process died"))}
                override fun onNullBinding(name:ComponentName){connected.completeExceptionally(IllegalStateException("Driver service unavailable"))}
            }
            val receiver=Messenger(Handler(Looper.getMainLooper()){msg->if(msg.what==1){output.complete(msg.data.getStringArray("rows")?.toList().orEmpty());true}else false})
            var binder:IBinder?=null
            var bound=false
            try{
                bound=context.bindService(Intent(context,VulkanProbeService::class.java),connection,Context.BIND_AUTO_CREATE)
                check(bound){"Cannot start Vulkan worker"}
                withTimeout(48_000){
                    val service=connected.await();binder=service
                    service.linkToDeath(death,0)
                    Messenger(service).send(Message.obtain(null,1).apply{replyTo=receiver;data=Bundle().apply{putString("driver",driver.id);putString("kind",kind)}})
                    output.await()
                }
            }finally{
                withContext(NonCancellable){
                    binder?.let{service->
                        if(!dead.isCompleted)runCatching{Messenger(service).send(Message.obtain(null,2))}
                        withTimeoutOrNull(2500){dead.await()}
                        runCatching{service.unlinkToDeath(death,0)}
                    }
                    if(bound)runCatching{context.unbindService(connection)}
                }
            }
        }
    }
}
