package org.hikyaku.mobile.shift.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShiftStatusTest {

    @Test
    fun satisfiesScan_trueForOnboardOrLater() {
        assertTrue(ShiftStatus.satisfiesScan(ShiftStatus.ONBOARD_FOR_DELIVERY))
        assertTrue(ShiftStatus.satisfiesScan(ShiftStatus.IN_TRANSIT))
        assertTrue(ShiftStatus.satisfiesScan(ShiftStatus.DELIVERED))
    }

    @Test
    fun satisfiesScan_falseForEarlierOrUnknownStatuses() {
        assertFalse(ShiftStatus.satisfiesScan("PENDING"))
        assertFalse(ShiftStatus.satisfiesScan("ASSIGNED"))
        assertFalse(ShiftStatus.satisfiesScan("FAILED"))
        assertFalse(ShiftStatus.satisfiesScan(null))
    }

    @Test
    fun satisfiesScan_isCaseInsensitive() {
        assertTrue(ShiftStatus.satisfiesScan("onboard_for_delivery"))
        assertTrue(ShiftStatus.satisfiesScan("In_Transit"))
    }
}
