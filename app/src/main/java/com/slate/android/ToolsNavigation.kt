// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
@Composable
internal fun SlateBottomNavigation(selected:Int,onSelect:(Int)->Unit){
    val labels=listOf(R.string.tool_home,R.string.tool_files,R.string.tool_gallery,R.string.tool_monitors)
    val icons=listOf(R.drawable.ic_device_overview,R.drawable.ic_storage_folder,R.drawable.ic_device_camera,R.drawable.ic_device_cpu)
    NavigationBar{
        labels.forEachIndexed{index,label->
            NavigationBarItem(selected=selected==index,onClick={onSelect(index)},icon={Icon(painterResource(icons[index]),null,Modifier.size(23.dp))},label={Text(stringResource(label),maxLines=1)})
        }
    }
}
