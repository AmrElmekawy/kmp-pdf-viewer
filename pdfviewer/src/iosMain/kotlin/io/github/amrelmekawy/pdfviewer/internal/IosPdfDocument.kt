package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.amrelmekawy.pdfviewer.PdfError
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSFileManager
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.kPDFDisplayBoxCropBox

/**
 * A document rendered by PDFKit into a CoreGraphics bitmap.
 *
 * Every PDFKit call, and [close], runs under [lock] on [Dispatchers.Default].
 *
 * @param ownedFilePath a file created by the library, deleted on close.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPdfDocument private constructor(
    private var document: PDFDocument?,
    private val pageSizes: List<Size>,
    private val ownedFilePath: String? = null,
) : PdfDocument {
    private val lock = Mutex()
    private var closed = false

    override val pageCount: Int get() = pageSizes.size

    override fun pageSize(index: Int): Size = pageSizes[index]

    override suspend fun renderPage(
        index: Int,
        widthPx: Int,
    ): ImageBitmap =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val page = document?.pageAtIndex(index.toULong())
                if (closed || page == null) throw CancellationException("The document is closed.")
                autoreleasepool {
                    render(page, widthPx, bitmapHeightFor(pageSizes[index], widthPx), pageSizes[index])
                }
            }
        }

    override suspend fun close() {
        withContext(NonCancellable + Dispatchers.Default) {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                document = null
                ownedFilePath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
            }
        }
    }

    companion object {
        fun from(
            document: PDFDocument?,
            ownedFilePath: String? = null,
        ): IosPdfDocument {
            try {
                if (document == null) throw PdfException(PdfError.InvalidDocument)
                if (document.isLocked) throw PdfException(PdfError.PasswordProtected)
                val sizes =
                    List(document.pageCount.toInt()) { index ->
                        val page = document.pageAtIndex(index.toULong()) ?: throw PdfException(PdfError.InvalidDocument)
                        page.displaySize()
                    }
                return IosPdfDocument(document, sizes, ownedFilePath)
            } catch (e: Exception) {
                ownedFilePath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
                throw e
            }
        }
    }
}

/** The crop box size in points with the page rotation applied. */
@OptIn(ExperimentalForeignApi::class)
private fun PDFPage.displaySize(): Size {
    val (width, height) = boundsForBox(kPDFDisplayBoxCropBox).useContents { size.width to size.height }
    return if (rotation % 180 == 0L) Size(width.toFloat(), height.toFloat()) else Size(height.toFloat(), width.toFloat())
}

/**
 * Draws [page] into an RGBA premultiplied CoreGraphics bitmap and copies the pixels into a
 * Skia bitmap that owns its memory, so the CoreGraphics buffer can be freed right after.
 */
@OptIn(ExperimentalForeignApi::class)
private fun render(
    page: PDFPage,
    widthPx: Int,
    heightPx: Int,
    pageSize: Size,
): ImageBitmap {
    val rowBytes = widthPx * PdfRenderLimits.BYTES_PER_PIXEL
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context =
        CGBitmapContextCreate(
            data = null,
            width = widthPx.toULong(),
            height = heightPx.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = rowBytes.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
        )
    try {
        checkNotNull(context) { "Could not create a bitmap context." }
        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, widthPx.toDouble(), heightPx.toDouble()))
        CGContextScaleCTM(context, widthPx / pageSize.width.toDouble(), heightPx / pageSize.height.toDouble())
        // drawWithBox applies the page rotation and the crop box origin.
        page.drawWithBox(kPDFDisplayBoxCropBox, toContext = context)

        val data: CPointer<ByteVar> = checkNotNull(CGBitmapContextGetData(context)).reinterpret()
        val pixels = data.readBytes(rowBytes * heightPx)
        val bitmap = Bitmap()
        val info = ImageInfo(widthPx, heightPx, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        // The ByteArray overload copies into Skia-owned memory freed with the bitmap
        // (verified in Skiko 0.150.1 Bitmap.cc), so the bitmap never references the CG buffer.
        check(bitmap.installPixels(info, pixels, rowBytes)) { "Could not copy the pixels." }
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    } finally {
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
    }
}
