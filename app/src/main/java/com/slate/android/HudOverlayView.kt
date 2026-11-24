// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

internal class HudOverlayView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val font=Typeface.create("sans-serif",Typeface.NORMAL)
    private val bold=Typeface.create("sans-serif",Typeface.BOLD)
    private var mask=HUD_DEFAULT
    private var sample=HudSnapshot()
    private var scale=1f
    private var opacity=.88f
    private var style=HudStyle.PANEL
    private var collapsed=false
    private val density=resources.displayMetrics.density
    private val factor get()=density*scale
    private val full get()=mask!=HUD_MINIMAL
    private val w get()=if(style==HudStyle.CLASSIC&&full)if(collapsed)240f else 196f else if(full)252f else 96f
    private fun heightDp():Float{
        if(style==HudStyle.CLASSIC&&full)return classicHeight()
        var h=16f
        if(mask.shows(HudMetric.FPS))h+=32f
        if(mask.shows(HudMetric.FRAMES))h+=25f
        for(metric in listOf(HudMetric.GPU,HudMetric.CPU,HudMetric.RAM,HudMetric.BATTERY))if(mask.shows(metric))h+=if(metric==HudMetric.RAM)37f else 27f
        if(mask.shows(HudMetric.PACKAGE))h+=24f
        return h
    }
    fun update(newMask:Int,value:HudSnapshot,newScale:Float,newOpacity:Float,newStyle:HudStyle=HudStyle.PANEL){
        val oldHeight=heightDp()
        val geometry=newMask!=mask||scale!=newScale||style!=newStyle
        if(style!=newStyle)collapsed=false
        mask=newMask;sample=value;scale=newScale;opacity=newOpacity;style=newStyle
        if(geometry||oldHeight!=heightDp())requestLayout()
        invalidate()
    }
    fun toggleClassic(){if(style==HudStyle.CLASSIC&&full){collapsed=!collapsed;requestLayout();invalidate()}}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        setMeasuredDimension(resolveSize((w*factor).toInt(),widthMeasureSpec),resolveSize((heightDp()*factor).toInt(),heightMeasureSpec))
    }
    private fun text(canvas:Canvas,value:String,x:Float,y:Float,size:Float,color:Int,strong:Boolean=false,align:Paint.Align=Paint.Align.LEFT){
        paint.style=Paint.Style.FILL;paint.color=color;paint.typeface=if(strong)bold else font;paint.textSize=size;paint.textAlign=align
        canvas.drawText(value,x,y,paint)
    }
    private fun line(canvas:Canvas,y:Float){paint.color=0x303E5863;paint.strokeWidth=.65f;canvas.drawLine(10f,y,w-10f,y,paint)}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(factor,factor)
        val h=heightDp()
        if(style==HudStyle.CLASSIC&&full){drawClassic(canvas,h);canvas.restore();return}
        paint.style=Paint.Style.FILL;paint.color=Color.argb((opacity*255).toInt(),9,13,20)
        canvas.drawRoundRect(.7f,.7f,w-.7f,h-.7f,8f,8f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f;paint.color=0x90334B59.toInt()
        canvas.drawRoundRect(.7f,.7f,w-.7f,h-.7f,8f,8f,paint)
        paint.style=Paint.Style.FILL
        val white=0xFFF4F6FA.toInt();val muted=0xFFABB6C3.toInt()
        var y=8f
        val intervals=sample.intervals
        if(mask.shows(HudMetric.FPS)){
            text(canvas,"FPS",10f,y+19f,11f,muted,true)
            text(canvas,sample.fps?:"—",if(full)52f else 38f,y+25f,25f,white,true)
            if(mask.shows(HudMetric.FRAMES)&&intervals.isNotEmpty())text(canvas,String.format(java.util.Locale.US,"%.1f ms",intervals.average()),w-10f,y+19f,10f,muted,align=Paint.Align.RIGHT)
            y+=32f
        }
        if(mask.shows(HudMetric.FRAMES)){
            if(intervals.size>=2){
                val top=y+1f;val bottom=y+20f
                val maxMs=max(16.7,intervals.maxOrNull()?:16.7)
                val path=Path()
                intervals.forEachIndexed{index,ms->
                    val x=10f+(w-20f)*index/(intervals.size-1)
                    val yy=bottom-((ms/maxMs).coerceIn(0.0,1.0)*(bottom-top)).toFloat()
                    if(index==0)path.moveTo(x,yy)else path.lineTo(x,yy)
                }
                paint.color=0xFF4BB9D4.toInt();paint.strokeWidth=1.15f;paint.style=Paint.Style.STROKE
                canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
            }else text(canvas,"— ms",10f,y+15f,10f,muted)
            if(!mask.shows(HudMetric.FPS)&&intervals.isNotEmpty())text(canvas,String.format(java.util.Locale.US,"%.1f ms",intervals.average()),w-10f,y+12f,10f,muted,align=Paint.Align.RIGHT)
            y+=25f;line(canvas,y)
        }
        for(row in sample.rows){
            val color=when(row.label){"GPU"->0xFFD47BFF.toInt();"CPU"->0xFF63D8F2.toInt();"RAM"->0xFF9EE6A6.toInt();else->0xFFFFB45D.toInt()}
            text(canvas,row.label,10f,y+17f,11f,color,true)
            text(canvas,row.value,58f,y+17f,11f,white,true)
            text(canvas,row.detail,116f,y+17f,10f,muted)
            text(canvas,row.extra,w-10f,y+17f,10f,if(row.extra.endsWith("°C"))0xFF9EE6A6.toInt()else muted,align=Paint.Align.RIGHT)
            if(row.label=="RAM"){
                paint.color=0xFF33414B.toInt();canvas.drawRoundRect(58f,y+24f,w-10f,y+27f,2f,2f,paint)
                row.fraction?.let{paint.color=color;canvas.drawRoundRect(58f,y+24f,58f+(w-68f)*it,y+27f,2f,2f,paint)}
                y+=37f
            }else y+=27f
            line(canvas,y)
        }
        if(mask.shows(HudMetric.PACKAGE)){
            val pkg=sample.pkg?:"—"
            paint.textSize=10f;paint.typeface=font
            val count=paint.breakText(pkg,true,w-20f,null)
            text(canvas,if(count<pkg.length)pkg.take((count-1).coerceAtLeast(0))+"…"else pkg,10f,y+16f,10f,muted)
        }
        canvas.restore()
    }
    private fun row(name:String)=sample.rows.firstOrNull{it.label==name}
    private fun classicLines():List<Pair<String,String>> = buildList{
        if(mask.shows(HudMetric.RAM))add("#RAM" to (row("RAM")?.value?:"—"))
        if(mask.shows(HudMetric.CPU)){
            val groups=sample.cores.groupBy{it.cluster}
            if(groups.isEmpty())add("#CPU" to (row("CPU")?.value?:"—"))
            for((_,cores) in groups){
                val range=if(cores.size==1)cores.first().id.toString()else "${cores.first().id}~${cores.last().id}"
                add("#$range" to (cores.mapNotNull{it.mhz}.maxOrNull()?.let{"${it}MHz"}?:"—"))
                cores.forEach{add("CPU${it.id}" to (it.load?.let{"$it%"}?:"—"))}
            }
        }
        if(mask.shows(HudMetric.FPS))add("#FPS" to (sample.fps?:"—"))
        if(mask.shows(HudMetric.BATTERY))add("#BAT" to (row("BAT")?.detail?.takeIf{it.isNotBlank()}?:"—"))
        if(mask.shows(HudMetric.FRAMES))add("#FT" to (sample.intervals.takeIf{it.isNotEmpty()}?.average()?.let{String.format(java.util.Locale.US,"%.1f ms",it)}?:"—"))
    }
    private fun ringCount()=listOf(HudMetric.CPU,HudMetric.GPU,HudMetric.BATTERY).count{mask.shows(it)}
    private fun classicHeight():Float=if(collapsed)32f else 12f+maxOf(ringCount()*78f,classicLines().size*15f,24f)+(if(mask.shows(HudMetric.PACKAGE))22f else 0f)
    private fun ring(canvas:Canvas,x:Float,y:Float,label:String,percent:String?,caption:String?,color:Int){
        val radius=23f
        paint.style=Paint.Style.STROKE;paint.strokeWidth=6f;paint.strokeCap=Paint.Cap.ROUND
        paint.color=0xFF303335.toInt();canvas.drawCircle(x,y,radius,paint)
        percent?.removeSuffix("%")?.toFloatOrNull()?.takeIf{it in 0f..100f}?.let{
            paint.color=color;canvas.drawArc(x-radius,y-radius,x+radius,y+radius,-90f,it*3.6f,false,paint)
        }
        paint.style=Paint.Style.FILL
        text(canvas,label,x,y+4f,12f,Color.WHITE,true,Paint.Align.CENTER)
        text(canvas,caption?.takeIf{it.isNotBlank()}?:"—",x,y+39f,10f,Color.WHITE,true,Paint.Align.CENTER)
    }
    private fun drawClassic(canvas:Canvas,h:Float){
        paint.style=Paint.Style.FILL;paint.color=Color.argb((opacity*255).toInt(),13,15,17)
        canvas.drawRoundRect(0f,0f,w,h,8f,8f,paint)
        val white=Color.WHITE;val muted=0xFFD0D0D0.toInt()
        if(collapsed){
            val parts=buildList{
                if(mask.shows(HudMetric.CPU))add("CPU: "+(row("CPU")?.value?:"—"))
                if(mask.shows(HudMetric.GPU))add("GPU: "+(row("GPU")?.value?:"—"))
                if(mask.shows(HudMetric.FPS))add("FPS: "+(sample.fps?:"—"))
                if(mask.shows(HudMetric.BATTERY))add(row("BAT")?.detail?.takeIf{it.isNotBlank()}?:"— W")
                if(mask.shows(HudMetric.RAM))add("RAM: "+(row("RAM")?.value?:"—"))
                if(mask.shows(HudMetric.FRAMES))add(sample.intervals.takeIf{it.isNotEmpty()}?.average()?.let{String.format(java.util.Locale.US,"%.1fms",it)}?:"— ms")
            }
            val str=parts.joinToString("   ").ifEmpty{sample.pkg?:"—"}
            paint.typeface=bold;paint.textSize=10.5f
            val fitted=(10.5f*(w-12f)/paint.measureText(str).coerceAtLeast(1f)).coerceAtMost(10.5f)
            text(canvas,str,w/2,20f,fitted,white,true,Paint.Align.CENTER)
            return
        }
        var y=32f
        if(mask.shows(HudMetric.CPU)){
            val cpu=row("CPU");val mhz=sample.cores.mapNotNull{it.mhz}.maxOrNull()?.let{"${it}MHz"}?:cpu?.detail
            ring(canvas,33f,y,"CPU",cpu?.value,mhz,0xFF46D09A.toInt());y+=78f
        }
        if(mask.shows(HudMetric.GPU)){
            val gpu=row("GPU");ring(canvas,33f,y,"GPU",gpu?.value,gpu?.detail,0xFF46D09A.toInt());y+=78f
        }
        if(mask.shows(HudMetric.BATTERY)){
            val bat=row("BAT");ring(canvas,33f,y,bat?.value?:"—",bat?.value,bat?.extra,0xFFA1D531.toInt())
        }
        val left=if(ringCount()>0)76f else 10f
        var lineY=17f
        classicLines().forEach{(label,value)->
            text(canvas,label,left,lineY,12f,if(label.startsWith("#"))white else muted,true)
            text(canvas,value,w-9f,lineY,12f,if(label.startsWith("#"))white else muted,true,Paint.Align.RIGHT)
            lineY+=15f
        }
        if(mask.shows(HudMetric.PACKAGE)){
            val pkg=sample.pkg?:"—"
            paint.textSize=10f;paint.typeface=font
            val n=paint.breakText(pkg,true,w-18f,null)
            text(canvas,if(n<pkg.length)pkg.take((n-1).coerceAtLeast(0))+"…"else pkg,9f,h-8f,10f,muted)
        }
    }

}
