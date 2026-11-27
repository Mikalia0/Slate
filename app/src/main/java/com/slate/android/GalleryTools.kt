// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

internal data class GalleryTask(val kind:String,val items:List<MediaItem>,val value:String)
internal fun galleryAlbumPath(name:String,video:Boolean):String{
    val clean=name.trim()
    require(validFileName(clean)&&clean.length<=100&& !clean.startsWith(".")){"Invalid album name"}
    return (if(video)"Movies/"else "Pictures/")+clean+"/"
}
private fun checkGalleryPath(path:String,video:Boolean):String{
    val parts=path.trimEnd('/').split('/')
    require(parts.isNotEmpty()&&parts.all{validFileName(it)&&!it.startsWith(".")})
    require(parts.first() in (if(video)setOf("DCIM","Movies","Pictures","Download")else setOf("DCIM","Pictures","Download"))){"Unsupported media folder"}
    return parts.joinToString("/")+"/"
}
private fun mediaCollection(item:MediaItem,volume:String):Uri =
    if(item.video)MediaStore.Video.Media.getContentUri(volume)else MediaStore.Images.Media.getContentUri(volume)

internal suspend fun executeGalleryTask(context:Context,task:GalleryTask){
    val resolver=context.contentResolver
    require(task.items.isNotEmpty())
    if(task.kind=="rename"){
        val item=task.items.single()
        val name=task.value.trim()
        require(validFileName(name)&&name.length<=240)
        require(name.substringAfterLast('.',"").equals(item.name.substringAfterLast('.',"") ,true)){"Keep the original file extension"}
        check(resolver.update(item.uri,ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,name)},null,null)==1){"Rename failed"}
        return
    }
    require(Build.VERSION.SDK_INT>=29){"Album operations require Android 10 or later"}
    require(task.kind in setOf("copy","move"))
    // Existing folder choices carry a relative path; new album names are resolved by media type.
    val existing=task.value.startsWith("path:")
    for(item in task.items){
        currentCoroutineContext().ensureActive()
        val path=if(existing)checkGalleryPath(task.value.removePrefix("path:"),item.video)else galleryAlbumPath(task.value,item.video)
        if(task.kind=="move"){
            if(item.relative==path)continue
            check(resolver.update(item.uri,ContentValues().apply{put(MediaStore.MediaColumns.RELATIVE_PATH,path)},null,null)==1){"Move failed: "+item.name}
        }else{
            val volume=item.volume.takeIf{it.isNotBlank()&&it!="external"}?:MediaStore.VOLUME_EXTERNAL_PRIMARY
            val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,item.name);put(MediaStore.MediaColumns.MIME_TYPE,item.mime);put(MediaStore.MediaColumns.RELATIVE_PATH,path);put(MediaStore.MediaColumns.IS_PENDING,1)}
            val output=checkNotNull(resolver.insert(mediaCollection(item,volume),values))
            try{
                checkNotNull(resolver.openInputStream(item.uri)).use{input->checkNotNull(resolver.openOutputStream(output,"w")).use{out->
                    val bytes=ByteArray(65536)
                    while(true){currentCoroutineContext().ensureActive();val n=input.read(bytes);if(n<0)break;out.write(bytes,0,n)}
                }}
                check(resolver.update(output,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1)
            }catch(e:Throwable){runCatching{resolver.delete(output,null,null)};throw e}
        }
    }
}

