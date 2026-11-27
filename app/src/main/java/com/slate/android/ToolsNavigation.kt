// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

@Composable
private fun SlateTopNavigation(selected:Int,onSelect:(Int)->Unit,enabled:Boolean){
    val labels=listOf(R.string.tool_home,R.string.tool_files,R.string.tool_gallery,R.string.tool_monitors)
    val icons=listOf(R.drawable.ic_device_overview,R.drawable.ic_storage_folder,R.drawable.ic_device_camera,R.drawable.ic_device_cpu)
    Row(horizontalArrangement=Arrangement.spacedBy(0.dp)){
        labels.forEachIndexed{index,label->
            IconToggleButton(checked=selected==index,onCheckedChange={onSelect(index)},enabled=enabled,modifier=Modifier.size(44.dp),colors=IconButtonDefaults.iconToggleButtonColors(checkedContainerColor=MaterialTheme.colorScheme.secondaryContainer,checkedContentColor=MaterialTheme.colorScheme.onSecondaryContainer)){
                Icon(painterResource(icons[index]),stringResource(label),Modifier.size(21.dp))
            }
        }
    }
}

/** Single persistent landscape header: the six controls never depend on the active screen. */
@Composable
internal fun SlateLandscapeHeader(selected:Int,onSelect:(Int)->Unit,onSettings:()->Unit,onAbout:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
        Image(painterResource(R.drawable.ic_slate),null,Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text("Slate",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        SlateTopNavigation(selected,onSelect,enabled)
        IconButton(onClick=onSettings,enabled=enabled,modifier=Modifier.size(44.dp)){Icon(Icons.Default.Settings,tr("Settings"))}
        IconButton(onClick=onAbout,enabled=enabled,modifier=Modifier.size(44.dp)){Icon(Icons.Default.Info,tr("About"))}
    }
}
