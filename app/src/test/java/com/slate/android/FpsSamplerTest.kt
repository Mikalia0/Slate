package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class FpsSamplerTest {
    @Test fun focusAndLayer(){
        assertEquals("com.game.test",focusedPackage("mCurrentFocus=Window{123 u0 com.game.test/.Main}"))
        assertTrue(belongsToLayer("SurfaceView[com.game.test/.Main](BLAST)#42","com.game.test"))
        assertFalse(belongsToLayer("com.game.test.other/.Main","com.game.test"))
    }
    @Test fun timestamps(){
        assertEquals(listOf(100L,200L),presentedFrames("16666667\n0 100 0\n0 9223372036854775807 0\n0 0 0\n0 200 0\n0 100 0"))
    }
    @Test fun counters(){
        assertEquals(listOf(LayerCount("SurfaceView[test]",100)),timeStatsLayers("layerName = SurfaceView[test]\ntotalFrames = 100"))
        assertEquals(60.0,frameRateFromDelta(100,160,1000000000)!!,0.01)
        assertNull(frameRateFromDelta(100,10,1000000000))
    }
}
