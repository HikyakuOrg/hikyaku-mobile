package org.hikyaku.mobile.packages.add

import org.hikyaku.mobile.api.generated.models.AssignmentOutcomeDto
import org.hikyaku.mobile.util.formatHourMinute
import org.hikyaku.mobile.util.isoDateTimeToHourMinute

/**
 * The assignment half of `POST /api/v1/packages`, reduced to what the confirmation panel actually
 * renders. The call answers 201 whether or not the package found a shift, so this is never an error
 * path — it is the point of the whole feature, and the only place the driver learns that a package
 * they just created is already stop 7 on someone's route.
 *
 * Deliberately a plain value with no string resources in it: the mapping from a wire outcome to
 * "assigned / opened a shift / queued" is the part worth testing, and keeping it free of
 * `getString` lets that test stay pure.
 */
data class PackageAssignmentDisplay(
    val outcome: AssignmentOutcomeDto.Outcome,
    /** Why it isn't on a shift. Set for [deferred] and [skipped] outcomes; null otherwise. */
    val reason: AssignmentOutcomeDto.Reason?,
    /** 1-based position on the route, as a driver counts stops. Null unless assigned. */
    val stopNumber: Int?,
    /** Planner ETA as `HH:MM`, or null when the shift has no times yet (or it isn't assigned). */
    val estimatedArrival: String?,
    /** How many other packages were bumped to make room. Zero in the normal case. */
    val evictedCount: Int,
) {
    /** True when the package is on a shift — whether it joined one or caused one to be opened. */
    val isAssigned: Boolean
        get() = outcome == AssignmentOutcomeDto.Outcome.assigned ||
            outcome == AssignmentOutcomeDto.Outcome.assigned_new_shift

    /** True when assignment had to open a new shift, which spends one of the org's allowance. */
    val openedNewShift: Boolean
        get() = outcome == AssignmentOutcomeDto.Outcome.assigned_new_shift
}

/**
 * Reduces an [AssignmentOutcomeDto] to its display form. `shift` is only populated for the two
 * assigned outcomes, so a deferred/skipped result never shows a stop number even if the backend
 * were to send one.
 */
fun AssignmentOutcomeDto.toDisplay(): PackageAssignmentDisplay {
    val assignedShift = shift?.takeIf {
        outcome == AssignmentOutcomeDto.Outcome.assigned ||
            outcome == AssignmentOutcomeDto.Outcome.assigned_new_shift
    }
    return PackageAssignmentDisplay(
        outcome = outcome,
        reason = reason,
        // stopIndex is zero-based on the wire; drivers count from one.
        stopNumber = assignedShift?.stopIndex?.toInt()?.plus(1),
        estimatedArrival = assignedShift?.estimatedArrival
            ?.let { isoDateTimeToHourMinute(it.toString()) }
            ?.let { (hour, minute) -> formatHourMinute(hour, minute) },
        evictedCount = evictedPackageIds.size,
    )
}
