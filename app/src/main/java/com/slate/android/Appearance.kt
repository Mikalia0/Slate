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

// Each theme owns its complete Material 3 palette, not just the primary accent.
internal enum class SlatePalette(val label:Int,val description:Int){
    SLATE(R.string.palette_slate,R.string.palette_slate_desc),
    OCEAN(R.string.palette_ocean,R.string.palette_ocean_desc),
    AURORA(R.string.palette_aurora,R.string.palette_aurora_desc),
    GRAPHITE(R.string.palette_graphite,R.string.palette_graphite_desc),
    WALLPAPER(R.string.palette_wallpaper,R.string.palette_wallpaper_desc)
}
internal fun migratePalette(saved:String?,legacyAccent:String?,legacyDynamic:Boolean):SlatePalette{
    saved?.let{runCatching{SlatePalette.valueOf(it)}.getOrNull()?.let{return it}}
    if(legacyDynamic&&Build.VERSION.SDK_INT>=31)return SlatePalette.WALLPAPER
    return when(legacyAccent){"BLUE","CYAN"->SlatePalette.OCEAN;"VIOLET","ROSE"->SlatePalette.AURORA;"GRAPHITE","AMBER"->SlatePalette.GRAPHITE;else->SlatePalette.SLATE}
}
private data class ThemeTones(val primary:Long,val onPrimary:Long,val primaryContainer:Long,val onPrimaryContainer:Long,val secondary:Long,val onSecondary:Long,val secondaryContainer:Long,val onSecondaryContainer:Long,val tertiary:Long,val onTertiary:Long,val tertiaryContainer:Long,val onTertiaryContainer:Long,val background:Long,val text:Long,val variant:Long,val outline:Long,val dim:Long,val low:Long,val container:Long,val high:Long,val highest:Long,val bright:Long,val lowest:Long)
private fun tones(palette:SlatePalette,dark:Boolean):ThemeTones=when(palette){
    SlatePalette.OCEAN->if(dark)ThemeTones(0xFFAFCBFF,0xFF082F61,0xFF254B7E,0xFFD5E3FF,0xFFB6C8E4,0xFF20334B,0xFF304762,0xFFD7E5FF,0xFF86D7E7,0xFF00363E,0xFF164B55,0xFFA7EDFA,0xFF101722,0xFFE1E8F5,0xFFBFCBDD,0xFF8B9BB2,0xFF101722,0xFF161F2C,0xFF1C2838,0xFF253347,0xFF304055,0xFF354357,0xFF0B111C)
        else ThemeTones(0xFF285DA8,0xFFFFFFFF,0xFFD5E3FF,0xFF102D57,0xFF4B607D,0xFFFFFFFF,0xFFD7E5FA,0xFF192E49,0xFF006878,0xFFFFFFFF,0xFFA7EDFA,0xFF00343D,0xFFF5F8FF,0xFF172334,0xFF435369,0xFF738298,0xFFD6E0EF,0xFFEDF2FA,0xFFE5EBF5,0xFFDAE4F1,0xFFD0DCEC,0xFFF5F8FF,0xFFFFFFFF)
    SlatePalette.AURORA->if(dark)ThemeTones(0xFFD5BCFF,0xFF3B2268,0xFF523782,0xFFF0DEFF,0xFFCFBDE5,0xFF392C4B,0xFF4C3C62,0xFFEEDFFF,0xFFF4B4CC,0xFF55283D,0xFF713E53,0xFFFFD8E7,0xFF18131F,0xFFEAE2F2,0xFFCDC1D9,0xFF998AA9,0xFF18131F,0xFF211A2A,0xFF292135,0xFF342940,0xFF40344E,0xFF41364D,0xFF120E19)
        else ThemeTones(0xFF704BA2,0xFFFFFFFF,0xFFEADDFF,0xFF2D1255,0xFF675477,0xFFFFFFFF,0xFFEADDF5,0xFF2D1D3C,0xFF95496B,0xFFFFFFFF,0xFFFFD8E7,0xFF421529,0xFFFCF7FF,0xFF251C30,0xFF56495F,0xFF84758E,0xFFE7DEEE,0xFFF6EFFB,0xFFF0E7F6,0xFFE9DEF0,0xFFE0D3EB,0xFFFCF7FF,0xFFFFFFFF)
    SlatePalette.GRAPHITE->if(dark)ThemeTones(0xFFD5DBE4,0xFF292E36,0xFF424A56,0xFFF1F4F8,0xFFBEC5CF,0xFF30363E,0xFF3D454F,0xFFE0E5EC,0xFFBAC9D9,0xFF263645,0xFF3A4B5C,0xFFDCEBFA,0xFF131416,0xFFE4E5E8,0xFFC4C7CD,0xFF93979F,0xFF131416,0xFF1A1C1F,0xFF212327,0xFF2B2E33,0xFF363940,0xFF383A3E,0xFF0D0E10)
        else ThemeTones(0xFF454F5C,0xFFFFFFFF,0xFFDEE4EC,0xFF202831,0xFF555D68,0xFFFFFFFF,0xFFE1E5EB,0xFF252B32,0xFF4B6378,0xFFFFFFFF,0xFFDCEBFA,0xFF1D3448,0xFFF8F9FB,0xFF202328,0xFF464B53,0xFF777C85,0xFFDBDDE2,0xFFF0F1F4,0xFFE9EBEF,0xFFE1E4E9,0xFFD9DCE2,0xFFF8F9FB,0xFFFFFFFF)
    else->if(dark)ThemeTones(0xFFA7E8CF,0xFF10372B,0xFF264D40,0xFFC1F6E0,0xFFB5CEC2,0xFF23382F,0xFF354C41,0xFFD0EBDD,0xFFA7CDD8,0xFF153640,0xFF294D59,0xFFC4E9F3,0xFF111514,0xFFE1E4E1,0xFFBFC9C3,0xFF89968E,0xFF111514,0xFF191F1D,0xFF202624,0xFF292F2D,0xFF343C38,0xFF373E3A,0xFF0C100E)
        else ThemeTones(0xFF246B54,0xFFFFFFFF,0xFFBDEED7,0xFF083B2A,0xFF4B6358,0xFFFFFFFF,0xFFD0EBDD,0xFF192E25,0xFF3D6472,0xFFFFFFFF,0xFFC4E9F3,0xFF11343F,0xFFF8FAF7,0xFF18211D,0xFF46554E,0xFF738279,0xFFD7E1DA,0xFFF0F5F0,0xFFE9EFEA,0xFFE0E8E2,0xFFD7E1D9,0xFFF8FAF7,0xFFFFFFFF)
}
private fun staticScheme(palette:SlatePalette,dark:Boolean):ColorScheme{
    val t=tones(palette,dark)
    val reverse=tones(palette,!dark)
    val base=if(dark)darkColorScheme()else lightColorScheme()
    return base.copy(
        primary=Color(t.primary),onPrimary=Color(t.onPrimary),primaryContainer=Color(t.primaryContainer),onPrimaryContainer=Color(t.onPrimaryContainer),inversePrimary=Color(reverse.primary),
        secondary=Color(t.secondary),onSecondary=Color(t.onSecondary),secondaryContainer=Color(t.secondaryContainer),onSecondaryContainer=Color(t.onSecondaryContainer),
        tertiary=Color(t.tertiary),onTertiary=Color(t.onTertiary),tertiaryContainer=Color(t.tertiaryContainer),onTertiaryContainer=Color(t.onTertiaryContainer),
        background=Color(t.background),onBackground=Color(t.text),surface=Color(t.background),onSurface=Color(t.text),
        surfaceVariant=Color(t.highest),onSurfaceVariant=Color(t.variant),surfaceTint=Color(t.primary),
        inverseSurface=Color(reverse.background),inverseOnSurface=Color(reverse.text),outline=Color(t.outline),outlineVariant=lerp(Color(t.background),Color(t.outline),.42f),
        surfaceDim=Color(t.dim),surfaceBright=Color(t.bright),surfaceContainerLowest=Color(t.lowest),
        surfaceContainerLow=Color(t.low),surfaceContainer=Color(t.container),surfaceContainerHigh=Color(t.high),surfaceContainerHighest=Color(t.highest)
    )
}
internal fun themeUsesAmoled(mode:SlateTheme,systemDark:Boolean):Boolean =
    mode==SlateTheme.AMOLED||(mode==SlateTheme.SYSTEM&&systemDark)

