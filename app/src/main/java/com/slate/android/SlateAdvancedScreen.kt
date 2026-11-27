// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable internal fun SlateAdvancedScreen(localFile:java.io.File,close:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val pt=Locale.getDefault().language=="pt"
    fun tr(en:String,br:String)=if(pt)br else en
    var input by remember(localFile){mutableStateOf<InputFile?>(InputFile(localFile.name,localFile))}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var operationTitle by remember{mutableStateOf<String?>(null)}
    var stage by remember{mutableStateOf(ApkWorkStage.PREPARING)}
    var failed by remember{mutableStateOf(false)}
    var manifest by remember{mutableStateOf<ManifestInfo?>(null)}
    var hex by remember{mutableStateOf<String?>(null)}
    var label by remember{mutableStateOf("")}
    var version by remember{mutableStateOf("")}
    var code by remember{mutableStateOf("")}
    var autoSign by remember{mutableStateOf(true)}
    var tab by remember{mutableIntStateOf(0)}
    fun load(uri:Uri){
        scope.launch{
            busy=true;status=null;manifest=null;hex=null;tab=0;operationTitle=tr("Open file","Abrir arquivo");stage=ApkWorkStage.READING;failed=false
            try{
                val next=withContext(Dispatchers.IO){stageInput(context,uri)}
                input?.file?.takeIf{it!=localFile}?.delete();input=next
                if(next.name.endsWith(".apk",true)){
                    manifest=withContext(Dispatchers.IO){readManifest(next)}
                    label="";version=manifest?.version.orEmpty();code=manifest?.code.orEmpty()
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){failed=true;status=e.message?:tr("Unable to open file","Não foi possível abrir o arquivo")}
            finally{busy=false;if(!failed)operationTitle=null}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{load(it)}}
    val extract=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        val source=input
        if(uri!=null&&source!=null)scope.launch{
            busy=true;status=null;operationTitle=tr("Extract files","Extrair arquivos");stage=ApkWorkStage.EXTRACTING;failed=false
            try{val folder=withContext(Dispatchers.IO){extractAdvanced(context,source,uri)};status=tr("Extracted: ","Extraído: ")+folder}
            catch(e:CancellationException){throw e}catch(e:Exception){failed=true;status=tr("Extraction failed. ","Falha na extração. ")+(e.message.orEmpty())}
            finally{busy=false}
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")){uri->
        val source=input
        if(uri!=null&&source!=null)scope.launch{
            busy=true;status=null;operationTitle=tr("Edit APK","Editar APK");stage=ApkWorkStage.PREPARING;failed=false
            try{withContext(Dispatchers.IO){editManifest(context,source,uri,label,version,code,autoSign){stage=it}};status=if(autoSign)tr("Signed copy saved.","Cópia assinada salva.")else tr("Unsigned copy saved.","Cópia sem assinatura salva.")}
            catch(e:CancellationException){throw e}catch(e:Exception){failed=true;status=tr("Editing failed. ","Falha na edição. ")+e.message.orEmpty()}
            finally{busy=false}
        }
    }
    LaunchedEffect(localFile){
        if(localFile.extension.equals("apk",true)){
            busy=true;operationTitle=tr("Read manifest","Ler manifesto");stage=ApkWorkStage.READING;failed=false
            try{manifest=withContext(Dispatchers.IO){readManifest(InputFile(localFile.name,localFile))};version=manifest?.version.orEmpty();code=manifest?.code.orEmpty()}
            catch(e:CancellationException){throw e}catch(e:Exception){failed=true;status=e.message?:tr("Unable to open file","Não foi possível abrir o arquivo")}
            finally{busy=false;if(!failed)operationTitle=null}
        }
    }
    val current by rememberUpdatedState(input)
    DisposableEffect(Unit){onDispose{current?.file?.takeIf{it!=localFile}?.delete()}}
    androidx.activity.compose.BackHandler(busy){}
    operationTitle?.let{title->ApkOperationDialog(title,input?.name?:localFile.name,stage,if(busy)null else status,failed,onClose={operationTitle=null;status=null})}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=close,enabled=!busy){Icon(Icons.Default.ArrowBack,tr("Back","Voltar"))}
            Text(tr("Advanced tools","Ferramentas avançadas"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text(tr("Open","Abrir"))}
        }
        if(operationTitle==null)status?.let{Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.padding(12.dp)){Text(it,Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}}
        val source=input
        if(source==null){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(tr("Advanced file tools","Ferramentas avançadas"),style=MaterialTheme.typography.headlineSmall)
                Text(tr("Choose an APK or archive. Files are processed locally and the original is never overwritten.","Escolha um APK ou arquivo compactado. O processamento é local e o original nunca é sobrescrito."))
                FilledTonalButton(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text(tr("Choose file","Escolher arquivo"))}
            }
        }else{
            Text(source.name,Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            val isApk=source.name.endsWith(".apk",true)
            val isArchive=source.name.lowercase().let{n->listOf(".7z",".rar",".tar",".tgz",".txz",".tbz2",".tzst",".zst",".gz",".xz",".bz2",".lz4").any{n.endsWith(it)}}
            val tabs=if(isApk)listOf(tr("Manifest","Manifesto"),tr("Edit","Editar"),"HEX")else listOf(tr("Tools","Ferramentas"),"HEX")
            TabRow(selectedTabIndex=tab.coerceIn(tabs.indices)){tabs.forEachIndexed{i,title->Tab(selected=tab==i,onClick={tab=i},text={Text(title)})}}
            val hexTab=tab==tabs.lastIndex
            if(hexTab){
                LaunchedEffect(source){if(hex==null)hex=withContext(Dispatchers.IO){readHex(source)}}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)){
                    Text(tr("First 16 KiB · read only","Primeiros 16 KiB · somente leitura"),style=MaterialTheme.typography.labelMedium)
                    SelectionContainer{Text(hex.orEmpty(),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
                }
            }else if(isApk&&tab==0){
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)){
                    SelectionContainer{Text(manifest?.xml.orEmpty(),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
                }
            }else if(isApk){
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Info,null);Text(tr("Creates a separate APK copy.","Cria uma cópia separada do APK."))}
                    OutlinedTextField(value=label,onValueChange={label=it},label={Text(tr("Application label (optional)","Nome do aplicativo (opcional)"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=version,onValueChange={version=it},label={Text(tr("Version name","Nome da versão"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=code,onValueChange={code=it},label={Text(tr("Version code","Código da versão"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    ApkAutoSignToggle(autoSign,{autoSign=it},enabled=!busy)
                    Button(enabled=!busy&&manifest!=null&&(code.isBlank()||code.toIntOrNull()?.let{it>0}==true),onClick={save.launch(source.name.substringBeforeLast('.')+"-edited.apk")},modifier=Modifier.fillMaxWidth()){Text(tr("Save copy","Salvar cópia"))}
                }
            }else{
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(tr("Archive extraction","Extração de arquivos"),style=MaterialTheme.typography.titleLarge)
                    Text(tr("Extract into a new folder selected through Android's file picker.","Extraia em uma nova pasta selecionada pelo seletor de arquivos do Android."))
                    FilledTonalButton(enabled=!busy&&isArchive,onClick={extract.launch(null)},modifier=Modifier.fillMaxWidth()){Text(tr("Extract to…","Extrair para…"))}
                    Text(tr("7Z, TAR, TZST, GZIP, XZ, BZIP2, LZ4, RAR (supported versions).","7Z, TAR, TZST, GZIP, XZ, BZIP2, LZ4, RAR (versões compatíveis)."),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
