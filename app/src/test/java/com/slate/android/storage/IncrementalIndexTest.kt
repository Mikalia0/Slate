package com.slate.android.storage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class IncrementalIndexTest {
 @get:Rule val tmp=TemporaryFolder()
 @Test fun replacesOnlySelectedSubtree()=runBlocking {
    val root=tmp.newFolder("root")
    val a=File(root,"a").apply{mkdir()}
    val b=File(root,"b").apply{mkdir()}
    File(a,"one").writeBytes(ByteArray(10))
    File(b,"two").writeBytes(ByteArray(20))
    val scanner=StorageScanner()
    val before=scanner.scanIndexed(root)
    File(a,"one").writeBytes(ByteArray(40))
    File(b,"two").writeBytes(ByteArray(100))
    val merged=before.replaceSubtree(scanner.scanIndexed(a))!!
    assertEquals(60L,merged.root.bytes)
    assertEquals(40L,merged.snapshot(a)!!.bytes)
    assertEquals(20L,merged.snapshot(b)!!.bytes)
    assertEquals(40L,merged.root.largestFiles.first().bytes)
 }
}
