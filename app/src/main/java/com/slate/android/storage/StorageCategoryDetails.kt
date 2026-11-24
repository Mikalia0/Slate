// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slate.android.R
import com.slate.android.tr

@Composable
internal fun StorageCategoryDetails(stats:CategoryStats,fmt:(Long)->String,onDismiss:()->Unit,onFile:(Entry)->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr(stats.category.label))},text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(fmt(stats.bytes),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
            Text(tr("Largest files")+" · "+stats.largest.size+" / "+stats.count,style=MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.heightIn(max=320.dp)){
                items(stats.largest,key={it.file.path}){entry->
                    ListItem(headlineContent={Text(entry.file.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                        supportingContent={Text(entry.file.parent.orEmpty(),maxLines=1,overflow=TextOverflow.Ellipsis)},
                        trailingContent={TextButton(onClick={onDismiss();onFile(entry)}){Text(fmt(entry.bytes))}})
                    HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text(tr("Close"))}})
}
