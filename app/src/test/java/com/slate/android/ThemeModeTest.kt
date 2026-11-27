package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class ThemeModeTest {
    @Test fun followsSystemWithAmoledDark(){
        assertTrue(themeUsesAmoled(SlateTheme.SYSTEM,true))
        assertFalse(themeUsesAmoled(SlateTheme.SYSTEM,false))
        assertTrue(themeUsesAmoled(SlateTheme.AMOLED,false))
        assertFalse(themeUsesAmoled(SlateTheme.DARK,true))
        assertFalse(themeUsesAmoled(SlateTheme.LIGHT,true))
    }
}
