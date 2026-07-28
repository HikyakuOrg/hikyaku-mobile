package org.hikyaku.mobile.shift.location.model

/** A single device location reading. `speed` is metres/second when the platform reports it. */
data class DeviceLocation(
    val lat: Double,
    val lng: Double,
    val speed: Double? = null,
)
