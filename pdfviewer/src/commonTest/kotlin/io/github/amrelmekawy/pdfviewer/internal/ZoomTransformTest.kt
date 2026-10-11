package io.github.amrelmekawy.pdfviewer.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoomTransformTest {
    // A 1000x1000 viewport showing a centered 1000x500 page.
    private val bounds = ZoomBounds(1000f, 1000f, 1000f, 500f)

    @Test
    fun zoomKeepsTheCentroidFixed() {
        val zoomed = ZoomTransform().transformedBy(2f, centroidX = 750f, centroidY = 500f, panX = 0f, panY = 0f, bounds)
        // The point 250px right of center moves to 500px, so offset compensates by -250.
        assertEquals(ZoomTransform(scale = 2f, offsetX = -250f, offsetY = 0f), zoomed)
    }

    @Test
    fun scaleIsClampedToRange() {
        assertEquals(5f, ZoomTransform().transformedBy(10f, 500f, 500f, 0f, 0f, bounds).scale)
        assertEquals(1f, ZoomTransform(scale = 2f).transformedBy(0.1f, 500f, 500f, 0f, 0f, bounds).scale)
    }

    @Test
    fun panIsClampedSoThePageNeverLeavesTheViewport() {
        val panned = ZoomTransform(scale = 2f).transformedBy(1f, 500f, 500f, panX = 5000f, panY = 5000f, bounds)
        // Width 2000 in a 1000 viewport may move 500 each way; height 1000 fits exactly, so no vertical pan.
        assertEquals(ZoomTransform(scale = 2f, offsetX = 500f, offsetY = 0f), panned)
    }

    @Test
    fun noPanAtScaleOne() {
        val panned = ZoomTransform().transformedBy(1f, 500f, 500f, panX = 300f, panY = 300f, bounds)
        assertEquals(ZoomTransform(), panned)
    }

    @Test
    fun doubleTapZoomsOnTheTapThenResets() {
        val zoomed = ZoomTransform().doubleTapTarget(tapX = 600f, tapY = 500f, bounds)
        assertEquals(ZoomTransform.DOUBLE_TAP_SCALE, zoomed.scale)
        assertEquals(-150f, zoomed.offsetX)
        assertEquals(ZoomTransform(), zoomed.doubleTapTarget(600f, 500f, bounds))
    }

    @Test
    fun settledSnapsNearOneToExactlyOne() {
        assertEquals(ZoomTransform(), ZoomTransform(scale = 1.004f, offsetX = 2f).settled())
        assertTrue(ZoomTransform(scale = 1.5f).settled().isZoomed)
    }

    @Test
    fun lerpInterpolatesAllFields() {
        val half = ZoomTransform().lerp(ZoomTransform(3f, 100f, -50f), 0.5f)
        assertEquals(ZoomTransform(2f, 50f, -25f), half)
    }
}
