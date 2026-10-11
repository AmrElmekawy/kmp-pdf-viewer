package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.ui.geometry.Size
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The pixel size of one rendered page bitmap. */
internal data class RenderSize(
    val widthPx: Int,
    val heightPx: Int,
) {
    val byteCount: Long get() = widthPx.toLong() * heightPx * PdfRenderLimits.BYTES_PER_PIXEL
}

/** The bitmap height for a page of [pageSize] rendered [widthPx] wide. Used by both platforms. */
internal fun bitmapHeightFor(
    pageSize: Size,
    widthPx: Int,
): Int = max(1, (widthPx * pageSize.height / pageSize.width).roundToInt())

/**
 * The size to render a page of [pageSize] at, as close to [requestedWidthPx] wide as the
 * per-bitmap limits in [PdfRenderLimits] allow.
 */
internal fun renderSizeFor(
    pageSize: Size,
    requestedWidthPx: Int,
): RenderSize {
    val aspect = pageSize.height / pageSize.width
    var width = max(1, requestedWidthPx).toDouble()

    val longestSide = max(width, width * aspect)
    if (longestSide > PdfRenderLimits.MAX_BITMAP_SIDE_PX) {
        width *= PdfRenderLimits.MAX_BITMAP_SIDE_PX / longestSide
    }
    val pixels = width * width * aspect
    if (pixels > PdfRenderLimits.MAX_BITMAP_PIXELS) {
        width *= sqrt(PdfRenderLimits.MAX_BITMAP_PIXELS / pixels)
    }

    // Rounding the height up can overshoot the pixel cap by a fraction of a row.
    var widthPx = max(1, floor(width).toInt())
    while (widthPx > 1 && widthPx.toLong() * bitmapHeightFor(pageSize, widthPx) > PdfRenderLimits.MAX_BITMAP_PIXELS) {
        widthPx--
    }
    return RenderSize(widthPx, bitmapHeightFor(pageSize, widthPx))
}
