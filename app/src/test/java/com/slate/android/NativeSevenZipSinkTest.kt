// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class NativeSevenZipSinkTest {
    @Test fun writesOnlyDeclaredBytesAndClosesEntry(){
        val out=ByteArrayOutputStream()
        val dirs=mutableListOf<String>()
        val sink=NativeSevenZipSink({dirs.add(it)},{out},{})
        sink.begin("folder",true,0,0x4000 shl 16)
        sink.finish()
        sink.begin("folder/file",false,3,0x8000 shl 16)
        sink.write(byteArrayOf(1,2,3),3)
        sink.finish()
        assertEquals(listOf("folder"),dirs)
        assertArrayEquals(byteArrayOf(1,2,3),out.toByteArray())
    }
    @Test fun rejectsSymbolicLinksAndReparsePoints(){
        val sink=NativeSevenZipSink({fail("unexpected directory")},{throw AssertionError("unexpected file")},{})
        for(attrs in listOf(0xA000 shl 16,0x400)){
            assertThrows(IllegalArgumentException::class.java){sink.begin("link",false,0,attrs)}
        }
    }
    @Test fun rejectsUnexpectedOutputLength(){
        val sink=NativeSevenZipSink({},{ByteArrayOutputStream()},{})
        sink.begin("file",false,1,0)
        assertThrows(IllegalArgumentException::class.java){sink.write(byteArrayOf(1,2),2)}
        sink.close()
    }
}
