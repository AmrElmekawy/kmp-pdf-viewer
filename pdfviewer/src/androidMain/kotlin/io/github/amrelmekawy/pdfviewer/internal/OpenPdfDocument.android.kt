package io.github.amrelmekawy.pdfviewer.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.coroutines.Dispatchers
import java.io.File

internal actual suspend fun openPdfDocument(
    source: PdfSource,
    cacheDir: CacheDir,
    bypassCache: Boolean,
): PdfDocument =
    openOn(Dispatchers.IO) {
        when (source) {
            is PdfSource.File -> {
                val file = File(source.path)
                if (!file.isFile) throw PdfException(PdfError.NotFound)
                AndroidPdfDocument.open(file)
            }
            // PdfRenderer needs a seekable file descriptor, so the bytes go to a temporary file.
            is PdfSource.Bytes -> {
                val dir = File(cacheDir.libraryDir()).apply { mkdirs() }
                val temp = File.createTempFile("bytes-", ".pdf", dir)
                try {
                    temp.writeBytes(source.bytes)
                } catch (e: Exception) {
                    temp.delete()
                    throw e
                }
                AndroidPdfDocument.open(temp, ownedFile = temp)
            }
            is PdfSource.Remote -> AndroidPdfDocument.open(downloadPdf(source, cacheDir, bypassCache))
        }
    }

@Composable
internal actual fun rememberCacheDir(): CacheDir {
    val context = LocalContext.current.applicationContext
    return remember(context) { CacheDir(context.cacheDir.absolutePath) }
}
