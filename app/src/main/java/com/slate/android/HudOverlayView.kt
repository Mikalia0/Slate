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
    private val density=resources.displayMetrics.density
    private val factor get()=density*scale
    private val full get()=mask!=HUD_MINIMAL
    private val w get()=if(full)252f else 96f
    private fun heightDp():Float{
        var h=16f
        if(mask.shows(HudMetric.FPS))h+=32f
        if(mask.shows(HudMetric.FRAMES))h+=25f
        for(metric in listOf(HudMetric.GPU,HudMetric.CPU,HudMetric.RAM,HudMetric.BATTERY))if(mask.shows(metric))h+=if(metric==HudMetric.RAM)37f else 27f
        if(mask.shows(HudMetric.PACKAGE))h+=24f
        return h
    }
    fun update(newMask:Int,value:HudSnapshot,newScale:Float,newOpacity:Float){
        val geometry=newMask!=mask||scale!=newScale
        mask=newMask;sample=value;scale=newScale;opacity=newOpacity
        if(geometry)requestLayout()
        invalidate()
    }
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
}
