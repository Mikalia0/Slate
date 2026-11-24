// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slate.android.storage.Entry
import com.slate.android.storage.FileCategory
import com.slate.android.storage.fileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class ApkDetails(val label:String,val packageName:String,val version:String,val code:Long,val installedCode:Long?)
private data class ApkPreview(val info:ApkDetails,val icon:Bitmap?)
private val apkCache=object:LruCache<String,ApkPreview>(64){}
private val filePreviews=object:LruCache<String,Bitmap>(12*1024*1024){
    override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
}
private fun cacheKey(file:File)=file.path+"|"+file.lastModified()+"|"+file.length()
@Suppress("DEPRECATION")
private fun apkPreview(context:Context,file:File):ApkPreview?=runCatching{
    val key=cacheKey(file)
    apkCache.get(key)?.let{return it}
    if(!file.isFile)return null
    val pm=context.packageManager
    val pkg=pm.getPackageArchiveInfo(file.path,0)?:return null
    val app=pkg.applicationInfo?:return null
    app.sourceDir=file.path;app.publicSourceDir=file.path
    val code=if(Build.VERSION.SDK_INT>=28)pkg.longVersionCode else pkg.versionCode.toLong()
    val installed=runCatching{pm.getPackageInfo(pkg.packageName,0)}.getOrNull()
    val installedCode=installed?.let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else it.versionCode.toLong()}
    val icon=runCatching{
        val drawable=app.loadIcon(pm)
        Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also{bitmap->
            drawable.setBounds(0,0,96,96);drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
    ApkPreview(ApkDetails(app.loadLabel(pm).toString(),pkg.packageName,pkg.versionName.orEmpty(),code,installedCode),icon).also{apkCache.put(key,it)}
}.getOrNull()
internal suspend fun readApkDetails(context:Context,file:File):ApkDetails?=withContext(Dispatchers.IO){apkPreview(context,file)?.info}

private fun mediaPreview(context:Context,file:File,category:FileCategory):Bitmap?=runCatching{
    val key=cacheKey(file)
    filePreviews.get(key)?.let{return it}
    val bitmap=when(category){
        FileCategory.INSTALLERS->if(file.extension.equals("apk",true))apkPreview(context,file)?.icon else null
        FileCategory.IMAGES->{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>192)sample*=2
            BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})
        }
        FileCategory.VIDEOS->{
            if(Build.VERSION.SDK_INT>=29)ThumbnailUtils.createVideoThumbnail(file,Size(144,144),null)
            else if(Build.VERSION.SDK_INT>=27){
                val retriever=MediaMetadataRetriever()
                try{retriever.setDataSource(file.path);retriever.getScaledFrameAtTime(-1,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,144,144)}
                finally{retriever.release()}
            }else null
        }
        else->null
    }
    bitmap?.also{filePreviews.put(key,it)}
}.getOrNull()

@Composable
internal fun FilePreview(entry:Entry,modifier:Modifier,tint:Color){
    val category=fileCategory(entry.file.extension)
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,entry.file.path,entry.modified,entry.bytes){
        value=null
        if(!entry.directory && category in setOf(FileCategory.IMAGES,FileCategory.VIDEOS,FileCategory.INSTALLERS)){
            value=withContext(Dispatchers.IO){mediaPreview(context,entry.file,category)}
        }
    }
    val image=bitmap
    if(image!=null)Image(image.asImageBitmap(),null,modifier.clip(RoundedCornerShape(5.dp)),contentScale=if(category==FileCategory.INSTALLERS)ContentScale.Fit else ContentScale.Crop)
    else Icon(painterResource(fileIcon(entry)),null,modifier,tint=tint)
}
