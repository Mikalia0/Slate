package com.slate.android.storage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class StorageCategoriesTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun groupsFilesWithoutCountingAndroidTwice()=runBlocking {
    val root=temp.newFolder("root")
    val docs=File(root,"Documents").apply{mkdir()}
    File(docs,"a.pdf").writeBytes(ByteArray(12))
    File(root,"a.JPG").writeBytes(ByteArray(7))
    val android=File(root,"Android").apply{mkdir()}
    File(android,"a.jpg").writeBytes(ByteArray(99))
    val result=StorageScanner().scanIndexed(root).personalCategories()!!
    assertEquals(19L,result.sumOf{it.bytes})
    assertEquals(7L,result.first{it.category==FileCategory.IMAGES}.bytes)
 }
}
