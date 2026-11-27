// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun vkText(en:String,pt:String)=if(LocalConfiguration.current.locales[0].language=="pt")pt else en
private data class VkRow(val section:String,val key:String,val value:String)
private fun parse(rows:List<String>)=rows.mapNotNull{val parts=it.split('\t',limit=3);if(parts.size==3)VkRow(parts[0],parts[1],parts[2])else null}
@Composable private fun DriverPicker(title:String,drivers:List<VulkanDriver>,current:String,enabled:Boolean=true,onSelect:(String)->Unit){
    var menu by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
        Box{
            TextButton(onClick={menu=true},enabled=enabled){Text((drivers.firstOrNull{it.id==current}?.name?:current)+" ▾",maxLines=1,overflow=TextOverflow.Ellipsis)}
            DropdownMenu(menu,{menu=false}){drivers.forEach{d->DropdownMenuItem(text={Text(d.name)},onClick={menu=false;onSelect(d.id)})}}
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VulkanDiagnosticsScreen(onBack:()->Unit,initialTab:Int=0){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var drivers by remember{mutableStateOf(listOf(VulkanDriverStore.system))}
    var generation by remember{mutableIntStateOf(0)}
    LaunchedEffect(generation){drivers=withContext(Dispatchers.IO){VulkanDriverStore.list(context)}}
    var page by rememberSaveable{mutableIntStateOf(initialTab)}
    var section by rememberSaveable{mutableIntStateOf(0)}
    var first by rememberSaveable{mutableStateOf("System")}
    var second by rememberSaveable{mutableStateOf("System")}
    var query by rememberSaveable{mutableStateOf("")}
    val reports=remember{mutableStateMapOf<String,List<VkRow>>()}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<Uri?>(null)}
    var remove by remember{mutableStateOf<VulkanDriver?>(null)}
    var onlyDifferences by remember{mutableStateOf(true)}
    val prefs=remember{context.getSharedPreferences("vulkan-benchmark",0)}
    var bench by remember{mutableStateOf<String?>(null)}
    var transfer by remember{mutableStateOf<String?>(null)}
    val active=drivers.firstOrNull{it.id==first}?:VulkanDriverStore.system
    val other=drivers.firstOrNull{it.id==second}?:VulkanDriverStore.system
    val labels=listOf(vkText("Overview","Visão geral"),vkText("Features","Recursos"),vkText("Extensions","Extensões"),vkText("Limits","Limites"),vkText("Formats","Formatos"))
    val keys=listOf("overview","feature","extension","limit","format")
    LaunchedEffect(active.id){bench=prefs.getString(active.id+":cube",null);transfer=prefs.getString(active.id+":transfer",null)}
    suspend fun report(driver:VulkanDriver):List<VkRow>{
        reports[driver.id]?.let{return it}
        val rows=parse(VulkanProbeClient.run(context.applicationContext,driver,"report"))
        if(rows.isEmpty())return listOf(VkRow("error","Vulkan","No capabilities returned"))
        if(rows.none{it.section=="error"})reports[driver.id]=rows
        return rows
    }
    var a by remember{mutableStateOf<List<VkRow>>(emptyList())}
    var b by remember{mutableStateOf<List<VkRow>>(emptyList())}
    LaunchedEffect(page,active.id,other.id,generation){
        if(page!=0&&page!=1)return@LaunchedEffect
        a=emptyList();b=emptyList();error=null;busy=true
        try{a=report(active);if(page==1)b=report(other)}
        catch(e:TimeoutCancellationException){error="Driver probe timed out"}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message}
        finally{busy=false}
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){if(it!=null)pending=it}
    BackHandler{if(!busy)onBack()}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!busy){Icon(Icons.Default.ArrowBack,"Back")}
            Text(vkText("Vulkan information","Informações Vulkan"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(page<=1)TextButton(enabled=!busy,onClick={
                val text=(listOf(active.name)+a.map{it.section+" / "+it.key+": "+it.value}+(if(page==1)listOf(other.name)+b.map{it.section+" / "+it.key+": "+it.value}else emptyList())).joinToString("\n")
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Vulkan",text))
            }){Text(vkText("Copy","Copiar"))}
        }
        ScrollableTabRow(page,edgePadding=0.dp){listOf(vkText("Information","Informações"),vkText("Compare","Comparar"),"Drivers",vkText("Tests","Testes")).forEachIndexed{i,label->Tab(page==i,onClick={page=i;error=null},enabled=!busy,text={Text(label)})}}
        if(page!=2)DriverPicker(vkText("Driver","Driver"),drivers,active.id,!busy){first=it}
        if(page==1)DriverPicker(vkText("Compare with","Comparar com"),drivers,other.id,!busy){second=it}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))}
        if(page<=1){
            ScrollableTabRow(section,edgePadding=0.dp){labels.forEachIndexed{i,label->Tab(section==i,onClick={section=i;query=""},text={Text(label)})}}
            Row(verticalAlignment=Alignment.CenterVertically){
                OutlinedTextField(query,{query=it},Modifier.weight(1f),singleLine=true,placeholder={Text(vkText("Search","Pesquisar"))})
                TextButton(enabled=!busy,onClick={reports.remove(active.id);reports.remove(other.id);generation++}){Text(vkText("Refresh","Atualizar"))}
            }
            if(page==1)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onlyDifferences,{onlyDifferences=it});Text(vkText("Differences only","Apenas diferenças"))}
            val left=remember(a,section){a.filter{it.section==keys[section]}.associate{it.key to it.value}}
            val right=remember(b,section){b.filter{it.section==keys[section]}.associate{it.key to it.value}}
            val names=remember(left,right,query,page,onlyDifferences){(left.keys+(if(page==1)right.keys else emptySet())).filter{it.contains(query,true)||left[it].orEmpty().contains(query,true)||right[it].orEmpty().contains(query,true)}.filter{page!=1||!onlyDifferences||left[it]!=right[it]}}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp)){
                items((a+b).filter{it.section=="error"||it.section=="warning"}){r->Text(r.value,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))}
                item{Text(names.size.toString()+" "+vkText("entries","itens"),style=MaterialTheme.typography.labelMedium)}
                items(names,key={it}){name->
                    ListItem(headlineContent={Text(name)},supportingContent={Text(if(page==1)active.name+": "+(left[name]?:"—")+"\n"+other.name+": "+(right[name]?:"—") else left[name]?:"—")})
                    HorizontalDivider()
                }
            }
        }else if(page==2){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Button(enabled=!busy,onClick={pick.launch(arrayOf("application/zip","application/octet-stream"))}){Text(vkText("Import ZIP","Importar ZIP"))}}
            Text(vkText("Custom drivers are experimental, regardless of GPU vendor. They affect only Slate diagnostics.","Drivers personalizados são experimentais, independentemente do fabricante. Afetam apenas os diagnósticos do Slate."),style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)){
                items(drivers,key={it.id}){d->ListItem(headlineContent={Text(d.name)},supportingContent={Text(listOf(d.version,d.author,d.library,if(d.minApi>0)"API "+d.minApi else "").filter{it.isNotBlank()}.joinToString(" · "))},trailingContent={
                    if(!d.system)TextButton(enabled=!busy,onClick={remove=d}){Text(vkText("Remove","Remover"))}
                })}
            }
        }else{
            fun run(kind:String){busy=true;error=null;scope.launch{
                try{val value=VulkanProbeClient.run(context.applicationContext,active,kind).firstOrNull()?:error("No result")
                    if(value.startsWith("ERROR")||value.startsWith("error")){error=value.substringAfterLast('\t')}
                    else{if(kind=="cube")bench=value else transfer=value;prefs.edit().putString(active.id+":"+kind,value).apply()}
                }catch(e:TimeoutCancellationException){error="GPU test timed out"}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}
            }}
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)){
                item{Text(vkText("GPU Cube Benchmark v3","Benchmark 3D v3"),style=MaterialTheme.typography.titleMedium);Text(vkText("Offscreen GPU timestamps, not display FPS.","Timestamps da GPU, não FPS da tela."));Button(enabled=!busy,onClick={run("cube")}){Text(vkText("Run benchmark","Executar benchmark"))}}
                val data=bench?.split('\t').orEmpty()
                if(data.size>=10&&data[0]=="SUITE3"){
                    items(3){i->val median=data[1+i*2].toDoubleOrNull();val p95=data[2+i*2].toDoubleOrNull();if(median!=null&&median>0&&p95!=null)ListItem(headlineContent={Text(listOf(vkText("Geometry","Geometria"),vkText("Shading","Sombreamento"),vkText("Balanced","Equilibrado"))[i])},supportingContent={Text("%.1f FPS · %.3f ms · P95 %.3f ms".format(1000/median,median,p95))})}
                }
                item{HorizontalDivider();Text(vkText("Buffer transfer test","Teste de transferência"),style=MaterialTheme.typography.titleMedium);Button(enabled=!busy,onClick={run("transfer")}){Text(vkText("Run test","Executar teste"))}}
                val t=transfer?.split('\t').orEmpty()
                if(t.size>=4&&t[0]=="PASS")item{Text(t[2]+" MiB/s · "+t[1]+" ms · "+t[3])}
            }
        }
    }
    pending?.let{uri->AlertDialog(onDismissRequest={pending=null},title={Text(vkText("Import native driver?","Importar driver nativo?"))},text={Text(vkText("Use trusted ZIP packages only. A driver contains executable code and can access app data when tested.","Use apenas ZIPs confiáveis. Um driver contém código executável e pode acessar dados do app ao ser testado."))},confirmButton={TextButton(onClick={pending=null;busy=true;error=null;scope.launch{try{withContext(Dispatchers.IO){VulkanDriverStore.import(context,uri)};generation++}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}}){Text(vkText("Import","Importar"))}},dismissButton={TextButton(onClick={pending=null}){Text(vkText("Cancel","Cancelar"))}})}
    remove?.let{d->AlertDialog(onDismissRequest={remove=null},title={Text(d.name)},text={Text(vkText("Remove this driver?","Remover este driver?"))},confirmButton={TextButton(onClick={remove=null;scope.launch{try{withContext(Dispatchers.IO){VulkanDriverStore.delete(context,d.id)};reports.remove(d.id);if(first==d.id)first="System";if(second==d.id)second="System";generation++}catch(e:Exception){error=e.message}}}){Text(vkText("Remove","Remover"))}},dismissButton={TextButton(onClick={remove=null}){Text(vkText("Cancel","Cancelar"))}})}
}
