package org.hikyaku.mobile.shift.pod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal class SignatureStroke(val path: Path, val color: Color, val strokeWidth: Float)

/** Drag-drawn path state for [SignaturePad]; export the result with [exportSignature]. */
@Stable
class SignatureState {
    internal val strokes = mutableStateListOf<SignatureStroke>()

    // quadraticTo appends to the same Path instance in place, so a default-equality mutableStateOf
    // would see "same reference written back" as unchanged and skip redrawing mid-stroke.
    // neverEqualPolicy forces every point added to actually invalidate the draw phase.
    var currentPath by mutableStateOf<Path?>(null, neverEqualPolicy())
        private set

    var strokeColor: Color = Color.Black
    var strokeWidth: Float = 6f

    private var lastPoint = Offset.Zero

    val canUndo: Boolean get() = strokes.isNotEmpty()

    fun startStroke(point: Offset) {
        currentPath = Path().apply { moveTo(point.x, point.y) }
        lastPoint = point
    }

    fun continueStroke(point: Offset) {
        val path = currentPath ?: return
        val mid = Offset((point.x + lastPoint.x) / 2f, (point.y + lastPoint.y) / 2f)
        path.quadraticTo(lastPoint.x, lastPoint.y, mid.x, mid.y)
        lastPoint = point
        currentPath = path
    }

    fun endStroke() {
        val path = currentPath ?: return
        path.lineTo(lastPoint.x, lastPoint.y)
        strokes.add(SignatureStroke(path, strokeColor, strokeWidth))
        currentPath = null
    }

    fun undo() {
        strokes.removeLastOrNull()
    }

    fun clear() {
        strokes.clear()
        currentPath = null
    }
}

@Composable
fun rememberSignatureState(): SignatureState = remember { SignatureState() }

private fun DrawScope.drawSignatureContent(state: SignatureState) {
    drawRect(Color.White) // opaque backing so the exported PNG isn't transparent
    val style = Stroke(width = state.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (stroke in state.strokes) drawPath(stroke.path, color = stroke.color, style = style)
    state.currentPath?.let { drawPath(it, color = state.strokeColor, style = style) }
}

/** A full-bleed drag-to-sign surface; wrap it with your own border/frame and size it. */
@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.pointerInput(state) {
            detectDragGestures(
                onDragStart = { state.startStroke(it) },
                onDrag = { change, _ -> change.consume(); state.continueStroke(change.position) },
                onDragEnd = { state.endStroke() },
                onDragCancel = { state.endStroke() },
            )
        },
    ) { drawSignatureContent(state) }
}

/**
 * Rasterizes [state] at [size] px. [GraphicsLayer.record] draws offscreen, outside any
 * composable's own layout pass, so density/layout direction have to be passed in explicitly
 * rather than read from the current composition.
 */
suspend fun exportSignature(
    state: SignatureState,
    graphicsLayer: GraphicsLayer,
    density: Density,
    layoutDirection: LayoutDirection,
    size: IntSize,
): ImageBitmap {
    graphicsLayer.record(density, layoutDirection, size) { drawSignatureContent(state) }
    return graphicsLayer.toImageBitmap()
}
