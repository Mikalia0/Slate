// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable internal fun GalleryTaskDialog(task:GalleryTask,media:List<MediaItem>,onClose:()->Unit,onConfirm:(GalleryTask)->Unit){
    var name by remember(task){mutableStateOf(task.value)}
    var path by remember(task){mutableStateOf<String?>(null)}
    val rename=task.kind=="rename"
    val paths=remember(task,media){media.filter{m->m.relative.isNotBlank()&&task.items.all{it.video==m.video&&it.volume==m.volume}}.distinctBy{it.relative}.sortedBy{it.folder.lowercase()}}
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(when(task.kind){"rename"->R.string.gallery_rename;"copy"->R.string.gallery_copy_album;else->R.string.gallery_move_album}))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(!rename){
                Text(stringResource(R.string.gallery_album_new),style=MaterialTheme.typography.titleSmall)
                paths.forEach{album->Row(Modifier.fillMaxWidth().clickable{path=album.relative;name=""},verticalAlignment=Alignment.CenterVertically){RadioButton(path==album.relative,{path=album.relative;name=""});Column(Modifier.weight(1f)){Text(album.folder);Text(album.relative,style=MaterialTheme.typography.labelSmall)}}}
            }
            OutlinedTextField(name,{name=it;path=null},singleLine=true,label={Text(stringResource(if(rename)R.string.connection_sort_name else R.string.gallery_album_new))})
            if(!rename)Text(stringResource(R.string.gallery_album_note),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=path!=null||validFileName(name.trim()),onClick={onConfirm(task.copy(value=path?.let{"path:"+it}?:name.trim()))}){Text(stringResource(android.R.string.ok))}},dismissButton={TextButton(onClick=onClose){Text(tr("Cancel"))}})
}
@Composable internal fun GalleryImageEditor(item:MediaItem,onClose:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var rotation by remember{mutableIntStateOf(0)}
    var crop by remember{mutableFloatStateOf(1f)}
    var brightness by remember{mutableFloatStateOf(0f)}
    var contrast by remember{mutableFloatStateOf(1f)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val source by produceState<Bitmap?>(null,item.uri){value=withContext(Dispatchers.IO){runCatching{galleryDecode(context,item,1024)}.getOrNull()}}
    val preview by produceState<Bitmap?>(null,source,rotation,crop,brightness,contrast){
        val bitmap=source?:return@produceState
        delay(80)
        value=withContext(Dispatchers.Default){galleryEditBitmap(bitmap,rotation,crop,brightness,contrast)}
    }
    fun save(uri:android.net.Uri?){
        busy=true;error=null
        scope.launch{
            try{withContext(Dispatchers.IO){gallerySaveEdit(context,item,rotation,crop,brightness,contrast,uri)};onSaved()}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:context.getString(R.string.file_failed)}
            finally{busy=false}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){if(it!=null)save(it)}
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.gallery_edit),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(enabled=!busy,onClick=onClose){Text(tr("Cancel"))};Button(enabled=!busy&&source!=null,onClick={if(Build.VERSION.SDK_INT>=29)save(null)else export.launch(item.name.substringBeforeLast('.')+"-edited.png")}){Text(stringResource(R.string.gallery_save_copy))}}
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){val image=preview?:source;if(image!=null)Image(image.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)else Text(stringResource(R.string.gallery_loading_preview))}
                Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(enabled=!busy,onClick={rotation=(rotation+90)%360}){Text(stringResource(R.string.gallery_rotate))};TextButton(enabled=!busy,onClick={rotation=0;crop=1f;brightness=0f;contrast=1f}){Text(stringResource(R.string.gallery_reset))}}
                    Text(stringResource(R.string.gallery_crop)+" · "+(crop*100).toInt()+"%");Slider(crop,{crop=it},valueRange=.25f..1f,enabled=!busy)
                    Text(stringResource(R.string.gallery_brightness));Slider(brightness,{brightness=it},valueRange=-1f..1f,enabled=!busy)
                    Text(stringResource(R.string.gallery_contrast));Slider(contrast,{contrast=it},valueRange=.5f..1.5f,enabled=!busy)
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
}
