package io.github.amrelmekawy.pdfviewer.internal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.amrelmekawy.pdfviewer.PdfError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * A document rendered by the framework [PdfRenderer].
 *
 * [PdfRenderer] allows one open page at a time and is not thread-safe, so every call, and
 * [close], runs under [lock] on [Dispatchers.IO].
 *
 * @param ownedFile a temporary file created by the library, deleted on close.
 */
internal class AndroidPdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val pageSizes: List<Size>,
    private val ownedFile: File?,
) : PdfDocument {
    private val lock = Mutex()
    private var closed = false

    override val pageCount: Int get() = pageSizes.size

    override fun pageSize(index: Int): Size = pageSizes[index]

    override suspend fun renderPage(
        index: Int,
        widthPx: Int,
    ): ImageBitmap =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (closed) throw CancellationException("The document is closed.")
                val page = renderer.openPage(index)
                try {
                    val bitmap = Bitmap.createBitmap(widthPx, bitmapHeightFor(pageSizes[index], widthPx), Bitmap.Config.ARGB_8888)
                    // Rendered pages are transparent where the PDF draws nothing.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                } finally {
                    page.close()
                }
            }
        }

    override suspend fun close() {
        withContext(NonCancellable + Dispatchers.IO) {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                renderer.close()
                descriptor.close()
                ownedFile?.delete()
            }
        }
    }

    companion object {
        /** Opens [file]; blocking, so call it on [Dispatchers.IO]. */
        fun open(
            file: File,
            ownedFile: File? = null,
        ): AndroidPdfDocument {
            val descriptor =
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: FileNotFoundException) {
                    ownedFile?.delete()
                    throw PdfException(PdfError.NotFound, e)
                }
            var renderer: PdfRenderer? = null
            try {
                val opened = PdfRenderer(descriptor)
                renderer = opened
                val sizes =
                    List(opened.pageCount) { index ->
                        opened.openPage(index).use { Size(it.width.toFloat(), it.height.toFloat()) }
                    }
                return AndroidPdfDocument(descriptor, opened, sizes, ownedFile)
            } catch (e: Exception) {
                renderer?.close()
                descriptor.close()
                ownedFile?.delete()
                throw when (e) {
                    is SecurityException -> PdfException(PdfError.PasswordProtected, e)
                    is IOException -> PdfException(PdfError.InvalidDocument, e)
                    else -> e
                }
            }
        }
    }
}
