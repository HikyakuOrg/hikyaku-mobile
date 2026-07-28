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
}
