package io.github.amrelmekawy.pdfviewer.internal

import io.github.amrelmekawy.pdfviewer.PdfError
import io.github.amrelmekawy.pdfviewer.PdfSource

/**
 * The cache key of a remote source: a hash of the URL and every header, so different
 * credentials never share a cached file. Neither the URL nor a header appears in the key.
 */
internal fun remoteCacheKey(source: PdfSource.Remote): String {
    val canonical =
        buildString {
            append(source.url).append('\n')
            source.headers.entries
                .map { (name, value) -> name.lowercase() to value }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .forEach { (name, value) -> append(name).append(':').append(value).append('\n') }
        }
    return sha256Hex(canonical.encodeToByteArray())
}

/** The directory, inside the app's cache directory, that holds the library's files. */
internal fun CacheDir.libraryDir(): String = "$path/pdfviewer"

/** Where the complete download of [source] is cached. */
internal fun CacheDir.remoteFile(source: PdfSource.Remote): String = "${libraryDir()}/${remoteCacheKey(source)}.pdf"

/** Fails with [PdfError.TooLarge] once [byteCount] (announced or streamed) exceeds the download limit. */
internal fun checkDownloadSize(byteCount: Long) {
    if (byteCount > PdfRenderLimits.MAX_DOWNLOAD_BYTES) throw PdfException(PdfError.TooLarge)
}
