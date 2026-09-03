package org.hikyaku.mobile.vehicles.vin.model

/**
 * What the add-vehicle form needs from a VIN decode: enough to prefill make/model/year. See
 * [org.hikyaku.mobile.vehicles.vin.VinDecodeRepository].
 */
data class VinDecodeResult(
    val make: String?,
    val model: String?,
    val year: Int?,
) {
    /** True once every field came back, so there's nothing left for the user to fill in by hand. */
    val isComplete: Boolean get() = make != null && model != null && year != null
}
