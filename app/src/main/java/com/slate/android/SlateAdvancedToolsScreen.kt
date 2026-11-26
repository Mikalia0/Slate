// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.text.format.Formatter
import android.media.MediaScannerConnection
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@Composable private fun ToolHeading(title:String,subtitle:String){
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ToolRow(title:String,subtitle:String,icon:Int,enabled:Boolean=true,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium){
                Icon(painterResource(icon),null,Modifier.padding(10.dp).size(22.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)){
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun InfoLine(label:String,value:String){
    Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer{Text(value,style=MaterialTheme.typography.bodyMedium)}
    }
}
@Composable internal fun SlateApkScreen(root:File,file:File,external:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val inspection by produceState<Result<ApkInspection>?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{inspectSlateApk(context,file)}}
    }
    val details=inspection?.getOrNull()
    var tab by remember{mutableIntStateOf(0)}
    var signDialog by remember{mutableStateOf(false)}
    var signing by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var keyUri by remember{mutableStateOf<android.net.Uri?>(null)}
    var password by remember{mutableStateOf("")}
    var alias by remember{mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->keyUri=uri}
    BackHandler(signing){}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FilePreview(com.slate.android.storage.Entry(file,false,file.length(),0,0,file.lastModified()),Modifier.size(52.dp),MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){
                Text(details?.label?:file.name,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(details?.pkg?:Formatter.formatFileSize(context,file.length()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(details!=null)Text(details.version,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick=external,enabled=!signing){Text(stringResource(R.string.apk_install))}
        }
        val tabs=listOf(R.string.apk_overview,R.string.apk_contents,R.string.apk_tools)
        val wide=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=840
        if(!wide)TabRow(selectedTabIndex=tab){tabs.forEachIndexed{index,label->Tab(selected=tab==index,onClick={tab=index},text={Text(stringResource(label),maxLines=1)})}}
        if(signing)LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let{Text(it,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        Row(Modifier.weight(1f).fillMaxWidth()){
        if(wide){
            Column(Modifier.width(210.dp).fillMaxHeight().padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                tabs.forEachIndexed{index,label->NavigationDrawerItem(label={Text(stringResource(label))},selected=tab==index,onClick={tab=index})}
            }
            VerticalDivider(Modifier.fillMaxHeight())
        }
        Column(Modifier.weight(1f).fillMaxHeight()){
        when(tab){
            0->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp)){
                if(inspection==null)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                else if(details==null)item{Text(stringResource(R.string.browser_preview_error))}
                else{
                    item{
                        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.large){
                            Column(Modifier.padding(14.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    Icon(if(details.verified)Icons.Default.CheckCircle else Icons.Default.Info,null,tint=if(details.verified)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    Text(stringResource(if(details.verified)R.string.apk_verified else R.string.apk_unverified),style=MaterialTheme.typography.titleSmall)
                                }
                                InfoLine("SDK", "${details.minSdk} → ${details.targetSdk}")
                                InfoLine(stringResource(R.string.apk_dex),details.dex.size.toString())
                                InfoLine(stringResource(R.string.apk_native),details.libraries.map{it.split('/').getOrElse(1){""}}.distinct().joinToString().ifBlank{"—"})
                            }
                        }
                    }
                    item{ToolHeading(stringResource(R.string.apk_certificates),"SHA-256")}
                    items(details.fingerprints){InfoLine("SHA-256",it)}
                    item{ToolHeading(stringResource(R.string.apk_permissions),details.permissions.size.toString())}
                    items(details.permissions){Text(it,Modifier.padding(vertical=5.dp),style=MaterialTheme.typography.bodySmall)}
                    item{ToolHeading(stringResource(R.string.apk_components),details.components.size.toString())}
                    items(details.components){Text(it,Modifier.padding(vertical=5.dp),style=MaterialTheme.typography.bodySmall)}
                }
            }
            1->Box(Modifier.weight(1f)){SlateArchiveScreen(root,file,external)}
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                item{ToolHeading(stringResource(R.string.apk_tools),stringResource(R.string.apk_tools_description))}
                item{ToolRow(stringResource(R.string.slate_addon_advanced),stringResource(R.string.slate_addon_apk_desc),R.drawable.ic_file_apk,enabled=!signing){openSlateAddon(context,root,file)}}
                item{ToolRow(stringResource(R.string.apk_sign),stringResource(R.string.apk_sign_desc),R.drawable.ic_file_apk,enabled=!signing){signDialog=true}}
                item{ToolRow(stringResource(R.string.apk_certificates),stringResource(R.string.apk_cert_desc),R.drawable.ic_storage_file,enabled=!signing){tab=0}}
                item{ToolRow(stringResource(R.string.apk_contents),stringResource(R.string.apk_contents_desc),R.drawable.ic_file_archive,enabled=!signing){tab=1}}
                item{ToolRow(stringResource(R.string.slate_export_documents),stringResource(R.string.slate_export_desc),R.drawable.ic_storage_folder,enabled=!signing){
                    scope.launch{signing=true;try{withContext(Dispatchers.IO){exportSlateDocument(context,root,file)};status=context.getString(R.string.slate_export_done)}catch(e:CancellationException){throw e}catch(_:Exception){status=context.getString(R.string.slate_tool_failed)}finally{signing=false}}
                }}
            }
        }
        }
        }
    }
    if(signDialog)AlertDialog(onDismissRequest={signDialog=false;password="";keyUri=null;alias=""},title={Text(stringResource(R.string.apk_sign))},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(stringResource(R.string.apk_sign_desc),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={picker.launch(arrayOf("*/*"))}){Text(if(keyUri==null)stringResource(R.string.apk_choose_key)else stringResource(R.string.apk_key_selected))}
            OutlinedTextField(value=password,onValueChange={password=it},label={Text(stringResource(R.string.apk_password))},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            OutlinedTextField(value=alias,onValueChange={alias=it},label={Text(stringResource(R.string.apk_alias))},singleLine=true)
        }
    },dismissButton={TextButton(onClick={signDialog=false;password="";keyUri=null;alias=""}){Text(tr("Cancel"))}},confirmButton={
        TextButton(enabled=keyUri!=null,onClick={
            val uri=checkNotNull(keyUri);val secret=password.toCharArray();val keyAlias=alias
            signDialog=false;password="";keyUri=null;alias=""
            scope.launch{
                signing=true;status=null
                try{
                    val dest=withContext(Dispatchers.IO){signSlateApk(context,root,file,uri,secret,keyAlias)}
                    MediaScannerConnection.scanFile(context,arrayOf(dest.path),null,null)
                    status=context.getString(R.string.slate_created,dest.name)
                }catch(e:CancellationException){throw e}catch(_:Exception){status=context.getString(R.string.slate_tool_failed)}
                finally{secret.fill('\u0000');signing=false}
            }
        }){Text(stringResource(R.string.apk_sign))}
    })
}

