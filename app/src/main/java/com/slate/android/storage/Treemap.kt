// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

data class Tile(val entry: Entry?, val bytes: Long)
data class Cell(val tile: Tile, val x: Float, val y: Float, val width: Float, val height: Float)

/** Binary partition with area proportional to logical bytes. Coordinates are normalized. */
object Treemap {
    fun layout(entries: List<Entry>, limit: Int = 12, aspect: Float = 1f): List<Cell> {
        require(limit >= 2)
        val sorted = entries.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
        val tiles = sorted.take(limit - 1).map { Tile(it, it.bytes) }.toMutableList()
        val remaining = sorted.drop(limit - 1)
        if (remaining.size == 1) tiles.add(Tile(remaining[0], remaining[0].bytes))
        else if (remaining.isNotEmpty()) tiles.add(Tile(null, remaining.sumOf { it.bytes }))
        val output = mutableListOf<Cell>()
        fun split(items: List<Tile>, x: Float, y: Float, w: Float, h: Float) {
            if (items.isEmpty()) return
            if (items.size == 1) { output.add(Cell(items.first(), x, y, w, h)); return }
            val total = items.sumOf { it.bytes }.toDouble()
            var sum = 0.0
            var index = 0
            while (index < items.lastIndex) {
                val next = sum + items[index].bytes
                if (index > 0 && kotlin.math.abs(total / 2 - sum) <= kotlin.math.abs(total / 2 - next)) break
                sum = next
                index++
            }
            val ratio = (sum / total).toFloat()
            if (w * aspect >= h) {
                val left = w * ratio
                split(items.take(index), x, y, left, h)
                split(items.drop(index), x + left, y, w - left, h)
            } else {
                val top = h * ratio
                split(items.take(index), x, y, w, top)
                split(items.drop(index), x, y + top, w, h - top)
            }
        }
        split(tiles.sortedByDescending { it.bytes }, 0f, 0f, 1f, 1f)
        return output
    }
}
