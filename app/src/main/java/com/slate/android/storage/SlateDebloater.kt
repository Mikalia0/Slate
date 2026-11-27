// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slate.android.R
import kotlinx.coroutines.*

private data class DebloatApp(val pkg:String,val label:String,val installed:Boolean,val enabled:Boolean,val protected:Boolean)
@Suppress("DEPRECATION")
private fun readDebloatApps(context:Context):List<DebloatApp>{
    val pm=context.packageManager
    val home=pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val ime=Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
    val admin=runCatching{context.getSystemService(android.app.admin.DevicePolicyManager::class.java)?.activeAdmins.orEmpty().map{it.packageName}.toSet()}.getOrDefault(emptySet())
    return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES).filter{it.flags and ApplicationInfo.FLAG_SYSTEM!=0}.map{app->
        DebloatApp(app.packageName,runCatching{pm.getApplicationLabel(app).toString()}.getOrDefault(app.packageName),app.flags and ApplicationInfo.FLAG_INSTALLED!=0,app.enabled,protectedDebloatPackage(app.packageName,context.packageName)||app.packageName==home||app.packageName==ime||app.packageName in admin)
    }.sortedBy{it.label.lowercase(java.util.Locale.ROOT)}
}
@Composable private fun RiskLabel(risk:String){
    val (label,color)=when(risk){
        "recommended"->R.string.debloat_recommended to Color(0xFF82CA9D)
        "advanced"->R.string.debloat_advanced to Color(0xFFEAC377)
        "expert"->R.string.debloat_expert to Color(0xFFEEA39A)
        "unsafe"->R.string.debloat_unsafe to Color(0xFFEC8B89)
        else->R.string.debloat_unknown to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color=color.copy(alpha=.13f),shape=MaterialTheme.shapes.small){Text(stringResource(label),Modifier.padding(horizontal=7.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=color)}
}
@Composable internal fun SlateDebloater(onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val authorized by produceState(shizukuInstallReady()){while(true){value=shizukuInstallReady();delay(1000)}}
    var database by remember{mutableStateOf<Map<String,DebloatInfo>>(emptyMap())}
    var rows by remember{mutableStateOf<List<DebloatApp>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf<String?>(null)}
    var removed by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableStateOf("all")}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())}
    var detail by remember{mutableStateOf<DebloatApp?>(null)}
    var pending by remember{mutableStateOf<Int?>(null)}
    fun refresh(force:Boolean){
        if(busy)return
        loading=true;status=null
        scope.launch{
            try{
                rows=withContext(Dispatchers.IO){readDebloatApps(context)}
                val result=loadDebloatDatabase(context,force)
                database=result.rows
                if(result.stale)status=context.getString(R.string.debloat_cached)
            }catch(e:CancellationException){throw e}catch(e:Exception){status=context.getString(R.string.debloat_load_failed)}
            finally{loading=false}
        }
    }
    LaunchedEffect(Unit){refresh(false)}
    BackHandler{if(!busy)onBack()}
    val visible=remember(rows,removed,query,filter,database){rows.filter{
        (if(removed)!it.installed||!it.enabled else it.installed&&it.enabled)&&
        (filter=="all"||(database[it.pkg]?.risk?:"unknown")==filter)&&
        (it.label.contains(query,true)||it.pkg.contains(query,true))
    }}
    fun allowed(row:DebloatApp,action:Int):Boolean=when(action){
        DEBLOAT_DISABLE->row.installed&&row.enabled&&!row.protected&&database[row.pkg]?.risk in setOf("recommended","advanced","expert")
        DEBLOAT_REMOVE->row.installed&&row.enabled&&!row.protected&&database[row.pkg]?.risk=="recommended"
        DEBLOAT_RESTORE->!row.installed
        DEBLOAT_ENABLE->row.installed&&!row.enabled
        else->false
    }
    val chosen=rows.filter{it.pkg in selected}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,enabled=!busy){Icon(Icons.Default.ArrowBack,null)}
            Text(stringResource(R.string.debloat_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={refresh(true)},enabled=!busy&&!loading){Icon(Icons.Default.Refresh,stringResource(R.string.debloat_update))}
        }
        if(!authorized)ShizukuAccessRow()
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!removed,onClick={removed=false;selected=emptySet()},label={Text(stringResource(R.string.debloat_installed))})
            FilterChip(selected=removed,onClick={removed=true;selected=emptySet()},label={Text(stringResource(R.string.debloat_removed))})
        }
        OutlinedTextField(query,{query=it;selected=emptySet()},singleLine=true,modifier=Modifier.fillMaxWidth(),placeholder={Text(stringResource(R.string.debloat_search))})
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=filter=="all",onClick={filter="all";selected=emptySet()},label={Text(stringResource(R.string.debloat_all))})
            listOf("recommended","advanced","expert","unsafe","unknown").forEach{risk->FilterChip(selected=filter==risk,onClick={filter=risk;selected=emptySet()},label={RiskLabel(risk)})}
        }
        if(loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            items(visible,key={it.pkg}){row->
                val risk=database[row.pkg]?.risk?:"unknown"
                ListItem(modifier=Modifier.clickable(enabled=!busy){detail=row},headlineContent={Text(row.label,maxLines=2,overflow=TextOverflow.Ellipsis)},
                    leadingContent={AppIcon(row.pkg)},supportingContent={Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(row.pkg,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){RiskLabel(risk);Text(com.slate.android.tr("System"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(row.protected)Text(stringResource(R.string.debloat_protected),style=MaterialTheme.typography.labelSmall)}
                    }},trailingContent={Checkbox(checked=row.pkg in selected,onCheckedChange={check->selected=if(check)selected+row.pkg else selected-row.pkg},enabled=!busy&&(if(removed)allowed(row,DEBLOAT_RESTORE)||allowed(row,DEBLOAT_ENABLE)else allowed(row,DEBLOAT_DISABLE)))})
            }
            item{Text("Universal Android Debloater Next Generation",Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        if(chosen.isNotEmpty())Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Text(chosen.size.toString(),Modifier.weight(1f))
            if(removed)Button(enabled=authorized&&!busy,onClick={pending=DEBLOAT_RESTORE}){Text(stringResource(R.string.debloat_restore))}
            else{
                OutlinedButton(enabled=authorized&&!busy&&chosen.all{allowed(it,DEBLOAT_DISABLE)},onClick={pending=DEBLOAT_DISABLE}){Text(stringResource(R.string.debloat_disable))}
                Button(enabled=authorized&&!busy&&chosen.all{allowed(it,DEBLOAT_REMOVE)},onClick={pending=DEBLOAT_REMOVE}){Text(stringResource(R.string.debloat_remove))}
            }
        }
    }
    detail?.let{row->
        AlertDialog(onDismissRequest={detail=null},title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AppIcon(row.pkg);Text(row.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)}},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(row.pkg,style=MaterialTheme.typography.bodySmall)
                RiskLabel(database[row.pkg]?.risk?:"unknown")
                Text(database[row.pkg]?.description?.takeIf{it.isNotBlank()}?:stringResource(R.string.debloat_no_info))
                Text(stringResource(R.string.debloat_source),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },confirmButton={TextButton(onClick={detail=null;runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+row.pkg)))}}){Text(stringResource(R.string.debloat_app_info))}},dismissButton={TextButton(onClick={detail=null}){Text(stringResource(android.R.string.cancel))}})
    }
    pending?.let{action->
        AlertDialog(onDismissRequest={pending=null},title={Text(stringResource(when(action){DEBLOAT_REMOVE->R.string.debloat_remove;DEBLOAT_DISABLE->R.string.debloat_disable;else->R.string.debloat_restore}))},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(if(action==DEBLOAT_RESTORE)R.string.debloat_restore_warning else R.string.debloat_warning))
                chosen.forEach{Text(it.label+" · "+it.pkg,style=MaterialTheme.typography.bodySmall)}
            }
        },confirmButton={TextButton(enabled=authorized&&!busy,onClick={
            val targets=chosen.toList();pending=null;busy=true;status=null
            scope.launch{
                var done=0;val failures=mutableListOf<String>()
                try{
                    withDebloatService(context){svc->
                        val current=readDebloatApps(context).associateBy{it.pkg}
                        targets.forEach{previous->
                            val row=current[previous.pkg]?:return@forEach
                            val op=if(action==DEBLOAT_RESTORE&&row.installed)DEBLOAT_ENABLE else action
                            if(!allowed(row,op)){failures.add(row.pkg);return@forEach}
                            val result=svc.change(row.pkg,op)
                            if(result=="Success")done++ else failures.add(row.pkg+": "+result.take(300))
                        }
                    }
                }catch(e:CancellationException){throw e}catch(e:Exception){failures.add(e.message.orEmpty())}
                finally{
                    rows=runCatching{withContext(Dispatchers.IO){readDebloatApps(context)}}.getOrDefault(rows)
                    selected=emptySet();busy=false
                    status=context.getString(R.string.debloat_result,done,targets.size)+if(failures.isEmpty())""else "\n"+failures.joinToString("\n")
                }
            }
        }){Text(stringResource(android.R.string.ok))}},dismissButton={TextButton(onClick={pending=null}){Text(stringResource(android.R.string.cancel))}})
    }
}
