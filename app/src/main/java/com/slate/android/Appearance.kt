// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal enum class SlateTheme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED") }

internal enum class SlateAccent(val label:Int,val light:Color,val dark:Color){
    MINT(R.string.accent_mint,Color(0xFF246B54),Color(0xFFA7E8CF)),
    VIOLET(R.string.accent_violet,Color(0xFF7051A2),Color(0xFFD1BCFF)),
    BLUE(R.string.accent_blue,Color(0xFF315DAA),Color(0xFFB0C9FF)),
    CYAN(R.string.accent_cyan,Color(0xFF006879),Color(0xFF80D5E6)),
    ROSE(R.string.accent_rose,Color(0xFF9C4166),Color(0xFFFFB0CD)),
    AMBER(R.string.accent_amber,Color(0xFF805600),Color(0xFFFFC76A)),
    GREEN(R.string.accent_green,Color(0xFF41682F),Color(0xFFACD796)),
    GRAPHITE(R.string.accent_graphite,Color(0xFF515F72),Color(0xFFBBC8DC))
}
private fun ColorScheme.withAccent(accent:SlateAccent,dark:Boolean):ColorScheme{
    if(accent==SlateAccent.MINT)return this
    val tone=if(dark)accent.dark else accent.light
    val neutral=if(dark)Color(0xFF131416)else Color(0xFFFAFAFC)
    val foreground=if(dark)Color(0xFFE5E3E9)else Color(0xFF1B1B20)
    val container=lerp(neutral,tone,if(dark).22f else .15f)
    val secondaryTone=lerp(tone,if(dark)Color(0xFFD0D0D8)else Color(0xFF555562),.52f)
    return copy(primary=tone,onPrimary=if(dark)Color(0xFF18141E)else Color.White,
        primaryContainer=container,onPrimaryContainer=if(dark)tone else accent.light,
        secondary=secondaryTone,onSecondary=if(dark)Color(0xFF1C1B20)else Color.White,
        secondaryContainer=lerp(neutral,tone,.14f),onSecondaryContainer=foreground,
        tertiary=tone,onTertiary=if(dark)Color(0xFF18141E)else Color.White,
        tertiaryContainer=container,onTertiaryContainer=foreground,
        background=neutral,surface=neutral,onBackground=foreground,onSurface=foreground,
        surfaceVariant=lerp(neutral,tone,.12f),
        onSurfaceVariant=if(dark)Color(0xFFC8C5CF)else Color(0xFF4A4953),
        surfaceContainer=lerp(neutral,tone,if(dark).075f else .055f),
        surfaceContainerLow=lerp(neutral,tone,.035f),surfaceContainerLowest=neutral,
        surfaceContainerHigh=lerp(neutral,tone,.11f),surfaceContainerHighest=lerp(neutral,tone,.16f),
        surfaceTint=tone,outlineVariant=lerp(neutral,tone,if(dark).25f else .29f))
}

