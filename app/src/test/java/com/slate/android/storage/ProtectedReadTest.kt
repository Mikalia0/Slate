package com.slate.android.storage
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class ProtectedReadTest{
    @Test fun scopedPaths(){
        assertTrue(validReadRelative("Android/data/com.game/files/a"))
        assertTrue(validReadRelative("Android/obb/com.game"))
        assertFalse(validReadRelative("Android/data/../media"))
        assertFalse(validReadRelative("/Android/data"))
        assertFalse(validReadRelative("Android/data//a"))
        assertFalse(validReadRelative("Android/media/a"))
        val base=File("/storage/emulated/0")
        assertEquals("Android/obb/a",protectedRelative(File(base,"Android/obb/a"),base))
        assertNull(protectedRelative(File("/data/user/0/a"),base))
    }
}
