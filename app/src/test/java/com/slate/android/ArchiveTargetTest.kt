package com.slate.android
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
class ArchiveTargetTest {
    @Test fun nestedEntry(){val root=File("/tmp/slate-test");assertEquals(File(root,"folder/item.txt").canonicalFile,archiveTarget(root,"folder/item.txt"))}
    @Test(expected=IllegalArgumentException::class) fun traversal(){archiveTarget(File("/tmp/slate-test"),"../escape")}
    @Test(expected=IllegalArgumentException::class) fun absolute(){archiveTarget(File("/tmp/slate-test"),"/escape")}
    @Test(expected=IllegalArgumentException::class) fun backslash(){archiveTarget(File("/tmp/slate-test"),"..\\escape")}
}
