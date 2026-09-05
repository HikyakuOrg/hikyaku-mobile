package org.hikyaku.mobile.shift.departure

import org.hikyaku.mobile.shift.departure.model.PendingDeparture

/**
 * Platform seam for the auto-start-on-warehouse-departure safety net.
 *
 * On Android [arm] registers a warehouse geofence and an activity-transition request (both
 * system-managed, delivered via a broadcast even when the app is backgrounded or killed) and
 * persists the [PendingDeparture]; [disarm] removes them and clears the persisted context.
 *
 * On iOS both are no-ops: the feature is Android-only for now.
 */
expect class DepartureWatcher() {
    /** Arms the geofence + activity-transition detectors and persists [pending]. */
    fun arm(pending: PendingDeparture)

    /** Removes the detectors and clears the persisted [PendingDeparture]. */
    fun disarm()
}
