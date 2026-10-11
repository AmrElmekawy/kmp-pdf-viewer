package io.github.amrelmekawy.pdfviewer.internal

import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads [source] into the cache and returns the complete file. Reuses a cached file
 * unless [bypassCache]. Streams into a `.part` file that is renamed only on success.
 */
internal suspend fun downloadPdf(
    source: PdfSource.Remote,
    cacheDir: CacheDir,
    bypassCache: Boolean,
): File =
    withContext(Dispatchers.IO) {
        val target = File(cacheDir.remoteFile(source))
        if (bypassCache) {
            target.delete()
        } else if (target.isFile) {
            return@withContext target
        }
        val dir = target.parentFile ?: throw PdfException(PdfError.Unknown)
        dir.mkdirs()

        val part = File.createTempFile(target.nameWithoutExtension, ".part", dir)
        try {
            val connection = openConnection(source)
            // Closing the connection is the only way to interrupt a blocking read, so a watcher
            // (on another IO thread) disconnects as soon as this coroutine is cancelled.
            val watcher =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        connection.disconnect()
                    }
                }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw PdfException(PdfError.Network(code))
                checkDownloadSize(connection.contentLengthLong)
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            checkDownloadSize(total)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (e: IOException) {
                ensureActive()
                throw PdfException(PdfError.Network(null), e)
            } finally {
                watcher.cancel()
                connection.disconnect()
            }
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) throw PdfException(PdfError.Unknown)
            }
            target
        } finally {
            part.delete()
        }
    }

private fun openConnection(source: PdfSource.Remote): HttpURLConnection {
    val url =
        try {
            URL(source.url)
        } catch (e: IOException) {
            throw PdfException(PdfError.Network(null), e)
        }
    if (url.protocol != "https" && url.protocol != "http") throw PdfException(PdfError.Network(null))
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = PdfRenderLimits.CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = PdfRenderLimits.READ_TIMEOUT_MILLIS
    source.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
    return connection
}
