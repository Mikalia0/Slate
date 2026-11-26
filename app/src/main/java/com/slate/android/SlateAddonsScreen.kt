// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File

private const val ADVANCED_PACKAGE="com.slate.android.advanced"
private const val RELEASES="https://github.com/stevenmx2/Slate/releases/latest"
internal fun isAddonArchive(name:String):Boolean=name.lowercase().let{n->listOf(".7z",".tar",".tgz",".tar.gz",".tar.xz",".txz",".tar.bz2",".tbz2",".tar.zst",".tzst",".zst",".gz",".xz",".bz2",".lz4",".rar").any{n.endsWith(it)}}
private fun hasSlateAddon(context:Context)=runCatching{
    context.packageManager.checkSignatures(context.packageName,ADVANCED_PACKAGE)==PackageManager.SIGNATURE_MATCH
}.getOrDefault(false)
private fun openReleases(context:Context){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(RELEASES)))}}
internal fun openSlateAddon(context:Context,root:File,file:File){
    if(!hasSlateAddon(context)){openReleases(context);return}
    runCatching{
        require(safeFile(root,file)&&file.isFile&&!com.slate.android.storage.protectedAppPath(file.toPath()))
        val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
        val intent=Intent(Intent.ACTION_VIEW).setClassName(ADVANCED_PACKAGE,"com.slate.advanced.AdvancedActivity")
            .setDataAndType(uri,"application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData=ClipData.newRawUri(file.name,uri)
        context.startActivity(intent)
    }.onFailure{android.widget.Toast.makeText(context,R.string.slate_tool_failed,android.widget.Toast.LENGTH_SHORT).show()}
}
@Composable internal fun SlateAddonsScreen(onClose:()->Unit){
    val context=LocalContext.current
    BackHandler(onBack=onClose)
    val installed=hasSlateAddon(context)
    Column(Modifier.fillMaxSize()){
        Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onClose){Icon(Icons.Default.ArrowBack,stringResource(R.string.browser_back))}
            Text(stringResource(R.string.slate_addons),style=MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(stringResource(R.string.slate_addon_included),style=MaterialTheme.typography.titleMedium)
            listOf(R.string.slate_basic_media,R.string.slate_basic_files,R.string.slate_basic_apk).forEach{label->
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(stringResource(label),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.slate_installed),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.slate_addon_optional),style=MaterialTheme.typography.titleMedium)
            Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.large){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(painterResource(R.drawable.ic_file_archive),null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)){
                            Text("Slate Advanced",style=MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.slate_addon_desc),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledTonalButton(onClick={
                        if(installed)runCatching{context.startActivity(Intent().setClassName(ADVANCED_PACKAGE,"com.slate.advanced.AdvancedActivity"))}
                        else openReleases(context)
                    },modifier=Modifier.fillMaxWidth()){Text(stringResource(if(installed)R.string.browser_internal else R.string.slate_addon_download))}
                }
            }
        }
    }
}
