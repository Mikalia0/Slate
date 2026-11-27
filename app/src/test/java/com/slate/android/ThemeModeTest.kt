package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class ThemeModeTest {
    @Test fun independentAmoledPreference(){
        assertTrue(themeUsesAmoled(SlateTheme.SYSTEM,true,true))
        assertFalse(themeUsesAmoled(SlateTheme.SYSTEM,false,true))
        assertFalse(themeUsesAmoled(SlateTheme.SYSTEM,true,false))
        assertTrue(themeUsesAmoled(SlateTheme.DARK,false,true))
        assertFalse(themeUsesAmoled(SlateTheme.DARK,true,false))
        assertFalse(themeUsesAmoled(SlateTheme.LIGHT,true,true))
    }
    @Test fun migratePreviousModes(){
        assertEquals(SlateTheme.DARK,migrateTheme("AMOLED"))
        assertTrue(migrateAmoled("AMOLED",null))
        assertTrue(migrateAmoled("SYSTEM",null))
        assertTrue(migrateAmoled(null,null))
        assertFalse(migrateAmoled("DARK",null))
        assertFalse(migrateAmoled("LIGHT",null))
        assertFalse(migrateAmoled("SYSTEM",false))
    }
}
