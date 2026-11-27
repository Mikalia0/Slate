// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Color
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

internal data class EditCrop(val left:Float=0f,val top:Float=0f,val right:Float=1f,val bottom:Float=1f){
    val width get()=right-left
    val height get()=bottom-top
    fun move(dx:Float,dy:Float):EditCrop{
        val x=dx.coerceIn(-left,1f-right);val y=dy.coerceIn(-top,1f-bottom)
        return EditCrop(left+x,top+y,right+x,bottom+y)
    }
    fun resize(handle:Int,dx:Float,dy:Float,ratio:Float=0f):EditCrop{
        val x=if(handle==0||handle==2)left else right
        val y=if(handle<=1)top else bottom
        val ax=if(handle==0||handle==2)right else left
        val ay=if(handle<=1)bottom else top
        val sx=if(x<ax)-1 else 1;val sy=if(y<ay)-1 else 1
        var w=((x+dx-ax)*sx).coerceIn(.03f,if(sx<0)ax else 1f-ax)
        var h=((y+dy-ay)*sy).coerceIn(.03f,if(sy<0)ay else 1f-ay)
        if(ratio>0f){
            val maxW=min(if(sx<0)ax else 1f-ax,(if(sy<0)ay else 1f-ay)*ratio)
            val minW=min(maxW,max(.03f,.03f*ratio))
            w=(if(abs(dx)>abs(dy*ratio))w else h*ratio).coerceIn(minW,maxW);h=w/ratio
        }
        val nx=ax+sx*w;val ny=ay+sy*h
        return EditCrop(min(ax,nx),min(ay,ny),max(ax,nx),max(ay,ny))
    }
}
internal fun aspectCrop(imageWidth:Int,imageHeight:Int,ratio:Float):EditCrop{
    if(ratio<=0f||imageWidth<=0||imageHeight<=0)return EditCrop()
    val normalized=ratio*imageHeight/imageWidth
    val w=min(1f,normalized);val h=min(1f,1f/normalized)
    return EditCrop((1-w)/2,(1-h)/2,(1+w)/2,(1+h)/2)
}
internal enum class PhotoFilter(val label:String,val pt:String){
    NONE("Original","Original"),VIVID("Vivid","Vívido"),WARM("Golden","Dourado"),COOL("Glacier","Glacial"),
    FILM("Cinema","Cinema"),FADE("Matte","Fosco"),NOIR("Noir","Noir"),MONO("Mono","Mono"),
    SEPIA("Sepia","Sépia"),TEAL("Teal & orange","Turquesa"),PORTRAIT("Portrait","Retrato"),FOREST("Forest","Floresta"),
    DUSK("Dusk","Crepúsculo"),CHROME("Chrome","Cromo"),VINTAGE("Vintage","Vintage")
}
internal data class PhotoEdits(
    val rotation:Int=0,val flip:Boolean=false,val crop:EditCrop=EditCrop(),
    val filter:PhotoFilter=PhotoFilter.NONE,val strength:Float=1f,
    val exposure:Float=0f,val contrast:Float=0f,val saturation:Float=0f,
    val warmth:Float=0f,val tint:Float=0f,val highlights:Float=0f,val shadows:Float=0f,
    val fade:Float=0f,val vignette:Float=0f
)
private data class Look(val exposure:Float=0f,val contrast:Float=0f,val saturation:Float=0f,val warmth:Float=0f,val tint:Float=0f,val highlights:Float=0f,val shadows:Float=0f,val fade:Float=0f,val vignette:Float=0f,val curve:Float=0f,val split:Float=0f)
private fun look(filter:PhotoFilter)=when(filter){
    PhotoFilter.NONE->Look()
    PhotoFilter.VIVID->Look(.05f,.22f,.42f,curve=.12f)
    PhotoFilter.WARM->Look(.1f,.08f,.15f,.34f,.04f,-.12f,.08f,curve=.08f)
    PhotoFilter.COOL->Look(.02f,.14f,.04f,-.38f,-.06f,-.12f,.12f)
    PhotoFilter.FILM->Look(-.08f,.18f,-.18f,.05f,0f,-.16f,.15f,.12f,.12f,.20f,.45f)
    PhotoFilter.FADE->Look(.02f,-.15f,-.20f,.12f,0f,-.08f,.18f,.35f)
    PhotoFilter.NOIR->Look(-.08f,.45f,-1f,0f,0f,-.15f,-.1f,.03f,.30f,.25f)
    PhotoFilter.MONO->Look(.02f,.12f,-1f,curve=.07f)
    PhotoFilter.SEPIA->Look(.03f,.03f,-.92f,.48f,.12f,.04f,.1f,.18f)
    PhotoFilter.TEAL->Look(-.03f,.22f,.10f,.03f,0f,-.12f,.07f,.05f,.12f,.14f,.85f)
    PhotoFilter.PORTRAIT->Look(.10f,-.08f,-.08f,.14f,.04f,-.16f,.20f,.06f)
    PhotoFilter.FOREST->Look(-.05f,.20f,.23f,-.10f,-.22f,-.20f,.05f,curve=.13f)
    PhotoFilter.DUSK->Look(-.12f,.12f,-.15f,-.20f,.24f,-.10f,.13f,.10f,.18f,.10f)
    PhotoFilter.CHROME->Look(.03f,.32f,.32f,-.05f,.02f,-.18f,.05f,curve=.18f)
    PhotoFilter.VINTAGE->Look(.04f,-.06f,-.38f,.32f,.08f,-.12f,.10f,.28f,.15f,.07f)
}
internal suspend fun renderPhoto(source:Bitmap,edit:PhotoEdits,applyCrop:Boolean=true):Bitmap{
    val ctx=currentCoroutineContext()
    val transform=Matrix().apply{postRotate(edit.rotation.toFloat());if(edit.flip)postScale(-1f,1f)}
    val oriented=if(transform.isIdentity)source else Bitmap.createBitmap(source,0,0,source.width,source.height,transform,true)
    var region:Bitmap?=null
    var result:Bitmap?=null
    try{
        val c=if(applyCrop)edit.crop else EditCrop()
        val x=(c.left*oriented.width).roundToInt().coerceIn(0,oriented.width-1)
        val y=(c.top*oriented.height).roundToInt().coerceIn(0,oriented.height-1)
        val w=(c.right*oriented.width).roundToInt().coerceIn(x+1,oriented.width)-x
        val h=(c.bottom*oriented.height).roundToInt().coerceIn(y+1,oriented.height)-y
        val input=Bitmap.createBitmap(oriented,x,y,w,h);region=input
        val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);result=out
        val l=look(edit.filter);val s=edit.strength.coerceIn(0f,1f)
        val gain=2f.pow((edit.exposure+l.exposure*s).coerceIn(-2f,2f))
        val con=(1f+edit.contrast+l.contrast*s).coerceIn(.2f,2.4f)
        val sat=(1f+edit.saturation+l.saturation*s).coerceIn(0f,2.5f)
        val warm=(edit.warmth+l.warmth*s).coerceIn(-1f,1f)
        val tint=(edit.tint+l.tint*s).coerceIn(-1f,1f)
        val hi=(edit.highlights+l.highlights*s).coerceIn(-1f,1f)
        val sh=(edit.shadows+l.shadows*s).coerceIn(-1f,1f)
        val fade=(edit.fade+l.fade*s).coerceIn(0f,1f)*.23f
        val vignette=(edit.vignette+l.vignette*s).coerceIn(0f,1f)
        val curve=l.curve*s;val split=l.split*s
        val lut=FloatArray(256){i->
            val v=i/255f
            val t=(v*gain-.5f)*con+.5f+sh*(1-v)*(1-v)*.32f+hi*v*v*.32f
            val a=t.coerceIn(0f,1f)
            (a+(a-.5f)*a*(1-a)*curve*4f)*(1-fade)+fade
        }
        val row=IntArray(w)
        fun channel(v:Float)=(v*255).roundToInt().coerceIn(0,255)
        for(j in 0 until h){
            ctx.ensureActive();input.getPixels(row,0,w,0,j,w,1)
            val yy=(j+.5f)/h*2-1
            for(i in 0 until w){
                val pixel=row[i]
                var red=lut[Color.red(pixel)];var green=lut[Color.green(pixel)];var blue=lut[Color.blue(pixel)]
                val lum=red*.2126f+green*.7152f+blue*.0722f
                red=lum+(red-lum)*sat;green=lum+(green-lum)*sat;blue=lum+(blue-lum)*sat
                red+=warm*.10f+tint*.035f;green-=tint*.07f;blue-=warm*.10f-tint*.035f
                val dark=(1-lum).coerceIn(0f,1f);val light=lum.coerceIn(0f,1f)
                red+=split*(light*.13f-dark*.08f);green+=split*dark*.06f;blue+=split*(dark*.10f-light*.08f)
                val xx=(i+.5f)/w*2-1
                val edge=((xx*xx+yy*yy-.20f)/1.8f).coerceIn(0f,1f)
                val shade=1-vignette*edge*edge*.70f
                row[i]=Color.argb(Color.alpha(pixel),channel(red*shade),channel(green*shade),channel(blue*shade))
            }
            out.setPixels(row,0,w,0,j,w,1)
        }
        result=null
        return out
    }finally{
        result?.recycle()
        region?.takeIf{it!==source&&it!==oriented}?.recycle()
        if(oriented!==source)oriented.recycle()
    }
}
