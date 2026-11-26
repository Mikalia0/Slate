// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun SlateSecondPane(root:File,folder:File,revision:Int,enabled:Boolean,canPaste:Boolean,onFolder:(File)->Unit,onPaste:()->Unit,modifier:Modifier=Modifier){
    val directories by produceState<List<File>>(emptyList(),folder,revision){
        value=withContext(Dispatchers.IO){
            if(!safeFile(root,folder)||com.slate.android.storage.protectedAppPath(folder.toPath()))emptyList()
            else folder.listFiles().orEmpty().filter{it.isDirectory&&safeFile(root,it)&&!com.slate.android.storage.protectedAppPath(it.toPath())&&!it.name.startsWith(".")}.sortedBy{it.name.lowercase()}
        }
    }
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Column(Modifier.fillMaxSize()){
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={onFolder(folder.parentFile?.takeIf{safeFile(root,it)}?:root)},enabled=enabled&&folder!=root){Icon(Icons.Default.ArrowBack,tr("Back"))}
                Text(folder.name.ifBlank{"/"},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={onFolder(root)},enabled=enabled){Icon(Icons.Default.Home,stringResource(R.string.browser_home))}
                TextButton(onClick=onPaste,enabled=enabled&&canPaste&&safeFile(root,folder)&&!com.slate.android.storage.protectedAppPath(folder.toPath())){Text(stringResource(R.string.file_paste))}
            }
            LazyColumn(Modifier.weight(1f)){
                items(directories,key={it.path}){dir->
                    Row(Modifier.fillMaxWidth().clickable(enabled=enabled){onFolder(dir)}.heightIn(min=40.dp).padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Icon(painterResource(R.drawable.ic_storage_folder),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                        Text(dir.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
