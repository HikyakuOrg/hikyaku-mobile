package org.hikyaku.mobile.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackingUrlTest {

    @Test
    fun parseScannedTrackingNumber_acceptsBareTrackingNumber() {
        assertEquals("YSD-2606-0001", parseScannedTrackingNumber("YSD-2606-0001"))
    }

    @Test
    fun parseScannedTrackingNumber_uppercasesAndTrims() {
        assertEquals("YSD-2606-0001", parseScannedTrackingNumber("  ysd-2606-0001  "))
    }

    @Test
    fun parseScannedTrackingNumber_extractsReferenceParamFromTrackingUrl() {
        val url = buildTrackingUrl(
            source = org.hikyaku.mobile.environment.model.EnvironmentSource.Default,
            orgSlug = "acme",
            trackingNumber = "YSD-2606-0001",
        )
        assertEquals("YSD-2606-0001", parseScannedTrackingNumber(url))
    }

    @Test
    fun parseScannedTrackingNumber_fallsBackToLastPathSegmentForOtherUrls() {
        assertEquals("ABC123", parseScannedTrackingNumber("https://example.com/some/path/ABC123"))
    }

    @Test
    fun parseScannedTrackingNumber_returnsNullForBlank() {
        assertNull(parseScannedTrackingNumber(""))
        assertNull(parseScannedTrackingNumber("   "))
    }
}
