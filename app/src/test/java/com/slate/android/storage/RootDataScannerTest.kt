package com.slate.android.storage
import org.junit.Assert.*
import org.junit.Test
class RootDataScannerTest{
    @Test fun pathsAndSizes(){
        assertEquals(RootFile("/data/user/0/test/a b",123L),parseRootFile("123 /data/user/0/test/a b"))
        assertNull(parseRootFile("123 /datax/a"))
        assertNull(parseRootFile("123 /data/../a"))
        assertNull(parseRootFile("-1 /data/a"))
    }
    @Test fun largestAcrossAllFolders(){
        val top=RootLargest(2)
        listOf(RootFile("/data/a",2),RootFile("/data/b/c",10),RootFile("/data/d/e",5)).forEach(top::add)
        assertEquals(3L,top.scanned)
        assertEquals(listOf(10L,5L),top.results().map{it.bytes})
    }
}
