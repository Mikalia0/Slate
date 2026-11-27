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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import com.slate.android.R
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
    val d=drivers.firstOrNull{it.id==current}
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Box{
            Row(Modifier.fillMaxWidth().clickable(enabled=enabled){menu=true}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(38.dp)){
                    Box(contentAlignment=Alignment.Center){DeviceGlyph(R.drawable.ic_device_gpu)}
                }
                Column(Modifier.weight(1f)){
                    Text(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(d?.system==true)vkText("System driver","Driver do sistema")else d?.name?:current,style=MaterialTheme.typography.titleSmall)
                }
                Icon(Icons.Default.KeyboardArrowDown,null,tint=MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(menu,{menu=false}){drivers.forEach{driver->DropdownMenuItem(text={Text(if(driver.system)vkText("System driver","Driver do sistema")else driver.name)},onClick={menu=false;onSelect(driver.id)},trailingIcon={if(driver.id==current)Icon(Icons.Default.Check,null)})}}
        }
    }
}
@Composable private fun VkPanel(content:@Composable ColumnScope.()->Unit){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable private fun VkMetric(label:String,value:String,modifier:Modifier=Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable private fun VkValue(value:String){
    val color=when(value){"Supported","Available"->MaterialTheme.colorScheme.primary;"Unsupported"->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.onSurfaceVariant}
    Text(when(value){"Supported"->vkText("Supported","Suportado");"Unsupported"->vkText("Unsupported","Não suportado");"Available"->vkText("Available","Disponível");else->value},style=MaterialTheme.typography.bodySmall,color=color)
}
@Composable private fun VkOverview(driver:VulkanDriver,rows:List<VkRow>){
    val values=remember(rows){rows.filter{it.section=="overview"}.associate{it.key to it.value}}
    val features=remember(rows){rows.filter{it.section=="feature"}.associate{it.key to it.value}}
    val formats=remember(rows){rows.filter{it.section=="format"}.associate{it.key to it.value}}
    val count=rows.count{it.section=="extension"}
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,modifier=Modifier.size(50.dp)){Box(contentAlignment=Alignment.Center){DeviceGlyph(R.drawable.ic_device_gpu,modifier=Modifier.size(30.dp))}}
            Column(Modifier.weight(1f)){
                Text(if(driver.system)vkText("Android system","Sistema Android")else "AdrenoTools",style=MaterialTheme.typography.labelMedium)
                Text(values["GPU"]?:"—",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text("Vulkan "+(values["Vulkan API"]?:"—"),style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        VkMetric(vkText("Extensions","Extensões"),count.toString(),Modifier.weight(1f))
        VkMetric(vkText("Supported features","Recursos suportados"),features.values.count{it=="Supported"}.toString(),Modifier.weight(1f))
    }
    VkPanel{
        Text(vkText("Driver details","Detalhes do driver"),style=MaterialTheme.typography.titleMedium)
        val type=when(values["Device type"]){"1"->vkText("Integrated GPU","GPU integrada");"2"->vkText("Discrete GPU","GPU dedicada");"3"->vkText("Virtual GPU","GPU virtual");"4"->"CPU";else->values["Device type"]?:"—"}
        listOf("Vendor ID" to values["Vendor ID"],"Device ID" to values["Device ID"],vkText("Driver version (raw)","Versão do driver (raw)") to values["Driver version (raw)"],vkText("Device type","Tipo de dispositivo") to type).forEach{(k,v)->
            Row(verticalAlignment=Alignment.CenterVertically){Text(k,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(v?:"—",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    VkPanel{
        Text(vkText("Key capabilities","Recursos principais"),style=MaterialTheme.typography.titleMedium)
        listOf("BCn" to features["textureCompressionBC"],"ASTC" to features["textureCompressionASTC_LDR"],"ETC2" to features["textureCompressionETC2"],"FP16" to features["shaderFloat16"],"FP64" to features["shaderFloat64"],"Dynamic rendering" to features["dynamicRendering"],"Timeline semaphore" to features["timelineSemaphore"]).forEach{(name,value)->
            Row(verticalAlignment=Alignment.CenterVertically){Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);VkValue(value?:"—")}
        }
        HorizontalDivider()
        Row(verticalAlignment=Alignment.CenterVertically){Text("BC7",Modifier.weight(1f));VkValue(formats["BC7"]?:"—")}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VulkanDiagnosticsScreen(onBack:()->Unit,initialTab:Int=0,embedded:Boolean=false,activePage:Boolean=true){
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
    LaunchedEffect(page,active.id,other.id,generation,activePage){
        if(!activePage||(page!=0&&page!=1))return@LaunchedEffect
        a=emptyList();b=emptyList();error=null;busy=true
        try{a=report(active);if(page==1)b=report(other)}
        catch(e:TimeoutCancellationException){error="Driver probe timed out"}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message}
        finally{busy=false}
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){if(it!=null)pending=it}
    BackHandler(enabled=!embedded){if(!busy)onBack()}
    val copyReport:()->Unit={
        val text=(listOf(active.name)+a.map{it.section+" / "+it.key+": "+it.value}+(if(page==1)listOf(other.name)+b.map{it.section+" / "+it.key+": "+it.value}else emptyList())).joinToString("\n")
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Vulkan",text))
    }
    Column(Modifier.fillMaxSize().padding(horizontal=if(embedded)0.dp else 12.dp)){
        if(!embedded)Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!busy){Icon(Icons.Default.ArrowBack,"Back")}
            Text(vkText("Vulkan information","Informações Vulkan"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            val pages=listOf(vkText("Info","Info"),vkText("Compare","Comparar"),"Drivers",vkText("Tests","Testes"))
            val icons=listOf(Icons.Default.Info,Icons.Default.CompareArrows,Icons.Default.Folder,Icons.Default.Speed)
            pages.forEachIndexed{i,label->
                Surface(onClick={page=i;error=null},enabled=!busy,modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.medium,color=if(page==i)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow){
                    Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Icon(icons[i],null,Modifier.size(20.dp),tint=if(page==i)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                }
            }
        }
        if(page!=2)DriverPicker(vkText("Driver","Driver"),drivers,active.id,!busy){first=it}
        if(page==1)Spacer(Modifier.height(6.dp))
        if(page==1)DriverPicker(vkText("Compare with","Comparar com"),drivers,other.id,!busy){second=it}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))}
        if(page<=1){
            LazyRow(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){items(labels.size){i->FilterChip(selected=section==i,onClick={section=i;query=""},label={Text(labels[i])})}}
            Row(verticalAlignment=Alignment.CenterVertically){
                if(section!=0||page==1)OutlinedTextField(query,{query=it},Modifier.weight(1f),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text(vkText("Search","Pesquisar"))},shape=MaterialTheme.shapes.large)
                else Text(vkText("GPU capabilities","Capacidades da GPU"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                IconButton(enabled=!busy&&a.isNotEmpty(),onClick=copyReport){Icon(Icons.Default.ContentCopy,vkText("Copy report","Copiar relatório"))}
                IconButton(enabled=!busy,onClick={reports.remove(active.id);reports.remove(other.id);generation++}){Icon(Icons.Default.Refresh,vkText("Refresh","Atualizar"))}
            }
            if(page==1)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onlyDifferences,{onlyDifferences=it});Text(vkText("Differences only","Apenas diferenças"))}
            val left=remember(a,section){a.filter{it.section==keys[section]}.associate{it.key to it.value}}
            val right=remember(b,section){b.filter{it.section==keys[section]}.associate{it.key to it.value}}
            val names=remember(left,right,query,page,onlyDifferences){(left.keys+(if(page==1)right.keys else emptySet())).filter{it.contains(query,true)||left[it].orEmpty().contains(query,true)||right[it].orEmpty().contains(query,true)}.filter{page!=1||!onlyDifferences||left[it]!=right[it]}}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                items((a+b).filter{it.section=="error"||it.section=="warning"}){r->VkPanel{Text(r.value,color=MaterialTheme.colorScheme.error)}}
                if(page==0&&section==0){
                    if(left.isNotEmpty())item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){VkOverview(active,a)}}
                }else{
                    item{Text(names.size.toString()+" "+vkText("entries","itens"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    items(names,key={it}){name->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                            Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(name,style=MaterialTheme.typography.bodyMedium)
                                if(page==1){
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        Column(Modifier.weight(1f)){Text(active.name,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis);VkValue(left[name]?:"—")}
                                        Column(Modifier.weight(1f)){Text(other.name,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis);VkValue(right[name]?:"—")}
                                    }
                                }else VkValue(left[name]?:"—")
                            }
                        }
                    }
                }
            }
        }else if(page==2){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(vkText("Installed drivers","Drivers instalados"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Button(enabled=!busy,onClick={pick.launch(arrayOf("application/zip","application/octet-stream"))}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(4.dp));Text(vkText("Import","Importar"))}
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)){
                items(drivers,key={it.id}){d->VkPanel{
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        DeviceGlyph(R.drawable.ic_device_gpu)
                        Column(Modifier.weight(1f)){Text(if(d.system)vkText("System driver","Driver do sistema")else d.name,style=MaterialTheme.typography.titleMedium);Text(if(d.system)"Android" else "AdrenoTools",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(!d.system)IconButton(enabled=!busy,onClick={remove=d}){Icon(Icons.Default.Delete,vkText("Remove","Remover"))}
                    }
                    val meta=listOf(d.version,d.author,d.library,if(d.minApi>0)"API "+d.minApi else "").filter{it.isNotBlank()}
                    if(meta.isNotEmpty())Text(meta.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled=!busy,onClick={first=d.id;section=0;page=0}){Text(vkText("View information","Ver informações"))}
                }}
                item{Text(vkText("Custom drivers are used only for Slate diagnostics.","Drivers personalizados são usados apenas nos diagnósticos do Slate."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }else{
            fun run(kind:String){busy=true;error=null;scope.launch{
                try{val value=VulkanProbeClient.run(context.applicationContext,active,kind).firstOrNull()?:error("No result")
                    if(value.startsWith("ERROR")||value.startsWith("error")){error=value.substringAfterLast('\t')}
                    else{if(kind=="cube")bench=value else transfer=value;prefs.edit().putString(active.id+":"+kind,value).apply()}
                }catch(e:TimeoutCancellationException){error="GPU test timed out"}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}
            }}
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)){
                item{VkPanel{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){DeviceGlyph(R.drawable.ic_device_gpu);Text(vkText("GPU Cube Benchmark v3","Benchmark 3D v3"),style=MaterialTheme.typography.titleMedium)};Text(vkText("Geometry, shading and balanced workloads.","Geometria, shaders e carga equilibrada."),style=MaterialTheme.typography.bodySmall);Button(enabled=!busy,onClick={run("cube")},modifier=Modifier.fillMaxWidth()){Text(vkText("Run benchmark","Executar benchmark"))}}}
                val data=bench?.split('\t').orEmpty()
                if(data.size>=10&&data[0]=="SUITE3"){
                    items(3){i->val median=data[1+i*2].toDoubleOrNull();val p95=data[2+i*2].toDoubleOrNull();if(median!=null&&median>0&&p95!=null)ListItem(headlineContent={Text(listOf(vkText("Geometry","Geometria"),vkText("Shading","Sombreamento"),vkText("Balanced","Equilibrado"))[i])},supportingContent={Text("%.1f FPS · %.3f ms · P95 %.3f ms".format(1000/median,median,p95))})}
                }
                item{VkPanel{Text(vkText("Buffer transfer test","Teste de transferência"),style=MaterialTheme.typography.titleMedium);Button(enabled=!busy,onClick={run("transfer")},modifier=Modifier.fillMaxWidth()){Text(vkText("Run test","Executar teste"))}}}
                val t=transfer?.split('\t').orEmpty()
                if(t.size>=4&&t[0]=="PASS")item{Text(t[2]+" MiB/s · "+t[1]+" ms · "+t[3])}
            }
        }
    }
    pending?.let{uri->AlertDialog(onDismissRequest={pending=null},title={Text(vkText("Import native driver?","Importar driver nativo?"))},text={Text(vkText("Use trusted ZIP packages only. A driver contains executable code and can access app data when tested.","Use apenas ZIPs confiáveis. Um driver contém código executável e pode acessar dados do app ao ser testado."))},confirmButton={TextButton(onClick={pending=null;busy=true;error=null;scope.launch{try{withContext(Dispatchers.IO){VulkanDriverStore.import(context,uri)};generation++}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}}){Text(vkText("Import","Importar"))}},dismissButton={TextButton(onClick={pending=null}){Text(vkText("Cancel","Cancelar"))}})}
    remove?.let{d->AlertDialog(onDismissRequest={remove=null},title={Text(d.name)},text={Text(vkText("Remove this driver?","Remover este driver?"))},confirmButton={TextButton(onClick={remove=null;scope.launch{try{withContext(Dispatchers.IO){VulkanDriverStore.delete(context,d.id)};reports.remove(d.id);if(first==d.id)first="System";if(second==d.id)second="System";generation++}catch(e:Exception){error=e.message}}}){Text(vkText("Remove","Remover"))}},dismissButton={TextButton(onClick={remove=null}){Text(vkText("Cancel","Cancelar"))}})}
}
