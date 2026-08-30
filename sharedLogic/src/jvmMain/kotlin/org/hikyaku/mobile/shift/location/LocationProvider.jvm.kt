package org.hikyaku.mobile.shift.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.hikyaku.mobile.shift.location.model.DeviceLocation

/** Desktop has no location source; the flow is empty. */
actual class LocationProvider {
    actual fun locationUpdates(): Flow<DeviceLocation> = emptyFlow()

    actual suspend fun currentLocation(): DeviceLocation? = null
}
