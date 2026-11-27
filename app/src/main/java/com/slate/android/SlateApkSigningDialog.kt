// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ApkAutoSignToggle(checked:Boolean,onChange:(Boolean)->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(stringResource(R.string.apk_clone_auto_sign),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(checked=checked,onCheckedChange=onChange,enabled=enabled)
    }
}
@Composable private fun SigningChoice(label:String,value:String,choices:List<Pair<String,String>>,onChoice:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth()){
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()){
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
                Text(value,Modifier.weight(1f));Icon(Icons.Default.KeyboardArrowDown,null)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                choices.forEach{(id,title)->DropdownMenuItem(text={Text(title)},onClick={expanded=false;onChoice(id)})}
            }
        }
    }
}
@Composable internal fun ApkSignDialog(pkg:String?,onClose:()->Unit,onSign:(ApkSignOptions)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var options by remember(pkg){mutableStateOf(apkSignOptions(context,pkg))}
    var rememberOptions by remember(pkg){mutableStateOf(hasApkSignOptions(context,pkg))}
    var customName by remember(pkg){mutableStateOf(options.v1Name!="Slate")}
    var manage by remember{mutableStateOf(false)}
    var revision by remember{mutableIntStateOf(0)}
    val keys by produceState(listOf(ApkSigningKey(DEFAULT_APK_KEY,"")),revision){value=withContext(Dispatchers.IO){runCatching{listApkSigningKeys(context)}.getOrDefault(listOf(ApkSigningKey(DEFAULT_APK_KEY,"")))}}
    val defaultLabel=stringResource(R.string.apk_sign_default)
    fun label(key:ApkSigningKey)=if(key.id==DEFAULT_APK_KEY)defaultLabel else key.label
    if(manage){
        ApkKeyManager(keys,onClose={manage=false},onChanged={id->revision++;if(id!=null)options=options.copy(key=id)else if(options.key!=DEFAULT_APK_KEY)options=options.copy(key=DEFAULT_APK_KEY)})
        return
    }
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.apk_sign))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
            SigningChoice(stringResource(R.string.apk_sign_key),keys.firstOrNull{it.id==options.key}?.let{label(it)}?:options.key,keys.map{it.id to label(it)}){options=options.copy(key=it)}
            SigningChoice(stringResource(R.string.apk_sign_scheme),options.scheme.title,ApkScheme.entries.map{it.name to it.title}){options=options.copy(scheme=ApkScheme.valueOf(it))}
            if(options.scheme.v1){
                Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.apk_sign_custom_v1),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(customName,{customName=it;if(!it)options=options.copy(v1Name="Slate")})}
                if(customName)OutlinedTextField(options.v1Name,{options=options.copy(v1Name=it)},singleLine=true,isError=!validV1Name(options.v1Name),label={Text(stringResource(R.string.apk_sign_v1_name))})
            }
            if(pkg!=null)Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(stringResource(R.string.apk_sign_remember),style=MaterialTheme.typography.bodyMedium);Text(pkg,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Switch(rememberOptions,{rememberOptions=it})
            }
            Text(stringResource(R.string.apk_sign_identity_warning),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={manage=true}){Text(stringResource(R.string.apk_sign_manage))}
        }
    },confirmButton={TextButton(enabled=keys.any{it.id==options.key}&&validV1Name(options.v1Name),onClick={
        if(pkg!=null)rememberApkSignOptions(context,pkg,if(rememberOptions)options else null)
        onSign(options)
    }){Text(stringResource(R.string.apk_sign))}},dismissButton={TextButton(onClick=onClose){Text(tr("Cancel"))}})
}
@Composable private fun ApkKeyManager(keys:List<ApkSigningKey>,onClose:()->Unit,onChanged:(String?)->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var uri by remember{mutableStateOf<Uri?>(null)}
    var password by remember{mutableStateOf("")}
    var alias by remember{mutableStateOf("")}
    var name by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var pendingDelete by remember{mutableStateOf<ApkSigningKey?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri=it;error=null}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text(stringResource(R.string.apk_sign_manage))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            keys.forEach{key->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(if(key.id==DEFAULT_APK_KEY)stringResource(R.string.apk_sign_default)else key.label,Modifier.weight(1f))
                    if(key.id!=DEFAULT_APK_KEY)TextButton(enabled=!busy,onClick={pendingDelete=key}){Text(tr("Delete"))}
                }
            }
            HorizontalDivider()
            TextButton(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text(stringResource(if(uri==null)R.string.apk_choose_key else R.string.apk_key_selected))}
            if(uri!=null){
                OutlinedTextField(name,{name=it},singleLine=true,label={Text(stringResource(R.string.apk_sign_key))})
                OutlinedTextField(password,{password=it},singleLine=true,visualTransformation=PasswordVisualTransformation(),label={Text(stringResource(R.string.apk_password))})
                OutlinedTextField(alias,{alias=it},singleLine=true,label={Text(stringResource(R.string.apk_alias))})
                Button(enabled=!busy,onClick={
                    val selected=checkNotNull(uri);val secret=password.toCharArray();password="";val a=alias;val n=name
                    busy=true;error=null
                    scope.launch{
                        try{val id=withContext(Dispatchers.IO){importApkSigningKey(context,selected,secret,a,n)};uri=null;alias="";name="";onChanged(id)}
                        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:context.getString(R.string.slate_tool_failed)}
                        finally{secret.fill('\u0000');busy=false}
                    }
                }){Text(stringResource(R.string.apk_sign_import))}
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onClose){Text(tr("Close"))}})
    pendingDelete?.let{key->AlertDialog(onDismissRequest={pendingDelete=null},title={Text(tr("Delete"))},text={Text(key.label)},confirmButton={TextButton(onClick={
        pendingDelete=null;busy=true
        scope.launch{try{withContext(Dispatchers.IO){removeApkSigningKey(context,key.id)};onChanged(null)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}
    }){Text(tr("Delete"))}},dismissButton={TextButton(onClick={pendingDelete=null}){Text(tr("Cancel"))}})}
}
