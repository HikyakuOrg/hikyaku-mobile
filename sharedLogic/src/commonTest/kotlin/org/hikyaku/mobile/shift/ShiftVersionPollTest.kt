package org.hikyaku.mobile.shift

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import org.hikyaku.mobile.api.generated.models.ShiftVersionDto

/**
 * Tests the live-shift version poll's rules. [ShiftVersionPoll] deliberately holds every decision
 * and no I/O — the ViewModel contributes only a `delay` loop — so all three behaviours the feature
 * rests on (fire only while resumed, stop on pause, reload only on a revision change) are checked
 * here with no fakes, no network and no virtual clock.
 */
class ShiftVersionPollTest {

    private fun version(revision: Double, stopCount: Int = 5) = ShiftVersionDto(
        id = "shift-1",
        revision = revision,
        updatedAt = Instant.parse("2026-09-01T09:00:00Z"),
        stopCount = stopCount.toDouble(),
        status = ShiftVersionDto.Status.planned,
    )

    /** Resumes and swallows the baseline answer, leaving the poll ready to report real changes. */
    private fun resumedAt(revision: Double, stopCount: Int = 5): ShiftVersionPoll {
        val poll = ShiftVersionPoll()
        poll.resume()
        assertNull(poll.observe(version(revision, stopCount)), "the first answer is the baseline")
        return poll
    }

    @Test
    fun doesNotPollBeforeTheScreenResumes() {
        val poll = ShiftVersionPoll()
        assertFalse(poll.isResumed)
        assertFalse(poll.shouldPoll(shiftStarted = false))
    }

    @Test
    fun pollsOnceResumedAndAsksForAnImmediateCheck() {
        val poll = ShiftVersionPoll()
        assertTrue(poll.resume(), "resuming should ask for an immediate poll, not wait 30s")
        assertTrue(poll.isResumed)
        assertTrue(poll.shouldPoll(shiftStarted = false))
    }

    @Test
    fun stopsPollingOnPause() {
        val poll = ShiftVersionPoll()
        poll.resume()
        poll.pause()
        assertFalse(poll.isResumed)
        assertFalse(poll.shouldPoll(shiftStarted = false))
    }

    @Test
    fun doesNotPollWhileTheShiftIsBeingDriven() {
        // Reloading a running shift would reset the in-progress session, and Tier 1 refuses to
        // assign to a dispatched shift anyway, so there is nothing to learn.
        val poll = ShiftVersionPoll()
        poll.resume()
        assertFalse(poll.shouldPoll(shiftStarted = true))
    }

    @Test
    fun firstAnswerAfterALoadIsTheBaselineNotNews() {
        val poll = ShiftVersionPoll()
        poll.resume()
        assertNull(poll.observe(version(revision = 7.0)))
    }

    @Test
    fun unchangedRevisionReportsNothing() {
        val poll = resumedAt(revision = 7.0)
        assertNull(poll.observe(version(revision = 7.0)))
        assertNull(poll.observe(version(revision = 7.0)))
    }

    @Test
    fun aChangedRevisionReportsHowManyStopsAppeared() {
        val poll = resumedAt(revision = 7.0, stopCount = 5)
        val notice = assertNotNull(poll.observe(version(revision = 8.0, stopCount = 6)))
        assertEquals(8.0, notice.revision)
        assertEquals(1, notice.addedStops)
    }

    @Test
    fun aReorderReportsAChangeWithNoAddedStops() {
        // A replan can rewrite the order and the ETAs without the stop count moving.
        val poll = resumedAt(revision = 7.0, stopCount = 5)
        val notice = assertNotNull(poll.observe(version(revision = 8.0, stopCount = 5)))
        assertEquals(0, notice.addedStops)
    }

    @Test
    fun aRemovedStopIsReportedAsAChangeNotANegativeCount() {
        val poll = resumedAt(revision = 7.0, stopCount = 5)
        val notice = assertNotNull(poll.observe(version(revision = 8.0, stopCount = 4)))
        assertEquals(0, notice.addedStops)
    }

    @Test
    fun theSameChangeIsNeverAnnouncedTwice() {
        val poll = resumedAt(revision = 7.0, stopCount = 5)
        assertNotNull(poll.observe(version(revision = 8.0, stopCount = 6)))
        assertNull(poll.observe(version(revision = 8.0, stopCount = 6)))
    }

    @Test
    fun anAnswerLandingAfterPauseIsIgnored() {
        // The request is in flight when the driver leaves the screen; its response must not raise a
        // snackbar over whatever they moved on to.
        val poll = resumedAt(revision = 7.0)
        poll.pause()
        assertNull(poll.observe(version(revision = 8.0, stopCount = 9)))
    }

    @Test
    fun reloadingTheRouteRebaselinesSoTheReloadedPlanIsNotReportedBack() {
        val poll = resumedAt(revision = 7.0, stopCount = 5)
        assertNotNull(poll.observe(version(revision = 8.0, stopCount = 6)))

        // The driver accepted the reload; the route now on screen is revision 8.
        poll.reset()
        assertNull(poll.observe(version(revision = 8.0, stopCount = 6)))
        assertNull(poll.observe(version(revision = 8.0, stopCount = 6)))
        assertNotNull(poll.observe(version(revision = 9.0, stopCount = 7)))
    }
}
