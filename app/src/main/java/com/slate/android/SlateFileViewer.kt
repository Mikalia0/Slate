// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private enum class SlateViewer { IMAGE, AUDIO, VIDEO, TEXT, PDF, ZIP, APK }
private fun viewerType(file:File):SlateViewer?=when(file.extension.lowercase()){
    "jpg","jpeg","png","webp","bmp","gif","heic","heif","avif"->SlateViewer.IMAGE
    "mp3","wav","flac","ogg","opus","aac","m4a","amr"->SlateViewer.AUDIO
    "mp4","mkv","webm","3gp","mov","m4v"->SlateViewer.VIDEO
    "txt","log","json","xml","ini","cfg","conf","patch","diff","md","csv","html","css","js","kt","java","c","cpp","h","sh","bat","yaml","yml","properties"->SlateViewer.TEXT
    "pdf"->SlateViewer.PDF
    "zip","jar","cbz"->SlateViewer.ZIP
    "apk"->SlateViewer.APK
    else->null
}
internal fun canViewSlateFile(root:File,file:File):Boolean=viewerType(file)!=null&&safeFile(root,file)&&!com.slate.android.storage.protectedAppPath(file.toPath())&&file.isFile&&file.canRead()

@Composable
internal fun SlateFileViewer(root:File,file:File,onClose:()->Unit,onExternal:()->Unit){
    BackHandler(onBack=onClose)
    var menu by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,stringResource(R.string.browser_back))}
            Text(file.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Box{
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,null)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    DropdownMenuItem(text={Text(stringResource(R.string.browser_external))},onClick={menu=false;onExternal()})
                }
            }
        }
        if(!canViewSlateFile(root,file)){ViewerError(onExternal);return@Column}
        key(file.path){when(viewerType(file)){
            SlateViewer.IMAGE->ImageViewer(file,onExternal)
            SlateViewer.AUDIO->AudioViewer(file,onExternal)
            SlateViewer.VIDEO->VideoViewer(file,onExternal)
            SlateViewer.TEXT->TextViewer(file,onExternal)
            SlateViewer.PDF->PdfViewer(file,onExternal)
            SlateViewer.ZIP->SlateArchiveScreen(root,file,onExternal)
            SlateViewer.APK->SlateApkScreen(root,file,onExternal)
            null->ViewerError(onExternal)
        }}
    }
}
@Composable private fun ViewerError(external:()->Unit){
    Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text(stringResource(R.string.browser_preview_error))
        TextButton(onClick=external){Text(stringResource(R.string.browser_external))}
    }
}
@Composable private fun ImageViewer(file:File,external:()->Unit){
    val result by produceState<Result<Bitmap>?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0)
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>3072)sample*=2
            checkNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample}))
        }}
    }
    val bitmap=result?.getOrNull()
    if(bitmap!=null)Image(bitmap.asImageBitmap(),file.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    else if(result==null)LinearProgressIndicator(Modifier.fillMaxWidth()) else ViewerError(external)
}
@Composable private fun TextViewer(file:File,external:()->Unit){
    val result by produceState<Result<Pair<String,Boolean>>?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{
            file.inputStream().buffered().use{input->
                val buffer=ByteArray(256*1024+1)
                var offset=0
                while(offset<buffer.size){val n=input.read(buffer,offset,buffer.size-offset);if(n<=0)break;offset+=n}
                String(buffer,0,minOf(offset,buffer.size-1),Charsets.UTF_8) to (offset==buffer.size)
            }
        }}
    }
    val content=result?.getOrNull()
    if(content!=null)Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)){
        if(content.second)Text(stringResource(R.string.browser_truncated),color=MaterialTheme.colorScheme.primary)
        SelectionContainer{Text(content.first,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
    }else if(result==null)LinearProgressIndicator(Modifier.fillMaxWidth())else ViewerError(external)
}
@Composable private fun AudioViewer(file:File,external:()->Unit){
    var player by remember{mutableStateOf<MediaPlayer?>(null)}
    var ready by remember{mutableStateOf(false)}
    var playing by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var position by remember{mutableIntStateOf(0)}
    var duration by remember{mutableIntStateOf(0)}
    DisposableEffect(file){
        val p=MediaPlayer()
        player=p
        try{
            p.setDataSource(file.path)
            p.setOnPreparedListener{duration=it.duration.coerceAtLeast(0);ready=true}
            p.setOnCompletionListener{playing=false;position=duration}
            p.setOnErrorListener{_,_,_->failed=true;playing=false;true}
            p.prepareAsync()
        }catch(_:Exception){failed=true}
        onDispose{p.release()}
    }
    LaunchedEffect(playing){while(playing){position=runCatching{player?.currentPosition?:0}.getOrDefault(0);delay(300)}}
    if(failed)ViewerError(external)else Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Text(file.name,style=MaterialTheme.typography.titleLarge)
        if(!ready)LinearProgressIndicator(Modifier.fillMaxWidth())else{
            Slider(value=position.coerceIn(0,duration.coerceAtLeast(1)).toFloat(),onValueChange={position=it.toInt();runCatching{player?.seekTo(position)}},valueRange=0f..duration.coerceAtLeast(1).toFloat())
            Text("${position/60000}:${(position/1000%60).toString().padStart(2,'0')} / ${duration/60000}:${(duration/1000%60).toString().padStart(2,'0')}")
            Button(onClick={runCatching{if(playing)player?.pause()else player?.start();playing=!playing}.onFailure{failed=true}}){Text(stringResource(if(playing)R.string.browser_pause else R.string.browser_play))}
        }
    }
}
@Composable private fun VideoViewer(file:File,external:()->Unit){
    var failed by remember{mutableStateOf(false)}
    var video by remember{mutableStateOf<VideoView?>(null)}
    DisposableEffect(file){onDispose{video?.stopPlayback()}}
    if(failed)ViewerError(external)else AndroidView(factory={context->
        VideoView(context).apply{
            video=this
            setMediaController(MediaController(context).also{it.setAnchorView(this)})
            setOnPreparedListener{start()}
            setOnErrorListener{_,_,_->failed=true;true}
            setVideoPath(file.path)
        }
    },modifier=Modifier.fillMaxSize())
}
private data class PdfPreview(val bitmap:Bitmap,val count:Int)
@Composable private fun PdfViewer(file:File,external:()->Unit){
    var page by remember{mutableIntStateOf(0)}
    val result by produceState<Result<PdfPreview>?>(null,file,page){
        value=null
        value=withContext(Dispatchers.IO){runCatching{
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{descriptor->
                PdfRenderer(descriptor).use{renderer->
                    renderer.openPage(page).use{p->
                        val scale=minOf(2f,1600f/maxOf(p.width,p.height).coerceAtLeast(1))
                        val bitmap=Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPreview(bitmap,renderer.pageCount)
                    }
                }
            }
        }}
    }
    val preview=result?.getOrNull()
    if(preview!=null)Column(Modifier.fillMaxSize()){
        Image(preview.bitmap.asImageBitmap(),null,Modifier.weight(1f).fillMaxWidth(),contentScale=ContentScale.Fit)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
            TextButton(enabled=page>0,onClick={page--}){Text("‹")}
            Text("${page+1} / ${preview.count}")
            TextButton(enabled=page+1<preview.count,onClick={page++}){Text("›")}
        }
    }else if(result==null)LinearProgressIndicator(Modifier.fillMaxWidth())else ViewerError(external)
}
