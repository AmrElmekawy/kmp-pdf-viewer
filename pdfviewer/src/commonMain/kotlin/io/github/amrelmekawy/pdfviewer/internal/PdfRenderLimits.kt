package io.github.amrelmekawy.pdfviewer.internal

/** Internal limits for memory and downloads, kept in one place so they are easy to tune and test. */
internal object PdfRenderLimits {
    /** The longest side of one rendered bitmap. */
    const val MAX_BITMAP_SIDE_PX = 4096

    /** The pixel count of one rendered bitmap (about 32 MiB as RGBA). */
    const val MAX_BITMAP_PIXELS = 8_000_000L

    const val BYTES_PER_PIXEL = 4

    const val CACHE_MAX_ENTRIES = 5
    const val CACHE_MAX_BYTES = 64L * 1024 * 1024

    const val MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024
    const val CONNECT_TIMEOUT_MILLIS = 15_000
    const val READ_TIMEOUT_MILLIS = 30_000
}
