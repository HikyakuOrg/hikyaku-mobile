package org.hikyaku.mobile.packages.add

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.hikyaku.mobile.api.generated.models.AssignedShiftDto
import org.hikyaku.mobile.api.generated.models.AssignmentOutcomeDto

/**
 * Tests how `POST /api/v1/packages`'s assignment outcome is reduced for the confirmation panel.
 * [PackageAssignmentDisplay] is deliberately free of string resources, so this stays a pure
 * value-in/value-out test in the style of
 * [org.hikyaku.mobile.shift.ShiftDetailUiStateScanTest].
 */
class PackageAssignmentDisplayTest {

    private fun shift(stopIndex: Double, estimatedArrival: Instant?, revision: Double = 3.0) = AssignedShiftDto(
        id = "shift-1",
        driverId = "driver-1",
        vehicleId = "vehicle-1",
        shiftDate = LocalDate.parse("2026-09-01"),
        scheduledStart = Instant.parse("2026-09-01T08:00:00Z"),
        stopIndex = stopIndex,
        estimatedArrival = estimatedArrival,
        revision = revision,
    )

    private fun outcome(
        outcome: AssignmentOutcomeDto.Outcome,
        reason: AssignmentOutcomeDto.Reason? = null,
        shift: AssignedShiftDto? = null,
        evicted: List<String> = emptyList(),
    ) = AssignmentOutcomeDto(
        outcome = outcome,
        evictedPackageIds = evicted,
        reason = reason,
        shift = shift,
    )

    @Test
    fun assignedShowsAOneBasedStopNumberAndAnEta() {
        // stopIndex is zero-based on the wire; a driver counting stops starts at one.
        val display = outcome(
            AssignmentOutcomeDto.Outcome.assigned,
            shift = shift(stopIndex = 6.0, estimatedArrival = Instant.parse("2026-09-01T14:20:00Z")),
        ).toDisplay()

        assertTrue(display.isAssigned)
        assertFalse(display.openedNewShift)
        assertEquals(7, display.stopNumber)
        assertEquals("14:20", display.estimatedArrival)
        assertNull(display.reason)
    }

    @Test
    fun assignedNewShiftIsStillAssignedButFlagsTheNewShift() {
        // A new shift spends one of the organisation's monthly allowance, so it is worth saying.
        val display = outcome(
            AssignmentOutcomeDto.Outcome.assigned_new_shift,
            shift = shift(stopIndex = 0.0, estimatedArrival = Instant.parse("2026-09-01T09:05:00Z")),
        ).toDisplay()

        assertTrue(display.isAssigned)
        assertTrue(display.openedNewShift)
        assertEquals(1, display.stopNumber)
        assertEquals("09:05", display.estimatedArrival)
    }

    @Test
    fun anAssignedShiftWithNoEtaStillNamesTheStop() {
        val display = outcome(
            AssignmentOutcomeDto.Outcome.assigned,
            shift = shift(stopIndex = 2.0, estimatedArrival = null),
        ).toDisplay()

        assertEquals(3, display.stopNumber)
        assertNull(display.estimatedArrival)
    }

    @Test
    fun deferredCarriesItsReasonAndNoStop() {
        val display = outcome(
            AssignmentOutcomeDto.Outcome.deferred,
            reason = AssignmentOutcomeDto.Reason.no_capacity,
        ).toDisplay()

        assertFalse(display.isAssigned)
        assertNull(display.stopNumber)
        assertNull(display.estimatedArrival)
        assertEquals(AssignmentOutcomeDto.Reason.no_capacity, display.reason)
    }

    @Test
    fun skippedIsDistinctFromDeferred() {
        // The create-shift wizard's own packages come back this way; so does an ungeocoded address.
        val display = outcome(
            AssignmentOutcomeDto.Outcome.skipped,
            reason = AssignmentOutcomeDto.Reason.auto_assign_disabled,
        ).toDisplay()

        assertEquals(AssignmentOutcomeDto.Outcome.skipped, display.outcome)
        assertFalse(display.isAssigned)
        assertNull(display.stopNumber)
    }

    @Test
    fun anUnassignedOutcomeNeverShowsAStopEvenIfAShiftIsAttached() {
        // Defensive: `shift` is only meaningful for the two assigned outcomes.
        val display = outcome(
            AssignmentOutcomeDto.Outcome.deferred,
            reason = AssignmentOutcomeDto.Reason.no_free_driver_vehicle,
            shift = shift(stopIndex = 4.0, estimatedArrival = Instant.parse("2026-09-01T14:20:00Z")),
        ).toDisplay()

        assertNull(display.stopNumber)
        assertNull(display.estimatedArrival)
    }

    @Test
    fun evictedPackagesAreCounted() {
        val display = outcome(
            AssignmentOutcomeDto.Outcome.assigned,
            shift = shift(stopIndex = 1.0, estimatedArrival = null),
            evicted = listOf("pkg-a", "pkg-b"),
        ).toDisplay()

        assertEquals(2, display.evictedCount)
    }

    @Test
    fun theNormalCaseEvictsNothing() {
        val display = outcome(
            AssignmentOutcomeDto.Outcome.assigned,
            shift = shift(stopIndex = 1.0, estimatedArrival = null),
        ).toDisplay()

        assertEquals(0, display.evictedCount)
    }
}
