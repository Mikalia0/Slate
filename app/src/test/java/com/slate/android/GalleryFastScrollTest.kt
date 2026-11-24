// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryFastScrollTest{
    @Test fun dateHeadersAndPartialRows(){
        val anchors=galleryRowAnchors(listOf(5,2),3,true)
        assertArrayEquals(intArrayOf(0,1,4,6,7),anchors)
        assertEquals(2,galleryRowIndex(anchors,5))
        assertEquals(0,gallerySeekItem(anchors,0f))
        assertEquals(7,gallerySeekItem(anchors,1f))
    }
    @Test fun albumRowsAndEmptyGrid(){
        assertArrayEquals(intArrayOf(0,2,4),galleryRowAnchors(listOf(5),2,false))
        assertEquals(0,gallerySeekItem(intArrayOf(),.5f))
    }
}
