package org.hikyaku.mobile.shift.departure

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import org.hikyaku.mobile.auth.SupabaseClientProvider
import org.hikyaku.mobile.environment.EnvironmentRepository
import org.hikyaku.mobile.shift.ShiftActionsRepository
import org.hikyaku.mobile.shift.ShiftDetailRepository
import org.hikyaku.mobile.shift.departure.model.PendingDeparture
import org.hikyaku.mobile.shift.session.ShiftStatus
import org.hikyaku.mobile.shift.session.isWithinAutoStartWindow
import org.hikyaku.mobile.shift.session.model.ShiftPhase
import org.hikyaku.mobile.shift.session.model.ShiftSession
import org.hikyaku.mobile.shift.tracking.ShiftTracker

/**
 * The shared brain of the auto-start safety net, called by the Android receiver each time a
 * geofence or activity-transition signal arrives. It updates the accumulated [PendingDeparture]
 * flags and, once all conditions hold, performs a full shift start (mark first stop `IN_TRANSIT`,
 * persist an `IN_PROGRESS` session, begin background tracking) — the same outcome as the manual
 * Start button, but headless.
 *
 * Kept in shared code (no Android types) so the platform receiver stays thin and the orchestration
 * is testable. Backend-dependent collaborators are built lazily, only after [ensureBackendReady],
 * because a freshly-woken process may not have initialised the Supabase client yet.
 */
class AutoStartCoordinator(
    private val pendingStore: PendingDepartureStore = PendingDepartureStore(),
    private val watcher: DepartureWatcher = DepartureWatcher(),
) {
    /** What [onSignal] did, so the caller can decide whether to surface a notification. */
    enum class Outcome {
        /** Flags updated; not all conditions met yet (keep listening). */
        PENDING,

        /** Outside the window / already in transit / nothing pending: detectors removed. */
        DISARMED,

        /** A full shift start was performed; the caller should notify the driver. */
        SHIFT_STARTED,
    }

    /**
     * Applies [update] to the persisted pending departure (e.g. setting a flag for the signal that
     * just arrived) and evaluates whether to auto-start. [update] must be a pure copy of the flags.
     */
    suspend fun onSignal(update: (PendingDeparture) -> PendingDeparture): Outcome {
        val pending = pendingStore.load() ?: run {
            watcher.disarm()
            return Outcome.DISARMED
        }
        val updated = update(pending)
        pendingStore.save(updated)

        // The scheduled-time gate: outside the window the shift isn't "now", so tear down.
        if (!isWithinAutoStartWindow(updated.scheduledStart)) {
            watcher.disarm()
            return Outcome.DISARMED
        }
        if (!updated.departureConfirmed) return Outcome.PENDING

        // From here we touch the backend; bail (and retry on the next signal) if it isn't ready.
        if (!ensureBackendReady()) return Outcome.PENDING

        val actions = ShiftActionsRepository()
        if (actions.isInTransit(updated.firstPackageId)) {
            // The driver already started manually; nothing to do.
            watcher.disarm()
            return Outcome.DISARMED
        }

        // The load-scanning gate: never auto-start with an unloaded van. [updated.packageIds] is
        // empty for an ad-hoc shift or a record persisted before this gate shipped, in which case
        // it doesn't apply — same as the manual Start flow. Verified fresh on every signal (never a
        // snapshot), so scans finished after the driver already left keep this PENDING rather than
        // DISARMED, letting auto-start fire on a later signal once loading catches up.
        if (updated.packageIds.isNotEmpty()) {
            val statuses = ShiftDetailRepository().fetchCurrentStatuses(updated.packageIds).getOrNull()
                ?: return Outcome.PENDING // couldn't verify -> never start blind; retry next signal
            val allScanned = updated.packageIds.all { ShiftStatus.satisfiesScan(statuses[it]) }
            if (!allScanned) return Outcome.PENDING
        }

        if (actions.markInTransit(updated.firstPackageId).isFailure) return Outcome.PENDING

        val session = ShiftSession(
            shiftId = updated.shiftId,
            routeId = updated.routeId,
            orgSlug = updated.orgSlug,
            phase = ShiftPhase.IN_PROGRESS,
            inTransitPackageId = updated.firstPackageId,
            statusOverrides = mapOf(updated.firstPackageId to ShiftStatus.IN_TRANSIT),
            deliveriesComplete = false,
            depotLat = updated.depotLat,
            depotLng = updated.depotLng,
        )
        org.hikyaku.mobile.shift.session.ShiftSessionStore().save(session)
        ShiftTracker().start(session)
        watcher.disarm() // also clears the pending store
        return Outcome.SHIFT_STARTED
    }

    /**
     * Ensures the Supabase client is initialised (a cold-woken process may not have run the UI
     * bootstrap) and its persisted auth session has finished restoring, so the `IN_TRANSIT` insert
     * carries the JWT that RLS requires.
     */
    private suspend fun ensureBackendReady(): Boolean {
        if (!SupabaseClientProvider.isInitialized) {
            val stored = EnvironmentRepository().loadPersisted() ?: return false
            SupabaseClientProvider.initialize(stored.config)
        }
        val status = SupabaseClientProvider.client.auth.sessionStatus
            .first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated
    }
}