private data class ArchiveItem(val name:String,val bytes:Long,val directory:Boolean)
@Composable internal fun SlateArchiveScreen(root:File,file:File,external:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val entries by produceState<Result<List<ArchiveItem>>?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{ZipFile(file).use{zip->zip.entries().asSequence().take(5001).map{ArchiveItem(it.name,it.size,it.isDirectory)}.toList()}}}
    }
    var extracting by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var path by remember{mutableStateOf("")}
    val list=entries?.getOrNull()
    BackHandler(extracting){}
    BackHandler(path.isNotEmpty()&&!extracting){path=path.trimEnd('/').substringBeforeLast('/',"").let{if(it.isEmpty())""else "$it/"}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            if(path.isNotEmpty())IconButton(onClick={path=path.trimEnd('/').substringBeforeLast('/',"").let{if(it.isEmpty())""else "$it/"}}){Icon(Icons.Default.ArrowBack,stringResource(R.string.browser_back))}
            Text(path.ifEmpty{stringResource(R.string.browser_archive_contents)},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            FilledTonalButton(enabled=!extracting&&list!=null,onClick={
                scope.launch{
                    extracting=true;status=null
                    try{val dest=withContext(Dispatchers.IO){extractSlateZip(root,file)};status=context.getString(R.string.slate_created,dest.name)}
                    catch(e:CancellationException){throw e}catch(_:Exception){status=context.getString(R.string.slate_tool_failed)}
                    finally{extracting=false}
                }
            }){Text(stringResource(R.string.archive_extract))}
        }
        if(extracting||entries==null)LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let{Text(it,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}
        if(entries!=null&&list==null)Text(stringResource(R.string.browser_preview_error),Modifier.padding(16.dp))
        if(list!=null){
            val children=remember(list,path){
                list.asSequence().filter{it.name.startsWith(path)&&it.name.length>path.length}.map{entry->
                    val rest=entry.name.removePrefix(path)
                    if('/' in rest.trimEnd('/'))ArchiveItem(rest.substringBefore('/')+"/",0,true)
                    else ArchiveItem(rest,entry.bytes,entry.directory)
                }.distinctBy{it.name}.sortedWith(compareByDescending<ArchiveItem>{it.directory}.thenBy{it.name.lowercase()}).toList()
            }
            LazyColumn(Modifier.weight(1f)){
                if(list.size>5000)item{Text(stringResource(R.string.browser_truncated),Modifier.padding(12.dp))}
                items(children){entry->
                    Surface(onClick={if(entry.directory)path+=entry.name.trimEnd('/')+"/"},enabled=entry.directory&&!extracting,color=MaterialTheme.colorScheme.surface){
                        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            Icon(painterResource(if(entry.directory)R.drawable.ic_storage_folder else R.drawable.ic_storage_file),null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
                            Text(entry.name.trimEnd('/'),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(!entry.directory&&entry.bytes>=0)Text(Formatter.formatFileSize(context,entry.bytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun SlateDocumentsScreen(onClose:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var revision by remember{mutableIntStateOf(0)}
    var pendingDelete by remember{mutableStateOf<File?>(null)}
    val files by produceState<List<File>>(emptyList(),revision){
        value=withContext(Dispatchers.IO){slateDocuments(context).listFiles().orEmpty().filter{it.isFile&&!it.name.startsWith(".export-")}.sortedBy{it.name.lowercase()}}
    }
    BackHandler(onBack=onClose)
    Column(Modifier.fillMaxSize()){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,stringResource(R.string.browser_back))}
            Text(stringResource(R.string.slate_documents),style=MaterialTheme.typography.titleLarge)
        }
        ToolHeading(stringResource(R.string.slate_documents_title),stringResource(R.string.slate_documents_desc))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            if(files.isEmpty())item{Text(stringResource(R.string.slate_documents_empty),Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}
            items(files,key={it.path}){file->
                Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(painterResource(R.drawable.ic_storage_file),null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){
                        Text(file.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(Formatter.formatFileSize(context,file.length()),style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick={pendingDelete=file}){Icon(Icons.Default.Delete,tr("Delete"))}
                }
            }
        }
    }
    pendingDelete?.let{file->AlertDialog(onDismissRequest={pendingDelete=null},title={Text(tr("Delete"))},text={Text(file.name)},confirmButton={TextButton(onClick={pendingDelete=null;scope.launch{withContext(Dispatchers.IO){file.delete()};revision++}}){Text(tr("Delete"))}},dismissButton={TextButton(onClick={pendingDelete=null}){Text(tr("Cancel"))}})}
}
