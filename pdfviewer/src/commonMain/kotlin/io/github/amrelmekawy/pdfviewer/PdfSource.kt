package io.github.amrelmekawy.pdfviewer

/**
 * Where a PDF document comes from.
 *
 * Pass a source to [rememberPdfViewerState]. When the source changes (by `equals`),
 * the old document is closed and the new one is opened.
 */
public sealed interface PdfSource {
    /**
     * A PDF file on the device, given by its absolute [path].
     *
     * The library only reads the file; it never modifies or deletes it.
     * Compared by value, so building it inline during recomposition does not reopen the document.
     */
    public data class File(
        val path: String,
    ) : PdfSource

    /**
     * A PDF held in memory.
     *
     * Compared by **identity**, not by content: pass the same [Bytes] instance (or remember it)
     * to keep the document open across recompositions. The library never modifies [bytes],
     * and the caller must not modify them while the document is shown.
     */
    public class Bytes(
        public val bytes: ByteArray,
    ) : PdfSource {
        override fun toString(): String = "Bytes(size=${bytes.size})"
    }

    /**
     * A PDF downloaded over HTTP(S) from [url], sending [headers] with the request.
     *
     * The download is cached on disk per URL and header set, so reopening the same source
     * (for example after rotation) does not download it again. [PdfViewerState.reload]
     * ignores the cached copy and downloads the document again.
     *
     * Compared by value. [toString] omits header values, which often hold credentials.
     */
    public data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : PdfSource {
        override fun toString(): String = "Remote(url=$url, headers=${headers.keys})"
    }
}
