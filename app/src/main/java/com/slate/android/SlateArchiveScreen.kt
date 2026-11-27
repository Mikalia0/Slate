// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.net.Uri
import android.text.format.Formatter
import android.media.MediaScannerConnection
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private data class ArchiveItem(val name:String,val bytes:Long,val directory:Boolean)
@Composable internal fun SlateArchiveScreen(root:File,file:File,external:()->Unit){
    val context=LocalContext.current
    val pt=LocalConfiguration.current.locales[0].language=="pt"
    fun t(en:String,br:String)=if(pt)br else en
    val scope=rememberCoroutineScope()
    val apk=file.extension.equals("apk",true)
    var revision by remember(file){mutableIntStateOf(0)}
    val entries by produceState<Result<List<ArchiveItem>>?>(null,file,revision){
        value=null
        value=withContext(Dispatchers.IO){runCatching{ZipFile(file).use{zip->zip.entries().asSequence().take(100001).map{ArchiveItem(it.name,it.size,it.isDirectory)}.toList()}}}
    }
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var path by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<ArchiveItem?>(null)}
    var pending by remember{mutableStateOf<ZipChange?>(null)}
    var importTarget by remember{mutableStateOf<ArchiveItem?>(null)}
    var exportTarget by remember{mutableStateOf<String?>(null)}
    var editTarget by remember{mutableStateOf<String?>(null)}
    var editText by remember{mutableStateOf("")}
    var renameTarget by remember{mutableStateOf<ArchiveItem?>(null)}
    var renameText by remember{mutableStateOf("")}
    var newFolder by remember{mutableStateOf(false)}
    var folderName by remember{mutableStateOf("")}
    val list=entries?.getOrNull()
    val editable=list!=null&&list.size<=100000&&file.canWrite()&&file.parentFile?.canWrite()==true
    fun runTask(action:suspend ()->Unit){
        if(busy)return
        busy=true;status=null
        scope.launch{
            try{action()}catch(e:CancellationException){throw e}catch(e:Exception){status=t("Operation failed: ","Falha na operação: ")+(e.message?:t("Unknown error","Erro desconhecido"))}
            finally{busy=false}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val name=exportTarget;exportTarget=null
        if(uri!=null&&name!=null)runTask{withContext(Dispatchers.IO){copyArchiveEntry(context,file,name,uri)};status=t("File copied","Arquivo copiado")}
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val target=importTarget;importTarget=null
        if(uri!=null)runTask{
            val name=withContext(Dispatchers.IO){displayName(context,uri)}
            if(target!=null)pending=ZipChange(target.name,target.name,source=uri)
            else {
                val dest=checkedArchiveName(path+name)
                val existing=list?.firstOrNull{it.name==dest&&!it.directory}
                pending=ZipChange(existing?.name,dest,source=uri)
            }
        }
    }
    fun goBack(){path=path.trimEnd('/').substringBeforeLast('/',"").let{if(it.isEmpty())""else "$it/"}}
    BackHandler(busy){}
    BackHandler(path.isNotEmpty()&&!busy){goBack()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            if(path.isNotEmpty())IconButton(onClick={goBack()},enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Text(path.ifEmpty{stringResource(R.string.browser_archive_contents)},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(editable){
                var addMenu by remember{mutableStateOf(false)}
                Box{
                    IconButton(onClick={addMenu=true},enabled=!busy){Icon(Icons.Default.Add,t("Add","Adicionar"))}
                    DropdownMenu(expanded=addMenu,onDismissRequest={addMenu=false}){
                        DropdownMenuItem(text={Text(t("Add file","Adicionar arquivo"))},onClick={addMenu=false;importTarget=null;importer.launch(arrayOf("*/*"))})
                        DropdownMenuItem(text={Text(t("New folder","Nova pasta"))},onClick={addMenu=false;folderName="";newFolder=true})
                    }
                }
            }
            FilledTonalButton(enabled=!busy&&list!=null,onClick={
                runTask{val dest=withContext(Dispatchers.IO){extractSlateZip(root,file)};status=context.getString(R.string.slate_created,dest.name)}
            }){Text(stringResource(R.string.archive_extract))}
        }
        if(busy||entries==null)LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let{Text(it,Modifier.padding(horizontal=12.dp,vertical=5.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        if(entries!=null&&list==null)Text(stringResource(R.string.browser_preview_error),Modifier.padding(16.dp))
        if(list!=null){
            val children=remember(list,path){
                list.asSequence().filter{it.name.startsWith(path)&&it.name.length>path.length}.map{entry->
                    val rest=entry.name.removePrefix(path)
                    if('/' in rest.trimEnd('/'))ArchiveItem(path+rest.substringBefore('/')+"/",0,true)else entry
                }.distinctBy{it.name}.sortedWith(compareByDescending<ArchiveItem>{it.directory}.thenBy{it.name.lowercase()}).toList()
            }
            LazyColumn(Modifier.weight(1f)){
                if(list.size>100000)item{Text(stringResource(R.string.browser_truncated),Modifier.padding(12.dp))}
                items(children,key={it.name}){entry->
                    Surface(onClick={if(entry.directory)path=entry.name.trimEnd('/')+"/" else selected=entry},enabled=!busy,color=MaterialTheme.colorScheme.surface){
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp).heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Icon(painterResource(if(entry.directory)R.drawable.ic_storage_folder else R.drawable.ic_storage_file),null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
                            Text(entry.name.trimEnd('/').substringAfterLast('/'),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(!entry.directory&&entry.bytes>=0)Text(Formatter.formatFileSize(context,entry.bytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick={selected=entry},enabled=!busy){Icon(Icons.Default.MoreVert,tr("More"))}
                        }
                    }
                }
            }
        }
    }
    selected?.let{entry->
        AlertDialog(onDismissRequest={selected=null},title={Text(entry.name.trimEnd('/').substringAfterLast('/'),maxLines=2,overflow=TextOverflow.Ellipsis)},text={
            Column{
                if(!entry.directory){
                    TextButton(onClick={selected=null;exportTarget=entry.name;export.launch(entry.name.substringAfterLast('/'))}){Text(t("Copy out…","Copiar para…"))}
                    if(editable){
                        TextButton(onClick={selected=null;importTarget=entry;importer.launch(arrayOf("*/*"))}){Text(t("Replace with file…","Substituir por arquivo…"))}
                        if(entry.bytes in 0..1048576)TextButton(onClick={selected=null;runTask{
                            editText=withContext(Dispatchers.IO){readArchiveText(file,entry.name)}
                            editTarget=entry.name
                        }}){Text(t("Edit as UTF-8 text","Editar como texto UTF-8"))}
                    }
                }
                if(editable){
                    TextButton(onClick={selected=null;renameTarget=entry;renameText=entry.name.trimEnd('/').substringAfterLast('/')}){Text(t("Rename","Renomear"))}
                    TextButton(onClick={selected=null;pending=ZipChange(entry.name,null)}){Text(t("Delete","Excluir"),color=MaterialTheme.colorScheme.error)}
                }
            }
        },confirmButton={TextButton(onClick={selected=null}){Text(tr("Close"))}})
    }
    editTarget?.let{name->
        AlertDialog(onDismissRequest={editTarget=null},title={Text(name.substringAfterLast('/'))},text={
            OutlinedTextField(value=editText,onValueChange={if(it.length<=1048576)editText=it},modifier=Modifier.fillMaxWidth().heightIn(min=160.dp,max=340.dp),textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
        },confirmButton={TextButton(onClick={val bytes=editText.toByteArray(Charsets.UTF_8);if(bytes.size<=1048576){editTarget=null;pending=ZipChange(name,name,data=bytes)}else status=t("Text too large","Texto grande demais")}){Text(t("Save","Salvar"))}},dismissButton={TextButton(onClick={editTarget=null}){Text(tr("Cancel"))}})
    }
    renameTarget?.let{entry->
        AlertDialog(onDismissRequest={renameTarget=null},title={Text(t("Rename","Renomear"))},text={OutlinedTextField(renameText,{renameText=it},singleLine=true)},confirmButton={
            TextButton(enabled=renameText.isNotBlank()&&renameText !in listOf(".","..")&&!renameText.contains('/')&&!renameText.contains('\\'),onClick={
                renameTarget=null;val dest=entry.name.trimEnd('/').substringBeforeLast('/',"").let{if(it.isEmpty())""else "$it/"}+renameText+(if(entry.directory)"/"else "")
                if(dest!=entry.name)pending=ZipChange(entry.name,dest)
            }){Text(t("Save","Salvar"))}
        },dismissButton={TextButton(onClick={renameTarget=null}){Text(tr("Cancel"))}})
    }
    if(newFolder)AlertDialog(onDismissRequest={newFolder=false},title={Text(t("New folder","Nova pasta"))},text={OutlinedTextField(folderName,{folderName=it},singleLine=true)},confirmButton={
        TextButton(enabled=folderName.isNotBlank()&&folderName !in listOf(".","..")&&!folderName.contains('/')&&!folderName.contains('\\'),onClick={newFolder=false;pending=ZipChange(null,path+folderName+"/")}){Text(t("Create","Criar"))}
    },dismissButton={TextButton(onClick={newFolder=false}){Text(tr("Cancel"))}})
    pending?.let{change->
        var autoSign by remember(change){mutableStateOf(true)}
        AlertDialog(onDismissRequest={pending=null},title={Text(if(apk)t("Save modified APK copy","Salvar cópia modificada")else t("Update archive","Atualizar arquivo compactado"))},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(if(apk)t("A separate APK copy will be created.","Uma cópia separada do APK será criada.")else t("The archive will be rebuilt and replaced only after successful completion.","O arquivo será reconstruído e substituído somente após a conclusão com sucesso."))
                if(apk)ApkAutoSignToggle(autoSign,{autoSign=it})
            }
        },confirmButton={TextButton(onClick={pending=null;runTask{
            val dest=withContext(Dispatchers.IO){rewriteSlateZip(context,root,file,change,autoSign)}
            MediaScannerConnection.scanFile(context,arrayOf(dest.path),null,null)
            revision++;status=context.getString(R.string.slate_created,dest.name)
        }}){Text(t("Apply","Aplicar"))}},dismissButton={TextButton(onClick={pending=null}){Text(tr("Cancel"))}})
    }
}
