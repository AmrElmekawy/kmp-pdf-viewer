package io.github.amrelmekawy.pdfviewer.internal

import kotlin.math.abs

/**
 * The zoom of one page: a [scale] around the viewport center, then an offset in pixels.
 *
 * A content point `p`, measured from the viewport center, is drawn at `p * scale + offset`.
 * This is pure math with no Compose dependency, so it is unit-tested directly.
 */
internal data class ZoomTransform(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > MIN_SCALE

    /**
     * Zooms by [zoom] keeping the point under ([centroidX], [centroidY]) fixed, then pans by
     * ([panX], [panY]). Coordinates are viewport pixels from the top-left corner.
     */
    fun transformedBy(
        zoom: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        bounds: ZoomBounds,
    ): ZoomTransform {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val factor = newScale / scale
        val cx = centroidX - bounds.viewportWidth / 2
        val cy = centroidY - bounds.viewportHeight / 2
        return ZoomTransform(
            scale = newScale,
            offsetX = cx - (cx - offsetX) * factor + panX,
            offsetY = cy - (cy - offsetY) * factor + panY,
        ).clampedTo(bounds)
    }

    /** The double-tap target: reset when zoomed, otherwise zoom to [DOUBLE_TAP_SCALE] on the tap. */
    fun doubleTapTarget(
        tapX: Float,
        tapY: Float,
        bounds: ZoomBounds,
    ): ZoomTransform =
        if (isZoomed) {
            ZoomTransform()
        } else {
            transformedBy(DOUBLE_TAP_SCALE / scale, tapX, tapY, 0f, 0f, bounds)
        }

    /** Limits the offset so the scaled content never leaves the viewport. */
    fun clampedTo(bounds: ZoomBounds): ZoomTransform {
        val maxX = ((bounds.contentWidth * scale - bounds.viewportWidth) / 2).coerceAtLeast(0f)
        val maxY = ((bounds.contentHeight * scale - bounds.viewportHeight) / 2).coerceAtLeast(0f)
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** Linear interpolation towards [target]; [fraction] 0 is this, 1 is [target]. */
    fun lerp(
        target: ZoomTransform,
        fraction: Float,
    ): ZoomTransform =
        ZoomTransform(
            scale = scale + (target.scale - scale) * fraction,
            offsetX = offsetX + (target.offsetX - offsetX) * fraction,
            offsetY = offsetY + (target.offsetY - offsetY) * fraction,
        )

    /** Snaps a scale within rounding error of 1 back to exactly 1, so paging re-enables. */
    fun settled(): ZoomTransform = if (abs(scale - MIN_SCALE) < SNAP_EPSILON) ZoomTransform() else this

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        private const val SNAP_EPSILON = 0.01f
    }
}

/** The viewport size and the size of the content centered in it, both in pixels. */
internal data class ZoomBounds(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
)
