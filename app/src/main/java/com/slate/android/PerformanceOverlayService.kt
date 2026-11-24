// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.MotionEvent
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import com.slate.android.device.DeviceReader
import com.slate.android.device.reading
import com.slate.android.device.powerReading
import kotlinx.coroutines.*
class PerformanceOverlayService:Service(){
    companion object{@Volatile var running=false;private set}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var view:TextView?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action=="stop"){stopSelf();return START_NOT_STICKY}
        if(running)return START_NOT_STICKY
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("performance",getString(R.string.tool_monitors),NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,1,Intent(this,PerformanceOverlayService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(this,"performance").setSmallIcon(R.drawable.ic_device_cpu).setContentTitle(getString(R.string.monitor_overlay)).setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_device_cpu),getString(R.string.monitor_stop),stop).build()).build()
        startForeground(23,notification)
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        val wm=getSystemService(WindowManager::class.java)
        val density=resources.displayMetrics.density
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=12;y=(100*density).toInt()}
        val text=TextView(this).apply{
            setTextColor(Color.WHITE);textSize=11f;typeface=Typeface.MONOSPACE
            val pad=(8*density).toInt();setPadding(pad,pad,pad,pad)
            background=GradientDrawable().apply{setColor(0xD9222927.toInt());cornerRadius=10*density}
            var sx=0f;var sy=0f;var ox=0;var oy=0
            setOnTouchListener{_,event->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{sx=event.rawX;sy=event.rawY;ox=params.x;oy=params.y;true}
                    MotionEvent.ACTION_MOVE->{params.x=ox+(event.rawX-sx).toInt();params.y=oy+(event.rawY-sy).toInt();runCatching{wm.updateViewLayout(this,params)};true}
                    MotionEvent.ACTION_UP->{performClick();true}
                    else->false
                }
            }
        }
        try{wm.addView(text,params);view=text;running=true}
        catch(_:Exception){stopSelf();return START_NOT_STICKY}
        val reader=DeviceReader(applicationContext)
        scope.launch{
            while(isActive){
                if(!Settings.canDrawOverlays(this@PerformanceOverlayService)){stopSelf();break}
                val live=withContext(Dispatchers.IO){runCatching{reader.live()}.getOrNull()}
                if(live!=null){
                    val clock=live.cores.mapNotNull{it.current}.maxOrNull()?.let{"${it/1000} MHz"}?:"—"
                    val ram=live.meters.firstOrNull{it.title=="Memory"}
                    val gpu=live.gpu.reading("Utilization")
                    val power=live.battery.powerReading()?.value?:"—"
                    text.text="CPU $clock  GPU $gpu\nRAM ${ram?.value?:"—"}  ${live.battery.reading("Temperature")}  $power"
                }
                delay(1000)
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        scope.cancel()
        view?.let{runCatching{getSystemService(WindowManager::class.java).removeView(it)}}
        view=null;running=false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
