package com.slate.android.storage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanProgressTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun reportsMeasuredBytesAndVisitedItems() = runBlocking {
        val root = temp.newFolder()
        File(root, "a").writeBytes(ByteArray(21))
        val nested = File(root, "nested").apply { mkdir() }
        File(nested, "b").writeBytes(ByteArray(42))
        val progress = mutableListOf<ScanProgress>()
        val result = StorageScanner().scan(root) { progress.add(it) }
        assertEquals(63L, result.bytes)
        assertEquals(63L, progress.last().bytes)
        assertEquals(3L, progress.last().visited)
        assertTrue(progress.zipWithNext().all { (a,b) -> a.visited <= b.visited && a.bytes <= b.bytes && a.elapsedMs <= b.elapsedMs })
    }
}
