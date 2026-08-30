package org.hikyaku.mobile.shift.location

import kotlinx.coroutines.flow.Flow
import org.hikyaku.mobile.shift.location.model.DeviceLocation

/**
 * Streams the device's location while a shift is running. The caller is responsible for holding
 * the location permission before collecting; without it the flow completes (or errors) without
 * emitting. Android backs this with the system `LocationManager`; other platforms are stubbed.
 */
expect class LocationProvider() {
    fun locationUpdates(): Flow<DeviceLocation>

    /**
     * A single best-effort location fix (e.g. to stamp the courier's position onto a POD photo's
     * EXIF data at capture time), rather than committing to a continuous stream. Returns null if
     * a fix can't be obtained in time (permission missing, no signal, timeout) — callers should
     * treat the tagging it enables as optional, never block on it.
     */
    suspend fun currentLocation(): DeviceLocation?
}
