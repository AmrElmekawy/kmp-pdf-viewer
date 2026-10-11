package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderSizeTest {
    @Test
    fun keepsTheRequestedWidthWithinLimits() {
        assertEquals(RenderSize(1000, 1294), renderSizeFor(Size(612f, 792f), 1000))
    }

    @Test
    fun clampsTheLongestSide() {
        val size = renderSizeFor(Size(100f, 1000f), 1000)
        assertTrue(size.heightPx in 4080..4096, "$size")
        assertEquals(409, size.widthPx)
    }

    @Test
    fun clampsThePixelCount() {
        val size = renderSizeFor(Size(1000f, 1000f), 4000)
        assertTrue(size.widthPx.toLong() * size.heightPx <= PdfRenderLimits.MAX_BITMAP_PIXELS)
        assertTrue(size.widthPx >= 2820)
    }

    @Test
    fun neverExceedsLimitsForManyShapes() {
        for (aspect in listOf(0.1f, 0.5f, 0.77f, 1f, 1.29f, 3f, 10f)) {
            for (width in listOf(1, 500, 1080, 2400, 5400, 20_000)) {
                val size = renderSizeFor(Size(1000f, 1000f * aspect), width)
                assertTrue(size.widthPx in 1..PdfRenderLimits.MAX_BITMAP_SIDE_PX, "$aspect $width $size")
                assertTrue(size.heightPx in 1..PdfRenderLimits.MAX_BITMAP_SIDE_PX, "$aspect $width $size")
                assertTrue(size.widthPx.toLong() * size.heightPx <= PdfRenderLimits.MAX_BITMAP_PIXELS, "$aspect $width $size")
            }
        }
    }

    @Test
    fun byteCountIsRgba() {
        assertEquals(400L, RenderSize(10, 10).byteCount)
    }

    @Test
    fun fitKeepsAspectRatioInsideTheViewport() {
        assertEquals(Size(500f, 1000f), fit(Size(100f, 200f), Size(1000f, 1000f)))
        assertEquals(Size(1000f, 250f), fit(Size(400f, 100f), Size(1000f, 1000f)))
        assertEquals(Size.Zero, fit(Size(0f, 100f), Size(1000f, 1000f)))
    }
}
