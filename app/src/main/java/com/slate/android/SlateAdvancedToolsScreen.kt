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
@Composable internal fun SlateApkScreen(root:File,file:File,external:()->Unit,initialTab:Int=0,onClose:()->Unit={},directOnly:Boolean=false){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var tab by remember(file){mutableIntStateOf(if(initialTab>=3)2 else initialTab.coerceIn(0,2))}
    val packageResult by produceState<Result<String>?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{checkNotNull(apkPackage(context,file)){"Unable to read APK package"}}}
    }
    val pkg=packageResult?.getOrNull()
    val inspection by produceState<Result<ApkInspection>?>(null,file,tab,directOnly){
        if(!directOnly&&tab==0&&value==null)value=withContext(Dispatchers.IO){runCatching{inspectSlateApk(context,file)}}
    }
    val details=inspection?.getOrNull()
    var cloneDialog by remember{mutableStateOf(initialTab==3)}
    var clonePackage by remember{mutableStateOf("")}
    var autoSign by remember{mutableStateOf(true)}
    var providerAutoSign by remember{mutableStateOf(true)}
    LaunchedEffect(pkg){if(clonePackage.isBlank()&&pkg!=null)clonePackage=pkg+".clone"}
    var advanced by remember{mutableStateOf(false)}
    if(advanced){BackHandler{advanced=false};SlateAdvancedScreen(file){advanced=false};return}
    var signDialog by remember{mutableStateOf(initialTab==4)}
    var providerDialog by remember{mutableStateOf(false)}
    var signing by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var operationTitle by remember{mutableStateOf<String?>(null)}
    var stage by remember{mutableStateOf(ApkWorkStage.PREPARING)}
    var failed by remember{mutableStateOf(false)}
    fun dismissConfig(){if(directOnly)onClose()}
    fun runApkTask(title:String,action:suspend ((ApkWorkStage)->Unit)->String){
        if(signing)return
        operationTitle=title;stage=ApkWorkStage.PREPARING;status=null;failed=false;signing=true
        scope.launch{
            try{status=action{stage=it}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){failed=true;status=e.message?:context.getString(R.string.slate_tool_failed)}
            finally{signing=false}
        }
    }
    BackHandler(signing){}
    if(!directOnly)Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose,enabled=!signing){Icon(Icons.Default.ArrowBack,stringResource(R.string.browser_back))};Text(file.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FilePreview(com.slate.android.storage.Entry(file,false,file.length(),0,0,file.lastModified()),Modifier.size(52.dp),MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){
                Text(details?.label?:file.name,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(details?.pkg?:pkg?:Formatter.formatFileSize(context,file.length()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(details!=null)Text(details.version,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick=external,enabled=!signing){Text(stringResource(R.string.browser_open_with))}
        }
        val tabs=listOf(R.string.apk_overview,R.string.apk_contents,R.string.apk_tools)
        val wide=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=840
        if(!wide)TabRow(selectedTabIndex=tab){tabs.forEachIndexed{index,label->Tab(selected=tab==index,onClick={tab=index},text={Text(stringResource(label),maxLines=1)})}}
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
                item{ToolRow(stringResource(R.string.apk_clone),stringResource(R.string.apk_clone_desc),R.drawable.ic_file_apk,enabled=!signing&&pkg!=null){clonePackage=pkg.orEmpty()+".clone";autoSign=true;cloneDialog=true}}
                item{ToolRow(stringResource(R.string.apk_manifest_tools),stringResource(R.string.apk_manifest_tools_desc),R.drawable.ic_file_apk,enabled=!signing){advanced=true}}
                item{ToolRow(stringResource(R.string.apk_inject_provider),stringResource(R.string.apk_inject_provider_desc),R.drawable.ic_storage_folder,enabled=!signing){providerAutoSign=true;providerDialog=true}}
                item{ToolRow(stringResource(R.string.apk_sign),stringResource(R.string.apk_sign_desc),R.drawable.ic_file_apk,enabled=!signing){signDialog=true}}
                item{ToolRow(stringResource(R.string.apk_certificates),stringResource(R.string.apk_cert_desc),R.drawable.ic_storage_file,enabled=!signing){tab=0}}
                item{ToolRow(stringResource(R.string.apk_contents),stringResource(R.string.apk_contents_desc),R.drawable.ic_file_archive,enabled=!signing){tab=1}}
                item{ToolRow(stringResource(R.string.slate_export_documents),stringResource(R.string.slate_export_desc),R.drawable.ic_storage_folder,enabled=!signing){
                    runApkTask(context.getString(R.string.slate_export_documents)){progress->
                        progress(ApkWorkStage.COPYING)
                        withContext(Dispatchers.IO){exportSlateDocument(context,root,file)}
                        context.getString(R.string.slate_export_done)
                    }
                }}
            }
        }
        }
        }
    }
    if((cloneDialog||signDialog)&&packageResult==null){
        ApkOperationDialog(context.getString(if(cloneDialog)R.string.apk_clone else R.string.apk_sign),file.name,ApkWorkStage.READING,null,onClose={})
    }else if(cloneDialog&&pkg==null){
        ApkOperationDialog(context.getString(R.string.apk_clone),file.name,ApkWorkStage.READING,context.getString(R.string.browser_preview_error),true,onClose={cloneDialog=false;dismissConfig()})
    }else if(cloneDialog)AlertDialog(onDismissRequest={cloneDialog=false;dismissConfig()},title={Text(stringResource(R.string.apk_clone))},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(clonePackage,{clonePackage=it.trim()},label={Text(stringResource(R.string.apk_clone_package))},singleLine=true,isError=clonePackage.isNotEmpty()&&(!validClonePackage(clonePackage)||clonePackage==pkg))
            ApkAutoSignToggle(autoSign,{autoSign=it})
            Text(stringResource(R.string.apk_clone_warning),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={
        TextButton(enabled=pkg!=null&&validClonePackage(clonePackage)&&clonePackage!=pkg,onClick={
            val pkg=clonePackage;val sign=autoSign
            cloneDialog=false
            runApkTask(context.getString(R.string.apk_clone)){progress->
                val dest=withContext(Dispatchers.IO){cloneSlateApk(context,root,file,pkg,sign,progress)}
                MediaScannerConnection.scanFile(context,arrayOf(dest.path),null,null)
                context.getString(R.string.slate_created,dest.name)
            }
        }){Text(stringResource(R.string.apk_clone))}
    },dismissButton={TextButton(onClick={cloneDialog=false;dismissConfig()}){Text(tr("Cancel"))}})
    if(providerDialog)AlertDialog(onDismissRequest={providerDialog=false},title={Text(stringResource(R.string.apk_inject_provider))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.apk_provider_autosign_info));ApkAutoSignToggle(providerAutoSign,{providerAutoSign=it})}},confirmButton={
        TextButton(onClick={
            providerDialog=false
            val autoSign=providerAutoSign
            runApkTask(context.getString(R.string.apk_inject_provider)){progress->
                val dest=withContext(Dispatchers.IO){injectExternalDocumentsProvider(context,root,file,autoSign,progress)}
                MediaScannerConnection.scanFile(context,arrayOf(dest.path),null,null)
                context.getString(R.string.slate_created,dest.name)
            }
        }){Text(stringResource(R.string.apk_inject_provider))}
    },dismissButton={TextButton(onClick={providerDialog=false}){Text(tr("Cancel"))}})
    if(signDialog&&packageResult!=null)ApkSignDialog(pkg,onClose={signDialog=false;dismissConfig()},onSign={options->
        signDialog=false
        runApkTask(context.getString(R.string.apk_sign)){progress->
            val dest=withContext(Dispatchers.IO){signSlateApk(root,file,options,progress)}
            MediaScannerConnection.scanFile(context,arrayOf(dest.path),null,null)
            context.getString(R.string.slate_created,dest.name)
        }
    })
    operationTitle?.let{title->ApkOperationDialog(title,file.name,stage,if(signing)null else status,failed,onClose={operationTitle=null;status=null;if(directOnly)onClose()})}
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
