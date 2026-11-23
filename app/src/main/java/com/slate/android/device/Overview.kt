// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Mint = Color(0xFFA7E8CF)
private val Cyan = Color(0xFF9DC5EA)
private val Amber = Color(0xFFE9C398)
private val Violet = Color(0xFFC8BAEA)
private val Tile = Color(0xFF1B1F23)

private fun Section?.value(label: String) = this?.rows?.firstOrNull { it.label == label }?.value ?: UNKNOWN

@Composable
internal fun DeviceOverview(live: LiveInfo?, fixed: StaticInfo?, navigate: (Int) -> Unit) {
    val processor = fixed?.hardware?.firstOrNull()
    val graphics = fixed?.hardware?.getOrNull(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF182522), modifier = Modifier.fillMaxWidth().clickable { navigate(2) }) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF203A32), Color(0xFF17211F)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DEVICE PROFILE", color = Mint, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
                Text(android.os.Build.MODEL, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(android.os.Build.MANUFACTURER + " · Android " + android.os.Build.VERSION.RELEASE + " · API " + android.os.Build.VERSION.SDK_INT, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C9C3))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFF355047))
                Text(processor.value("SoC").takeUnless { it == UNKNOWN } ?: processor.value("Hardware"), color = Mint, style = MaterialTheme.typography.titleMedium)
                Text(graphics.value("GPU renderer"), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(live?.display.value("Resolution") + " · " + live?.display.value("Refresh rate"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C9C3))
            }
        }
        if (live == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Reading device information…", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gauge(live.meters[0], Mint, Modifier.weight(1f)) { navigate(1) }
            Gauge(live.meters[1], Cyan, Modifier.weight(1f)) { navigate(4) }
        }
        OverviewPanel("Processor", "Hardware ›", { navigate(1) }) {
            Text(processor.value("SoC").takeUnless { it == UNKNOWN } ?: processor.value("Hardware"), style = MaterialTheme.typography.titleMedium)
            Text("${live.cores.size} detected cores · current / maximum frequency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            live.cores.chunked(2).forEach { cores ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cores.forEach { core ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("CPU ${core.id}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                Text(if (core.online == false) "Offline" else core.current?.let { "${it / 1000} MHz" } ?: "—", color = Mint, style = MaterialTheme.typography.labelSmall)
                            }
                            val fraction = if (core.current != null && core.maximum != null && core.maximum > 0) (core.current.toDouble() / core.maximum).toFloat().coerceIn(0f,1f) else null
                            if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Mint, trackColor = Color(0xFF323A3D))
                            else Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF323A3D)))
                            Text(core.maximum?.let { "Max ${it / 1000} MHz" } ?: "Max unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (cores.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        OverviewPanel("Graphics", "Hardware ›", { navigate(1) }) {
            Text(graphics.value("GPU renderer"), color = Cyan, style = MaterialTheme.typography.titleLarge)
            DataLine("Driver API", graphics.value("OpenGL ES"))
            DataLine("Vulkan", graphics.value("Vulkan API (reported)"))
            DataLine("Clock", live.gpu.value("Frequency"))
            DataLine("Utilization", live.gpu.value("Utilization"))
        }
        OverviewPanel("Battery", "Details ›", { navigate(3) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ring(live.meters[2].fraction, live.meters[2].value, Amber, Modifier.size(74.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(live.battery.value("Status"), style = MaterialTheme.typography.titleMedium, color = Amber)
                    Text(live.battery.value("Temperature") + " · " + live.battery.value("Voltage"), style = MaterialTheme.typography.bodySmall)
                    Text(live.battery.value("Instant current (+ charging)"), style = MaterialTheme.typography.bodySmall)
                }
            }
            DataLine("Remaining charge", live.battery.value("Remaining charge"))
            DataLine("Power source", live.battery.value("Power source"))
            DataLine("Health status", live.battery.value("Health (system status)"))
            if (live.battery.value("Charge cycles") != UNKNOWN) DataLine("Cycles", live.battery.value("Charge cycles"))
        }
        OverviewPanel("System & thermals", "System ›", { navigate(2) }) {
            DataLine("Uptime", live.uptime.value("Since boot"))
            DataLine("Deep sleep", live.uptime.value("Deep sleep (estimate)"))
            DataLine("Thermal status", live.thermal.value("Thermal throttling status"))
            live.thermal.rows.drop(1).take(3).forEach { DataLine(it.label, it.value) }
            DataLine("Security patch", android.os.Build.VERSION.SECURITY_PATCH)
        }
        OverviewPanel("Connection", "Network ›", { navigate(5) }) {
            DataLine("Transport", live.connection.value("Connection"))
            DataLine("Interface", live.connection.value("Interface"))
            DataLine("Internet validated", live.connection.value("Internet validated"))
        }
        Text("Live values update only while this screen is open. A dash or Unavailable means the device has not exposed the metric. Clock bars show frequency, not CPU load.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Gauge(meter: Meter, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = Tile) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meter.title, style = MaterialTheme.typography.labelLarge)
            Ring(meter.fraction, meter.fraction?.let { "${(it * 100).toInt()}%" } ?: "—", color, Modifier.size(76.dp))
            Text(meter.value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
            Text(meter.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Ring(fraction: Float?, label: String, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 6.dp.toPx()
            val top = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0xFF343B40), -90f, 360f, false, topLeft = top, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction != null && fraction > 0f) drawArc(color, -90f, fraction.coerceIn(0f,1f) * 360, false, topLeft = top, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OverviewPanel(title: String, action: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action, style = MaterialTheme.typography.labelMedium) }
            }
            content()
        }
    }
}

@Composable
private fun DataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.weight(0.45f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, modifier = Modifier.weight(0.55f), style = MaterialTheme.typography.bodySmall)
    }
}
