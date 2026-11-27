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
