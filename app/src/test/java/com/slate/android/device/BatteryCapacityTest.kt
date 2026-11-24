package com.slate.android.device
import org.junit.Test
import org.junit.Assert.*
class BatteryCapacityTest {
 @Test fun estimatesOnlyWithUsableInputs(){
    assertEquals(4000.0,estimatedCapacityMah(2_000_000,50)!!,0.001)
    assertNull(estimatedCapacityMah(0,50))
    assertNull(estimatedCapacityMah(2_000_000,0))
    assertNull(estimatedCapacityMah(null,50))
 }
}
