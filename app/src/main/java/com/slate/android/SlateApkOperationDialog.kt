// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class ApkWorkStage(val en:String,val pt:String){
    PREPARING("Preparing…","Preparando…"),
    READING("Reading APK…","Lendo APK…"),
    EDITING("Updating manifest…","Atualizando manifesto…"),
    REBUILDING("Rebuilding archive…","Reconstruindo arquivo…"),
    SIGNING("Signing APK…","Assinando APK…"),
    VERIFYING("Verifying APK…","Verificando APK…"),
    SAVING("Saving output…","Salvando resultado…"),
    COPYING("Copying files…","Copiando arquivos…"),
    EXTRACTING("Extracting files…","Extraindo arquivos…")
}

@Composable internal fun ApkOperationDialog(title:String,fileName:String,stage:ApkWorkStage,result:String?,failed:Boolean=false,onClose:()->Unit){
    val pt=LocalConfiguration.current.locales[0].language=="pt"
    AlertDialog(
        onDismissRequest={if(result!=null)onClose()},
        title={Text(title)},
        text={
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(fileName,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                if(result==null){
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if(pt)stage.pt else stage.en,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }else{
                    Text(result,color=if(failed)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton={if(result!=null)TextButton(onClick=onClose){Text(tr("Close"))}}
    )
}
