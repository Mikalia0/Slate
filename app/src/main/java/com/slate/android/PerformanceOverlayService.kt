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
import com.slate.android.storage.shizukuGranted
import kotlin.math.roundToInt
import kotlinx.coroutines.*
class PerformanceOverlayService:Service(){
    companion object{
        @Volatile var running=false;private set
        @Volatile var packageName:String?=null;private set
        @Volatile var fps:String?=null;private set
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var view:HudOverlayView?=null
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
        if(!Settings.canDrawOverlays(this)||!shizukuGranted()){stopSelf();return START_NOT_STICKY}
        val wm=getSystemService(WindowManager::class.java)
        val density=resources.displayMetrics.density
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=12;y=(100*density).toInt()}
        val prefs=getSharedPreferences("monitor",MODE_PRIVATE)
        val metrics=HudMetrics(applicationContext)
        val text=HudOverlayView(this).apply{
            update(prefs.getInt("metrics",HUD_DEFAULT).let{if(it==0)HUD_MINIMAL else it},HudSnapshot(),prefs.getFloat("scale",1f).coerceIn(.7f,1.4f),prefs.getFloat("opacity",.88f).coerceIn(.35f,1f),hudStyle(prefs.getString("style",null)))
            var sx=0f;var sy=0f;var ox=0;var oy=0;var dragged=false
            setOnTouchListener{_,event->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{sx=event.rawX;sy=event.rawY;ox=params.x;oy=params.y;dragged=false;true}
                    MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(event.rawX-sx)>6*density||kotlin.math.abs(event.rawY-sy)>6*density)dragged=true;if(dragged){params.x=ox+(event.rawX-sx).toInt();params.y=oy+(event.rawY-sy).toInt();runCatching{wm.updateViewLayout(this,params)}};true}
                    MotionEvent.ACTION_UP->{if(!dragged){toggleClassic();performClick()};true}
                    else->false
                }
            }
        }
        try{wm.addView(text,params);view=text;running=true}
        catch(_:Exception){stopSelf();return START_NOT_STICKY}
        scope.launch{
            var retryMs=500L
            try{
                while(currentCoroutineContext().isActive&&Settings.canDrawOverlays(this@PerformanceOverlayService)){
                    if(!shizukuGranted()){
                        fps=null;PerformanceOverlayService.packageName=null
                        val mask=prefs.getInt("metrics",HUD_DEFAULT).and(HUD_ALL).let{if(it==0)HUD_MINIMAL else it}
                        text.update(mask,HudSnapshot(),prefs.getFloat("scale",1f).coerceIn(.7f,1.4f),prefs.getFloat("opacity",.88f).coerceIn(.35f,1f),hudStyle(prefs.getString("style",null)))
                        delay(1000);continue
                    }
                    try{
                        withFpsService(applicationContext){service->
                    while(currentCoroutineContext().isActive){
                        if(!shizukuGranted()||!Settings.canDrawOverlays(this@PerformanceOverlayService))break
                        val mask=prefs.getInt("metrics",HUD_DEFAULT).and(HUD_ALL).let{if(it==0)HUD_MINIMAL else it}
                        if(!service.asBinder().isBinderAlive)throw android.os.DeadObjectException()
                        val style=hudStyle(prefs.getString("style",null))
                        val sample=service.sample(mask.shows(HudMetric.CPU),mask.shows(HudMetric.FRAMES),style==HudStyle.CLASSIC&&mask.shows(HudMetric.CPU))
                        retryMs=500L
                        val app=sample?.getOrNull(0)?.takeIf{it.isNotBlank()}
                        val rate=sample?.getOrNull(1)?.toDoubleOrNull()?.takeIf{it.isFinite()&&it in 0.0..1000.0}?.roundToInt()?.toString()
                        val intervals=sample?.getOrNull(3)?.split(',')?.mapNotNull{it.toDoubleOrNull()?.takeIf{n->n.isFinite()&&n>0&&n<=1000}}.orEmpty()
                        val rows=runCatching{metrics.read(mask,sample?.getOrNull(2))}.getOrDefault(emptyList())
                        withContext(Dispatchers.Main){
                            PerformanceOverlayService.packageName=app;fps=rate
                            text.update(mask,HudSnapshot(rate,app,intervals,rows,decodeHudCores(sample.getOrNull(4))),prefs.getFloat("scale",1f).coerceIn(.7f,1.4f),prefs.getFloat("opacity",.88f).coerceIn(.35f,1f),hudStyle(prefs.getString("style",null)))
                        }
                        delay(350)
                    }
                        }
                    }catch(e:CancellationException){throw e}
                    catch(_:Exception){
                        fps=null;PerformanceOverlayService.packageName=null
                        val mask=prefs.getInt("metrics",HUD_DEFAULT).and(HUD_ALL).let{if(it==0)HUD_MINIMAL else it}
                        text.update(mask,HudSnapshot(),prefs.getFloat("scale",1f).coerceIn(.7f,1.4f),prefs.getFloat("opacity",.88f).coerceIn(.35f,1f),hudStyle(prefs.getString("style",null)))
                        delay(retryMs);retryMs=(retryMs*2).coerceAtMost(8000L)
                    }
                }
            }finally{stopSelf()}
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        scope.cancel()
        view?.let{runCatching{getSystemService(WindowManager::class.java).removeView(it)}}
        view=null;running=false;PerformanceOverlayService.packageName=null;fps=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
