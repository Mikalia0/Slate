// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slate.android.storage.ShizukuAccessRow

internal enum class PrivilegeMode(val title:Int,val description:Int){
    ROOT(R.string.privilege_root,R.string.privilege_root_desc),
    SHIZUKU(R.string.privilege_shizuku,R.string.privilege_shizuku_desc),
    NONE(R.string.privilege_none,R.string.privilege_none_desc)
}

@Composable
internal fun PrivilegeOptions(mode:PrivilegeMode,onMode:(PrivilegeMode)->Unit,rootAuthorized:Boolean,onRootChanged:(Boolean)->Unit){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(stringResource(R.string.privilege_title),style=MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.privilege_subtitle),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(6.dp)){
            PrivilegeMode.entries.forEach{option->
                val selected=option==mode
                Surface(shape=MaterialTheme.shapes.medium,color=if(selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer){
                    Row(Modifier.fillMaxWidth().selectable(selected=selected,onClick={onMode(option)},role=Role.RadioButton).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        RadioButton(selected=selected,onClick=null)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                            Text(stringResource(option.title),style=MaterialTheme.typography.titleSmall)
                            Text(stringResource(option.description),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        when(mode){
            PrivilegeMode.ROOT->RootAccessRow(rootAuthorized,onRootChanged)
            PrivilegeMode.SHIZUKU->ShizukuAccessRow()
            PrivilegeMode.NONE->Unit
        }
    }
}

