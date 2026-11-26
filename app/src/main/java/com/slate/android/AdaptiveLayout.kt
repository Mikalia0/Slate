// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable internal fun useNavigationRail():Boolean {
    val c=LocalConfiguration.current
    return c.screenWidthDp>=840 || (c.screenWidthDp>=600 && c.screenHeightDp>=480 && c.screenWidthDp>c.screenHeightDp)
}
@Composable internal fun AdaptiveContent(maxWidth:Int=900,content:@Composable ()->Unit){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        Box(Modifier.widthIn(max=maxWidth.dp).fillMaxSize()){content()}
    }
}
@Composable internal fun AdaptiveSettings(labels:List<String>,onBack:()->Unit,sections:List<@Composable ()->Unit>){
    var selected by rememberSaveable{mutableIntStateOf(0)}
    BoxWithConstraints(Modifier.fillMaxSize()){
        if(maxWidth>=720.dp){
            Row(Modifier.fillMaxSize()){
                Surface(Modifier.width(232.dp).fillMaxHeight(),color=MaterialTheme.colorScheme.surfaceContainerLow){
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        TextButton(onClick=onBack){Text("‹  "+tr("Settings"),style=MaterialTheme.typography.titleMedium)}
                        labels.forEachIndexed{index,label->
                            NavigationDrawerItem(label={Text(label)},selected=selected==index,onClick={selected=index})
                        }
                    }
                }
                VerticalDivider()
                key(selected){
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Text(labels[selected],style=MaterialTheme.typography.headlineSmall)
                        sections[selected]()
                    }
                }
            }
        }else{
            Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                TextButton(onClick=onBack){Text("‹  "+tr("Settings"),style=MaterialTheme.typography.headlineSmall)}
                sections.forEachIndexed{index,section->if(index>0)HorizontalDivider();section()}
            }
        }
    }
}
