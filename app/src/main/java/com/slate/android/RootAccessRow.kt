// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.slate.android.storage.verifyRootDataAccess
import kotlinx.coroutines.launch

@Composable
internal fun RootAccessRow(authorized:Boolean,onChanged:(Boolean)->Unit){
    val scope=rememberCoroutineScope()
    var checking by remember{mutableStateOf(false)}
    var denied by remember{mutableStateOf(false)}
    ListItem(headlineContent={Text(stringResource(R.string.root_access_title))},
        supportingContent={Text(stringResource(when{
            checking->R.string.root_access_checking
            authorized->R.string.root_access_granted
            denied->R.string.root_data_denied
            else->R.string.root_access_optional
        }))},
        trailingContent={
            TextButton(enabled=!checking,onClick={
                if(authorized){onChanged(false);denied=false}
                else{checking=true;denied=false;scope.launch{
                    try{val allowed=verifyRootDataAccess();onChanged(allowed);denied=!allowed}
                    finally{checking=false}
                }}
            }){Text(stringResource(if(authorized)R.string.root_access_disable else R.string.root_access_allow))}
        })
}
