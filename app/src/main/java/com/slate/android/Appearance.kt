// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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

@Composable
internal fun SlateAppearance(mode: SlateTheme, dynamic: Boolean, activity: ComponentActivity, content: @Composable () -> Unit) {
    val dark = when (mode) { SlateTheme.SYSTEM -> isSystemInDarkTheme(); SlateTheme.LIGHT -> false; else -> true }
    val context = LocalContext.current
    val base = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFA7E8CF), onPrimary = Color(0xFF10372B), primaryContainer = Color(0xFF264D40), onPrimaryContainer = Color(0xFFC1F6E0), background = Color(0xFF111514), surface = Color(0xFF111514), surfaceContainer = Color(0xFF202624), surfaceContainerHigh = Color(0xFF292F2D), onSurface = Color(0xFFE1E4E1), onSurfaceVariant = Color(0xFFBFC9C3), outlineVariant = Color(0xFF404A45))
    else lightColorScheme(primary = Color(0xFF246B54), onPrimary = Color.White, primaryContainer = Color(0xFFBDEED7), onPrimaryContainer = Color(0xFF083B2A), background = Color(0xFFF8FAF7), surface = Color(0xFFF8FAF7), surfaceContainer = Color(0xFFE9EFEA), surfaceContainerHigh = Color(0xFFE0E8E2), onSurface = Color(0xFF18211D), onSurfaceVariant = Color(0xFF46554E), outlineVariant = Color(0xFFC1CDC5))
    val colors = if (mode == SlateTheme.AMOLED) base.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF141817), surfaceContainerHigh = Color(0xFF202523)) else base
    SideEffect {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun ThemeOptions(mode: SlateTheme, dynamic: Boolean, onMode: (SlateTheme) -> Unit, onDynamic: (Boolean) -> Unit) {
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
                                Box(Modifier.fillMaxWidth(.6f).height(5.dp).background(Color(0xFFA7E8CF), RoundedCornerShape(3.dp)))
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
        if (Build.VERSION.SDK_INT >= 31) ListItem(headlineContent = { Text(tr("Dynamic colors")) }, supportingContent = { Text(tr("Use your wallpaper colors")) }, trailingContent = { Switch(checked = dynamic, onCheckedChange = onDynamic) })
    }
}

@Composable
internal fun Onboarding(granted: Boolean, request: () -> Unit, mode: SlateTheme, dynamic: Boolean, onMode: (SlateTheme) -> Unit, onDynamic: (Boolean) -> Unit, finish: () -> Unit) {
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=24.dp, vertical=12.dp)) {
            if (page > 0) IconButton(onClick = { page-- }) { Icon(Icons.Default.ArrowBack, "Back") } else Spacer(Modifier.height(48.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when(page) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Image(painterResource(R.drawable.ic_slate), null, Modifier.size(76.dp))
                        Text(tr("Welcome to Slate"), style = MaterialTheme.typography.headlineLarge)
                        Text(tr("Understand your storage and device in one place."), style = MaterialTheme.typography.bodyLarge)
                        ListItem(headlineContent={Text(tr("Storage analyzer"))}, supportingContent={Text(tr("Find large files and explore folders."))}, leadingContent={Icon(Icons.Default.Search,null)})
                        ListItem(headlineContent={Text(tr("Device information"))}, supportingContent={Text(tr("Hardware, battery, display and Android."))}, leadingContent={Icon(Icons.Default.Info,null)})
                        Text(tr("Your files and measurements stay on your device."), color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> {
                        Text(tr("Permissions"), style=MaterialTheme.typography.headlineLarge)
                        Text(tr("Only grant the access needed for the tools you use."))
                        Card(Modifier.fillMaxWidth()) {
                            ListItem(headlineContent={Text(tr("Storage access"))}, supportingContent={Text(tr(if(granted) "Granted" else "Required to scan shared files and folders."))}, leadingContent={Icon(Icons.Default.Search,null)}, trailingContent={ if(granted) Icon(Icons.Default.Check,"Granted") else TextButton(onClick=request){Text(tr("Allow"))} })
                        }
                        ListItem(headlineContent={Text(tr("Device information"))}, supportingContent={Text(tr("No additional permission required."))}, leadingContent={Icon(Icons.Default.Info,null)})
                        com.slate.android.storage.ShizukuAccessRow()
                        Text(tr("You can continue without storage access and enable it later."), style=MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Text(tr("Make it yours"),style=MaterialTheme.typography.headlineLarge)
                        Text(tr("Choose your appearance. You can change it in Settings."))
                        ThemeOptions(mode,dynamic,onMode,onDynamic)
                    }
                }
            }
            Button(onClick={if(page==2)finish() else page++}, modifier=Modifier.fillMaxWidth()){Text(tr(if(page==2) "Finish setup" else "Continue"))}
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.Center) {
                repeat(3){ i -> Box(Modifier.padding(4.dp).size(if(i==page) 9.dp else 6.dp).background(if(i==page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))) }
            }
        }
    }
}