internal fun galleryExif(context:Context,item:MediaItem):List<String>{
    if(item.video)return emptyList()
    return runCatching{
        checkNotNull(context.contentResolver.openFileDescriptor(item.uri,"r")).use{fd->
            val exif=ExifInterface(fd.fileDescriptor)
            listOf("Camera" to ExifInterface.TAG_MODEL,"Manufacturer" to ExifInterface.TAG_MAKE,"Date" to ExifInterface.TAG_DATETIME_ORIGINAL,"ISO" to ExifInterface.TAG_ISO_SPEED_RATINGS,"Exposure" to ExifInterface.TAG_EXPOSURE_TIME,"Aperture" to ExifInterface.TAG_F_NUMBER,"Focal length" to ExifInterface.TAG_FOCAL_LENGTH)
                .mapNotNull{(name,tag)->exif.getAttribute(tag)?.takeIf{it.isNotBlank()}?.let{"$name: $it"}}
        }
    }.getOrDefault(emptyList())
}
internal fun galleryDecode(context:Context,item:MediaItem,maxDimension:Int=6000):Bitmap{
    if(Build.VERSION.SDK_INT>=28)return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver,item.uri)){decoder,info,_->
        val pixels=info.size.width.toLong()*info.size.height
        val sample=maxOf(1,(maxOf(info.size.width,info.size.height)+maxDimension-1)/maxDimension,kotlin.math.ceil(kotlin.math.sqrt(pixels/12000000.0)).toInt())
        if(sample>1)decoder.setTargetSampleSize(sample)
        decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val resolver=context.contentResolver
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    checkNotNull(resolver.openInputStream(item.uri)).use{BitmapFactory.decodeStream(it,null,bounds)}
    require(bounds.outWidth>0&&bounds.outHeight>0)
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxDimension||bounds.outWidth.toLong()*bounds.outHeight/sample/sample>12000000L)sample*=2
    val decoded=checkNotNull(resolver.openInputStream(item.uri)).use{checkNotNull(BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample}))}
    val orientation=runCatching{checkNotNull(resolver.openFileDescriptor(item.uri,"r")).use{ExifInterface(it.fileDescriptor).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}}.getOrDefault(1)
    val matrix=Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}}
    return if(matrix.isIdentity)decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}
}
internal fun galleryEditBitmap(source:Bitmap,rotation:Int,crop:Float,brightness:Float,contrast:Float):Bitmap{
    val factor=crop.coerceIn(.25f,1f)
    val w=(source.width*factor).toInt().coerceAtLeast(1);val h=(source.height*factor).toInt().coerceAtLeast(1)
    val matrix=Matrix().apply{postRotate(rotation.toFloat())}
    val region=Bitmap.createBitmap(source,(source.width-w)/2,(source.height-h)/2,w,h,matrix,true)
    val c=contrast.coerceIn(.5f,1.5f)
    val offset=(1-c)*128+brightness.coerceIn(-1f,1f)*100
    val filter=ColorMatrix(floatArrayOf(c,0f,0f,0f,offset,0f,c,0f,0f,offset,0f,0f,c,0f,offset,0f,0f,0f,1f,0f))
    val result=Bitmap.createBitmap(region.width,region.height,Bitmap.Config.ARGB_8888)
    Canvas(result).drawBitmap(region,0f,0f,Paint(Paint.ANTI_ALIAS_FLAG).apply{colorFilter=ColorMatrixColorFilter(filter)})
    if(region!==source)region.recycle()
    return result
}
internal fun gallerySaveEdit(context:Context,item:MediaItem,rotation:Int,crop:Float,brightness:Float,contrast:Float,destination:Uri?=null){
    val original=galleryDecode(context,item)
    try{
        val edited=galleryEditBitmap(original,rotation,crop,brightness,contrast)
        try{
            val resolver=context.contentResolver
            if(destination!=null){
                checkNotNull(resolver.openOutputStream(destination,"wt")).use{check(edited.compress(Bitmap.CompressFormat.PNG,100,it))}
            }else{
                require(Build.VERSION.SDK_INT>=29)
                val values=ContentValues().apply{put(MediaStore.MediaColumns.DISPLAY_NAME,item.name.substringBeforeLast('.')+"-edited.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/Slate/");put(MediaStore.MediaColumns.IS_PENDING,1)}
                val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values))
                try{checkNotNull(resolver.openOutputStream(uri,"w")).use{check(edited.compress(Bitmap.CompressFormat.PNG,100,it))};check(resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1)}
                catch(e:Throwable){runCatching{resolver.delete(uri,null,null)};throw e}
            }
        }finally{edited.recycle()}
    }finally{original.recycle()}
}
internal suspend fun shareWithoutExif(context:Context,item:MediaItem){
    require(!item.video)
    val file=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        val dir=File(context.cacheDir,"gallery-share").apply{mkdirs()}
        dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>86400000L}?.forEach{it.delete()}
        val out=File.createTempFile("photo-",".png",dir)
        val bitmap=galleryDecode(context,item)
        try{out.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};out}
        catch(e:Throwable){out.delete();throw e}finally{bitmap.recycle()}
    }
    val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Photo",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},null))
}
internal fun galleryHashDistance(a:Long,b:Long)=java.lang.Long.bitCount(a xor b)
internal suspend fun galleryDuplicates(context:Context,items:List<MediaItem>,similar:Boolean):Set<String>{
    val output=linkedSetOf<String>()
    if(!similar){
        val candidates=items.filter{it.bytes>0}.groupBy{it.bytes}.values.filter{it.size>1}
        for(group in candidates){
            val hashes=mutableMapOf<String,MutableList<String>>()
            for(item in group){
                currentCoroutineContext().ensureActive()
                val digest=MessageDigest.getInstance("SHA-256")
                try{
                    checkNotNull(context.contentResolver.openInputStream(item.uri)).use{input->
                        val buf=ByteArray(65536)
                        while(true){currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break;digest.update(buf,0,n)}
                    }
                    val key=digest.digest().joinToString(""){"%02x".format(it)}
                    hashes.getOrPut(key){mutableListOf()}.add(item.uri.toString())
                }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){}
            }
            hashes.values.filter{it.size>1}.forEach{output.addAll(it)}
        }
    }else{
        val candidates=items.filter{!it.video}
        require(candidates.size<=3000){"Similar-image scan supports up to 3000 images at a time"}
        val hashes=mutableListOf<Pair<String,Long>>()
        for(item in candidates){
            currentCoroutineContext().ensureActive()
            val image=runCatching{if(Build.VERSION.SDK_INT>=29)context.contentResolver.loadThumbnail(item.uri,android.util.Size(64,64),null)else galleryDecode(context,item,64)}.getOrNull()?:continue
            val tiny=Bitmap.createScaledBitmap(image,9,8,true)
            fun gray(x:Int,y:Int):Int{val p=tiny.getPixel(x,y);return ((p shr 16 and 255)*299+(p shr 8 and 255)*587+(p and 255)*114)/1000}
            var hash=0L
            for(y in 0..7)for(x in 0..7)if(gray(x,y)>gray(x+1,y))hash=hash or (1L shl (y*8+x))
            if(tiny!==image)tiny.recycle()
            image.recycle()
            hashes.forEach{(key,other)->if(galleryHashDistance(hash,other)<=5){output.add(key);output.add(item.uri.toString())}}
            hashes.add(item.uri.toString() to hash)
        }
    }
    return output
}
