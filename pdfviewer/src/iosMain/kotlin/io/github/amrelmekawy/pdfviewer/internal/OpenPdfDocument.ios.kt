package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.PDFKit.PDFDocument

internal actual suspend fun openPdfDocument(
    source: PdfSource,
    cacheDir: CacheDir,
    bypassCache: Boolean,
): PdfDocument =
    openOn(Dispatchers.Default) {
        when (source) {
            is PdfSource.File -> {
                if (!NSFileManager.defaultManager.isReadableFileAtPath(source.path)) throw PdfException(PdfError.NotFound)
                IosPdfDocument.from(pdfDocument(NSURL.fileURLWithPath(source.path)))
            }
            is PdfSource.Bytes -> IosPdfDocument.from(pdfDocument(source.bytes.toNSData()))
            is PdfSource.Remote -> {
                val path = downloadPdf(source, cacheDir, bypassCache)
                IosPdfDocument.from(pdfDocument(NSURL.fileURLWithPath(path)))
            }
        }
    }

@Composable
internal actual fun rememberCacheDir(): CacheDir =
    remember {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        CacheDir(caches)
    }

// The PDFDocument(uRL/data) constructors are typed non-null and throw a NullPointerException when
// PDFKit returns nil for an invalid document. The nullable initializers are the nil-safe path.
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
private fun pdfDocument(url: NSURL): PDFDocument? = PDFDocument.alloc()?.initWithURL(url)

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
private fun pdfDocument(data: NSData): PDFDocument? = PDFDocument.alloc()?.initWithData(data)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }
