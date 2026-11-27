package com.slate.android
import org.junit.Assert.*
import org.junit.Test
class GalleryToolsTest {
    @Test fun albumPathUsesMediaType(){
        assertEquals("Pictures/Trips/",galleryAlbumPath("Trips",false))
        assertEquals("Movies/Clips/",galleryAlbumPath("Clips",true))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsTraversal(){galleryAlbumPath("../private",false)}
    @Test fun hashDistance(){assertEquals(0,galleryHashDistance(7,7));assertEquals(3,galleryHashDistance(0,7))}
}
