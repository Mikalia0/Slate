// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

internal class ExclusionStore(context:Context) {
    private val prefs=context.getSharedPreferences("storage_exclusions",Context.MODE_PRIVATE)
    var paths by mutableStateOf(prefs.getStringSet("paths",emptySet()).orEmpty().toSet())
        private set
    fun add(raw:String):Boolean {
        val path=raw.trim().trimEnd('/')
        if(path.isEmpty() || path.startsWith("/") || path.split('/').any{it.isBlank()||it=="."||it==".."})return false
        if(paths.size>=200)return false
        paths=paths+path;save();return true
    }
    fun remove(path:String){paths=paths-path;save()}
    private fun save(){prefs.edit().putStringSet("paths",paths.toSet()).apply()}
    fun absolute():Set<File> = paths.map{File(Environment.getExternalStorageDirectory(),it)}.toSet()
}
internal fun isExcluded(file:File,excluded:Set<File>):Boolean =
    excluded.any{file.toPath().normalize().startsWith(it.toPath().normalize())}

@Composable
internal fun ExclusionEditor(store:ExclusionStore,onChanged:()->Unit) {
    var input by remember{mutableStateOf("")}
    var invalid by remember{mutableStateOf(false)}
    var expanded by remember{mutableStateOf(false)}
    Text("Storage exclusions",style=MaterialTheme.typography.titleMedium)
    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide exclusions" else "Manage exclusions (${store.paths.size})")}
    if(expanded) {
        Text("Paths relative to internal storage.",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(input,{input=it;invalid=false},modifier=Modifier.weight(1f),singleLine=true,isError=invalid,placeholder={Text("Download/example")})
            TextButton(onClick={if(store.add(input)){input="";invalid=false;onChanged()}else invalid=true}){Text("Add")}
        }
        if(invalid)Text("Enter a valid relative folder or file path.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        store.paths.sorted().forEach{path->
            ListItem(headlineContent={Text(path)},trailingContent={IconButton(onClick={store.remove(path);onChanged()}){Icon(Icons.Default.Delete,"Remove exclusion")}})
        }
    }
}
