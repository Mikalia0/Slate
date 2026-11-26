package com.slate.android

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class SlateZipEditorTest {
    @Test fun nestedFile(){assertEquals("folder/note.txt",checkedArchiveName("folder/note.txt"))}
    @Test fun directory(){assertEquals("folder/",checkedArchiveName("folder/"))}
    @Test(expected=IllegalArgumentException::class) fun traversal(){checkedArchiveName("folder/../note.txt")}
    @Test(expected=IllegalArgumentException::class) fun absolute(){checkedArchiveName("/note.txt")}
    @Test(expected=IllegalArgumentException::class) fun emptySegment(){checkedArchiveName("a//b")}
    @Test fun textEntry(){
        val archive=File.createTempFile("slate-text",".zip")
        try{
            ZipOutputStream(archive.outputStream()).use{out->
                out.putNextEntry(ZipEntry("test.txt"))
                out.write("test".toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
            assertEquals("test",readArchiveText(archive,"test.txt"))
        }finally{archive.delete()}
    }
}
