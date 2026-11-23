package com.slate.android.storage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LargestFilesTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun largestFilesAreRecursiveAndBounded() = runBlocking {
        val root = temp.newFolder()
        val nested = File(root, "nested").apply { mkdir() }
        (1..140).forEach { File(if (it % 2 == 0) nested else root, "file-$it").writeBytes(ByteArray(it)) }
        val snapshot = StorageScanner().scan(root)
        assertEquals(140L, snapshot.files)
        assertEquals(9870L, snapshot.bytes)
        assertEquals(100, snapshot.largestFiles.size)
        assertEquals((140 downTo 41).map { it.toLong() }, snapshot.largestFiles.map { it.bytes })
        assertEquals(nested, snapshot.largestFiles.first().file.parentFile)
    }
}
