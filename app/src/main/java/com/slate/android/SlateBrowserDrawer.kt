// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.slate.android.storage.FileCategory
import com.slate.android.storage.Volume
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun readBrowserHistory(prefs:android.content.SharedPreferences):List<String> = runCatching{
    val array=org.json.JSONArray(prefs.getString("history","[]"))
    (0 until array.length()).map{array.getString(it)}.filter{it.contains('\n')}.take(30)
}.getOrDefault(emptyList())

@Composable
private fun DrawerHeading(text:String){
    Text(text,Modifier.padding(start=20.dp,top=14.dp,bottom=5.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable
private fun DrawerEntry(label:String,icon:Int,description:String?=null,usage:Float?=null,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=50.dp).padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.shapes.medium),contentAlignment=Alignment.Center){
            Icon(painterResource(icon),null,Modifier.size(21.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(label,style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            usage?.let{LinearProgressIndicator(progress={it},modifier=Modifier.fillMaxWidth().height(3.dp))}
            description?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
        }
    }
}
@Composable
internal fun SlateBrowserDrawer(volumes:List<Volume>,bookmarks:List<String>,history:List<String>,sources:List<String>,shizuku:Boolean,onHome:()->Unit,onDashboard:()->Unit,onFolder:(String)->Unit,onCategory:(String)->Unit,onAddStorage:()->Unit,onSource:(String)->Unit,onFavorites:()->Unit,onDocuments:()->Unit,onAddons:()->Unit,onSettings:()->Unit){
    val context=LocalContext.current
    Column(Modifier.fillMaxHeight().widthIn(max=320.dp)){
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            Text(stringResource(R.string.tool_files),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick=onSettings){Icon(Icons.Default.Settings,tr("Settings"))}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=12.dp)){
            item{DrawerEntry(stringResource(R.string.browser_home),R.drawable.ic_storage_folder,onClick=onHome)}
            item{DrawerEntry(stringResource(R.string.browser_dashboard),R.drawable.ic_device_storage,onClick=onDashboard)}
            item{HorizontalDivider();DrawerHeading(tr("Storage"))}
            items(volumes,key={"vol:"+it.root.path}){volume->
                val space by produceState<Pair<Long,Long>?>(null,volume.root){value=withContext(Dispatchers.IO){runCatching{
                    val stat=android.os.StatFs(volume.root.path);(stat.totalBytes-stat.availableBytes).coerceAtLeast(0L) to stat.totalBytes
                }.getOrNull()}}
                DrawerEntry(tr(volume.label),R.drawable.ic_device_storage,space?.let{Formatter.formatFileSize(context,it.first)+" / "+Formatter.formatFileSize(context,it.second)},space?.takeIf{it.second>0}?.let{(it.first.toFloat()/it.second).coerceIn(0f,1f)}){onFolder(volume.root.path+"\n"+volume.root.path)}
            }
            if(shizuku)items(listOf("Android/data","Android/obb")){relative->
                val root=android.os.Environment.getExternalStorageDirectory()
                DrawerEntry(relative,R.drawable.ic_storage_folder){onFolder(root.path+"\n"+File(root,relative).path)}
            }
            items(sources,key={"saf:"+it}){uri->
                val label by produceState<String?>(null,uri){value=withContext(Dispatchers.IO){runCatching{DocumentFile.fromTreeUri(context,Uri.parse(uri))?.name}.getOrNull()}}
                DrawerEntry(label?:stringResource(R.string.browser_document_source),R.drawable.ic_storage_folder){onSource(uri)}
            }
            item{DrawerEntry(stringResource(R.string.browser_add_storage),R.drawable.ic_storage_folder,onClick=onAddStorage)}
            item{HorizontalDivider();DrawerHeading(stringResource(R.string.files_categories))}
            item{DrawerEntry(stringResource(R.string.files_recent),R.drawable.ic_storage_file){onCategory("recent")}}
            item{DrawerEntry(stringResource(R.string.browser_all_files),R.drawable.ic_storage_file){onCategory("all")}}
            items(listOf(FileCategory.INSTALLERS,FileCategory.IMAGES,FileCategory.AUDIO,FileCategory.VIDEOS,FileCategory.DOCUMENTS,FileCategory.ARCHIVES)){category->
                DrawerEntry(categoryTitle(category),categoryIcon(category)){onCategory(category.name)}
            }
            item{DrawerEntry(stringResource(R.string.browser_global_search),R.drawable.ic_storage_file){onCategory("search")}}
            item{
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.weight(1f)){DrawerHeading(stringResource(R.string.browser_favorites))}
                    TextButton(onClick=onFavorites){Text(stringResource(R.string.browser_manage))}
                }
            }
            items(bookmarks,key={"fav:"+it}){key->DrawerEntry(File(key.substringAfter('\n')).name.ifBlank{"/"},R.drawable.ic_storage_folder){onFolder(key)}}
            if(history.isNotEmpty()){
                item{DrawerHeading(stringResource(R.string.browser_history))}
                items(history.take(8),key={"hist:"+it}){key->DrawerEntry(File(key.substringAfter('\n')).name.ifBlank{"/"},R.drawable.ic_storage_folder){onFolder(key)}}
            }
            item{HorizontalDivider()}
            item{DrawerEntry(stringResource(R.string.slate_documents),R.drawable.ic_storage_folder,onClick=onDocuments)}
            item{DrawerEntry(stringResource(R.string.slate_addons),R.drawable.ic_file_archive,onClick=onAddons)}
        }
    }
}
@Composable
internal fun SlateFavoritesDialog(bookmarks:List<String>,onClose:()->Unit,onRemove:(String)->Unit){
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.browser_favorites))},text={
        if(bookmarks.isEmpty())Text(stringResource(R.string.file_empty))
        else LazyColumn(Modifier.heightIn(max=400.dp)){
            items(bookmarks,key={it}){key->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(File(key.substringAfter('\n')).name.ifBlank{"/"})
                        Text(key.substringAfter('\n'),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick={onRemove(key)}){Icon(Icons.Default.Close,tr("Delete"))}
                }
            }
        }
    },confirmButton={TextButton(onClick=onClose){Text(tr("Close"))}})
}
