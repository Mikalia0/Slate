package com.slate.android.device
import org.junit.Test
import org.junit.Assert.*

class BatteryPowerTest {
    @Test fun computesBatteryWattsFromMicroampsAndMillivolts() {
        assertEquals(8.4,batteryWatts(2_000_000,4200)!!,0.0001)
        assertEquals(8.4,batteryWatts(-2_000_000,4200)!!,0.0001)
        assertEquals(0.0,batteryWatts(0,4200)!!,0.0001)
    }
    @Test fun missingReadingsAreNotZero() {
        assertNull(batteryWatts(null,4200))
        assertNull(batteryWatts(Int.MIN_VALUE,4200))
        assertNull(batteryWatts(1000000,0))
    }
}
