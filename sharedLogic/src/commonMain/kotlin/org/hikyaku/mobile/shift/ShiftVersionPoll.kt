package org.hikyaku.mobile.shift

import org.hikyaku.mobile.api.generated.models.ShiftVersionDto

/**
 * Something changed the driver's shift after they opened it — the backend assigned a new package to
 * it, or a replan reordered the stops.
 *
 * Deliberately *not* acted on automatically. Re-routing a driver who is already following a route
 * is a safety problem, not a UX detail, so this only ever becomes an offer ("tap to reload") that
 * the driver accepts.
 */
data class ShiftUpdateNotice(
    /** The revision that produced this notice, so the same change can't be announced twice. */
    val revision: Double,
    /**
     * Stops that appeared since the last known plan. Zero when the plan changed some other way —
     * a reorder, or an ETA rewrite after a replan.
     */
    val addedStops: Int,
)

/**
 * The decision half of the live-shift version poll: when to ask `GET /api/v1/shifts/{id}/version`,
 * and whether the answer is worth telling the driver about.
 *
 * Split out from [ShiftDetailViewModel] because everything interesting here is a decision, not I/O:
 * the ViewModel keeps a plain `delay`-driven loop and asks this class what to do. That keeps the
 * rules — poll only while the screen is resumed, never while a shift is being driven, never
 * announce a revision twice — testable offline with no fakes and no virtual clock.
 *
 * Not thread-safe: it is only ever touched from the ViewModel's main-dispatcher coroutines.
 */
class ShiftVersionPoll {

    /** True between [resume] and [pause] — i.e. while the shift screen is actually on screen. */
    var isResumed: Boolean = false
        private set

    /**
     * The last version this poll has accounted for. Null until the first successful poll, whose job
     * is only to establish the baseline: the route load doesn't carry a revision, so the first
     * answer is "what the driver is already looking at", never news.
     */
    private var knownRevision: Double? = null
    private var knownStopCount: Int? = null

    /**
     * Marks the screen resumed. Returns true when a poll should fire immediately rather than waiting
     * out the interval — coming back to the screen is exactly when the driver most wants to know
     * that the shift moved under them.
     */
    fun resume(): Boolean {
        isResumed = true
        return true
    }

    /** Marks the screen paused. [shouldPoll] is false until the next [resume]. */
    fun pause() {
        isResumed = false
    }

    /**
     * Forgets the baseline, so the next observed version re-establishes it silently. Called when the
     * route is (re)loaded: whatever the server says at that point is what the driver is looking at.
     */
    fun reset() {
        knownRevision = null
        knownStopCount = null
    }

    /**
     * Whether a poll should be issued right now.
     *
     * [shiftStarted] gates it as hard as [isResumed] does: once the driver is running the shift the
     * plan is theirs, reloading it would reset the in-progress session, and a banner about stops
     * being shuffled is a distraction at the wheel. Tier 1 refuses to assign to a dispatched shift
     * anyway, so there is nothing to learn.
     */
    fun shouldPoll(shiftStarted: Boolean): Boolean = isResumed && !shiftStarted

    /**
     * Feeds a freshly polled [version] in. Returns a [ShiftUpdateNotice] only when the plan actually
     * moved: the same revision twice is silence, and so is a response that lands after the screen
     * has already paused.
     */
    fun observe(version: ShiftVersionDto): ShiftUpdateNotice? {
        if (!isResumed) return null

        val previousRevision = knownRevision
        val previousStopCount = knownStopCount
        knownRevision = version.revision
        knownStopCount = version.stopCount.toInt()

        // First answer after a (re)load is the baseline, not news.
        if (previousRevision == null) return null
        if (previousRevision == version.revision) return null

        val added = version.stopCount.toInt() - (previousStopCount ?: version.stopCount.toInt())
        return ShiftUpdateNotice(
            revision = version.revision,
            addedStops = if (added > 0) added else 0,
        )
    }
}
