package org.hikyaku.mobile.shift.model

/** Delivery counts for a shift. A shift is complete once every package is delivered. */
data class ShiftProgress(val delivered: Int, val total: Int) {
    val isComplete: Boolean get() = total > 0 && delivered == total

    /** True once any package has been delivered, whether or not the rest have too. */
    val hasDelivered: Boolean get() = delivered > 0
}