@Composable
internal fun SlateAppearance(mode: SlateTheme, dynamic: Boolean, accent:SlateAccent, activity: ComponentActivity, content: @Composable () -> Unit) {
    val dark = when (mode) { SlateTheme.SYSTEM -> isSystemInDarkTheme(); SlateTheme.LIGHT -> false; else -> true }
    val context = LocalContext.current
    val base = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFA7E8CF), onPrimary = Color(0xFF10372B), primaryContainer = Color(0xFF264D40), onPrimaryContainer = Color(0xFFC1F6E0), background = Color(0xFF111514), surface = Color(0xFF111514), surfaceContainer = Color(0xFF202624), surfaceContainerHigh = Color(0xFF292F2D), onSurface = Color(0xFFE1E4E1), onSurfaceVariant = Color(0xFFBFC9C3), outlineVariant = Color(0xFF404A45))
    else lightColorScheme(primary = Color(0xFF246B54), onPrimary = Color.White, primaryContainer = Color(0xFFBDEED7), onPrimaryContainer = Color(0xFF083B2A), background = Color(0xFFF8FAF7), surface = Color(0xFFF8FAF7), surfaceContainer = Color(0xFFE9EFEA), surfaceContainerHigh = Color(0xFFE0E8E2), onSurface = Color(0xFF18211D), onSurfaceVariant = Color(0xFF46554E), outlineVariant = Color(0xFFC1CDC5))
    val palette=if(dynamic&&Build.VERSION.SDK_INT>=31)base else base.withAccent(accent,dark)
    val colors = if (mode == SlateTheme.AMOLED) palette.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest=Color.Black,surfaceContainerLow=Color(0xFF101012),surfaceContainer = Color(0xFF141416), surfaceContainerHigh = Color(0xFF202024)) else palette
    SideEffect {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun ThemeOptions(mode: SlateTheme, dynamic: Boolean, accent:SlateAccent, onMode: (SlateTheme) -> Unit, onDynamic: (Boolean) -> Unit, onAccent:(SlateAccent)->Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SlateTheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    val selected = mode == option
                    OutlinedCard(onClick = { onMode(option) }, modifier = Modifier.weight(1f),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val preview = when(option) { SlateTheme.LIGHT -> Color(0xFFF2F5F3); SlateTheme.AMOLED -> Color.Black; SlateTheme.DARK -> Color(0xFF202624); else -> Color(0xFF687771) }
                            Column(Modifier.fillMaxWidth().height(54.dp).background(preview, RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.fillMaxWidth(.6f).height(5.dp).background((if(option==SlateTheme.LIGHT)accent.light else accent.dark), RoundedCornerShape(3.dp)))
                                Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFF829C91).copy(alpha=.55f), RoundedCornerShape(4.dp)))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr(option.label), modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.accent_title),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SlateAccent.entries.chunked(4).forEach{choices->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                choices.forEach{option->
                    val selected=accent==option&&!dynamic
                    Column(Modifier.weight(1f).selectable(selected=selected,role=Role.RadioButton,onClick={onDynamic(false);onAccent(option)}).padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Surface(Modifier.size(42.dp),shape=CircleShape,color=option.dark,border=if(selected)BorderStroke(2.dp,MaterialTheme.colorScheme.onSurface)else null){
                            Box(contentAlignment=Alignment.Center){if(selected)Icon(Icons.Default.Check,null,tint=Color(0xFF1B1722),modifier=Modifier.size(20.dp))}
                        }
                        Text(stringResource(option.label),style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) ListItem(headlineContent = { Text(tr("Dynamic colors")) }, supportingContent = { Text(tr("Use your wallpaper colors")) }, trailingContent = { Switch(checked = dynamic, onCheckedChange = onDynamic) })
    }
}

@Composable
private fun SetupFeature(icon:Int,title:String,subtitle:String){
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
        Surface(Modifier.size(46.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer){
            Box(contentAlignment=Alignment.Center){Icon(painterResource(icon),null,tint=MaterialTheme.colorScheme.onSecondaryContainer,modifier=Modifier.size(24.dp))}
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
internal fun Onboarding(granted:Boolean,request:()->Unit,mode:SlateTheme,dynamic:Boolean,accent:SlateAccent,onMode:(SlateTheme)->Unit,onDynamic:(Boolean)->Unit,onAccent:(SlateAccent)->Unit,finish:()->Unit){
    var page by androidx.compose.runtime.saveable.rememberSaveable{mutableIntStateOf(0)}
    BackHandler(page>0){page--}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing){padding->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter){
            Column(Modifier.widthIn(max=520.dp).fillMaxSize().padding(horizontal=22.dp,vertical=10.dp)){
                Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
                    if(page>0)IconButton(onClick={page--}){Icon(Icons.Default.ArrowBack,tr("Back"))}
                    else Image(painterResource(R.drawable.ic_slate),null,Modifier.size(34.dp))
                    Spacer(Modifier.weight(1f))
                    repeat(3){index->
                        Box(Modifier.padding(horizontal=3.dp).width(if(index==page)23.dp else 7.dp).height(7.dp).background(if(index<=page)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,CircleShape))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    Spacer(Modifier.height(12.dp))
                    when(page){
                        0->{
                            Image(painterResource(R.drawable.ic_slate),null,Modifier.size(88.dp))
                            Text(tr("Welcome to Slate"),style=MaterialTheme.typography.headlineLarge)
                            Text(tr("Understand your storage and device in one place."),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(Modifier.padding(vertical=4.dp))
                            SetupFeature(R.drawable.ic_device_storage,tr("Storage analyzer"),tr("Find large files and explore folders."))
                            SetupFeature(R.drawable.ic_device_cpu,tr("Device information"),tr("Hardware, battery, display and Android."))
                            SetupFeature(R.drawable.ic_storage_folder,stringResource(R.string.setup_files),stringResource(R.string.setup_files_desc))
                            Text(tr("Your files and measurements stay on your device."),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                        }
                        1->{
                            Text(tr("Permissions"),style=MaterialTheme.typography.headlineLarge)
                            Text(tr("Only grant the access needed for the tools you use."),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                                Column(Modifier.padding(8.dp)){
                                    ListItem(headlineContent={Text(tr("Storage access"))},supportingContent={Text(tr(if(granted)"Granted"else"Required to scan shared files and folders."))},leadingContent={Icon(painterResource(R.drawable.ic_device_storage),null,Modifier.size(24.dp))},trailingContent={if(granted)Icon(Icons.Default.Check,tr("Granted"),tint=MaterialTheme.colorScheme.primary)else TextButton(onClick=request){Text(tr("Allow"))}},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainer))
                                    HorizontalDivider(Modifier.padding(horizontal=12.dp))
                                    com.slate.android.storage.ShizukuAccessRow()
                                }
                            }
                            SetupFeature(R.drawable.ic_device_cpu,tr("Device information"),tr("No additional permission required."))
                            Text(tr("You can continue without storage access and enable it later."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else->{
                            Text(tr("Make it yours"),style=MaterialTheme.typography.headlineLarge)
                            Text(tr("Choose your appearance. You can change it in Settings."),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            ThemeOptions(mode,dynamic,accent,onMode,onDynamic,onAccent)
                        }
                    }
                }
                Button(onClick={if(page==2)finish()else page++},modifier=Modifier.fillMaxWidth().padding(top=8.dp).height(52.dp)){Text(tr(if(page==2)"Finish setup"else"Continue"));Spacer(Modifier.width(8.dp));Icon(if(page==2)Icons.Default.Check else Icons.Default.KeyboardArrowRight,null)}
            }
        }
    }
}
