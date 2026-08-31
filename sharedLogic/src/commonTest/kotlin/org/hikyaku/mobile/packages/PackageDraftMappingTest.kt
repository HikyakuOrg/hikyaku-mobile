package org.hikyaku.mobile.packages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.hikyaku.mobile.geocode.model.AddressSuggestion
import org.hikyaku.mobile.packages.model.PackageDraft
import org.hikyaku.mobile.customer.model.CustomerInput

/**
 * Tests the `PackageDraft` -> `CreatePackageDto` mapping — the whole request side of
 * `POST /api/v1/packages` bar the two customer ids the repository resolves. Pure by construction:
 * the mapping takes plain values and returns a generated DTO, so no client is involved.
 */
class PackageDraftMappingTest {

    private fun address(label: String) = AddressSuggestion(
        label = label,
        street = null,
        suburb = "Fitzroy",
        state = "VIC",
        country = "Australia",
        postcode = "3065",
        lat = -37.80,
        lon = 144.98,
        gid = null,
        confidence = 0.9,
    )

    private fun draft(
        deliveryNotes: String? = "Leave at the front door",
        scheduledArrival: String = "2026-09-01T14:30:00Z",
        autoAssign: Boolean = true,
    ) = PackageDraft(
        organisationId = "org-1",
        orgSlug = "acme",
        sender = CustomerInput("Sender", "+61400000001", address("1 Smith St")),
        receiver = CustomerInput("Receiver", "+61400000002", address("2 Brunswick St")),
        warehouseId = "wh-1",
        weightKg = 2.5,
        lengthCm = 30.0,
        widthCm = 20.0,
        heightCm = 15.0,
        deliveryNotes = deliveryNotes,
        scheduledArrival = scheduledArrival,
        images = emptyList(),
        autoAssign = autoAssign,
    )

    @Test
    fun mapsWarehouseCustomersAndDimensionsOntoTheRequest() {
        val dto = draft().toCreatePackageDto(id = "pkg-1", fromCustomerId = "cust-a", toCustomerId = "cust-b")

        assertEquals("wh-1", dto.warehouseId)
        assertEquals("cust-a", dto.fromCustomerId)
        assertEquals("cust-b", dto.toCustomerId)
        assertEquals(2.5, dto.dimensions.weightKg)
        assertEquals(30.0, dto.dimensions.lengthCm)
        assertEquals(20.0, dto.dimensions.widthCm)
        assertEquals(15.0, dto.dimensions.heightCm)
    }

    @Test
    fun sendsTheClientMintedIdSoThePhotoStoragePathStaysStable() {
        // CreatePackageDto.id is optional precisely so the client can name {packageId}/photo_0.jpg
        // before the row exists. Dropping it would break the upload path and idempotent replay.
        val dto = draft().toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertEquals("pkg-1", dto.id)
    }

    @Test
    fun autoAssignDefaultsToTrue() {
        // The point of the feature: a package created on the add-package screen should be on a
        // shift before the driver puts their phone away. Built without naming autoAssign, so this
        // pins the default on PackageDraft itself, not just the mapping.
        val defaulted = PackageDraft(
            organisationId = "org-1",
            orgSlug = "acme",
            sender = CustomerInput("Sender", "+61400000001", address("1 Smith St")),
            receiver = CustomerInput("Receiver", "+61400000002", address("2 Brunswick St")),
            warehouseId = "wh-1",
            weightKg = 2.5,
            lengthCm = 30.0,
            widthCm = 20.0,
            heightCm = 15.0,
            deliveryNotes = null,
            scheduledArrival = "2026-09-01T14:30:00Z",
            images = emptyList(),
        )
        assertTrue(defaulted.autoAssign)
        assertEquals(true, defaulted.toCreatePackageDto("pkg-1", "a", "b").autoAssign)
    }

    @Test
    fun wizardCanTurnAutoAssignOff() {
        // The create-shift wizard hands these ids to POST /optimisation/adhoc, which 409s on a
        // package that already belongs to an optimisation.
        val dto = draft(autoAssign = false)
            .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertEquals(false, dto.autoAssign)
    }

    @Test
    fun scheduledArrivalBecomesTheDeadline() {
        // scheduled_arrival is the customer promise, never planner output — planner ETAs land in
        // estimated_arrival instead.
        val dto = draft(scheduledArrival = "2026-09-01T14:30:00Z")
            .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertEquals(Instant.parse("2026-09-01T14:30:00Z"), dto.deadlineAt)
    }

    @Test
    fun blankDeliveryNotesAreOmittedRatherThanSentEmpty() {
        val blank = draft(deliveryNotes = "   ")
            .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertNull(blank.deliveryNotes)

        val absent = draft(deliveryNotes = null)
            .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertNull(absent.deliveryNotes)

        val present = draft(deliveryNotes = "Leave at the front door")
            .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertEquals("Leave at the front door", present.deliveryNotes)
    }

    @Test
    fun trackingNumberIsLeftToTheServerTrigger() {
        val dto = draft().toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        assertNull(dto.trackingNumber)
    }

    @Test
    fun anUnparseableArrivalFailsRatherThanSendingADeadlinelessPackage() {
        // A package with no deadline is the one the backend is allowed to bump off a shift, so
        // silently dropping a malformed deadline would change its handling.
        val failure = assertFailsWith<IllegalArgumentException> {
            draft(scheduledArrival = "not-a-timestamp")
                .toCreatePackageDto(id = "pkg-1", fromCustomerId = "a", toCustomerId = "b")
        }
        assertTrue(failure.message.orEmpty().isNotBlank())
    }
}
