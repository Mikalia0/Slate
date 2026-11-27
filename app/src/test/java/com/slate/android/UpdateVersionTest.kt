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
        assertEquals(1083L to 1L,updateVersion("v0.1.0","Version code: 1083\n\nFirst release"))
        assertNull(updateVersion("v0.1.0"))
        assertNull(updateVersion("v0.1.0","Version code: 999"))
        assertNull(updateVersion("v0.1.0","Version code: 1083\nVersion code: 1084"))
    }
    @Test fun validatesManifestVersion() {
        assertTrue(manifestVersion("dev-89-1",1089L,1L,"Version code: 1089"))
        assertTrue(manifestVersion("v0.1.0",1083L,1L,"Version code: 1083"))
        assertFalse(manifestVersion("dev-89-1",1090L,1L,"Version code: 1090"))
        assertFalse(manifestVersion("dev-89-1",1089L,2L,"Version code: 1089"))
        assertFalse(manifestVersion("v0.1.0",1083L,1L,"Version code: 1084"))
        assertFalse(manifestVersion("../other",1083L,1L,"Version code: 1083"))
    }
}
