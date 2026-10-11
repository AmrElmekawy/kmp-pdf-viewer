package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import io.github.amrelmekawy.pdfviewer.PdfSource

/**
 * An open native PDF document. This is the only platform seam of the library.
 *
 * Implementations guard every native call and [close] with one per-document lock and a
 * `closed` flag, so [close] waits for an in-flight render and nothing renders afterwards.
 */
internal interface PdfDocument {
    val pageCount: Int

    /** Page size in PDF points, rotation applied. Read from memory; safe on the main thread. */
    fun pageSize(index: Int): Size

    /**
     * Renders page [index] at [widthPx] wide, keeping the aspect ratio, on a white background.
     * Throws [kotlinx.coroutines.CancellationException] if the document is closed.
     */
    suspend fun renderPage(
        index: Int,
        widthPx: Int,
    ): ImageBitmap

    /** Frees native resources after any in-flight render finishes. Later calls do nothing. */
    suspend fun close()
}

/**
 * Opens [source] off the main thread. A [PdfSource.Remote] is downloaded into [cacheDir]
 * first; [bypassCache] discards a previously downloaded copy.
 *
 * Throws [PdfException] for expected failures.
 */
internal expect suspend fun openPdfDocument(
    source: PdfSource,
    cacheDir: CacheDir,
    bypassCache: Boolean,
): PdfDocument

/** The directory the library may use for its own temporary and downloaded files. */
internal class CacheDir(
    val path: String,
)

@Composable
internal expect fun rememberCacheDir(): CacheDir
