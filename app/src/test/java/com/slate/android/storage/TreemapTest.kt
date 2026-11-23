package com.slate.android.storage
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TreemapTest {
    private fun entry(name: String, size: Long) = Entry(File(name), false, size, 1, 0)

    @Test fun proportionalAreaAndNoOverlap() {
        val cells = Treemap.layout(listOf(entry("a", 60), entry("b", 30), entry("c", 10)), aspect = 1.6f)
        assertEquals(3, cells.size)
        for (cell in cells) {
            assertEquals(cell.tile.bytes / 100f, cell.width * cell.height, 0.0001f)
            assertTrue(cell.x >= 0 && cell.y >= 0 && cell.x + cell.width <= 1.0001f && cell.y + cell.height <= 1.0001f)
        }
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            val a = cells[i]; val b = cells[j]
            val overlapW = minOf(a.x+a.width,b.x+b.width)-maxOf(a.x,b.x)
            val overlapH = minOf(a.y+a.height,b.y+b.height)-maxOf(a.y,b.y)
            assertTrue(overlapW <= 0.0001f || overlapH <= 0.0001f)
        }
    }

    @Test fun groupsSmallEntries() {
        val cells = Treemap.layout((1..30).map { entry(it.toString(), it.toLong()) }, limit = 5)
        assertEquals(5, cells.size)
        assertEquals(465L, cells.sumOf { it.tile.bytes })
        assertEquals(1, cells.count { it.tile.entry == null })
        assertTrue(Treemap.layout(listOf(entry("zero", 0))).isEmpty())
    }
}
