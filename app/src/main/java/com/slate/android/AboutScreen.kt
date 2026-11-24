// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PROJECT="https://github.com/stevenmx2/Slate"
private data class Credit(val title:String,val subtitle:String,val url:String)
@Composable
internal fun AboutScreen(onBack:()->Unit){
    val context=LocalContext.current
    val info=remember{context.packageManager.getPackageInfo(context.packageName,0)}
    @Suppress("DEPRECATION")
    val build=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    val version=info.versionName.orEmpty()
    var document by rememberSaveable{mutableStateOf<String?>(null)}
    var failed by remember{mutableStateOf(false)}
    fun open(url:String){failed=runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.isFailure}
    BackHandler{if(document!=null)document=null else onBack()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(document!=null)document=null else onBack()}){Icon(Icons.Default.ArrowBack,tr("Back"))}
            Text(stringResource(when(document){"license"->R.string.about_license;"credits"->R.string.about_credits;else->R.string.about_title}),style=MaterialTheme.typography.headlineSmall)
        }
        if(document!=null){
            val doc=document
            val body by produceState<String?>(null,doc){
                value=withContext(Dispatchers.IO){runCatching{context.assets.open(if(doc=="license")"about/license.txt" else "about/credits.md").bufferedReader().use{it.readText()}}.getOrNull()}
            }
            Column(Modifier.fillMaxSize().padding(horizontal=18.dp).verticalScroll(rememberScrollState())){
                Text(body?:stringResource(R.string.about_loading),style=MaterialTheme.typography.bodySmall)
            }
        }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)){
                Image(painterResource(R.drawable.ic_slate),null,Modifier.size(84.dp))
                Text("Slate",style=MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.about_version,version,build),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.secondaryContainer){
                    Text("GPL-3.0-only",Modifier.padding(horizontal=12.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
                }
            }
            Text(stringResource(R.string.about_project),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column{
                    AboutLink("GitHub",stringResource(R.string.about_source)){open(PROJECT)}
                    HorizontalDivider(Modifier.padding(horizontal=16.dp))
                    AboutLink(stringResource(R.string.about_report),stringResource(R.string.about_report_desc)){open(PROJECT+"/issues")}
                    HorizontalDivider(Modifier.padding(horizontal=16.dp))
                    AboutLink(stringResource(R.string.about_license),"GNU General Public License v3"){document="license"}
                }
            }
            Text(stringResource(R.string.about_credits),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
            val credits=listOf(
                Credit("SD Maid SE",stringResource(R.string.about_reference),"https://github.com/d4rken-org/sdmaid-se"),
                Credit("App Manager",stringResource(R.string.about_reference),"https://github.com/MuntashirAkon/AppManager"),
                Credit("Shizuku API","RikkaApps · Apache-2.0","https://github.com/RikkaApps/Shizuku-API"),
                Credit("AndroidHiddenApiBypass","LSPosed · Apache-2.0","https://github.com/LSPosed/AndroidHiddenApiBypass"),
                Credit("Kotlin & Jetpack Compose","Apache-2.0","https://developer.android.com/jetpack/compose"),
                Credit("Simple Icons","CC0 · "+stringResource(R.string.about_marks),"https://github.com/simple-icons/simple-icons")
            )
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                Column{
                    credits.forEachIndexed{index,credit->
                        if(index>0)HorizontalDivider(Modifier.padding(horizontal=16.dp))
                        AboutLink(credit.title,credit.subtitle){open(credit.url)}
                    }
                    HorizontalDivider(Modifier.padding(horizontal=16.dp))
                    AboutLink(stringResource(R.string.about_full_credits),stringResource(R.string.about_notices)){document="credits"}
                }
            }
            OutlinedButton(onClick={
                val clipboard=context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Slate","Slate "+version+" ("+build+")\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")"))
            },modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.about_copy))}
            if(failed)Text(stringResource(R.string.about_link_failed),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.about_warranty),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
        }
    }
}
@Composable
private fun AboutLink(title:String,subtitle:String,onClick:()->Unit){
    ListItem(modifier=Modifier.clickable(onClick=onClick),headlineContent={Text(title)},supportingContent={Text(subtitle)},trailingContent={Icon(Icons.Default.KeyboardArrowRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
}
