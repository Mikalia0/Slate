package com.slate.android.storage

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IndexedScanTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun nestedResultsAreAvailableWithoutFilesystemAccess()=runBlocking {
        val root=temp.newFolder()
        val folder=File(root,"Download").apply{mkdir()}
        val inner=File(folder,"nested").apply{mkdir()}
        val file=File(inner,"large.bin").apply{writeBytes(ByteArray(512))}
        File(folder,"small.bin").writeBytes(ByteArray(16))
        val indexed=StorageScanner().scanIndexed(root)
        assertEquals(528L,indexed.root.bytes)
        assertTrue(file.delete())
        val cached=indexed.snapshot(folder)!!
        assertEquals(528L,cached.bytes)
        assertEquals(2L,cached.files)
        assertEquals(file,cached.largestFiles.first().file)
        assertEquals(512L,indexed.snapshot(inner)!!.bytes)
    }
}
