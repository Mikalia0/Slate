package com.slate.android.storage
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Paths
class ProtectedDeletionTest {
 @Test fun protectedFoldersRemainBlockedEvenWhenRootChanges(){
    assertTrue(protectedAppPath(Paths.get("/storage/emulated/0/Android/data/com.example/cache")))
    assertTrue(protectedAppPath(Paths.get("/storage/emulated/0/Android/obb/com.example/game")))
    assertFalse(protectedAppPath(Paths.get("/storage/emulated/0/Android/media/com.example/file")))
 }
}
