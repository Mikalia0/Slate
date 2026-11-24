// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
private data class MediaItem(val id:Long,val uri:Uri,val video:Boolean,val name:String,val bucket:String,val folder:String,val date:Long)
private fun mediaPermissions():Array<String> = when{
    Build.VERSION.SDK_INT>=34->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT>=33->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)
    else->arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
private suspend fun readMedia(context:Context):List<MediaItem> = withContext(Dispatchers.IO){
    val output=ArrayList<MediaItem>()
    var queries=0
    for(video in listOf(false,true)){
        val uri=if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try{
            context.contentResolver.query(uri,arrayOf("_id","_display_name","bucket_id","bucket_display_name","date_added"),null,null,"date_added DESC")?.use{cursor->
                queries++
                while(cursor.moveToNext()){
                    currentCoroutineContext().ensureActive()
                    val id=cursor.getLong(0)
                    output.add(MediaItem(id,ContentUris.withAppendedId(uri,id),video,cursor.getString(1).orEmpty(),cursor.getString(2).orEmpty(),cursor.getString(3).orEmpty(),cursor.getLong(4)))
                }
            }
        }catch(_:SecurityException){}
    }
    check(queries>0)
    output.sortedByDescending{it.date}
}
@Suppress("DEPRECATION")
private fun thumbnail(context:Context,item:MediaItem,size:Int):Bitmap?=runCatching{
    if(Build.VERSION.SDK_INT>=29)context.contentResolver.loadThumbnail(item.uri,Size(size,size),null)
    else if(item.video)MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Video.Thumbnails.MINI_KIND,null)
    else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Images.Thumbnails.MINI_KIND,null)
}.getOrNull()
@Composable
private fun MediaTile(item:MediaItem,modifier:Modifier=Modifier,onClick:()->Unit){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,item.uri){value=withContext(Dispatchers.IO){thumbnail(context,item,320)}}
    Box(modifier.aspectRatio(1f).clickable(onClick=onClick),contentAlignment=Alignment.Center){
        val image=bitmap
        if(image!=null)Image(image.asImageBitmap(),item.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surfaceContainer){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Info,null)}}
        if(item.video)Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.inverseSurface){Icon(Icons.Default.PlayArrow,null,Modifier.padding(4.dp).size(18.dp),tint=MaterialTheme.colorScheme.inverseOnSurface)}
    }
}
private fun openMedia(context:Context,item:MediaItem,share:Boolean){
    runCatching{
        val mime=if(item.video)"video/*"else "image/*"
        val intent=if(share)Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,item.uri)}else Intent(Intent.ACTION_VIEW).apply{setDataAndType(item.uri,mime)}
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData=ClipData.newRawUri(item.name,item.uri)
        context.startActivity(Intent.createChooser(intent,null))
    }
}
@Composable
internal fun GalleryScreen(granted:Boolean,request:()->Unit){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var revision by remember{mutableIntStateOf(0)}
    var media by remember{mutableStateOf<List<MediaItem>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var albums by rememberSaveable{mutableStateOf(false)}
    var bucket by rememberSaveable{mutableStateOf<String?>(null)}
    var preview by remember{mutableStateOf<MediaItem?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){revision++}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){revision++}}
    val allowed=granted||mediaPermissions().any{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    LaunchedEffect(granted,revision){
        if(!allowed){media=emptyList();return@LaunchedEffect}
        loading=true;error=false
        try{media=readMedia(context)}
        catch(e:CancellationException){throw e}
        catch(_:Exception){error=true}
        finally{loading=false}
    }
    BackHandler(preview!=null||bucket!=null){if(preview!=null)preview=null else bucket=null}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
            if(bucket!=null)IconButton(onClick={bucket=null}){Icon(Icons.Default.ArrowBack,null)}
            Text(if(bucket!=null)media.firstOrNull{it.bucket==bucket}?.folder.orEmpty()else stringResource(R.string.tool_gallery),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
            IconButton(onClick={revision++}){Icon(Icons.Default.Refresh,null)}
        }
        if(!allowed||error){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(R.string.gallery_access))
                Button(onClick={if(Build.VERSION.SDK_INT<33)request()else launcher.launch(mediaPermissions())}){Text(tr("Grant access"))}
            }
            return@Column
        }
        if(bucket==null)Row(Modifier.padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!albums,onClick={albums=false},label={Text(stringResource(R.string.gallery_photos))})
            FilterChip(selected=albums,onClick={albums=true},label={Text(stringResource(R.string.gallery_folders))})
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(media.isEmpty()&&!loading)Text(stringResource(R.string.gallery_empty),Modifier.padding(16.dp))
        val groups=remember(media){media.groupBy{it.bucket}.values.sortedByDescending{it.first().date}}
        val visible=remember(media,bucket){media.filter{bucket==null||it.bucket==bucket}}
        val days=remember(visible){val format=DateFormat.getDateInstance();visible.groupBy{format.format(Date(it.date*1000))}}
        LazyVerticalGrid(columns=GridCells.Adaptive(if(albums&&bucket==null)152.dp else 105.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(2.dp),contentPadding=PaddingValues(4.dp)){
            if(albums&&bucket==null){
                items(groups,key={it.first().bucket}){group->
                    Column(Modifier.padding(4.dp).clickable{bucket=group.first().bucket}){
                        MediaTile(group.first(),Modifier.fillMaxWidth()){bucket=group.first().bucket}
                        Text(group.first().folder,Modifier.padding(top=5.dp),maxLines=1)
                        Text(group.size.toString(),style=MaterialTheme.typography.labelSmall)
                    }
                }
            }else{
                days.forEach{(day,photos)->
                    item(key="day-$day",span={GridItemSpan(maxLineSpan)}){Text(day,Modifier.padding(10.dp),style=MaterialTheme.typography.titleSmall)}
                    items(photos,key={it.uri.toString()}){item->MediaTile(item,Modifier.fillMaxWidth()){preview=item}}
                }
            }
        }
    }
    preview?.let{item->
        AlertDialog(onDismissRequest={preview=null},title={Text(item.name,maxLines=2)},text={
            val bitmap by produceState<Bitmap?>(null,item.uri){value=withContext(Dispatchers.IO){thumbnail(context,item,1024)}}
            bitmap?.let{Image(it.asImageBitmap(),item.name,Modifier.fillMaxWidth().heightIn(max=400.dp),contentScale=ContentScale.Fit)}
        },confirmButton={TextButton(onClick={openMedia(context,item,false)}){Text(tr("Open"))}},dismissButton={TextButton(onClick={openMedia(context,item,true)}){Text(tr("Share"))}})
    }
}
