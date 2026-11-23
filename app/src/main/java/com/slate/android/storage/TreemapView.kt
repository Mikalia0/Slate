// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TreemapView(entries: List<Entry>, format: (Long) -> String, onEntry: (Entry) -> Unit) {
    val palette = listOf(Color(0xFF2F6256), Color(0xFF37536B), Color(0xFF66513D), Color(0xFF52496F), Color(0xFF355E60))
    val shape = RoundedCornerShape(8.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(212.dp).clip(shape).background(Color(0xFF202629))) {
        val w = maxWidth
        val h = maxHeight
        val cells = remember(entries, w, h) { Treemap.layout(entries, aspect = w.value / h.value) }
        cells.forEachIndexed { index, cell ->
            val entry = cell.tile.entry
            val width = w * cell.width
            val height = h * cell.height
            val label = entry?.file?.name ?: "Other"
            Box(
                Modifier.offset(w * cell.x, h * cell.y).size(width, height)
                    .border(0.5.dp, Color(0xFF101214))
                    .background(if (entry == null) Color(0xFF41464A) else palette[index % palette.size])
                    .clickable(enabled = entry != null) { entry?.let(onEntry) }
            ) {
                if (width >= 54.dp && height >= 24.dp) Column(Modifier.padding(6.dp)) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (height >= 48.dp) Text(format(cell.tile.bytes), color = Color(0xFFE1E8E5), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
