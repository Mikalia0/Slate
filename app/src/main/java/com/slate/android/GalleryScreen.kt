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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.VideoView
import android.widget.MediaController
import android.util.LruCache
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import android.text.format.Formatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.state.ToggleableState
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
import kotlinx.coroutines.sync.withPermit
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import java.text.DateFormat
import java.util.Date
internal data class MediaItem(val id:Long,val uri:Uri,val video:Boolean,val name:String,val bucket:String,val folder:String,val date:Long,val bytes:Long,val duration:Long,val relative:String,val volume:String,val modified:Long,val width:Int,val height:Int,val mime:String)
private fun mediaPermissions():Array<String> = when{
    Build.VERSION.SDK_INT>=34->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT>=33->arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)
    else->arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
private var gallerySnapshot:Pair<String,List<MediaItem>>?=null
private suspend fun readMedia(context:Context,fullAccess:Boolean):List<MediaItem> = withContext(Dispatchers.IO){
    val stamp=if(fullAccess&&Build.VERSION.SDK_INT>=30)runCatching{MediaStore.getExternalVolumeNames(context).sorted().joinToString("|"){volume->volume+":"+MediaStore.getVersion(context,volume)+":"+MediaStore.getGeneration(context,volume)}}.getOrNull()else null
    if(stamp!=null) synchronized(thumbnails){gallerySnapshot?.takeIf{it.first==stamp}?.second}?.let{return@withContext it}
    if(stamp!=null)GalleryCatalogStore.read(context,stamp)?.let{saved->synchronized(thumbnails){gallerySnapshot=stamp to saved};return@withContext saved}
    if(stamp==null){synchronized(thumbnails){gallerySnapshot=null};GalleryCatalogStore.clear(context)}
    val output=ArrayList<MediaItem>()
    var queries=0
    for(video in listOf(false,true)){
        val uri=if(video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try{
            context.contentResolver.query(uri,arrayOf("_id","_display_name","bucket_id","bucket_display_name","date_added","_size","datetaken","date_modified","width","height","mime_type")+(if(video)arrayOf("duration")else emptyArray())+(if(Build.VERSION.SDK_INT>=29)arrayOf("relative_path","volume_name")else emptyArray()),null,null,"date_added DESC")?.use{cursor->
                queries++
                while(cursor.moveToNext()){
                    currentCoroutineContext().ensureActive()
                    val id=cursor.getLong(0)
                    val date=cursor.getLong(6).takeIf{it>0}?.div(1000)?:cursor.getLong(4)
                    val volume=if(Build.VERSION.SDK_INT>=29)cursor.getString(cursor.getColumnIndexOrThrow("volume_name")).orEmpty()else ""
                    val relative=if(Build.VERSION.SDK_INT>=29)cursor.getString(cursor.getColumnIndexOrThrow("relative_path")).orEmpty()else ""
                    val collection=if(Build.VERSION.SDK_INT>=29&&volume.isNotBlank())(if(video)MediaStore.Video.Media.getContentUri(volume)else MediaStore.Images.Media.getContentUri(volume))else uri
                    output.add(MediaItem(id,ContentUris.withAppendedId(collection,id),video,cursor.getString(1).orEmpty(),volume+":"+cursor.getString(2).orEmpty(),cursor.getString(3).orEmpty(),date,cursor.getLong(5).coerceAtLeast(0),if(video)cursor.getLong(11).coerceAtLeast(0)else 0L,relative,volume,cursor.getLong(7),cursor.getInt(8),cursor.getInt(9),cursor.getString(10) ?: (if(video) "video/mp4" else "image/jpeg")))
                }
            }
        }catch(_:SecurityException){}
    }
    check(queries>0)
    val result=output.sortedByDescending{it.date}
    if(stamp!=null&&queries==2){synchronized(thumbnails){gallerySnapshot=stamp to result};GalleryCatalogStore.write(context,stamp,result)}
    result
}
private val thumbnails=object:LruCache<String,Bitmap>(24*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
private val thumbnailSlots=kotlinx.coroutines.sync.Semaphore(3)
private var lastThumbTrim=0L
private fun thumbnailKey(item:MediaItem,size:Int)=item.uri.toString()+":"+item.modified+":"+item.bytes+":"+size
private fun cachedThumbnail(context:Context,item:MediaItem,size:Int):Bitmap? {
    val key=thumbnailKey(item,size)
    thumbnails.get(key)?.let{return it}
    val folder=java.io.File(context.cacheDir,"gallery-thumbs").apply{mkdirs()}
    val hash=com.slate.android.storage.hexDigest(java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()))
    val disk=java.io.File(folder,hash+".jpg")
    val cached=runCatching{android.graphics.BitmapFactory.decodeFile(disk.path)}.getOrNull()
    if(cached!=null){disk.setLastModified(System.currentTimeMillis());thumbnails.put(key,cached);return cached}
    val image=thumbnail(context,item,size)?:return null
    thumbnails.put(key,image)
    runCatching{
        val temp=java.io.File.createTempFile("thumb-",".tmp",folder)
        try{temp.outputStream().use{image.compress(Bitmap.CompressFormat.JPEG,85,it)};temp.renameTo(disk)}
        finally{temp.delete()}
        synchronized(thumbnails){
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-lastThumbTrim>=60000||lastThumbTrim==0L){
                lastThumbTrim=now
                val files=folder.listFiles()?.filter{it.extension=="jpg"}.orEmpty().sortedBy{it.lastModified()}
                var bytes=files.sumOf{it.length()}
                for(f in files){if(bytes<=64L*1024*1024)break;val n=f.length();if(f.delete())bytes-=n}
            }
        }
    }
    return image
}
@Suppress("DEPRECATION")
private fun thumbnail(context:Context,item:MediaItem,size:Int):Bitmap?=runCatching{
    if(Build.VERSION.SDK_INT>=29)context.contentResolver.loadThumbnail(item.uri,Size(size,size),null)
    else if(item.video)MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Video.Thumbnails.MINI_KIND,null)
    else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Images.Thumbnails.MINI_KIND,null)
}.getOrNull()
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item:MediaItem,modifier:Modifier=Modifier,thumbSize:Int=320,selected:Boolean=false,favorite:Boolean=false,selecting:Boolean=false,onLongClick:(()->Unit)?=null,onClick:()->Unit){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(thumbnails.get(thumbnailKey(item,thumbSize)),item.uri,item.modified,item.bytes,thumbSize){value=thumbnails.get(thumbnailKey(item,thumbSize))?:thumbnailSlots.withPermit{withContext(Dispatchers.IO){cachedThumbnail(context,item,thumbSize)}}}
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(3.dp)).combinedClickable(onClick=onClick,onLongClick=onLongClick),contentAlignment=Alignment.Center){
        val image=bitmap
        if(image!=null)Image(image.asImageBitmap(),item.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surfaceContainer){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Info,null)}}
        if(item.video)Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp),shape=MaterialTheme.shapes.small,color=Color.Black.copy(alpha=.65f)){
            Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PlayArrow,null,Modifier.size(15.dp),tint=Color.White);Text(videoDuration(item.duration),color=Color.White,style=MaterialTheme.typography.labelSmall)}
        }
        if(favorite&&!selecting)Icon(Icons.Default.Favorite,null,Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),tint=Color.White)
        if(selecting||selected){
            if(selected)Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha=.22f)).border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(3.dp)))
            Surface(Modifier.align(Alignment.TopEnd).padding(5.dp),shape=androidx.compose.foundation.shape.CircleShape,color=if(selected)MaterialTheme.colorScheme.primary else Color.Black.copy(alpha=.45f)){
                Box(Modifier.size(24.dp),contentAlignment=Alignment.Center){
                    if(selected)Icon(Icons.Default.Check,null,Modifier.size(17.dp),tint=MaterialTheme.colorScheme.onPrimary)
                    else Box(Modifier.size(19.dp).border(1.5.dp,Color.White,androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
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

private fun videoDuration(ms:Long):String {
    val seconds=ms/1000
    return if(seconds>=3600)"${seconds/3600}:%02d:%02d".format(seconds/60%60,seconds%60) else "${seconds/60}:%02d".format(seconds%60)
}

private fun shareMedia(context:Context,items:List<MediaItem>){
    if(items.isEmpty())return
    runCatching{
        val uris=ArrayList(items.map{it.uri})
        val mime=if(items.all{it.video})"video/*"else if(items.none{it.video})"image/*"else "*/*"
        val intent=Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply{
            type=mime
            if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris.first())else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData=ClipData.newRawUri("Slate",uris.first()).apply{uris.drop(1).forEach{addItem(ClipData.Item(it))}}
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,null))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(items:List<MediaItem>,initial:Int,favorites:Set<String>,onFavorite:(String)->Unit,onClose:()->Unit,onDelete:(MediaItem)->Unit,onEdit:(MediaItem)->Unit,onAction:(String,MediaItem)->Unit){
    if(items.isEmpty())return
    val context=LocalContext.current
    val pager=rememberPagerState(initialPage=initial.coerceIn(items.indices),pageCount={items.size})
    val current=items[pager.currentPage.coerceIn(items.indices)]
    var details by remember{mutableStateOf(false)}
    var chrome by remember{mutableStateOf(true)}
    var viewerMenu by remember{mutableStateOf(false)}
    var slideshow by remember{mutableStateOf(false)}
    var fullResolution by remember{mutableStateOf(false)}
    val strip=rememberLazyListState(initialFirstVisibleItemIndex=(initial-2).coerceAtLeast(0))
    val pagerScope=rememberCoroutineScope()
    LaunchedEffect(pager.currentPage){strip.animateScrollToItem((pager.currentPage-2).coerceAtLeast(0))}
    LaunchedEffect(slideshow,pager.currentPage){if(slideshow){delay(3500);pager.animateScrollToPage((pager.currentPage+1)%items.size)}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=true)){
        Surface(Modifier.fillMaxSize(),color=Color.Black){
            Column(Modifier.fillMaxSize()){
                if(chrome)Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}
                    Column(Modifier.weight(1f)){
                        Text(current.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${pager.currentPage+1} / ${items.size}",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick={details=true}){Icon(Icons.Default.Info,null,tint=Color.White)}
                    Box{
                        IconButton(onClick={viewerMenu=true}){Icon(Icons.Default.MoreVert,null,tint=Color.White)}
                        DropdownMenu(viewerMenu,{viewerMenu=false}){
                            if(!current.video){
                                DropdownMenuItem(text={Text(stringResource(R.string.gallery_full_resolution))},onClick={fullResolution=!fullResolution;viewerMenu=false},trailingIcon={if(fullResolution)Icon(Icons.Default.Check,null)})
                                DropdownMenuItem(text={Text(stringResource(R.string.gallery_wallpaper))},onClick={viewerMenu=false;onAction("wallpaper",current)})
                                DropdownMenuItem(text={Text(stringResource(R.string.gallery_private_share))},onClick={viewerMenu=false;onAction("private",current)})
                            }
                            DropdownMenuItem(text={Text(stringResource(R.string.gallery_slideshow))},onClick={slideshow=!slideshow;viewerMenu=false},trailingIcon={if(slideshow)Icon(Icons.Default.Check,null)})
                            DropdownMenuItem(text={Text(stringResource(R.string.gallery_rename))},onClick={viewerMenu=false;onAction("rename",current)})
                            DropdownMenuItem(text={Text(stringResource(R.string.gallery_open_folder))},onClick={viewerMenu=false;onAction("folder",current)})
                            DropdownMenuItem(text={Text(stringResource(R.string.browser_external))},onClick={viewerMenu=false;openMedia(context,current,false)})
                        }
                    }
                }
                HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxWidth(),userScrollEnabled=true){index->
                    val item=items[index]
                    if(item.video){
                        if(index==pager.settledPage){
                            var player by remember(item.uri){mutableStateOf<VideoView?>(null)}
                            AndroidView(factory={ctx->VideoView(ctx).apply{
                                val controller=MediaController(ctx);controller.setAnchorView(this);setMediaController(controller)
                                setVideoURI(item.uri);setOnPreparedListener{start()};player=this
                            }},modifier=Modifier.fillMaxSize())
                            DisposableEffect(item.uri){onDispose{player?.stopPlayback()}}
                        }else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.PlayArrow,null,tint=Color.White)}
                    }else{
                        var scale by remember(item.uri){mutableFloatStateOf(1f)}
                        var x by remember(item.uri){mutableFloatStateOf(0f)}
                        var y by remember(item.uri){mutableFloatStateOf(0f)}
                        val transform=rememberTransformableState{zoom,pan,_->
                            scale=(scale*zoom).coerceIn(1f,4f)
                            if(scale>1f){x+=pan.x;y+=pan.y}else{x=0f;y=0f}
                        }
                        val bitmap by produceState<Bitmap?>(null,item.uri,fullResolution){
                            value=withContext(Dispatchers.IO){
                                if(Build.VERSION.SDK_INT>=28)runCatching{
                                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver,item.uri)){decoder,info,_->
                                        val max=maxOf(info.size.width,info.size.height)
                                        val limit=if(fullResolution)6000 else 2400
                                        val pixels=info.size.width.toLong()*info.size.height
                                        val sample=maxOf(1,(max+limit-1)/limit,kotlin.math.ceil(kotlin.math.sqrt(pixels/24000000.0)).toInt())
                                        if(sample>1)decoder.setTargetSampleSize(sample)
                                        decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
                                }.getOrNull()?:thumbnail(context,item,1024)
                                else thumbnail(context,item,1024)
                            }
                        }
                        Box(Modifier.fillMaxSize().transformable(transform).pointerInput(item.uri){detectTapGestures(onTap={chrome=!chrome},onDoubleTap={scale=if(scale>1f)1f else 2.5f;x=0f;y=0f})},contentAlignment=Alignment.Center){
                            bitmap?.let{Image(it.asImageBitmap(),item.name,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y},contentScale=ContentScale.Fit)}
                        }
                    }
                }
                if(chrome&&items.size>1&&LocalConfiguration.current.screenHeightDp>=420)LazyRow(state=strip,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=8.dp,vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    items(items.size,key={items[it].uri.toString()}){index->
                        MediaTile(items[index],Modifier.size(48.dp).then(if(index==pager.currentPage)Modifier.border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(3.dp))else Modifier),thumbSize=96){pagerScope.launch{pager.animateScrollToPage(index)}}
                    }
                }
                if(chrome)Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                    IconButton(onClick={shareMedia(context,listOf(current))}){Icon(Icons.Default.Share,stringResource(R.string.gallery_share),tint=Color.White)}
                    IconButton(onClick={onFavorite(current.uri.toString())}){Icon(if(current.uri.toString() in favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,stringResource(R.string.gallery_favorites),tint=Color.White)}
                    if(!current.video)IconButton(onClick={onEdit(current)}){Icon(Icons.Default.Edit,stringResource(R.string.gallery_edit),tint=Color.White)}
                    IconButton(onClick={onDelete(current)}){Icon(Icons.Default.Delete,tr("Delete"),tint=Color.White)}
                }
            }
        }
        if(details){
        val exif by produceState<List<String>>(emptyList(),current.uri){value=withContext(Dispatchers.IO){galleryExif(context,current)}}
        AlertDialog(onDismissRequest={details=false},title={Text(stringResource(R.string.gallery_details))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(current.name);Text(current.folder);Text(Formatter.formatFileSize(context,current.bytes))
                Text(DateFormat.getDateTimeInstance().format(Date(current.date*1000)))
                Text("${current.width} × ${current.height}")
                if(current.video)Text(videoDuration(current.duration))
                exif.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
            }
        },confirmButton={TextButton(onClick={details=false}){Text(tr("Close"))}})}
    }
}

