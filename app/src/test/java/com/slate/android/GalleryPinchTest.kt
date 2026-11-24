// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryPinchTest{
    @Test fun smallMovementKeepsDensity(){
        for(scale in listOf(.9f,1f,1.1f,Float.NaN,0f))assertEquals(0,galleryPinchStep(scale))
    }
    @Test fun spreadingFingersEnlargesTiles(){assertEquals(-1,galleryPinchStep(1.3f))}
    @Test fun pinchingFingersMakesTilesSmaller(){assertEquals(1,galleryPinchStep(.75f))}
}
