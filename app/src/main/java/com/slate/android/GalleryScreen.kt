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
import java.text.DateFormat
import java.util.Date
private data class MediaItem(val id:Long,val uri:Uri,val video:Boolean,val name:String,val bucket:String,val folder:String,val date:Long,val bytes:Long,val duration:Long)
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
            context.contentResolver.query(uri,arrayOf("_id","_display_name","bucket_id","bucket_display_name","date_added","_size")+(if(video)arrayOf("duration")else emptyArray()),null,null,"date_added DESC")?.use{cursor->
                queries++
                while(cursor.moveToNext()){
                    currentCoroutineContext().ensureActive()
                    val id=cursor.getLong(0)
                    output.add(MediaItem(id,ContentUris.withAppendedId(uri,id),video,cursor.getString(1).orEmpty(),cursor.getString(2).orEmpty(),cursor.getString(3).orEmpty(),cursor.getLong(4),cursor.getLong(5).coerceAtLeast(0),if(video)cursor.getLong(6).coerceAtLeast(0)else 0L))
                }
            }
        }catch(_:SecurityException){}
    }
    check(queries>0)
    output.sortedByDescending{it.date}
}
private val thumbnails=object:LruCache<String,Bitmap>(24*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
private fun cachedThumbnail(context:Context,item:MediaItem):Bitmap? {
    val key=item.uri.toString()+":"+item.date+":"+item.bytes
    return thumbnails.get(key)?:thumbnail(context,item,320)?.also{thumbnails.put(key,it)}
}
@Suppress("DEPRECATION")
private fun thumbnail(context:Context,item:MediaItem,size:Int):Bitmap?=runCatching{
    if(Build.VERSION.SDK_INT>=29)context.contentResolver.loadThumbnail(item.uri,Size(size,size),null)
    else if(item.video)MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Video.Thumbnails.MINI_KIND,null)
    else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver,item.id,MediaStore.Images.Thumbnails.MINI_KIND,null)
}.getOrNull()
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item:MediaItem,modifier:Modifier=Modifier,selected:Boolean=false,favorite:Boolean=false,selecting:Boolean=false,onLongClick:(()->Unit)?=null,onClick:()->Unit){
    val context=LocalContext.current
    val bitmap by produceState<Bitmap?>(null,item.uri){value=withContext(Dispatchers.IO){cachedThumbnail(context,item)}}
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
private fun MediaViewer(items:List<MediaItem>,initial:Int,favorites:Set<String>,onFavorite:(String)->Unit,onClose:()->Unit,onDelete:(MediaItem)->Unit){
    if(items.isEmpty())return
    val context=LocalContext.current
    val pager=rememberPagerState(initialPage=initial.coerceIn(items.indices),pageCount={items.size})
    val current=items[pager.currentPage.coerceIn(items.indices)]
    var details by remember{mutableStateOf(false)}
    var chrome by remember{mutableStateOf(true)}
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
                        val bitmap by produceState<Bitmap?>(null,item.uri){
                            value=withContext(Dispatchers.IO){
                                if(Build.VERSION.SDK_INT>=28)runCatching{
                                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver,item.uri)){decoder,info,_->
                                        val max=maxOf(info.size.width,info.size.height)
                                        if(max>2400)decoder.setTargetSampleSize((max+2399)/2400)
                                        decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
                                }.getOrNull()?:thumbnail(context,item,1024)
                                else thumbnail(context,item,1024)
                            }
                        }
                        Box(Modifier.fillMaxSize().transformable(transform).clickable{chrome=!chrome},contentAlignment=Alignment.Center){
                            bitmap?.let{Image(it.asImageBitmap(),item.name,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y},contentScale=ContentScale.Fit)}
                        }
                    }
                }
                if(chrome)Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                    IconButton(onClick={shareMedia(context,listOf(current))}){Icon(Icons.Default.Share,stringResource(R.string.gallery_share),tint=Color.White)}
                    IconButton(onClick={onFavorite(current.uri.toString())}){Icon(if(current.uri.toString() in favorites)Icons.Default.Favorite else Icons.Default.FavoriteBorder,stringResource(R.string.gallery_favorites),tint=Color.White)}
                    IconButton(onClick={onDelete(current)}){Icon(Icons.Default.Delete,tr("Delete"),tint=Color.White)}
                }
            }
        }
        if(details)AlertDialog(onDismissRequest={details=false},title={Text(stringResource(R.string.gallery_details))},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(current.name);Text(current.folder);Text(Formatter.formatFileSize(context,current.bytes))
                Text(DateFormat.getDateTimeInstance().format(Date(current.date*1000)))
                if(current.video)Text(videoDuration(current.duration))
            }
        },confirmButton={TextButton(onClick={details=false}){Text(tr("Close"))}})
    }
}

