// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ConnectionLabel(val name:String,val provider:String)
private val connectionLabels=LruCache<String,ConnectionLabel>(64)
private fun connectionLabel(context:Context,uri:Uri):ConnectionLabel{
    connectionLabels.get(uri.toString())?.let{return it}
    val pm=context.packageManager
    val provider=runCatching{pm.resolveContentProvider(uri.authority.orEmpty(),0)?.applicationInfo?.loadLabel(pm)?.toString()}.getOrNull().orEmpty()
    val name=runCatching{DocumentFile.fromTreeUri(context,uri)?.name}.getOrNull().orEmpty()
    return ConnectionLabel(name.ifBlank{provider.ifBlank{uri.authority.orEmpty()}},provider).also{connectionLabels.put(uri.toString(),it)}
}
@Composable internal fun StorageConnectionRow(source:String,onClick:()->Unit){
    val context=LocalContext.current
    val label by produceState(connectionLabels.get(source),source){value=withContext(Dispatchers.IO){connectionLabel(context,Uri.parse(source))}}
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=50.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        BrowserEntryIcon(true,"",Modifier.size(36.dp)){Icon(painterResource(R.drawable.ic_storage_folder),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
        Column(Modifier.weight(1f)){
            Text(label?.name?.takeIf{it.isNotBlank()}?:stringResource(R.string.browser_document_source),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(label?.provider?.takeIf{it.isNotBlank()}?:stringResource(R.string.browser_document_source),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun StorageConnectionDialog(onClose:()->Unit,onPick:()->Unit){
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.browser_add_storage))},text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            ListItem(headlineContent={Text(stringResource(R.string.browser_document_source))},supportingContent={Text(stringResource(R.string.connection_provider_desc))},leadingContent={Icon(painterResource(R.drawable.ic_storage_folder),null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},modifier=Modifier.clickable(onClick=onPick))
            HorizontalDivider()
            ListItem(headlineContent={Text(stringResource(R.string.connection_usb))},supportingContent={Text(stringResource(R.string.connection_usb_desc))},leadingContent={Icon(painterResource(R.drawable.ic_device_storage),null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},modifier=Modifier.clickable(onClick=onPick))
            HorizontalDivider()
            ListItem(headlineContent={Text(stringResource(R.string.connection_cloud))},supportingContent={Text(stringResource(R.string.connection_cloud_desc))},leadingContent={Icon(painterResource(R.drawable.ic_storage_folder),null)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},modifier=Modifier.clickable(onClick=onPick))
        }
    },confirmButton={TextButton(onClick=onClose){Text(tr("Cancel"))}})
}
