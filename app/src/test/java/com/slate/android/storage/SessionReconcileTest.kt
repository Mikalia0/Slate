package com.slate.android.storage

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionReconcileTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun detectsChangesAndReusesUnchangedFolders()=runBlocking {
    val root=temp.newFolder("root")
    val stable=File(root,"stable").apply{mkdir()}
    val changed=File(root,"changed").apply{mkdir()}
    File(stable,"keep").writeBytes(ByteArray(8))
    val edited=File(changed,"edit").apply{writeBytes(ByteArray(16))}
    val removed=File(changed,"remove").apply{writeBytes(ByteArray(32))}
    val scanner=StorageScanner()
    val before=scanner.scanIndexed(root)
    edited.writeBytes(ByteArray(64))
    assertTrue(removed.delete())
    File(changed,"new").writeBytes(ByteArray(4))
    val after=scanner.scanIndexed(root,previous=before)
    assertEquals(76L,after.root.bytes)
    assertSame(before.folders[stable],after.folders[stable])
    assertNotSame(before.folders[changed],after.folders[changed])
    assertEquals(64L,after.root.largestFiles.first().bytes)
 }
 @Test fun detectsNestedFileGrowthWithoutDirectoryTimestampChange()=runBlocking{
    val root=temp.newFolder("nested")
    val sub=File(root,"sub").apply{mkdir()}
    val file=File(sub,"file").apply{writeBytes(ByteArray(1))}
    val scanner=StorageScanner()
    val before=scanner.scanIndexed(root)
    val stamp=sub.lastModified()
    file.writeBytes(ByteArray(512))
    sub.setLastModified(stamp)
    assertEquals(512L,scanner.scanIndexed(root,previous=before).root.bytes)
 }
}
