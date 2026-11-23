package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class UpdateVersionTest {
    @Test fun mapsReleaseTagToVersionCode() {
        assertEquals(1007L to 2L,updateVersion("dev-7-2"))
        assertEquals(1010L to 1L,updateVersion("dev-10-1"))
        assertNull(updateVersion("dev-0-1"))
        assertNull(updateVersion("dev-999999999999999999999-1"))
        assertNull(updateVersion("v1.0"))
    }
}