@Composable
internal fun GalleryScreen(granted:Boolean,request:()->Unit){
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
    var columns by rememberSaveable{mutableIntStateOf(prefs.getInt("columns",3).coerceIn(1,6))}
    val width=LocalConfiguration.current.screenWidthDp
    var albumColumns by rememberSaveable{mutableIntStateOf(prefs.getInt("album_columns",0).coerceIn(0,5))}
    val albumGrid=albums&&bucket==null
    val currentColumns=if(albumGrid)albumColumns.takeIf{it>0}?:((width-28+12)/164).coerceIn(1,5) else columns
    val maxColumns=if(albumGrid)5 else 6
    val photoGridState=rememberLazyGridState()
    val albumGridState=rememberLazyGridState()
    fun setColumns(count:Int){
        val next=count.coerceIn(1,maxColumns)
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
    var preview by remember{mutableStateOf<String?>(null)}
    var deletion by remember{mutableStateOf<List<MediaItem>>(emptyList())}
    var deletionDay by remember{mutableStateOf<String?>(null)}
    var pendingDelete by rememberSaveable{mutableStateOf(arrayListOf<String>())}
    var deleting by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    val onGridPinch by rememberUpdatedState<(Int)->Unit>({delta->
        if(!deleting && preview==null)setColumns(currentColumns+delta)
    })
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){revision++}
    val deleteLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode==Activity.RESULT_OK){selected=selected-pendingDelete.toSet();if(preview in pendingDelete)preview=null;revision++}
        pendingDelete=arrayListOf();deleting=false
    }
    fun favorite(key:String){
        favorites=if(key in favorites)favorites-key else favorites+key
        prefs.edit().putStringSet("favorites",favorites).apply()
    }
    fun toggle(key:String){selected=if(key in selected)selected-key else selected+key}
    LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){revision++}}
    val allowed=granted||mediaPermissions().any{context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    LaunchedEffect(granted,revision){
        if(!allowed){media=emptyList();return@LaunchedEffect}
        loading=true;error=false
        try{media=readMedia(context);selected=selected.intersect(media.map{it.uri.toString()}.toSet())}
        catch(e:CancellationException){throw e}
        catch(_:Exception){error=true}
        finally{loading=false}
    }
    BackHandler(preview!=null||selected.isNotEmpty()||bucket!=null||search){when{preview!=null->preview=null;selected.isNotEmpty()->selected=emptySet();search->{search=false;query=""};else->bucket=null}}
    val visible=remember(media,bucket,filter,favorites,query){media.filter{(bucket==null||it.bucket==bucket)&&(query.isBlank()||it.name.contains(query,true)||it.folder.contains(query,true))&&when(filter){1->!it.video;2->it.video;3->it.uri.toString() in favorites;else->true}}}
    val groups=remember(media,query){media.groupBy{it.bucket}.values.filter{group->query.isBlank()||group.any{it.folder.contains(query,true)||it.name.contains(query,true)}}.sortedByDescending{it.first().date}}
    val days=remember(visible){val format=DateFormat.getDateInstance();visible.groupBy{format.format(Date(it.date*1000))}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            if(bucket!=null||selected.isNotEmpty())IconButton(onClick={if(selected.isNotEmpty())selected=emptySet()else bucket=null}){Icon(Icons.Default.ArrowBack,null)}
            Text(if(selected.isNotEmpty())stringResource(R.string.tool_selected,selected.size)else if(bucket!=null)media.firstOrNull{it.bucket==bucket}?.folder.orEmpty()else stringResource(R.string.tool_gallery),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,maxLines=1)
            if(selected.isNotEmpty()){
                IconButton(onClick={selected=visible.map{it.uri.toString()}.toSet()}){Icon(Icons.Default.Check,stringResource(R.string.cache_select_all))}
                IconButton(onClick={shareMedia(context,media.filter{it.uri.toString() in selected})}){Icon(Icons.Default.Share,null)}
                IconButton(enabled=!deleting,onClick={deletionDay=null;deletion=media.filter{it.uri.toString() in selected}}){Icon(Icons.Default.Delete,null)}
            }else{
                IconButton(onClick={search=!search;if(!search)query=""}){Icon(Icons.Default.Search,tr("Search"))}
                Box{
                    IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text(tr("Refresh"))},onClick={revision++;menu=false})
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
            TabRow(selectedTabIndex=if(albums)1 else 0,containerColor=MaterialTheme.colorScheme.surface){
                Tab(selected=!albums,onClick={albums=false;filter=0},text={Text(stringResource(R.string.gallery_photos))})
                Tab(selected=albums,onClick={albums=true;filter=0},text={Text(stringResource(R.string.gallery_folders))})
            }
        }
        if(search)OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text(tr("Search"))},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Default.Close,null)}})
        if((!albums||bucket!=null)&&selected.isEmpty())Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf(R.string.gallery_all,R.string.gallery_images,R.string.gallery_videos,R.string.gallery_favorites).forEachIndexed{index,id->FilterChip(selected=filter==index,onClick={filter=index},label={Text(stringResource(id))})}
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)}
        if((if(albums&&bucket==null)groups.isEmpty()else visible.isEmpty())&&!loading){
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(Icons.Default.Info,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.gallery_empty),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(if(albums&&bucket==null)groups.isNotEmpty()||loading else visible.isNotEmpty()||loading)LazyVerticalGrid(columns=GridCells.Fixed(currentColumns),state=if(albumGrid)albumGridState else photoGridState,modifier=Modifier.weight(1f).pointerInput(albumGrid){
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
        },horizontalArrangement=Arrangement.spacedBy(if(albums&&bucket==null)12.dp else 3.dp),verticalArrangement=Arrangement.spacedBy(if(albums&&bucket==null)14.dp else 3.dp),contentPadding=PaddingValues(if(albums&&bucket==null)14.dp else 4.dp)){
            if(albums&&bucket==null){
                items(groups,key={it.first().bucket}){group->
                    Column(Modifier.fillMaxWidth().clickable{bucket=group.first().bucket;filter=0;query=""}){
                            MediaTile(group.first(),Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)){bucket=group.first().bucket;filter=0;query=""}
                            Column(Modifier.padding(top=8.dp,bottom=4.dp)){
                                Text(group.first().folder.ifBlank{stringResource(R.string.gallery_folders)},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.file_items,group.size),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Column(Modifier.weight(1f).clickable(enabled=!deleting){toggleDay()}.padding(vertical=8.dp)){
                                Text(day,style=MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.file_items,photos.size),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(selected.isNotEmpty()){
                                TriStateCheckbox(state=groupState,onClick={toggleDay()},enabled=!deleting)
                                if(selectedCount>0)IconButton(enabled=!deleting,onClick={
                                    deletionDay=day
                                    deletion=photos.toList()
                                }){Icon(Icons.Default.Delete,stringResource(R.string.gallery_delete_day),tint=MaterialTheme.colorScheme.error)}
                            }
                        }
                    }
                    items(photos,key={it.uri.toString()}){item->
                        val key=item.uri.toString()
                        MediaTile(item,Modifier.fillMaxWidth(),selected=key in selected,favorite=key in favorites,selecting=selected.isNotEmpty(),onLongClick={toggle(key)}){
                            if(selected.isNotEmpty())toggle(key)else preview=key
                        }
                    }
                }
            }
        }
    }
    preview?.let{key->
        val initial=visible.indexOfFirst{it.uri.toString()==key}
        if(initial>=0)MediaViewer(visible,initial,favorites,::favorite,onClose={preview=null},onDelete={deletionDay=null;deletion=listOf(it)})
    }
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
                    selected=selected-removed;if(preview in removed)preview=null;revision++
                }finally{deleting=false}
            }
        }){Text(tr("Delete"))}
    })
}
