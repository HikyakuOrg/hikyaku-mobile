package org.hikyaku.mobile.shift.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.hikyaku.mobile.shift.location.model.DeviceLocation

/**
 * iOS stub. Real implementation (CoreLocation `CLLocationManager`) to be added on a Mac; until
 * then the flow is empty so a shift simply doesn't stream location on iOS.
 */
actual class LocationProvider {
    actual fun locationUpdates(): Flow<DeviceLocation> = emptyFlow()

    actual suspend fun currentLocation(): DeviceLocation? = null
}
