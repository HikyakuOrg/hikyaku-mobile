package org.hikyaku.mobile.shift.pod

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * SignatureState builds an androidx.compose.ui.graphics.Path, which on Android is backed by
 * android.graphics.Path -- moveTo/quadraticTo/lineTo and getBounds() call into the real graphics
 * stack, so (like VinRecognitionDeviceTest) this needs a real device rather than a plain JVM test.
 *
 * Run with:
 * ```
 * ./gradlew :sharedUI:connectedAndroidDeviceTest --tests "org.hikyaku.mobile.shift.pod.SignatureStateDeviceTest"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SignatureStateDeviceTest {

    @Test
    fun singleStrokeProducesANonEmptyPathWithinItsInputBounds() {
        val state = SignatureState()
        assertFalse(state.canUndo)

        state.startStroke(Offset(10f, 10f))
        state.continueStroke(Offset(50f, 60f))
        state.continueStroke(Offset(90f, 20f))
        state.endStroke()

        assertTrue(state.canUndo, "a completed stroke should be recorded")
        val bounds = state.strokes.single().path.getBounds()
        // quadraticTo's control points can pull the curve slightly past the raw input points, so
        // this checks the path stayed near the drag rather than pinning an exact rectangle.
        assertTrue(bounds.left in 0f..20f, "unexpected left bound: ${bounds.left}")
        assertTrue(bounds.right in 80f..100f, "unexpected right bound: ${bounds.right}")
        assertTrue(bounds.top in 0f..20f, "unexpected top bound: ${bounds.top}")
        assertTrue(bounds.bottom in 50f..70f, "unexpected bottom bound: ${bounds.bottom}")
    }

    @Test
    fun undoRemovesOnlyTheLastStroke() {
        val state = SignatureState()
        state.startStroke(Offset.Zero)
        state.continueStroke(Offset(5f, 5f))
        state.endStroke()
        state.startStroke(Offset(20f, 20f))
        state.continueStroke(Offset(25f, 25f))
        state.endStroke()
        assertEquals(2, state.strokes.size)

        state.undo()
        assertEquals(1, state.strokes.size)
        assertTrue(state.canUndo)

        state.undo()
        assertEquals(0, state.strokes.size)
        assertFalse(state.canUndo)
    }

    @Test
    fun clearDropsEveryStrokeAndAnyInProgressDrag() {
        val state = SignatureState()
        state.startStroke(Offset.Zero)
        state.continueStroke(Offset(5f, 5f))
        state.endStroke()
        state.startStroke(Offset(1f, 1f)) // left mid-drag, deliberately not ended

        state.clear()

        assertFalse(state.canUndo)
        assertEquals(null, state.currentPath)
    }

    @Test
    fun endStrokeWithoutAStartIsANoOp() {
        val state = SignatureState()
        state.endStroke() // no startStroke first
        assertFalse(state.canUndo)
    }
}
