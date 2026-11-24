package com.slate.android.storage
import org.junit.Test
import org.junit.Assert.*
class ProtectedPathTest {
 @Test fun limitsOperationsToPackageFolders(){
    assertTrue(validProtectedPath("Android/data/com.example.app"))
    assertTrue(validProtectedPath("Android/obb/com.example.game"))
    for(path in listOf("Android/data","Android/data/../com.example","Android/media/com.example","/Android/data/com.example","Android/data/com.example/extra","Android/data/com.foo;id","Android/data/com.foo\n"))assertFalse(path,validProtectedPath(path))
 }
 @Test fun rejectsInvalidRows(){
    val rows=parseProtectedRows(listOf("1\tAndroid/data/com.example.app","0\tAndroid/obb/com.example.game","1\tAndroid/data/../oops"))
    assertEquals(2,rows.size);assertTrue(rows.first().empty);assertFalse(rows.last().empty)
 }
}
