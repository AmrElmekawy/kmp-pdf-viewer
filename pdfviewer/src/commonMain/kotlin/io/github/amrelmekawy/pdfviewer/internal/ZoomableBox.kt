package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hosts one page's [content] with pinch zoom, pan while zoomed, and double-tap zoom/reset.
 *
 * At scale 1, single-finger drags are left unconsumed so the surrounding pager can swipe.
 * The zoom resets when the page stops being [isCurrent].
 *
 * @param bounds the viewport and the centered content size, in pixels.
 * @param onZoomedChange called with whether the page is zoomed, while it [isCurrent].
 * @param onZoomSettled called with the scale once a gesture or animation ends.
 */
@Composable
internal fun ZoomableBox(
    bounds: ZoomBounds,
    isCurrent: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onZoomSettled: (scale: Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var transform by remember { mutableStateOf(ZoomTransform()) }
    val isZoomed by remember { derivedStateOf { transform.isZoomed } }
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnZoomSettled by rememberUpdatedState(onZoomSettled)
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }

    fun settle() {
        transform = transform.settled()
        currentOnZoomSettled(transform.scale)
    }

    LaunchedEffect(isCurrent, isZoomed) {
        if (isCurrent) onZoomedChange(isZoomed)
    }
    LaunchedEffect(bounds) {
        transform = transform.clampedTo(bounds)
    }
    LaunchedEffect(isCurrent) {
        if (!isCurrent && transform != ZoomTransform()) {
            animation?.cancel()
            transform = ZoomTransform()
            currentOnZoomSettled(transform.scale)
        }
    }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val start = transform
                            val target = start.doubleTapTarget(tap.x, tap.y, currentBounds)
                            animation?.cancel()
                            animation =
                                scope.launch {
                                    animate(0f, 1f) { fraction, _ -> transform = start.lerp(target, fraction) }
                                    settle()
                                }
                        },
                    )
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transformed = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2 || transform.isZoomed) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    animation?.cancel()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    transform =
                                        transform.transformedBy(zoom, centroid.x, centroid.y, pan.x, pan.y, currentBounds)
                                    transformed = true
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (transformed) settle()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = transform.offsetX
                translationY = transform.offsetY
            },
        ) {
            content()
        }
    }
}
