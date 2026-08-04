package org.hikyaku.mobile.packages.optimisation

/**
 * The full-screen optimise-progress dialog's state; non-null on
 * [org.hikyaku.mobile.packages.PackagesUiState] while a one-click warehouse optimisation run is
 * being started, polled, or has just finished. The backend doesn't report partial progress within
 * a run (`GET /api/v1/optimisation/run/latest` only exposes its lifecycle status), so [phase]
 * drives an indeterminate spinner rather than a fraction-complete one; [packageCount] is resolved
 * client-side before the run starts and shown alongside it.
 */
data class OptimisationProgress(
    val packageCount: Int,
    val phase: Phase = Phase.RUNNING,
    /** Failure detail, set only once [phase] is [Phase.FAILED]. */
    val message: String? = null,
) {
    enum class Phase { RUNNING, SUCCEEDED, FAILED }
}
