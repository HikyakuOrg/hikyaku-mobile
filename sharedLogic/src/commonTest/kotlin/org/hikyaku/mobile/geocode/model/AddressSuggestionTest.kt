package org.hikyaku.mobile.geocode.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Table-driven coverage of [isLikelyBuilding] over realistic Photon feature shapes (HIK-51). */
class AddressSuggestionTest {

    private fun suggestion(
        street: String? = "12 Smith St",
        osmKey: String? = null,
        osmValue: String? = null,
        extent: List<Double>? = null,
    ) = AddressSuggestion(
        label = "$street, Fitzroy, VIC 3065, Australia",
        street = street,
        suburb = "Fitzroy",
        state = "VIC",
        country = "Australia",
        postcode = "3065",
        lat = -37.80,
        lon = 144.98,
        gid = "N123",
        confidence = null,
        osmKey = osmKey,
        osmValue = osmValue,
        extent = extent,
    )

    @Test
    fun buildingApartmentsWithExtentIsLikelyBuilding() {
        val s = suggestion(osmKey = "building", osmValue = "apartments", extent = listOf(144.97, -37.81, 144.99, -37.79))
        assertEquals(true, isLikelyBuilding(s))
    }

    @Test
    fun buildingYesWithNoHousenumberIsLikelyBuilding() {
        val s = suggestion(street = null, osmKey = "building", osmValue = "yes", extent = null)
        assertEquals(true, isLikelyBuilding(s))
    }

    @Test
    fun plainHouseNumberMatchWithNoExtentIsNotLikelyBuilding() {
        val s = suggestion(osmKey = "house", osmValue = null, extent = null)
        assertEquals(false, isLikelyBuilding(s))
    }

    @Test
    fun streetLevelMatchIsNotLikelyBuilding() {
        val s = suggestion(street = null, osmKey = "highway", osmValue = "residential", extent = null)
        assertEquals(false, isLikelyBuilding(s))
    }

    @Test
    fun droppedPinFallbackIsNotLikelyBuilding() {
        val droppedPin = AddressSuggestion(
            label = "Dropped pin (-37.8, 144.98)",
            street = null,
            suburb = null,
            state = null,
            country = null,
            postcode = null,
            lat = -37.8,
            lon = 144.98,
            gid = null,
            confidence = null,
        )
        assertEquals(false, isLikelyBuilding(droppedPin))
    }

    @Test
    fun extentWithoutAResolvedStreetIsNotLikelyBuilding() {
        // A city/region-level match can carry an extent too; only pair it with a real address.
        val s = suggestion(street = null, osmKey = "place", osmValue = "city", extent = listOf(144.5, -38.0, 145.5, -37.5))
        assertEquals(false, isLikelyBuilding(s))
    }
}
