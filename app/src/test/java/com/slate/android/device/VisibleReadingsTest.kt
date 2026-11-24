package com.slate.android.device
import org.junit.Assert.*
import org.junit.Test
class VisibleReadingsTest {
    @Test fun filtersMissingAndDuplicateLabels(){
        assertEquals(listOf(Info("RAM","8 GB"),Info("Status","false")),visibleReadings(listOf(Info("RAM","8 GB"),Info("RAM","4 GB"),Info("Unavailable",UNKNOWN),Info("Empty",""),Info("Missing","—"),Info("Status","false"))))
    }
}
