// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val languages=listOf("en" to "English","pt-BR" to "Português (Brasil)","es" to "Español","fr" to "Français","it" to "Italiano","ru" to "Русский")
@Composable
internal fun LanguageSettings(){
    var show by remember{mutableStateOf(false)}
    val locales=AppCompatDelegate.getApplicationLocales()
    val current=locales[0]?.toLanguageTag().orEmpty()
    val label=languages.firstOrNull{it.first==current}?.second
        ?:current.takeIf{it.isNotEmpty()}?.let{tag->languages.firstOrNull{it.first.substringBefore('-')==tag.substringBefore('-')}?.second}
        ?:stringResource(R.string.language_system)
    ListItem(headlineContent={Text(stringResource(R.string.language_title))},supportingContent={Text(label)},trailingContent={TextButton(onClick={show=true}){Text(stringResource(R.string.language_change))}})
    if(show)AlertDialog(onDismissRequest={show=false},title={Text(stringResource(R.string.language_title))},text={
        Column(Modifier.selectableGroup().heightIn(max=420.dp).verticalScroll(rememberScrollState())){
            (listOf("" to stringResource(R.string.language_system))+languages).forEach{(tag,name)->
                val selected=if(tag.isEmpty())current.isEmpty() else current==tag
                Row(Modifier.fillMaxWidth().selectable(selected=selected,role=Role.RadioButton,onClick={
                    show=false
                    AppCompatDelegate.setApplicationLocales(if(tag.isEmpty())LocaleListCompat.getEmptyLocaleList()else LocaleListCompat.forLanguageTags(tag))
                }).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    RadioButton(selected=selected,onClick=null)
                    Text(name)
                }
            }
        }
    },confirmButton={TextButton(onClick={show=false}){Text(tr("Close"))}})
}
