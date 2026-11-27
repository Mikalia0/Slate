// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import org.junit.Assert.*
import org.junit.Test

class PhotoCropTest {
    @Test fun squareCropUsesImageAspect(){
        val crop=aspectCrop(4000,2000,1f)
        assertEquals(.5f,crop.width,.0001f)
        assertEquals(1f,crop.height,.0001f)
    }
    @Test fun movementPreservesSizeAndBounds(){
        val crop=EditCrop(.2f,.2f,.8f,.8f).move(1f,-1f)
        assertEquals(1f,crop.right,.0001f)
        assertEquals(0f,crop.top,.0001f)
        assertEquals(.6f,crop.width,.0001f)
    }
    @Test fun constrainedResizeKeepsRatio(){
        val crop=EditCrop(.2f,.2f,.8f,.8f).resize(3,.1f,.05f,1f)
        assertEquals(crop.width,crop.height,.0001f)
        assertTrue(crop.right<=1f&&crop.bottom<=1f)
    }
}
