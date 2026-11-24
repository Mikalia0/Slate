package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class FileNameTest {
    @Test fun rejectsTraversal(){listOf("","..",".","a/b","a\\b","\n").forEach{assertFalse(validFileName(it))}}
    @Test fun acceptsRegularNames(){assertTrue(validFileName("photo 1.jpg"))}
}
