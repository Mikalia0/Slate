// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class NativeDirectoryTest {
    @Test fun decodesUnicodeAndLongMetadata(){
        val name="foto-😀.png".toByteArray(Charsets.UTF_8)
        val data=ByteBuffer.allocate(21+name.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(name.size).put(2.toByte()).putLong(5000000000L).putLong(1700000000000L).put(name).array()
        val row=NativeDirectory.decode(File("/storage"),data).single()
        assertEquals("foto-😀.png",row.file.name)
        assertTrue(row.isRegularFile)
        assertEquals(5000000000L,row.size)
        assertEquals(1700000000000L,row.modified)
    }
    @Test fun rejectsTraversalNames(){
        val name="../a".toByteArray()
        val data=ByteBuffer.allocate(21+name.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(name.size).put(2.toByte()).putLong(1).putLong(0).put(name).array()
        assertThrows(IllegalArgumentException::class.java){NativeDirectory.decode(File("/storage"),data)}
    }
}
