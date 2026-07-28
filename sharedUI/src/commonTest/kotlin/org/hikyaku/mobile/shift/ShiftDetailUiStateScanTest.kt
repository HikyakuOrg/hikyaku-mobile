package org.hikyaku.mobile.shift

import org.hikyaku.mobile.shift.detail.model.PackageAssignment
import org.hikyaku.mobile.shift.detail.model.PackageInfo
import org.hikyaku.mobile.shift.detail.model.RouteStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the load-scanning gate's derived [ShiftDetailViewModel.UiState] properties. These are
 * pure functions of [ShiftDetailViewModel.UiState.steps]/[ShiftDetailViewModel.UiState.packageStatuses]/
 * [ShiftDetailViewModel.UiState.statusOverrides], so the VM's collaborators never need faking.
 */
class ShiftDetailUiStateScanTest {

    private fun jobStep(id: Long, packageId: String?, currentStatus: String?) = RouteStep(
        id = id,
        stepIndex = id.toInt(),
        type = "job",
        assignment = if (packageId != null) {
            PackageAssignment(packageId = packageId, packageInfo = PackageInfo(currentStatus = currentStatus))
        } else {
            null
        },
    )

    @Test
    fun scannedCount_countsOnboardInTransitAndDeliveredButNotEarlierStatuses() {
        val state = ShiftDetailViewModel.UiState(
            steps = listOf(
                jobStep(1, "p1", "ONBOARD_FOR_DELIVERY"),
                jobStep(2, "p2", "IN_TRANSIT"),
                jobStep(3, "p3", "DELIVERED"),
                jobStep(4, "p4", "PENDING"),
                jobStep(5, "p5", "ASSIGNED"),
            ),
        )
        assertEquals(5, state.scanTotal)
        assertEquals(3, state.scannedCount)
        assertFalse(state.allPackagesScanned)
    }

    @Test
    fun allPackagesScanned_trueOnceEveryPackageIsAtLeastOnboard() {
        val state = ShiftDetailViewModel.UiState(
            steps = listOf(
                jobStep(1, "p1", "ONBOARD_FOR_DELIVERY"),
                jobStep(2, "p2", "IN_TRANSIT"),
            ),
        )
        assertTrue(state.allPackagesScanned)
    }

    @Test
    fun allPackagesScanned_trivallyTrueForAdHocShiftWithNoPackages() {
        val state = ShiftDetailViewModel.UiState(
            steps = listOf(jobStep(1, packageId = null, currentStatus = null)),
        )
        assertEquals(0, state.scanTotal)
        assertTrue(state.allPackagesScanned)
    }

    @Test
    fun effectiveStatus_prefersOverrideThenPackageStatusesThenEmbeddedValue() {
        val state = ShiftDetailViewModel.UiState(
            steps = listOf(jobStep(1, "p1", "PENDING")),
            packageStatuses = mapOf("p1" to "ONBOARD_FOR_DELIVERY"),
            statusOverrides = mapOf("p1" to "IN_TRANSIT"),
        )
        assertEquals("IN_TRANSIT", state.effectiveStatus("p1"))

        val withoutOverride = state.copy(statusOverrides = emptyMap())
        assertEquals("ONBOARD_FOR_DELIVERY", withoutOverride.effectiveStatus("p1"))

        val embeddedOnly = withoutOverride.copy(packageStatuses = emptyMap())
        assertEquals("PENDING", embeddedOnly.effectiveStatus("p1"))
    }

    @Test
    fun unscannedStops_excludesScannedPackagesRegardlessOfRouteOrder() {
        val state = ShiftDetailViewModel.UiState(
            steps = listOf(
                jobStep(1, "p1", "DELIVERED"),
                jobStep(2, "p2", "PENDING"),
                jobStep(3, "p3", "ONBOARD_FOR_DELIVERY"),
            ),
        )
        assertEquals(listOf(2L), state.unscannedStops.map { it.id })
    }

    @Test
    fun isStopLocked_trueForOnboardStatus() {
        val state = ShiftDetailViewModel.UiState(steps = listOf(jobStep(1, "p1", "ONBOARD_FOR_DELIVERY")))
        assertTrue(state.isStopLocked(state.steps.first()))
    }

    @Test
    fun isStopLocked_falseForPendingStatus() {
        val state = ShiftDetailViewModel.UiState(steps = listOf(jobStep(1, "p1", "PENDING")))
        assertFalse(state.isStopLocked(state.steps.first()))
    }
}