private fun ColorScheme.amoled():ColorScheme=copy(
    background=Color.Black,surface=Color.Black,surfaceDim=Color.Black,surfaceContainerLowest=Color.Black,
    surfaceContainerLow=lerp(Color.Black,surfaceContainerLow,.45f),
    surfaceContainer=lerp(Color.Black,surfaceContainer,.62f),
    surfaceContainerHigh=lerp(Color.Black,surfaceContainerHigh,.72f),
    surfaceContainerHighest=lerp(Color.Black,surfaceContainerHighest,.78f)
)
@Composable
private fun paletteScheme(palette:SlatePalette,dark:Boolean,amoled:Boolean=false):ColorScheme{
    val context=LocalContext.current
    val scheme=if(palette==SlatePalette.WALLPAPER&&Build.VERSION.SDK_INT>=31){
        if(dark)dynamicDarkColorScheme(context)else dynamicLightColorScheme(context)
    }else staticScheme(palette,dark)
    return if(amoled)scheme.amoled()else scheme
}
@Composable
internal fun SlateAppearance(mode:SlateTheme,palette:SlatePalette,activity:ComponentActivity,content:@Composable ()->Unit){
    val dark=when(mode){SlateTheme.SYSTEM->isSystemInDarkTheme();SlateTheme.LIGHT->false;else->true}
    val colors=paletteScheme(palette,dark,themeUsesAmoled(mode,dark))
    SideEffect{
        val style=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
    }
    MaterialTheme(colorScheme=colors){Surface(color=colors.background,modifier=Modifier.fillMaxSize()){content()}}
}

