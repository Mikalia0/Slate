package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class ApkSigningOptionsTest {
    @Test fun defaultSchemesAndName(){
        val options=ApkSignOptions()
        assertEquals(DEFAULT_APK_KEY,options.key)
        assertTrue(options.scheme.v1&&options.scheme.v2&&options.scheme.v3)
        assertTrue(validV1Name(options.v1Name))
    }
    @Test fun validatesV1Filename(){
        assertTrue(validV1Name("CERT_123"))
        assertFalse(validV1Name("CERT_1234"))
        assertFalse(validV1Name("../CERT"))
        assertFalse(validV1Name(""))
    }
}
