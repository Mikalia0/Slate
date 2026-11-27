// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val browserTypeLabels=setOf("zip","rar","7z","tar","gz","xz","zst","tzst","bz2","dll","exe","msi","patch","diff","so","json","xml","ini","cfg","log","txt","pdf","bin","iso","sh","bat")

@Composable
internal fun BrowserEntryIcon(directory:Boolean,extension:String,modifier:Modifier=Modifier.size(40.dp),content:@Composable ()->Unit){
    val colors=MaterialTheme.colorScheme
    Box(modifier.background(if(directory)colors.primaryContainer else colors.secondaryContainer,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
        if(!directory&&extension.lowercase() in browserTypeLabels)Text(extension.uppercase(),style=MaterialTheme.typography.labelSmall,color=colors.onSecondaryContainer,maxLines=1)
        else content()
    }
}
@Composable
internal fun BrowserEntryRow(name:String,date:String,metadata:String?,active:Boolean,compact:Boolean,modifier:Modifier=Modifier,icon:@Composable ()->Unit){
    Row(modifier.fillMaxWidth().background(if(active)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).heightIn(min=if(compact)44.dp else 51.dp).padding(horizontal=8.dp,vertical=if(compact)2.dp else 5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        icon()
        if(compact){
            Text(name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(date,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            Text(metadata.orEmpty(),Modifier.widthIn(min=64.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        }else Column(Modifier.weight(1f)){
            Text(name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Text(date,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                metadata?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
            }
        }
    }
}
@Composable
internal fun DelayedBrowserProgress(loading:Boolean){
    var visible by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(false)}
    androidx.compose.runtime.LaunchedEffect(loading){
        visible=false
        if(loading){kotlinx.coroutines.delay(300);visible=true}
    }
    // Reserve a thin, fixed slot so delayed metadata never shifts rows.
    Box(Modifier.fillMaxWidth().height(2.dp)){if(visible&&loading)LinearProgressIndicator(Modifier.fillMaxSize())}
}
