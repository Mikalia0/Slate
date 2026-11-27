// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlin.math.*

@Composable internal fun photoText(en:String,pt:String):String=if(LocalConfiguration.current.locales[0].language=="pt")pt else en
private data class PhotoBounds(val x:Float,val y:Float,val w:Float,val h:Float)
private fun photoBounds(w:Float,h:Float,iw:Int,ih:Int):PhotoBounds{
    val scale=min(w/iw,h/ih)
    val dw=iw*scale;val dh=ih*scale
    return PhotoBounds((w-dw)/2,(h-dh)/2,dw,dh)
}
@Composable private fun CropOverlay(bitmap:Bitmap,crop:EditCrop,ratio:Float,enabled:Boolean,onCrop:(EditCrop)->Unit){
    val latest by rememberUpdatedState(crop)
    val change by rememberUpdatedState(onCrop)
    Canvas(Modifier.fillMaxSize().pointerInput(bitmap.width,bitmap.height,ratio,enabled){
        if(!enabled)return@pointerInput
        var handle=-1
        var working=EditCrop()
        detectDragGestures(onDragStart={pos->
            val b=photoBounds(size.width.toFloat(),size.height.toFloat(),bitmap.width,bitmap.height)
            working=latest
            val corners=listOf(Offset(working.left,working.top),Offset(working.right,working.top),Offset(working.left,working.bottom),Offset(working.right,working.bottom))
            val local=Offset((pos.x-b.x)/b.w,(pos.y-b.y)/b.h)
            val closest=corners.indices.minByOrNull{idx->val d=corners[idx]-local;d.x*d.x*b.w*b.w+d.y*d.y*b.h*b.h}?:0
            val d=corners[closest]-local
            handle=if(hypot(d.x*b.w,d.y*b.h)<=44.dp.toPx())closest else if(local.x in working.left..working.right&&local.y in working.top..working.bottom)4 else -1
        },onDrag={event,delta->
            if(handle>=0){
                event.consume()
                val b=photoBounds(size.width.toFloat(),size.height.toFloat(),bitmap.width,bitmap.height)
                val dx=delta.x/b.w;val dy=delta.y/b.h
                working=if(handle==4)working.move(dx,dy)else working.resize(handle,dx,dy,if(ratio>0)ratio*bitmap.height/bitmap.width else 0f)
                change(working)
            }
        })
    }){
        val b=photoBounds(size.width,size.height,bitmap.width,bitmap.height)
        val l=b.x+crop.left*b.w;val t=b.y+crop.top*b.h;val r=b.x+crop.right*b.w;val bottom=b.y+crop.bottom*b.h
        val shade=Color.Black.copy(alpha=.65f)
        drawRect(shade,Offset(b.x,b.y),Size(b.w,(t-b.y).coerceAtLeast(0f)))
        drawRect(shade,Offset(b.x,bottom),Size(b.w,(b.y+b.h-bottom).coerceAtLeast(0f)))
        drawRect(shade,Offset(b.x,t),Size((l-b.x).coerceAtLeast(0f),bottom-t))
        drawRect(shade,Offset(r,t),Size((b.x+b.w-r).coerceAtLeast(0f),bottom-t))
        drawRect(Color.White,Offset(l,t),Size(r-l,bottom-t),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        for(i in 1..2){
            val xx=l+(r-l)*i/3;val yy=t+(bottom-t)*i/3
            drawLine(Color.White.copy(alpha=.65f),Offset(xx,t),Offset(xx,bottom),1.dp.toPx())
            drawLine(Color.White.copy(alpha=.65f),Offset(l,yy),Offset(r,yy),1.dp.toPx())
        }
        for(x in listOf(l,r))for(y in listOf(t,bottom))drawCircle(Color.White,5.dp.toPx(),Offset(x,y))
    }
}
private enum class Adjustment(val en:String,val pt:String){EXPOSURE("Exposure","Exposição"),CONTRAST("Contrast","Contraste"),SATURATION("Saturation","Saturação"),WARMTH("Warmth","Temperatura"),TINT("Tint","Matiz"),HIGHLIGHTS("Highlights","Realces"),SHADOWS("Shadows","Sombras"),FADE("Fade","Desvanecer"),VIGNETTE("Vignette","Vinheta")}
private fun PhotoEdits.get(a:Adjustment)=when(a){Adjustment.EXPOSURE->exposure;Adjustment.CONTRAST->contrast;Adjustment.SATURATION->saturation;Adjustment.WARMTH->warmth;Adjustment.TINT->tint;Adjustment.HIGHLIGHTS->highlights;Adjustment.SHADOWS->shadows;Adjustment.FADE->fade;Adjustment.VIGNETTE->vignette}
private fun PhotoEdits.set(a:Adjustment,v:Float)=when(a){Adjustment.EXPOSURE->copy(exposure=v);Adjustment.CONTRAST->copy(contrast=v);Adjustment.SATURATION->copy(saturation=v);Adjustment.WARMTH->copy(warmth=v);Adjustment.TINT->copy(tint=v);Adjustment.HIGHLIGHTS->copy(highlights=v);Adjustment.SHADOWS->copy(shadows=v);Adjustment.FADE->copy(fade=v);Adjustment.VIGNETTE->copy(vignette=v)}
@Composable internal fun GalleryImageEditor(item:MediaItem,onClose:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var edit by remember(item.uri){mutableStateOf(PhotoEdits())}
    var tab by remember{mutableIntStateOf(0)}
    var ratio by remember{mutableFloatStateOf(0f)}
    var adjustment by remember{mutableStateOf(Adjustment.EXPOSURE)}
    var busy by remember{mutableStateOf(false)}
    var compare by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var jpeg by remember{mutableStateOf(false)}
    var quality by remember{mutableFloatStateOf(95f)}
    var saveDialog by remember{mutableStateOf(false)}
    val source by produceState<Bitmap?>(null,item.uri){value=withContext(Dispatchers.IO){runCatching{galleryDecode(context,item,1200)}.getOrNull()}}
    val previewEdit=if(compare)PhotoEdits(rotation=edit.rotation,flip=edit.flip,crop=edit.crop)else edit
    val renderEdit=if(tab==0)previewEdit.copy(crop=EditCrop())else previewEdit
    val preview by produceState<Bitmap?>(null,source,renderEdit){
        val b=source?:return@produceState
        delay(45)
        value=withContext(Dispatchers.Default){renderPhoto(b,renderEdit)}
    }
    val filterThumbs by produceState<Map<PhotoFilter,Bitmap>>(emptyMap(),source){
        val b=source?:return@produceState
        val small=withContext(Dispatchers.Default){val scale=min(1f,144f/max(b.width,b.height));Bitmap.createScaledBitmap(b,(b.width*scale).roundToInt().coerceAtLeast(1),(b.height*scale).roundToInt().coerceAtLeast(1),true)}
        try{for(filter in PhotoFilter.entries){val thumbnail=withContext(Dispatchers.Default){renderPhoto(small,PhotoEdits(filter=filter))};value=value+(filter to thumbnail)}}
        finally{if(small!==b)small.recycle()}
    }
    fun save(uri:android.net.Uri?){
        val settings=edit;busy=true;error=null
        scope.launch{
            try{withContext(Dispatchers.IO){savePhotoEdits(context,item,settings,jpeg,quality.toInt(),uri)};onSaved()}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:context.getString(R.string.file_failed)}
            finally{busy=false}
        }
    }
    val pngExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){if(it!=null)save(it)}
    val jpegExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")){if(it!=null)save(it)}
    val changes=edit!=PhotoEdits()
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick=onClose,enabled=!busy){Text(tr("Cancel"))}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={edit=PhotoEdits();ratio=0f;compare=false},enabled=!busy&&changes){Text(stringResource(R.string.gallery_reset))}
                    Button(enabled=!busy&&source!=null&&changes,onClick={saveDialog=true}){Text(stringResource(R.string.gallery_save_copy))}
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black),contentAlignment=Alignment.Center){
                    val image=preview?:source
                    if(image!=null){
                        Image(remember(image){image.asImageBitmap()},null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                        if(tab==0&&preview!=null)CropOverlay(image,edit.crop,ratio,!busy){edit=edit.copy(crop=it)}
                    }else Text(stringResource(R.string.gallery_loading_preview),color=Color.White)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    val b=source
                    if(b!=null){
                        val rotated=edit.rotation%180!=0
                        val w=if(rotated)b.height else b.width;val h=if(rotated)b.width else b.height
                        Text(photoText("Crop","Recorte")+" · "+(edit.crop.width*100).roundToInt()+"% × "+(edit.crop.height*100).roundToInt()+"%",style=MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick={compare=!compare},enabled=!busy){Text(if(compare)photoText("Edited","Editada")else photoText("Original","Original"))}
                }
                TabRow(selectedTabIndex=tab){listOf(photoText("Crop","Cortar"),photoText("Adjust","Ajustar"),photoText("Filters","Filtros")).forEachIndexed{i,label->Tab(selected=tab==i,onClick={tab=i},text={Text(label)})}}
                when(tab){
                    0->Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedButton(enabled=!busy,onClick={edit=edit.copy(rotation=(edit.rotation+90)%360,crop=EditCrop());ratio=0f}){Text(stringResource(R.string.gallery_rotate))}
                            OutlinedButton(enabled=!busy,onClick={edit=edit.copy(flip=!edit.flip,crop=EditCrop());ratio=0f}){Text(photoText("Mirror","Espelhar"))}
                        }
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            val ratios=listOf(0f to "Free",1f to "1:1",4f/3 to "4:3",3f/4 to "3:4",16f/9 to "16:9",9f/16 to "9:16",3f/2 to "3:2",2f/3 to "2:3")
                            items(ratios){(value,label)->FilterChip(selected=ratio==value,onClick={
                                ratio=value
                                source?.let{b->val rotated=edit.rotation%180!=0;edit=edit.copy(crop=aspectCrop(if(rotated)b.height else b.width,if(rotated)b.width else b.height,value))}
                            },label={Text(if(value==0f)photoText("Free","Livre")else label)})}
                        }
                    }
                    1->Column(Modifier.padding(vertical=8.dp)){
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(Adjustment.entries){a->FilterChip(selected=a==adjustment,onClick={adjustment=a},label={Text(photoText(a.en,a.pt))})}}
                        Row(verticalAlignment=Alignment.CenterVertically){Text(photoText(adjustment.en,adjustment.pt),Modifier.weight(1f));Text((edit.get(adjustment)*100).roundToInt().toString());TextButton(onClick={edit=edit.set(adjustment,0f)}){Text("0")}}
                        Slider(edit.get(adjustment),{edit=edit.set(adjustment,it)},valueRange=if(adjustment in listOf(Adjustment.FADE,Adjustment.VIGNETTE))0f..1f else -1f..1f,enabled=!busy)
                    }
                    else->Column(Modifier.padding(vertical=8.dp)){
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            items(PhotoFilter.entries){filter->
                                Column(Modifier.width(82.dp).clickable(enabled=!busy){edit=edit.copy(filter=filter)},horizontalAlignment=Alignment.CenterHorizontally){
                                    Surface(shape=MaterialTheme.shapes.medium,border=if(edit.filter==filter)androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null){
                                        Box(Modifier.size(76.dp),contentAlignment=Alignment.Center){val b=filterThumbs[filter];if(b!=null)Image(remember(b){b.asImageBitmap()},null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
                                    }
                                    Text(photoText(filter.label,filter.pt),style=MaterialTheme.typography.labelSmall,maxLines=1)
                                }
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically){Text(photoText("Intensity","Intensidade"),Modifier.weight(1f));Text((edit.strength*100).roundToInt().toString()+"%")}
                        Slider(edit.strength,{edit=edit.copy(strength=it)},enabled=!busy&&edit.filter!=PhotoFilter.NONE)
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    if(saveDialog)AlertDialog(onDismissRequest={saveDialog=false},title={Text(stringResource(R.string.gallery_save_copy))},text={Column{
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!jpeg,{jpeg=false},label={Text("PNG")});FilterChip(jpeg,{jpeg=true},label={Text("JPEG")})}
        if(jpeg){Text(photoText("Quality","Qualidade")+" "+quality.toInt()+"%");Slider(quality,{quality=it},valueRange=60f..100f)}
        Text(photoText("The original is preserved. Large images may be downsampled to fit memory.","O original é preservado. Imagens grandes podem ter a resolução reduzida para caber na memória."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={saveDialog=false;if(Build.VERSION.SDK_INT>=29)save(null)else if(jpeg)jpegExport.launch(item.name.substringBeforeLast('.')+"-edited.jpg")else pngExport.launch(item.name.substringBeforeLast('.')+"-edited.png")}){Text(photoText("Save","Salvar"))}},dismissButton={TextButton(onClick={saveDialog=false}){Text(tr("Cancel"))}})
}
