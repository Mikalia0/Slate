// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.Formatter
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class ApkQuickInfo(val label:String,val pkg:String,val version:String,val installed:String?,val min:Int,val target:Int)
@Suppress("DEPRECATION")
@Composable internal fun SlateApkActions(root:File,file:File,onClose:()->Unit,onInspect:(Int)->Unit){
    val context=LocalContext.current
    val info by produceState<ApkQuickInfo?>(null,file){
        value=withContext(Dispatchers.IO){runCatching{
            val pm=context.packageManager
            val pkg=checkNotNull(pm.getPackageArchiveInfo(file.path,0))
            val app=checkNotNull(pkg.applicationInfo).apply{sourceDir=file.path;publicSourceDir=file.path}
            fun version(p:android.content.pm.PackageInfo)=p.versionName.orEmpty()+" ("+(if(android.os.Build.VERSION.SDK_INT>=28)p.longVersionCode else p.versionCode.toLong())+")"
            val installed=runCatching{version(pm.getPackageInfo(pkg.packageName,0))}.getOrNull()
            ApkQuickInfo(app.loadLabel(pm).toString(),pkg.packageName,version(pkg),installed,app.minSdkVersion,app.targetSdkVersion)
        }.getOrNull()}
    }
    val pt=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language=="pt"
    fun t(en:String,br:String)=if(pt)br else en
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onClose,title={
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FilePreview(com.slate.android.storage.Entry(file,false,file.length(),0,0,file.lastModified()),Modifier.size(48.dp),MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)){
                Text(info?.label?:file.name,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(info?.version?:Formatter.formatFileSize(context,file.length()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            info?.let{p->
                Text(p.pkg,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("SDK ${p.min} → ${p.target} · "+Formatter.formatFileSize(context,file.length()),style=MaterialTheme.typography.bodySmall)
                p.installed?.let{Text(t("Installed: ","Instalado: ")+it,style=MaterialTheme.typography.bodySmall)}
            }
            HorizontalDivider()
            val actions=listOf(Triple(t("Information","Informações"),Icons.Default.Info,0),Triple(t("Explore APK","Explorar APK"),Icons.Default.List,1),Triple(t("APK tools","Ferramentas APK"),Icons.Default.Build,2),Triple(stringResource(R.string.apk_clone),Icons.Default.ContentCopy,3))
            actions.forEach{(title,icon,tab)->
                Row(Modifier.fillMaxWidth().clickable{onInspect(tab)}.heightIn(min=46.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Icon(Icons.Default.KeyboardArrowRight,null)
                }
            }
            if(info?.installed!=null)TextButton(onClick={
                val launch=context.packageManager.getLaunchIntentForPackage(info!!.pkg)
                if(launch!=null&&runCatching{context.startActivity(launch)}.isSuccess)onClose()else error=true
            }){Text(t("Launch installed app","Abrir aplicativo instalado"))}
            TextButton(onClick={if(openSharedFile(context,root,file,false))onClose()else error=true}){Text(stringResource(R.string.browser_open_with))}
            if(error)Text(stringResource(R.string.file_failed),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={Button(onClick={
        val ok=runCatching{
            require(safeFile(root,file)&&file.isFile)
            val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
            val intent=Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData=ClipData.newRawUri(file.name,uri)
            context.startActivity(intent)
        }.isSuccess
        if(ok)onClose()else if(openSharedFile(context,root,file,false))onClose()else error=true
    }){Text(t("Install","Instalar"))}},dismissButton={TextButton(onClick=onClose){Text(t("Cancel","Cancelar"))}})
}
