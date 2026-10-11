package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A [PdfDocument] for common tests that follows the same lock-and-closed-flag contract. */
internal class FakePdfDocument(
    override val pageCount: Int = 3,
    private val size: Size = Size(600f, 800f),
) : PdfDocument {
    private val lock = Mutex()

    /** When set, renders suspend until it completes. */
    var renderGate: CompletableDeferred<Unit>? = null
    var renderCount = 0
        private set
    var closeCount = 0
        private set
    val isClosed: Boolean get() = closeCount > 0

    override fun pageSize(index: Int): Size = size

    override suspend fun renderPage(
        index: Int,
        widthPx: Int,
    ): ImageBitmap =
        lock.withLock {
            if (isClosed) throw CancellationException("closed")
            renderCount++
            renderGate?.await()
            FakeImageBitmap(widthPx, bitmapHeightFor(size, widthPx))
        }

    override suspend fun close() {
        lock.withLock { closeCount++ }
    }
}

/** An [ImageBitmap] without pixels, so common tests run on the JVM host too. */
internal class FakeImageBitmap(
    override val width: Int,
    override val height: Int,
) : ImageBitmap {
    override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
    override val hasAlpha: Boolean get() = false
    override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888

    override fun prepareToDraw() = Unit

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit
}