@Composable
private fun ThemePreview(scheme:ColorScheme){
    // Miniature application surface uses exactly the same roles as the actual UI.
    Column(Modifier.fillMaxWidth().height(100.dp).background(scheme.background,RoundedCornerShape(9.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Box(Modifier.size(12.dp).background(scheme.primary,CircleShape))
            Box(Modifier.weight(1f).height(6.dp).background(scheme.onSurface.copy(alpha=.75f),RoundedCornerShape(3.dp)))
            Box(Modifier.size(9.dp).background(scheme.onSurfaceVariant.copy(alpha=.7f),CircleShape))
        }
        Column(Modifier.fillMaxWidth().weight(1f).background(scheme.surfaceContainer,RoundedCornerShape(6.dp)).padding(7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Box(Modifier.fillMaxWidth(.54f).height(5.dp).background(scheme.onSurfaceVariant,RoundedCornerShape(3.dp)))
            Box(Modifier.fillMaxWidth().height(6.dp).background(scheme.surfaceContainerHighest,RoundedCornerShape(3.dp))){
                Box(Modifier.fillMaxWidth(.63f).fillMaxHeight().background(scheme.primary,RoundedCornerShape(3.dp)))
            }
            Box(Modifier.fillMaxWidth(.85f).height(8.dp).background(scheme.secondaryContainer,RoundedCornerShape(4.dp)))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
            Box(Modifier.weight(1f).height(7.dp).background(scheme.primary,RoundedCornerShape(4.dp)))
            Box(Modifier.weight(1f).height(7.dp).background(scheme.tertiary,RoundedCornerShape(4.dp)))
            Box(Modifier.weight(1f).height(7.dp).background(scheme.surfaceContainerHigh,RoundedCornerShape(4.dp)))
        }
    }
}
@Composable
internal fun ThemeOptions(mode:SlateTheme,palette:SlatePalette,onMode:(SlateTheme)->Unit,onPalette:(SlatePalette)->Unit){
    val dark=when(mode){SlateTheme.SYSTEM->isSystemInDarkTheme();SlateTheme.LIGHT->false;else->true}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(stringResource(R.string.palette_mode),style=MaterialTheme.typography.titleSmall)
        SlateTheme.entries.chunked(2).forEach{row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                row.forEach{option->
                    FilterChip(selected=mode==option,onClick={onMode(option)},label={Text(tr(option.label))},modifier=Modifier.weight(1f),leadingIcon=if(mode==option){{Icon(Icons.Default.Check,null,Modifier.size(16.dp))}}else null)
                }
            }
        }
        if(mode==SlateTheme.SYSTEM)Text(stringResource(R.string.setup_system_amoled),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.palette_title),style=MaterialTheme.typography.titleSmall)
        SlatePalette.entries.filter{it!=SlatePalette.WALLPAPER}.chunked(2).forEach{row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                row.forEach{option->
                    val selected=palette==option
                    val scheme=paletteScheme(option,dark,themeUsesAmoled(mode,dark))
                    OutlinedCard(onClick={onPalette(option)},modifier=Modifier.weight(1f),
                        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer),
                        border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
                        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                            ThemePreview(scheme)
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Text(stringResource(option.label),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                if(selected)Icon(Icons.Default.Check,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                            }
                            Text(stringResource(option.description),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if(Build.VERSION.SDK_INT>=31){
            val selected=palette==SlatePalette.WALLPAPER
            val scheme=paletteScheme(SlatePalette.WALLPAPER,dark,themeUsesAmoled(mode,dark))
            OutlinedCard(onClick={onPalette(SlatePalette.WALLPAPER)},modifier=Modifier.fillMaxWidth(),border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        listOf(scheme.primary,scheme.secondary,scheme.tertiary).forEach{color->Box(Modifier.size(17.dp).background(color,CircleShape))}
                    }
                    Column(Modifier.weight(1f)){
                        Text(stringResource(R.string.palette_wallpaper),style=MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.palette_wallpaper_desc),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(selected)Icon(Icons.Default.Check,null,tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SetupFeature(icon:Int,title:String,subtitle:String){
    Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(Modifier.size(42.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer){
            Box(contentAlignment=Alignment.Center){Icon(painterResource(icon),null,tint=MaterialTheme.colorScheme.onSecondaryContainer,modifier=Modifier.size(22.dp))}
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun SetupTheme(mode:SlateTheme,palette:SlatePalette,onMode:(SlateTheme)->Unit,onPalette:(SlatePalette)->Unit){
    val systemDark=isSystemInDarkTheme()
    val dark=when(mode){SlateTheme.SYSTEM->systemDark;SlateTheme.LIGHT->false;else->true}
    val scheme=paletteScheme(palette,dark,themeUsesAmoled(mode,systemDark))
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedCard(Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                ThemePreview(scheme)
                Text(stringResource(R.string.setup_live_preview),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.palette_mode),style=MaterialTheme.typography.titleSmall)
        SlateTheme.entries.chunked(2).forEach{options->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                options.forEach{option->
                    FilterChip(selected=option==mode,onClick={onMode(option)},label={Text(tr(option.label))},modifier=Modifier.weight(1f),leadingIcon=if(option==mode){{Icon(Icons.Default.Check,null,Modifier.size(16.dp))}}else null)
                }
            }
        }
        if(mode==SlateTheme.SYSTEM)Text(stringResource(R.string.setup_system_amoled),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.palette_title),style=MaterialTheme.typography.titleSmall)
        SlatePalette.entries.filter{it!=SlatePalette.WALLPAPER||Build.VERSION.SDK_INT>=31}.chunked(2).forEach{options->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                options.forEach{option->
                    val preview=paletteScheme(option,dark,themeUsesAmoled(mode,systemDark))
                    FilterChip(selected=option==palette,onClick={onPalette(option)},modifier=Modifier.weight(1f),label={Text(stringResource(option.label),maxLines=2)},leadingIcon={Box(Modifier.size(14.dp).background(preview.primary,CircleShape))})
                }
                if(options.size==1)Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Onboarding(granted:Boolean,request:()->Unit,mode:SlateTheme,palette:SlatePalette,onMode:(SlateTheme)->Unit,onPalette:(SlatePalette)->Unit,privilege:PrivilegeMode,onPrivilege:(PrivilegeMode)->Unit,rootAuthorized:Boolean,onRootChanged:(Boolean)->Unit,finish:()->Unit){
    var page by androidx.compose.runtime.saveable.rememberSaveable{mutableIntStateOf(0)}
    BackHandler(page>0){page--}
    val title=when(page){0->tr("Welcome to Slate");1->tr("Permissions");else->tr("Make it yours")}
    val subtitle=when(page){0->tr("Understand your storage and device in one place.");1->tr("Only grant the access needed for the tools you use.");else->tr("Choose your appearance. You can change it in Settings.")}
    val hero:@Composable (Boolean)->Unit={wide->
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(if(wide)10.dp else 12.dp)){
            if(page==0){
                Surface(Modifier.size(if(wide)68.dp else 84.dp),shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainer){
                    Box(contentAlignment=Alignment.Center){Image(painterResource(R.drawable.ic_slate),null,Modifier.size(if(wide)48.dp else 60.dp))}
                }
            }
            Text(title,style=if(wide)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
            Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(page==0&&wide)Text(tr("Your files and measurements stay on your device."),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        }
    }
    val body:@Composable (Boolean)->Unit={wide->
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
            when(page){
                0->{
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
                        Column(Modifier.padding(horizontal=14.dp,vertical=7.dp)){
                            SetupFeature(R.drawable.ic_device_storage,tr("Storage analyzer"),tr("Find large files and explore folders."))
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            SetupFeature(R.drawable.ic_device_cpu,tr("Device information"),tr("Hardware, battery, display and Android."))
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            SetupFeature(R.drawable.ic_storage_folder,stringResource(R.string.setup_files),stringResource(R.string.setup_files_desc))
                        }
                    }
                    if(!wide)Text(tr("Your files and measurements stay on your device."),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                }
                1->{
                    OutlinedCard(Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            Icon(painterResource(R.drawable.ic_device_storage),null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(tr("Storage access"),style=MaterialTheme.typography.titleSmall)
                                Text(tr(if(granted)"Granted"else"Required to scan shared files and folders."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(granted)Icon(Icons.Default.Check,tr("Granted"),tint=MaterialTheme.colorScheme.primary)else FilledTonalButton(onClick=request){Text(tr("Allow"))}
                        }
                    }
                    PrivilegeOptions(privilege,onPrivilege,rootAuthorized,onRootChanged)
                    Text(tr("You can continue without storage access and enable it later."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else->SetupTheme(mode,palette,onMode,onPalette)
            }
        }
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing){padding->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter){
            val wide=maxWidth>=640.dp&&maxWidth>maxHeight
            val compact=maxHeight<440.dp
            Column(Modifier.widthIn(max=if(wide)980.dp else 520.dp).fillMaxSize().padding(horizontal=if(compact)16.dp else 22.dp,vertical=if(compact)4.dp else 12.dp)){
                Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    if(page>0)IconButton(onClick={page--},modifier=Modifier.size(40.dp)){Icon(Icons.Default.ArrowBack,tr("Back"))}
                    else Image(painterResource(R.drawable.ic_slate),null,Modifier.size(30.dp))
                    Text("Slate",style=MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.setup_step,page+1,3),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().padding(bottom=if(compact)6.dp else 14.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    repeat(3){index->Box(Modifier.weight(1f).height(4.dp).background(if(index<=page)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,CircleShape))}
                }
                key(page,wide){
                    if(wide){
                        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(22.dp)){
                            Column(Modifier.weight(.85f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical=4.dp),verticalArrangement=Arrangement.Center){hero(true)}
                            Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom=4.dp)){body(true)}
                        }
                    }else{
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                            hero(false);body(false)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    if(page==2)TextButton(onClick=finish){Text(stringResource(R.string.setup_use_current))}
                    Button(onClick={if(page==2)finish()else page++},modifier=Modifier.weight(1f).height(48.dp)){
                        Text(tr(if(page==2)"Finish setup"else"Continue"));Spacer(Modifier.width(8.dp));Icon(if(page==2)Icons.Default.Check else Icons.Default.KeyboardArrowRight,null)
                    }
                }
            }
        }
    }
}
