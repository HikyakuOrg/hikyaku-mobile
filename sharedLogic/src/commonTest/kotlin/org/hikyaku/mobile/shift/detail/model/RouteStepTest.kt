package org.hikyaku.mobile.shift.detail.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Coverage of [Customer.fullAddress] and [Customer.streetAddress] (HIK-54). */
class RouteStepTest {

    private fun customer(unit: String? = null) = Customer(
        id = "c1",
        name = "Jamie Lee",
        phone = "+61400000000",
        address = "12 Smith St, Fitzroy, VIC 3065, Australia",
        suburb = "Fitzroy",
        state = "VIC",
        postcode = "3065",
        unit = unit,
    )

    @Test
    fun noUnitRendersExactlyAsBefore() {
        val c = customer(unit = null)
        assertEquals("12 Smith St, Fitzroy, VIC 3065", c.fullAddress)
        assertEquals(c.streetAddress, c.fullAddress)
    }

    @Test
    fun blankUnitRendersExactlyAsBefore() {
        val c = customer(unit = "   ")
        assertEquals("12 Smith St, Fitzroy, VIC 3065", c.fullAddress)
    }

    @Test
    fun unitAppearsOnItsOwnLineAboveTheStreet() {
        val c = customer(unit = "Suite 5")
        assertEquals("Suite 5\n12 Smith St, Fitzroy, VIC 3065", c.fullAddress)
    }

    @Test
    fun streetAddressNeverIncludesTheUnit() {
        val c = customer(unit = "Suite 5")
        assertEquals("12 Smith St, Fitzroy, VIC 3065", c.streetAddress)
    }
}
