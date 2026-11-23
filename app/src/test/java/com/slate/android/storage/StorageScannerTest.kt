package com.slate.android.storage

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageScannerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun aggregatesNestedFilesAndSorts() = runBlocking {
        val root = temp.newFolder()
        File(root, "small").writeBytes(ByteArray(10))
        val folder = File(root, "folder").apply { mkdir() }
        File(folder, "large").writeBytes(ByteArray(50))
        File(folder, "empty").mkdir()
        val result = StorageScanner().scan(root)
        assertEquals(60L, result.bytes)
        assertEquals(2L, result.files)
        assertEquals(0L, result.skipped)
        assertEquals("folder", result.entries.first().file.name)
    }

    @Test fun ignoresSymlinks() = runBlocking {
        val root = temp.newFolder()
        val outside = temp.newFile().apply { writeBytes(ByteArray(200)) }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val result = StorageScanner().scan(root)
        assertEquals(0L, result.bytes)
        assertEquals(1L, result.skipped)
    }

    @Test fun reportsMissingDirectory() = runBlocking {
        assertTrue(StorageScanner().scan(File(temp.root, "missing")).rootUnreadable)
    }
}
