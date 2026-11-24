// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

internal class DashboardPreferences(context:Context) {
    private val prefs=context.getSharedPreferences("dashboard",Context.MODE_PRIVATE)
    private val defaults=setOf("device","tools","insights","external")
    var visible by mutableStateOf(prefs.getStringSet("visible",defaults).orEmpty().toSet())
        private set
    fun set(key:String,value:Boolean){
        visible=if(value)visible+key else visible-key
        prefs.edit().putStringSet("visible",visible.toSet()).apply()
    }
}
@Composable
internal fun DashboardSettings(prefs:DashboardPreferences){
    Text("Home dashboard",style=MaterialTheme.typography.titleMedium)
    listOf("device" to "Device status","tools" to "Quick tools","insights" to "Storage insights","external" to "External storage").forEach{(key,label)->
        ListItem(headlineContent={Text(label)},trailingContent={Switch(key in prefs.visible,{prefs.set(key,it)})})
    }
}
