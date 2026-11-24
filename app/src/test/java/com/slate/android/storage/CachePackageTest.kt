package com.slate.android.storage
import org.junit.Assert.*
import org.junit.Test
class CachePackageTest {
    @Test fun validPackages(){
        assertTrue(validCachePackage("android"))
        assertTrue(validCachePackage("com.example.app"))
        assertTrue(validCachePackage("com.example_app.v2"))
    }
    @Test fun rejectsArgumentsAndPaths(){
        listOf("", "--user", "com.example;id", "com.example app", "../data", "com..example").forEach{
            assertFalse(it,validCachePackage(it))
        }
    }
}
