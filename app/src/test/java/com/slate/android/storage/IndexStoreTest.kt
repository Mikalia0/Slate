package com.slate.android.storage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
class IndexStoreTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun restoresNestedResults()=runBlocking {
    val root=temp.newFolder("root")
    val sub=File(root,"sub").apply{mkdir()}
    File(sub,"a.bin").writeBytes(ByteArray(64))
    val store=IndexStore(File(temp.root,"cache.gz"),root)
    store.save(StorageScanner().scanIndexed(root))
    val restored=store.load()!!
    assertEquals(64L,restored.root.bytes)
    assertEquals(64L,restored.snapshot(sub)!!.bytes)
 }
}