@Composable
internal fun GalleryScreen(granted:Boolean,request:()->Unit,onOpenFolder:(MediaItem)->Unit={},onChanged:()->Unit={},onSelectionChanged:(Boolean)->Unit={}){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    val prefs=remember{context.getSharedPreferences("gallery",0)}
    var favorites by remember{mutableStateOf(prefs.getStringSet("favorites",emptySet()).orEmpty().toSet())}
    var revision by remember{mutableIntStateOf(0)}
    var media by remember{mutableStateOf<List<MediaItem>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    var albums by rememberSaveable{mutableStateOf(false)}
    var bucket by rememberSaveable{mutableStateOf<String?>(null)}
    var filter by rememberSaveable{mutableIntStateOf(0)}
    var sort by rememberSaveable{mutableIntStateOf(0)}
    var filterMenu by remember{mutableStateOf(false)}
    var hiddenAlbums by remember{mutableStateOf(prefs.getStringSet("hidden_albums",emptySet()).orEmpty().toSet())}
    var showHidden by rememberSaveable{mutableStateOf(false)}
    var duplicateKeys by remember{mutableStateOf<Set<String>?>(null)}
    var scanning by remember{mutableStateOf(false)}
    var editItem by remember{mutableStateOf<MediaItem?>(null)}
    var task by remember{mutableStateOf<GalleryTask?>(null)}
    var operationBusy by remember{mutableStateOf(false)}
    var albumAction by remember{mutableStateOf<String?>(null)}
    var columns by rememberSaveable{mutableIntStateOf(prefs.getInt("columns",3).coerceIn(1,6))}
    val width=(LocalConfiguration.current.screenWidthDp).coerceAtLeast(320)
    val densityFactor=(width/420f).coerceIn(1f,3.5f)
    var albumColumns by rememberSaveable{mutableIntStateOf(prefs.getInt("album_columns",0).coerceIn(0,5))}
    val albumGrid=albums&&bucket==null
    val maxColumns=if(albumGrid)(5*densityFactor).toInt().coerceAtMost(12)else (6*densityFactor).toInt().coerceAtMost(18)
    var widePhotoColumns by rememberSaveable{mutableIntStateOf(prefs.getInt("wide_photo_columns",0))}
    var wideAlbumColumns by rememberSaveable{mutableIntStateOf(prefs.getInt("wide_album_columns",0))}
    val wide=width>=600
    val compact=useTopNavigation()
    val currentColumns=(if(albumGrid){if(wide&&wideAlbumColumns>0)wideAlbumColumns else albumColumns.takeIf{it>0}?.let{(it*densityFactor).toInt()}?:((width-16)/164)}else if(wide&&widePhotoColumns>0)widePhotoColumns else (columns*densityFactor).toInt()).coerceIn(1,maxColumns)
    val photoGridState=rememberLazyGridState()
    val albumGridState=rememberLazyGridState()
    fun setColumns(count:Int){
        if(wide){val next=count.coerceIn(1,maxColumns);if(albumGrid){wideAlbumColumns=next;prefs.edit().putInt("wide_album_columns",next).apply()}else{widePhotoColumns=next;prefs.edit().putInt("wide_photo_columns",next).apply()};return}
        val next=count.coerceIn(1,if(albumGrid)5 else 6)
        if(albumGrid){
            albumColumns=next;prefs.edit().putInt("album_columns",next).apply()
        }else{
            columns=next;prefs.edit().putInt("columns",next).apply()
        }
    }

    var menu by remember{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())}
    val selectionCallback by rememberUpdatedState(onSelectionChanged)
    SideEffect{selectionCallback(selected.isNotEmpty())}
    DisposableEffect(Unit){onDispose{selectionCallback(false)}}
    var preview by remember{mutableStateOf<String?>(null)}
    var deletion by remember{mutableStateOf<List<MediaItem>>(emptyList())}
    var deletionDay by remember{mutableStateOf<String?>(null)}
    var pendingDelete by rememberSaveable{mutableStateOf(arrayListOf<String>())}
    var deleting by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    val thumbSize=(width*context.resources.displayMetrics.density/currentColumns).toInt().coerceIn(96,640)
    fun report(e:Exception){message=e.message?:context.getString(R.string.file_failed)}
    fun runTask(t:GalleryTask){operationBusy=true;message=null;scope.launch{try{withContext(Dispatchers.IO){executeGalleryTask(context,t)};selected=emptySet();synchronized(thumbnails){gallerySnapshot=null};revision++;onChanged()}catch(e:CancellationException){throw e}catch(e:Exception){report(e)}finally{operationBusy=false}}}
    var pendingWrite by remember{mutableStateOf<GalleryTask?>(null)}
    val writeLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->val t=pendingWrite;pendingWrite=null;if(result.resultCode==Activity.RESULT_OK&&t!=null)runTask(t)}
    fun submit(t:GalleryTask){task=null;if(t.kind in setOf("move","rename")&&Build.VERSION.SDK_INT>=30){runCatching{pendingWrite=t;writeLauncher.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(context.contentResolver,t.items.map{it.uri}).intentSender).build())}.onFailure{pendingWrite=null;message=it.message}}else runTask(t)}
    fun scanDuplicates(similar:Boolean){scanning=true;duplicateKeys=null;scope.launch{try{duplicateKeys=withContext(Dispatchers.IO){galleryDuplicates(context,media.filter{it.bucket !in hiddenAlbums},similar)};albums=false;bucket=null;filter=0;message=null}catch(e:CancellationException){throw e}catch(e:Exception){report(e)}finally{scanning=false}}}
    val onGridPinch by rememberUpdatedState<(Int)->Unit>({delta->
        if(!deleting && preview==null)setColumns(currentColumns+delta)
    })
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){revision++}
    val deleteLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode==Activity.RESULT_OK){selected=selected-pendingDelete.toSet();if(preview in pendingDelete)preview=null;revision++;onChanged()}
        pendingDelete=arrayListOf();deleting=false
    }
    fun favorite(key:String){
        val item=media.firstOrNull{it.uri.toString()==key}
        val legacy=item?.takeIf{it.volume=="external_primary"}?.let{ContentUris.withAppendedId(if(it.video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,it.id).toString()}
        favorites=if(key in favorites)favorites-key-legacy.orEmpty() else favorites+key
        prefs.edit().putStringSet("favorites",favorites).apply()
    }
    fun toggle(key:String){selected=if(key in selected)selected-key else selected+key}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){revision++}}
    DisposableEffect(context){val handler=android.os.Handler(android.os.Looper.getMainLooper());val refresh=Runnable{revision++};val observer=object:android.database.ContentObserver(handler){override fun onChange(selfChange:Boolean){handler.removeCallbacks(refresh);handler.postDelayed(refresh,350)}};context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer);context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,true,observer);onDispose{context.contentResolver.unregisterContentObserver(observer);handler.removeCallbacks(refresh)}}
    val allowed=granted||mediaPermissions().any{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    val fullAccess=granted||(Build.VERSION.SDK_INT>=33&&listOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO).all{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED})
    LaunchedEffect(granted,revision,allowed,fullAccess){
        if(!allowed){media=emptyList();synchronized(thumbnails){gallerySnapshot=null};withContext(Dispatchers.IO){GalleryCatalogStore.clear(context)};return@LaunchedEffect}
        if(!fullAccess)media=emptyList()
        loading=true;error=false
        try{media=readMedia(context,fullAccess);selected=selected.intersect(media.map{it.uri.toString()}.toSet());val migrated=media.filter{item->item.volume=="external_primary"&&ContentUris.withAppendedId(if(item.video)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,item.id).toString() in favorites}.map{it.uri.toString()};if(migrated.any{it !in favorites}){favorites=favorites+migrated;prefs.edit().putStringSet("favorites",favorites).apply()}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){error=true}
        finally{loading=false}
    }
    BackHandler(preview!=null||selected.isNotEmpty()||bucket!=null||search||duplicateKeys!=null){when{preview!=null->preview=null;selected.isNotEmpty()->selected=emptySet();search->{search=false;query=""};duplicateKeys!=null->duplicateKeys=null;else->bucket=null}}
    val visible by key(allowed,fullAccess,bucket,hiddenAlbums,showHidden){produceState<List<MediaItem>>(emptyList(),media,bucket,filter,favorites,query,sort,hiddenAlbums,showHidden,duplicateKeys){
        val sourceMedia=media
        value=withContext(Dispatchers.Default){val source=sourceMedia.filter{(showHidden||it.bucket !in hiddenAlbums)&&(bucket==null||it.bucket==bucket)&&(duplicateKeys==null||it.uri.toString() in duplicateKeys.orEmpty())&&(query.isBlank()||it.name.contains(query,true)||it.folder.contains(query,true))&&when(filter){1->!it.video;2->it.video;3->it.uri.toString() in favorites;4->!it.video&&it.folder.contains("screenshot",true);5->it.video&&(it.folder.contains("screen",true)||it.name.contains("screenrecord",true));else->true}};when(sort){1->source.sortedBy{it.date};2->source.sortedByDescending{it.bytes};3->source.sortedBy{it.name.lowercase()};else->source}}
    }}
    val groups by key(allowed,fullAccess,hiddenAlbums,showHidden){produceState<List<List<MediaItem>>>(emptyList(),media,query,hiddenAlbums,showHidden,sort){value=withContext(Dispatchers.Default){val groups=media.filter{showHidden||it.bucket !in hiddenAlbums}.groupBy{it.bucket}.values.filter{group->query.isBlank()||group.any{it.folder.contains(query,true)||it.name.contains(query,true)}};when(sort){1->groups.sortedBy{it.first().date};2->groups.sortedByDescending{it.sumOf{m->m.bytes}};3->groups.sortedBy{it.first().folder.lowercase()};else->groups.sortedByDescending{it.first().date}}}}}
    val allLabel=stringResource(R.string.gallery_all)
    val todayLabel=stringResource(R.string.gallery_today)
    val yesterdayLabel=stringResource(R.string.gallery_yesterday)
    val days by key(allowed,fullAccess,bucket,hiddenAlbums,showHidden){produceState<Map<String,List<MediaItem>>>(emptyMap(),visible,sort,allLabel,todayLabel,yesterdayLabel){value=withContext(Dispatchers.Default){val format=DateFormat.getDateInstance();val now=java.util.Calendar.getInstance();val today=format.format(now.time);now.add(java.util.Calendar.DAY_OF_YEAR,-1);val yesterday=format.format(now.time);if(sort>=2)mapOf(allLabel to visible)else visible.groupBy{val day=format.format(Date(it.date*1000));when(day){today->todayLabel;yesterday->yesterdayLabel;else->day}}}}}
    val scrollAnchors=remember(albumGrid,groups,days,currentColumns){
        galleryRowAnchors(if(albumGrid)listOf(groups.size)else days.values.map{it.size},currentColumns,!albumGrid)
    }
    val gridState=if(albumGrid)albumGridState else photoGridState
    val dragSelect by rememberUpdatedState<(String)->Unit>({key->selected=selected+key})
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            if(bucket!=null||selected.isNotEmpty()||duplicateKeys!=null)IconButton(onClick={if(selected.isNotEmpty())selected=emptySet()else if(duplicateKeys!=null)duplicateKeys=null else bucket=null}){Icon(Icons.Default.ArrowBack,null)}
            Text(if(selected.isNotEmpty())androidx.compose.ui.res.pluralStringResource(R.plurals.gallery_selected_count,selected.size,selected.size)else if(bucket!=null)media.firstOrNull{it.bucket==bucket}?.folder.orEmpty()else stringResource(R.string.tool_gallery),Modifier.weight(1f),style=if(compact)MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(selected.isNotEmpty()){
                IconButton(onClick={val all=visible.map{it.uri.toString()}.toSet();selected=if(selected==all)emptySet()else all}){Icon(Icons.Default.Check,stringResource(R.string.cache_select_all))}
                IconButton(onClick={shareMedia(context,media.filter{it.uri.toString() in selected})}){Icon(Icons.Default.Share,null)}
                IconButton(enabled=!deleting&&!operationBusy,onClick={deletionDay=null;deletion=media.filter{it.uri.toString() in selected}}){Icon(Icons.Default.Delete,null)}
                Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)};DropdownMenu(menu,{menu=false}){val targets=media.filter{it.uri.toString() in selected};DropdownMenuItem(text={Text(stringResource(R.string.gallery_add_favorites))},onClick={favorites=favorites+selected;prefs.edit().putStringSet("favorites",favorites).apply();menu=false;selected=emptySet()});if(targets.size==1)DropdownMenuItem(text={Text(stringResource(R.string.gallery_rename))},onClick={task=GalleryTask("rename",targets,targets.first().name);menu=false});if(Build.VERSION.SDK_INT>=29){DropdownMenuItem(text={Text(stringResource(R.string.gallery_copy_album))},onClick={task=GalleryTask("copy",targets,"");menu=false});DropdownMenuItem(text={Text(stringResource(R.string.gallery_move_album))},onClick={task=GalleryTask("move",targets,"");menu=false})}}}
            }else{
                IconButton(onClick={search=!search;if(!search)query=""}){Icon(Icons.Default.Search,tr("Search"))}
                IconButton(onClick={filterMenu=true}){Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_browser_sort),stringResource(R.string.gallery_filter))}
                Box{
                    IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text(tr("Refresh"))},onClick={revision++;menu=false})
                        DropdownMenuItem(text={Text(stringResource(R.string.gallery_duplicates))},onClick={menu=false;scanDuplicates(false)},enabled=!scanning)
                        DropdownMenuItem(text={Text(stringResource(R.string.gallery_similar))},onClick={menu=false;scanDuplicates(true)},enabled=!scanning)
                        DropdownMenuItem(text={Text(stringResource(R.string.gallery_hidden))},onClick={menu=false;showHidden=!showHidden},trailingIcon={if(showHidden)Icon(Icons.Default.Check,null)})
                        (1..maxColumns).forEach{count->
                            DropdownMenuItem(text={Text(stringResource(R.string.gallery_grid_columns,count))},trailingIcon={if(currentColumns==count)Icon(Icons.Default.Check,null)},onClick={setColumns(count);menu=false})
                        }
                    }
                }
            }
        }
        if(!allowed||error){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(R.string.gallery_access))
                Button(onClick={if(Build.VERSION.SDK_INT<33)request()else launcher.launch(mediaPermissions())}){Text(tr("Grant access"))}
            }
            return@Column
        }
        if(bucket==null&&selected.isEmpty()){
            TabRow(selectedTabIndex=if(albums)1 else if(filter==3)2 else 0,containerColor=MaterialTheme.colorScheme.surface){
                Tab(selected=!albums&&filter!=3,onClick={albums=false;filter=0;duplicateKeys=null},text={Text(stringResource(R.string.gallery_photos))})
                Tab(selected=albums,onClick={albums=true;filter=0;duplicateKeys=null},text={Text(stringResource(R.string.gallery_folders))})
                Tab(selected=!albums&&filter==3,onClick={albums=false;filter=3;duplicateKeys=null},text={Text(stringResource(R.string.gallery_favorites))})
            }
        }
        if(search)OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=8.dp),singleLine=true,placeholder={Text(tr("Search"))})
        if(duplicateKeys!=null)TextButton(onClick={duplicateKeys=null}){Text(stringResource(R.string.gallery_clear_results))}
        DelayedBrowserProgress(loading||scanning||operationBusy)
        message?.let{Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)}
        if((if(albums&&bucket==null)groups.isEmpty()else visible.isEmpty())&&!loading){
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(Icons.Default.Info,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.gallery_empty),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(if(albums&&bucket==null)groups.isNotEmpty()||loading else visible.isNotEmpty()||loading)Box(Modifier.fillMaxWidth().weight(1f)){
        LazyVerticalGrid(columns=GridCells.Fixed(currentColumns),state=gridState,modifier=Modifier.fillMaxSize().pointerInput(albumGrid){
            awaitEachGesture{
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                var pinching=false
                var accumulated=1f
                do{
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val pressed=event.changes.count{it.pressed}
                    if(pressed>=2){
                        pinching=true
                        val zoom=event.calculateZoom()
                        if(zoom.isFinite()&&zoom>0f){
                            accumulated*=zoom
                            val step=galleryPinchStep(accumulated)
                            if(step!=0){onGridPinch(step);accumulated=1f}
                        }
                    }
                    // Once a pinch starts, do not let the remaining finger open a photo,
                    // select an item or fling the grid. Single-finger scrolling is untouched.
                    if(pinching)event.changes.forEach{it.consume()}
                }while(event.changes.any{it.pressed})
            }
        }.then(if(selected.isNotEmpty()&&!albumGrid)Modifier.pointerInput(gridState){
            var position=androidx.compose.ui.geometry.Offset.Zero
            fun selectAt(point:androidx.compose.ui.geometry.Offset){gridState.layoutInfo.visibleItemsInfo.firstOrNull{info->point.x>=info.offset.x&&point.x<info.offset.x+info.size.width&&point.y>=info.offset.y&&point.y<info.offset.y+info.size.height}?.key?.let{key->if(key is String&&key.startsWith("content:"))dragSelect(key)}}
            detectHorizontalDragGestures(onDragStart={position=it;selectAt(it)},onHorizontalDrag={change,_->position=change.position;selectAt(position);change.consume()})
        }else Modifier),horizontalArrangement=Arrangement.spacedBy(if(albums&&bucket==null)12.dp else 3.dp),verticalArrangement=Arrangement.spacedBy(if(albums&&bucket==null)14.dp else 3.dp),contentPadding=PaddingValues(start=if(albumGrid)14.dp else 4.dp,top=if(albumGrid)14.dp else 4.dp,end=if(albumGrid)14.dp else 8.dp,bottom=if(albumGrid)14.dp else 4.dp)){
            if(albums&&bucket==null){
                items(groups,key={it.first().bucket}){group->
                    Column(Modifier.fillMaxWidth().clickable{bucket=group.first().bucket;filter=0;query=""}){
                            MediaTile(group.first(),Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),thumbSize=thumbSize,onLongClick={albumAction=group.first().bucket}){bucket=group.first().bucket;filter=0;query=""}
                            Column(Modifier.padding(top=8.dp,bottom=4.dp)){
                                Text(group.first().folder.ifBlank{stringResource(R.string.gallery_folders)},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.file_items,group.size)+" · "+Formatter.formatFileSize(context,group.sumOf{it.bytes}),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                    }
                }
            }else{
                days.forEach{(day,photos)->
                    item(key="day-$day",span={GridItemSpan(maxLineSpan)}){
                        val keys=remember(photos){photos.map{it.uri.toString()}.toSet()}
                        val selectedCount=keys.count{it in selected}
                        val groupState=when(selectedCount){0->ToggleableState.Off;keys.size->ToggleableState.On;else->ToggleableState.Indeterminate}
                        fun toggleDay(){selected=if(groupState==ToggleableState.On)selected-keys else selected+keys}
                        Row(Modifier.fillMaxWidth().padding(start=10.dp,end=4.dp,top=2.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f).clickable(enabled=!deleting){toggleDay()}.padding(vertical=if(compact)2.dp else 8.dp)){
                                Text(day,style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.file_items,photos.size),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(selected.isNotEmpty()){
                                TriStateCheckbox(state=groupState,onClick={toggleDay()},enabled=!deleting)

                            }
                        }
                    }
                    items(photos,key={it.uri.toString()}){item->
                        val key=item.uri.toString()
                        MediaTile(item,Modifier.fillMaxWidth(),thumbSize=thumbSize,selected=key in selected,favorite=key in favorites,selecting=selected.isNotEmpty(),onLongClick={toggle(key)}){
                            if(selected.isNotEmpty())toggle(key)else preview=key
                        }
                    }
                }
            }
        }
        GalleryFastScrollbar(gridState,scrollAnchors,Modifier.align(Alignment.CenterEnd).fillMaxHeight(),enabled=!deleting)
        }
    }
    preview?.let{key->
        val initial=visible.indexOfFirst{it.uri.toString()==key}
        if(initial>=0&&editItem==null&&task==null)MediaViewer(visible,initial,favorites,::favorite,onClose={preview=null},onDelete={deletionDay=null;deletion=listOf(it)},onEdit={editItem=it},onAction={action,item->when(action){
            "rename"->task=GalleryTask("rename",listOf(item),item.name)
            "folder"->{preview=null;onOpenFolder(item)}
            "wallpaper"->runCatching{val intent=Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(intent,null))}.onFailure{message=it.message}
            "private"->{scope.launch{try{shareWithoutExif(context,item)}catch(e:Exception){report(e)}}}
        }})
    }
    if(filterMenu)AlertDialog(onDismissRequest={filterMenu=false},title={Text(stringResource(R.string.gallery_filter))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(3.dp)){
            listOf(R.string.gallery_all,R.string.gallery_images,R.string.gallery_videos,R.string.gallery_favorites,R.string.gallery_screenshots,R.string.gallery_recordings).forEachIndexed{idx,id->Row(Modifier.fillMaxWidth().clickable{filter=idx;albums=false},verticalAlignment=Alignment.CenterVertically){RadioButton(filter==idx,{filter=idx;albums=false});Text(stringResource(id))}}
            HorizontalDivider()
            listOf(R.string.gallery_newest,R.string.gallery_oldest,R.string.gallery_largest,R.string.gallery_name).forEachIndexed{idx,id->Row(Modifier.fillMaxWidth().clickable{sort=idx},verticalAlignment=Alignment.CenterVertically){RadioButton(sort==idx,{sort=idx});Text(stringResource(id))}}
        }
    },confirmButton={TextButton(onClick={filterMenu=false}){Text(tr("Close"))}})
    albumAction?.let{key->AlertDialog(onDismissRequest={albumAction=null},title={Text(media.firstOrNull{it.bucket==key}?.folder.orEmpty())},text={Text(stringResource(R.string.gallery_hide_desc))},confirmButton={TextButton(onClick={hiddenAlbums=if(key in hiddenAlbums)hiddenAlbums-key else hiddenAlbums+key;prefs.edit().putStringSet("hidden_albums",hiddenAlbums).apply();albumAction=null}){Text(stringResource(if(key in hiddenAlbums)R.string.gallery_unhide else R.string.gallery_hide))}},dismissButton={TextButton(onClick={albumAction=null}){Text(tr("Cancel"))}})}
    task?.let{t->GalleryTaskDialog(t,media,onClose={task=null},onConfirm={submit(it)})}
    editItem?.let{item->GalleryImageEditor(item,onClose={editItem=null},onSaved={editItem=null;synchronized(thumbnails){gallerySnapshot=null};revision++;onChanged()})}
    if(deletion.isNotEmpty())AlertDialog(onDismissRequest={deletion=emptyList();deletionDay=null},title={Text(if(deletionDay!=null)stringResource(R.string.gallery_delete_day)else tr("Delete"))},text={Text(deletionDay?.let{stringResource(R.string.gallery_delete_day_confirm,it,deletion.size)}?:stringResource(R.string.gallery_delete_confirm,deletion.size))},dismissButton={TextButton(onClick={deletion=emptyList();deletionDay=null}){Text(tr("Cancel"))}},confirmButton={
        TextButton(onClick={
            val targets=deletion.distinctBy{it.uri};deletion=emptyList();deletionDay=null
            deleting=true;message=null
            if(Build.VERSION.SDK_INT>=30){
                runCatching{
                    val pending=MediaStore.createDeleteRequest(context.contentResolver,targets.map{it.uri})
                    pendingDelete=ArrayList(targets.map{it.uri.toString()})
                    deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                }.onFailure{pendingDelete=arrayListOf();deleting=false;message=context.getString(R.string.file_failed)}
            }else scope.launch{
                try{
                    val removed=withContext(Dispatchers.IO){targets.mapNotNull{item->if(runCatching{context.contentResolver.delete(item.uri,null,null)>0}.getOrDefault(false))item.uri.toString()else null}.toSet()}
                    if(removed.size!=targets.size)message=context.getString(R.string.file_failed)
                    selected=selected-removed;if(preview in removed)preview=null;revision++;onChanged()
                }finally{deleting=false}
            }
        }){Text(tr("Delete"))}
    })
}
